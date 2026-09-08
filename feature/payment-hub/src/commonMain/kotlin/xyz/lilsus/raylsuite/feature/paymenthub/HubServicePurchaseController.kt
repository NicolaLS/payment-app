package xyz.lilsus.raylsuite.feature.paymenthub

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.core.hubapi.HubServiceContent
import xyz.lilsus.raylsuite.core.hubapi.HubServiceMoney
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrder
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrderRequest
import xyz.lilsus.raylsuite.core.payment.LightningInputParser
import xyz.lilsus.raylsuite.feature.paymenthub.create.cleanAmountInput
import xyz.lilsus.raylsuite.feature.paymenthub.create.hasFractionForWholeCurrency
import xyz.lilsus.raylsuite.feature.paymenthub.create.parseMinorAmount
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.integration.hub.HubServiceOrderResult
import xyz.lilsus.raylsuite.integration.hub.HubWidgetContentResult
import xyz.lilsus.raylsuite.integration.hub.HubWidgetUnavailableReason
import xyz.lilsus.raylsuite.integration.hub.KtorHubWidgetCatalogClient

internal data class HubServiceSessionState(
    val purchase: HubServicePurchaseState? = null,
    val hasOrder: Boolean = false,
    val paymentReady: Boolean = false
)

/** Owns anonymous order recovery and quote validation, never wallet execution or retry policy. */
internal class HubServicePurchaseController(
    private val client: KtorHubWidgetCatalogClient?,
    private val store: HubServiceOrderStore?,
    private val host: PaymentHubController,
    private val locale: () -> String,
    private val scope: CoroutineScope
) {
    private var storageFailed = false
    private var saved = try {
        store?.load()
    } catch (_: Exception) {
        storageFailed = true
        null
    }
    private val mutableState = MutableStateFlow(HubServiceSessionState(hasOrder = saved != null))
    val state = mutableState.asStateFlow()
    private var content: HubServiceContent? = null
    private var variantId: String? = null
    private var definitionId: String? = null
    private var active = true
    private var job: Job? = null
    private var refreshJob: Job? = null
    private var pendingInvoice: String? = null
    private val parser = LightningInputParser()

    fun open(widget: HubWidget, service: HubServiceContent, offerId: String?) {
        if (saved?.latest?.terminal != true && saved != null) {
            openSaved()
            return
        }
        if (job?.isActive == true) return
        content = service
        variantId = widget.variant.id
        definitionId = widget.definitionId
        val kind = if (widget.variant.template == "service-topup") "topup" else "package"
        val offers = service.offers.filter { it.kind == kind }
        val selected = offers.firstOrNull { it.id == offerId } ?: offers.firstOrNull()
        mutableState.update {
            it.copy(
                purchase = HubServicePurchaseState(
                    widget.id,
                    widget.title ?: service.title,
                    widget.configuration["phone"].orEmpty(),
                    offers,
                    selected?.id
                )
            )
        }
    }

    fun openSaved() {
        val order = saved ?: return
        mutableState.update {
            it.copy(
                purchase = HubServicePurchaseState(
                    order.widgetId,
                    order.title,
                    order.request.phone,
                    order = order.latest
                )
            )
        }
        refresh()
    }

    fun close() {
        mutableState.update { it.copy(purchase = null) }
    }

    fun updatePhone(value: String) = edit { it.copy(phone = value.take(32)) }
    fun selectOffer(id: String) = edit {
        if (it.offers.any { offer -> offer.id == id }) {
            it.copy(selectedOfferId = id, amountInput = "")
        } else {
            it
        }
    }
    fun updateAmount(value: String) = edit {
        val digits = it.selectedOffer?.range?.fractionDigits ?: 2
        it.copy(
            amountInput = if (value.hasFractionForWholeCurrency(
                    digits
                )
            ) {
                value
            } else {
                value.cleanAmountInput(digits)
            }
        )
    }

    fun prepare() {
        val purchase = mutableState.value.purchase ?: return
        val service = content ?: return
        val offer = purchase.selectedOffer ?: return fail(HubServiceError.SelectOffer)
        if (saved != null && saved?.latest?.terminal != true) return openSaved()
        if (storageFailed || store == null) return fail(HubServiceError.SaveFailed)
        val raw = purchase.phone.filter { it.isDigit() || it == '+' }
        val phone = if (raw.startsWith('+')) raw else service.callingCode + raw
        if (!phone.matches(Regex("\\+[1-9][0-9]{6,14}")) ||
            !phone.startsWith(service.callingCode)
        ) {
            return fail(HubServiceError.InvalidPhone)
        }
        val minor = offer.range?.let { range ->
            if (purchase.amountInput.hasFractionForWholeCurrency(range.fractionDigits)) {
                return fail(HubServiceError.InvalidAmount)
            }
            val amount = purchase.amountInput.parseMinorAmount(range.fractionDigits)
            val min = range.minMinor.toLong()
            val max = range.maxMinor.toLong()
            val step = range.stepMinor.toLong()
            if (amount == null || amount !in min..max || (amount - min) % step != 0L) {
                return fail(HubServiceError.InvalidAmount)
            }
            amount.toString()
        }
        val request = HubServiceOrderRequest(
            definitionId ?: return,
            variantId ?: return,
            service.revision,
            offer.id,
            phone,
            minor
        )
        perform {
            val credentials = newHubOrderCredentials()
            val order = StoredHubServiceOrder(
                credentials.id,
                credentials.token,
                purchase.widgetId,
                purchase.title,
                request,
                expectedAmount = offer.range?.let {
                    HubServiceMoney(checkNotNull(minor), it.currency, it.fractionDigits)
                } ?: offer.amount
            )
            // Durable before any network mutation, including a response lost during app shutdown.
            store.save(order)
            saved = order
            mutableState.update {
                it.copy(
                    hasOrder = true,
                    purchase = it.purchase?.copy(
                        phone = phone,
                        offers = emptyList(),
                        selectedOfferId = null
                    )
                )
            }
            accept(client?.prepareOrder(order.id, order.token, request, locale()), order, purchase)
        }
    }

    fun refresh() {
        val order = saved ?: return
        perform {
            val result = client?.fetchOrder(order.id, order.token, locale())
            if (result is HubServiceOrderResult.Unavailable && result.code == "order_not_found" &&
                order.latest == null
            ) {
                // Reconfirm persistence before resubmission, including a prior failed disk write.
                checkNotNull(store).save(order)
                accept(client.prepareOrder(order.id, order.token, order.request, locale()), order)
            } else {
                accept(result, order)
            }
        }
    }

    fun pay() {
        val order = saved ?: return
        val reviewed = mutableState.value.purchase?.order ?: return
        if (mutableState.value.purchase?.canPay != true) return
        perform {
            val result = client?.fetchOrder(order.id, order.token, locale())
            val latest = accept(result, order) ?: return@perform
            if (latest.payment != reviewed.payment ||
                latest.requestedAmount != reviewed.requestedAmount ||
                latest.itemTitle != reviewed.itemTitle
            ) {
                return@perform fail(HubServiceError.Changed)
            }
            if (!canPay(latest)) return@perform fail(HubServiceError.InvalidInvoice)
            pendingInvoice = latest.payment?.invoice
            mutableState.update { it.copy(purchase = null, paymentReady = true) }
        }
    }

    /** Called after the native service sheet has closed, before provider presentation begins. */
    fun completePaymentHandoff() {
        val invoice = pendingInvoice ?: return
        pendingInvoice = null
        mutableState.update { it.copy(paymentReady = false) }
        val payment = saved?.latest?.payment
        if (!active || payment?.invoice != invoice ||
            Instant.parse(payment.expiresAt).toEpochMilliseconds() <= platformCurrentTimeMillis()
        ) {
            openSaved()
            return
        }
        host.payServiceInvoice(invoice)
    }

    fun setActive(value: Boolean) {
        active = value
        refreshJob?.cancel()
        // An in-flight PUT is never replaced because the app moved to the background.
        if (value && saved != null && job?.isActive != true) refresh()
    }

    private suspend fun accept(
        result: HubServiceOrderResult?,
        original: StoredHubServiceOrder,
        draft: HubServicePurchaseState? = null
    ): HubServiceOrder? {
        if (result !is HubServiceOrderResult.Available) {
            val rejection = result as? HubServiceOrderResult.Unavailable
            val reason = rejection?.reason
            if (original.latest == null && rejection?.code in setOf(
                    "invalid_request",
                    "invalid_phone",
                    "invalid_amount",
                    "invalid_offer",
                    "catalog_changed",
                    "offer_unavailable",
                    "service_unavailable"
                )
            ) {
                store?.remove()
                saved = null
                mutableState.update { it.copy(hasOrder = false, purchase = draft) }
                if (draft != null && reason == HubWidgetUnavailableReason.Conflict) {
                    refreshChangedDraft(original, draft)
                }
            }
            fail(
                if (reason ==
                    HubWidgetUnavailableReason.Conflict
                ) {
                    HubServiceError.Changed
                } else {
                    HubServiceError.Unavailable
                }
            )
            return null
        }
        val order = result.order
        // Bind a recovered quote to the recipient selected before the request was sent.
        if (order.phone != original.request.phone ||
            (original.expectedAmount != null && order.requestedAmount != original.expectedAmount)
        ) {
            fail(HubServiceError.InvalidInvoice)
            return null
        }
        val updated = original.copy(latest = order)
        store?.save(updated) ?: return null
        saved = updated
        val payable = canPay(order)
        mutableState.update {
            it.copy(
                hasOrder = true,
                purchase = it.purchase?.copy(
                    order = order,
                    canPay = payable,
                    error = null
                )
            )
        }
        return order
    }

    private suspend fun refreshChangedDraft(
        original: StoredHubServiceOrder,
        draft: HubServicePurchaseState
    ) {
        val result = client?.fetchContent(
            original.request.widgetId,
            original.request.variantId,
            mapOf("phone" to original.request.phone),
            locale()
        ) as? HubWidgetContentResult.Available ?: return
        val fresh = result.content.service ?: return
        content = fresh
        val previousKind = draft.selectedOffer?.kind
        val offers = fresh.offers.filter { previousKind == null || it.kind == previousKind }
        mutableState.update {
            it.copy(
                purchase = draft.copy(
                    offers = offers,
                    selectedOfferId =
                        offers.firstOrNull { offer -> offer.id == draft.selectedOfferId }?.id
                            ?: offers.firstOrNull()?.id,
                    amountInput = ""
                )
            )
        }
    }

    private suspend fun canPay(order: HubServiceOrder): Boolean {
        if (order.state != "awaiting_payment" || order.paymentStatus != "unpaid") return false
        val payment = order.payment ?: return false
        if (Instant.parse(payment.expiresAt).toEpochMilliseconds() <=
            platformCurrentTimeMillis()
        ) {
            return false
        }
        if (!payment.invoice.startsWith("lnbc", ignoreCase = true) ||
            payment.invoice.startsWith("lnbcrt", ignoreCase = true)
        ) {
            return false
        }
        val parsed =
            parser.parse(payment.invoice) as? LightningInputParser.ParseResult.Success
                ?: return false
        val invoice =
            (parsed.target as? LightningInputParser.Target.Bolt11)?.invoice ?: return false
        return invoice.amount?.msat == payment.amountMsat.toLongOrNull() &&
            !invoice.isExpired(platformCurrentTimeMillis() / 1000)
    }

    private fun edit(transform: (HubServicePurchaseState) -> HubServicePurchaseState) {
        val purchase = mutableState.value.purchase ?: return
        if (purchase.busy || purchase.order != null || purchase.offers.isEmpty()) return
        mutableState.update { it.copy(purchase = transform(purchase).copy(error = null)) }
    }

    private fun fail(error: HubServiceError) {
        mutableState.update { it.copy(purchase = it.purchase?.copy(error = error, canPay = false)) }
    }

    private fun perform(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        refreshJob?.cancel()
        mutableState.update { it.copy(purchase = it.purchase?.copy(busy = true, error = null)) }
        job = scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                fail(HubServiceError.SaveFailed)
            } finally {
                mutableState.update { it.copy(purchase = it.purchase?.copy(busy = false)) }
                scheduleRefresh()
            }
        }
    }

    private fun scheduleRefresh() {
        if (!active || saved == null || saved?.latest?.terminal == true) return
        refreshJob = scope.launch {
            delay(10_000)
            refresh()
        }
    }
}

private val HubServiceOrder.terminal: Boolean get() = state in
    setOf("delivered", "expired", "failed")
