package xyz.lilsus.raylsuite.feature.paymenthub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import platform.Foundation.NSBundle
import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import xyz.lilsus.raylsuite.core.hubapi.HubServiceMoney
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOffer
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.ui.format.currentAmountFormatter
import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubGridSpan
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.gridRowCount
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.packHubGrid
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController

/** Localized values and explicit intents for the native SwiftUI widget hub. */
class NativePaymentHubController(
    repository: PaymentHubRepository,
    host: PaymentHubController,
    languageChanges: Flow<*>,
    currencyCodes: Flow<String>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var currencyCode = CurrencyCatalog.DEFAULT_CODE
    private var canvasColumns = 2
    private val remote = createHubRemoteSession()
    private val viewModel = WidgetHubViewModel(
        repository = repository,
        host = host,
        defaultCurrencyCode = { currencyCode },
        locale = { NSBundle.mainBundle.preferredLocalizations.firstOrNull()?.toString() ?: "en" },
        catalog = remote.catalog,
        orderStore = remote.orderStore
    )
    private val snapshot = MutableStateFlow<NativePaymentHubSnapshot?>(null)

    init {
        scope.launch { viewModel.state.collect { publish() } }
        scope.launch {
            languageChanges.collect {
                publish()
                viewModel.refreshCatalog()
            }
        }
        scope.launch { currencyCodes.collect { currencyCode = it } }
    }

    fun observe(onChange: (NativePaymentHubSnapshot) -> Unit): () -> Unit {
        val job = scope.launch { snapshot.filterNotNull().collect(onChange) }
        return { job.cancel() }
    }

    fun openGallery() = viewModel.openGallery()

    fun back() {
        viewModel.back()
    }

    fun close() = viewModel.close()

    fun selectDefinition(id: String) = viewModel.selectDefinition(id)

    fun selectVariant(id: String) = viewModel.selectVariant(id)

    fun configureSelected() = viewModel.configureSelected()

    fun updateQuery(value: String) = viewModel.updateQuery(value)

    fun toggleContact(id: String) = viewModel.toggleContact(id)

    fun moveContact(id: String, offset: Int) = viewModel.moveContact(id, offset)

    fun updateTitle(value: String) = viewModel.updateTitle(value)

    fun updateAmount(value: String) = viewModel.updateAmount(value)

    fun selectCurrency(code: String) = viewModel.selectCurrency(code)

    fun updateComment(value: String) = viewModel.updateComment(value)

    fun updateConfiguration(key: String, value: String) = viewModel.updateConfiguration(key, value)

    fun addContact(title: String, address: String) = viewModel.addContact(title, address)

    fun deleteContact(id: String) = viewModel.deleteContact(id)

    fun saveWidget() = viewModel.saveWidget()

    fun editWidget(id: String) = viewModel.editWidget(id)

    fun removeWidget(id: String) = viewModel.removeWidget(id)

    fun moveWidget(id: String, onto: String) = viewModel.moveWidget(id, onto)

    fun moveWidgetBy(id: String, offset: Int) = viewModel.moveWidgetBy(id, offset)

    fun setArranging(value: Boolean) = viewModel.setArranging(value)

    fun setActive(value: Boolean) = viewModel.setActive(value)

    fun pay(actionId: String) = viewModel.pay(actionId)

    fun refreshCatalog() = viewModel.refreshCatalog()

    fun refreshContent() = viewModel.refreshContent()

    fun openService(widgetId: String, offerId: String?) = viewModel.openService(widgetId, offerId)

    fun closePurchase() = viewModel.closePurchase()

    fun updateServicePhone(value: String) = viewModel.updateServicePhone(value)

    fun selectServiceOffer(id: String) = viewModel.selectServiceOffer(id)

    fun updateServiceAmount(value: String) = viewModel.updateServiceAmount(value)

    fun prepareServiceOrder() = viewModel.prepareServiceOrder()

    fun payServiceOrder() = viewModel.payServiceOrder()

    fun refreshServiceOrder() = viewModel.refreshServiceOrder()

    fun openPendingServiceOrder() = viewModel.openPendingServiceOrder()

    fun completeServicePaymentHandoff() = viewModel.completeServicePaymentHandoff()

    fun resizeWidget(id: String, variantId: String) {
        viewModel.editWidget(id)
        if (viewModel.state.value.editor?.existingWidgetId != id) return
        viewModel.selectVariant(variantId)
        viewModel.configureSelected()
    }

    fun setCanvasColumns(value: Int) {
        val columns = if (value >= 4) 4 else 2
        if (columns == canvasColumns) return
        canvasColumns = columns
        publish()
    }

    fun clear() {
        viewModel.clear()
        remote.close()
        scope.cancel()
    }

    private fun publish() {
        val state = viewModel.state.value
        val copy = loadCopy()
        val definitions = state.gallery.map { it.toNative(state.contacts) }
        val placements = packHubGrid(state.widgets, canvasColumns) {
            HubGridSpan(it.variant.columns, it.variant.rows)
        }
        snapshot.value = NativePaymentHubSnapshot(
            screen = state.screen.name.lowercase(),
            text = copy,
            canvas = NativeHubCanvas(
                tiles = placements.map { placement ->
                    placement.value.toNative(state.gallery).copy(
                        column = placement.column,
                        row = placement.row
                    )
                },
                columns = canvasColumns,
                rows = placements.gridRowCount(),
                arranging = state.arranging
            ),
            gallery = definitions,
            selectedDefinition = definitions.firstOrNull { it.id == state.editor?.definitionId },
            editor = state.editor?.let { editor ->
                val variant = state.selectedVariant ?: return@let null
                val query = state.query.trim()
                NativeHubEditor(
                    isEditing = editor.existingWidgetId != null,
                    kind = editor.kind.name.lowercase(),
                    variantId = editor.variantId,
                    title = editor.title,
                    amount = editor.amountInput,
                    currencyCode = editor.currencyCode,
                    currencyCodes = CurrencyCatalog.supportedCodes,
                    comment = editor.comment,
                    selectedContacts = editor.contactIds.mapNotNull { id ->
                        state.contacts.firstOrNull { it.id == id }?.toNative()
                    },
                    availableContacts = state.contacts.filter { contact ->
                        contact.id !in editor.contactIds && (
                            query.isEmpty() || contact.title.contains(query, ignoreCase = true) ||
                                contact.address.full.contains(query, ignoreCase = true)
                            )
                    }.map { it.toNative() },
                    selectionTitle = localized("hub_widget_choose_contacts", variant.capacity),
                    selectionCount = localized(
                        "hub_widget_selected_count",
                        editor.contactIds.size,
                        variant.capacity
                    ),
                    capacity = variant.capacity,
                    fields = state.selectedDefinition?.fields.orEmpty().map { field ->
                        NativeHubField(
                            key = field.key,
                            label = field.label,
                            type = field.type,
                            required = field.required,
                            options = field.options.map { NativeHubChoice(it.id, it.label) },
                            value = editor.configuration[field.key].orEmpty()
                        )
                    }
                )
            },
            query = state.query,
            contactsEmpty = state.contacts.isEmpty(),
            busy = state.busy,
            catalogLoading = state.catalogLoading,
            catalogUnavailable = state.catalogUnavailable,
            error = state.error?.let { error ->
                when (error) {
                    HubWidgetError.ContactNameRequired -> localized("hub_error_enter_title")

                    HubWidgetError.InvalidAddress -> localized("hub_error_invalid_address")

                    HubWidgetError.SelectContacts -> localized("hub_widget_select_contacts_error")

                    HubWidgetError.TooManyContacts -> localized(
                        "hub_widget_choose_contacts",
                        state.selectedVariant?.capacity ?: 1
                    )

                    HubWidgetError.InvalidAmount -> localized("hub_error_enter_amount")

                    HubWidgetError.RequiredConfiguration -> localized("hub_widget_required_fields")

                    HubWidgetError.Unavailable -> copy.unavailable

                    HubWidgetError.SaveFailed -> localized("hub_widget_error_save")
                }
            },
            contactSavedSerial = state.contactSavedSerial,
            purchase = state.purchase?.toNative(),
            hasServiceOrder = state.hasServiceOrder,
            servicePaymentReady = state.servicePaymentReady
        )
    }

    private fun HubWidgetDefinition.toNative(contacts: List<HubContact>): NativeHubDefinition =
        NativeHubDefinition(
            id = id,
            kind = kind.name.lowercase(),
            title = title ?: localized(kind.titleKey()),
            detail = description ?: localized(kind.descriptionKey()),
            symbol = kind.symbol(),
            variants = variants.map { variant ->
                NativeHubVariant(
                    id = variant.id,
                    title = variant.title ?: localized(variant.titleKey()),
                    detail = if (kind == HubWidgetKind.Metric || kind == HubWidgetKind.Service) {
                        description.orEmpty()
                    } else {
                        localized(variant.descriptionKey())
                    },
                    columns = variant.columns,
                    rows = variant.rows,
                    capacity = variant.capacity,
                    preview = NativeHubTile(
                        id = "preview:$id:${variant.id}",
                        kind = kind.name.lowercase(),
                        title = title ?: localized(kind.titleKey()),
                        variantId = variant.id,
                        column = 0,
                        row = 0,
                        columns = variant.columns,
                        rows = variant.rows,
                        people = if (kind == HubWidgetKind.Metric ||
                            kind == HubWidgetKind.Service
                        ) {
                            emptyList()
                        } else {
                            (0 until variant.capacity).map { index ->
                                val contact = contacts.getOrNull(index)
                                NativeHubPerson(
                                    id = "preview:$index",
                                    actionId = "",
                                    title =
                                        contact?.title
                                            ?: localized("hub_widget_contact_placeholder"),
                                    initials = contact?.title?.initials().orEmpty(),
                                    address = "",
                                    amount = if (kind == HubWidgetKind.Shortcut) {
                                        localized("hub_widget_shortcut_amount")
                                    } else {
                                        null
                                    }
                                )
                            }
                        },
                        sizes = emptyList(),
                        metric = null,
                        loading = false,
                        unavailable = false,
                        emptyText = "",
                        template = variant.template,
                        service = null,
                        servicePhone = ""
                    )
                )
            }
        )

    private fun HubWidgetTile.toNative(definitions: List<HubWidgetDefinition>): NativeHubTile =
        NativeHubTile(
            id = id,
            kind = kind.name.lowercase(),
            title = title?.takeIf { it.isNotBlank() }
                ?: definitions.firstOrNull { it.id == definitionId }?.title
                ?: localized(kind.titleKey()),
            variantId = variant.id,
            column = 0,
            row = 0,
            columns = variant.columns,
            rows = variant.rows,
            people = people.map { person ->
                NativeHubPerson(
                    id = person.actionId,
                    actionId = person.actionId,
                    title = person.title,
                    initials = person.title.initials(),
                    address = person.address,
                    amount = person.amount?.let { amount ->
                        currentAmountFormatter().format(
                            DisplayAmount(
                                amount.minor,
                                CurrencyCatalog.infoFor(amount.currencyCode).currency
                            )
                        )
                    }
                )
            },
            sizes = definitions.firstOrNull { it.id == definitionId }?.variants.orEmpty().map {
                NativeHubSizeChoice(it.id, it.title ?: localized(it.titleKey()))
            },
            metric = metric?.let { NativeHubMetric(it.value, it.unit, it.label, it.asOf) },
            loading = loading,
            unavailable = unavailable,
            emptyText = localized(
                when (kind) {
                    HubWidgetKind.Favorites -> "hub_widget_favorites_empty"
                    HubWidgetKind.Recents -> "hub_widget_recent_empty"
                    HubWidgetKind.Metric, HubWidgetKind.Service -> "hub_widget_unavailable"
                    else -> "hub_widget_contacts_empty"
                }
            ),
            template = variant.template,
            service = service?.let {
                NativeHubService(it.title, it.offers.map(HubServiceOffer::toNative))
            },
            servicePhone = servicePhone
        )
}

data class NativePaymentHubSnapshot(
    val screen: String,
    val text: NativePaymentHubCopy,
    val canvas: NativeHubCanvas,
    val gallery: List<NativeHubDefinition>,
    val selectedDefinition: NativeHubDefinition?,
    val editor: NativeHubEditor?,
    val query: String,
    val contactsEmpty: Boolean,
    val busy: Boolean,
    val catalogLoading: Boolean,
    val catalogUnavailable: Boolean,
    val error: String?,
    val contactSavedSerial: Int,
    val purchase: NativeHubServicePurchase?,
    val hasServiceOrder: Boolean,
    val servicePaymentReady: Boolean
)

data class NativeHubCanvas(
    val tiles: List<NativeHubTile>,
    val columns: Int,
    val rows: Int,
    val arranging: Boolean
)

data class NativeHubDefinition(
    val id: String,
    val kind: String,
    val title: String,
    val detail: String,
    val symbol: String,
    val variants: List<NativeHubVariant>
)

data class NativeHubVariant(
    val id: String,
    val title: String,
    val detail: String,
    val columns: Int,
    val rows: Int,
    val capacity: Int,
    val preview: NativeHubTile
)

data class NativeHubTile(
    val id: String,
    val kind: String,
    val title: String,
    val variantId: String,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int,
    val people: List<NativeHubPerson>,
    val sizes: List<NativeHubSizeChoice>,
    val metric: NativeHubMetric?,
    val loading: Boolean,
    val unavailable: Boolean,
    val emptyText: String,
    val template: String?,
    val service: NativeHubService?,
    val servicePhone: String
)

data class NativeHubSizeChoice(val id: String, val title: String)

data class NativeHubPerson(
    val id: String,
    val actionId: String,
    val title: String,
    val initials: String,
    val address: String,
    val amount: String?
)

data class NativeHubMetric(
    val value: String,
    val unit: String,
    val label: String,
    val asOf: String?
)

data class NativeHubContact(
    val id: String,
    val title: String,
    val address: String,
    val initials: String
)

data class NativeHubField(
    val key: String,
    val label: String,
    val type: String,
    val required: Boolean,
    val options: List<NativeHubChoice>,
    val value: String
)

data class NativeHubChoice(val id: String, val label: String)

data class NativeHubEditor(
    val isEditing: Boolean,
    val kind: String,
    val variantId: String,
    val title: String,
    val amount: String,
    val currencyCode: String,
    val currencyCodes: List<String>,
    val comment: String,
    val selectedContacts: List<NativeHubContact>,
    val availableContacts: List<NativeHubContact>,
    val selectionTitle: String,
    val selectionCount: String,
    val capacity: Int,
    val fields: List<NativeHubField>
)

data class NativePaymentHubCopy(
    val galleryTitle: String,
    val galleryBody: String,
    val addWidget: String,
    val editWidget: String,
    val widgetOptions: String,
    val chooseLayout: String,
    val continueTitle: String,
    val widgetName: String,
    val edit: String,
    val done: String,
    val back: String,
    val cancel: String,
    val save: String,
    val remove: String,
    val moveUp: String,
    val moveDown: String,
    val name: String,
    val address: String,
    val amount: String,
    val comment: String,
    val search: String,
    val addContact: String,
    val saveContact: String,
    val deleteContact: String,
    val deleteContactTitle: String,
    val deleteContactBody: String,
    val noContacts: String,
    val noMatches: String,
    val emptyTitle: String,
    val emptyBody: String,
    val loading: String,
    val unavailable: String,
    val retry: String,
    val catalogUnavailable: String,
    val contactsTitle: String,
    val automaticHint: String,
    val service: NativeHubServiceCopy
)

private fun loadCopy() = NativePaymentHubCopy(
    galleryTitle = localized("hub_widget_gallery_title"),
    galleryBody = localized("hub_widget_gallery_body"),
    addWidget = localized("hub_widget_add"),
    editWidget = localized("hub_widget_edit"),
    widgetOptions = localized("hub_widget_options"),
    chooseLayout = localized("hub_widget_select_variant"),
    continueTitle = localized("hub_widget_continue"),
    widgetName = localized("hub_widget_name"),
    edit = localized("hub_canvas_edit"),
    done = localized("hub_canvas_done"),
    back = localized("hub_new_back"),
    cancel = localized("hub_canvas_remove_cancel"),
    save = localized("hub_configure_save"),
    remove = localized("hub_canvas_remove_confirm"),
    moveUp = localized("hub_action_move_up"),
    moveDown = localized("hub_action_move_down"),
    name = localized("hub_target_name_label"),
    address = localized("hub_target_address_label"),
    amount = localized("hub_widget_shortcut_amount"),
    comment = localized("hub_target_comment_label"),
    search = localized("hub_new_search"),
    addContact = localized("hub_widget_add_contact"),
    saveContact = localized("hub_widget_save_contact"),
    deleteContact = localized("hub_contact_delete"),
    deleteContactTitle = localized("hub_contact_delete_title"),
    deleteContactBody = localized("hub_contact_delete_body"),
    noContacts = localized("hub_new_no_contacts"),
    noMatches = localized("hub_new_no_matches"),
    emptyTitle = localized("hub_widget_empty_title"),
    emptyBody = localized("hub_widget_empty_body"),
    loading = localized("hub_widget_loading"),
    unavailable = localized("hub_widget_unavailable"),
    retry = localized("hub_widget_retry"),
    catalogUnavailable = localized("hub_widget_catalog_unavailable"),
    contactsTitle = localized("hub_widget_contacts"),
    automaticHint = localized("hub_widget_recents_hint"),
    service = loadServiceCopy()
)

private fun HubContact.toNative() = NativeHubContact(id, title, address.full, title.initials())

private fun String.initials(): String = trim().split(Regex("\\s+"))
    .filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }

private fun HubWidgetKind.titleKey(): String =
    if (this == HubWidgetKind.Service) "hub_service_title" else "hub_widget_${name.lowercase()}"

private fun HubWidgetKind.descriptionKey(): String = when (this) {
    HubWidgetKind.Metric -> "hub_widget_metric"
    HubWidgetKind.Service -> "hub_service_body"
    else -> "hub_widget_${name.lowercase()}_body"
}

private fun HubWidgetKind.symbol(): String = when (this) {
    HubWidgetKind.Contacts -> "person.crop.circle"
    HubWidgetKind.Shortcut -> "bolt.fill"
    HubWidgetKind.Favorites -> "star.fill"
    HubWidgetKind.Recents -> "clock.fill"
    HubWidgetKind.Metric -> "chart.line.uptrend.xyaxis"
    HubWidgetKind.Service -> "cellularbars"
}

private fun HubWidgetVariant.titleKey(): String = when (id) {
    "single" -> "hub_widget_single"
    "row" -> "hub_widget_row"
    "card" -> "hub_widget_card"
    else -> "hub_widget_metric"
}

private fun HubWidgetVariant.descriptionKey(): String = when (id) {
    "single" -> "hub_widget_single_body"
    "row" -> "hub_widget_row_body"
    "card" -> "hub_widget_card_body"
    else -> "hub_widget_metric"
}

private fun resource(key: String) = NativeStringResource("PaymentHub", key)

private fun localized(key: String): String = nativeString(resource(key))

private fun localized(key: String, value: Int): String = nativeString(resource(key), value)

private fun localized(key: String, first: Int, second: Int): String =
    nativeString(resource(key), first, second)

data class NativeHubService(val title: String, val offers: List<NativeHubServiceOffer>)

data class NativeHubServiceOffer(
    val id: String,
    val title: String,
    val detail: String?,
    val kind: String,
    val amountText: String?,
    val rangeText: String?,
    val currencyCode: String?,
    val requiresAmount: Boolean
)

data class NativeHubServicePurchase(
    val title: String,
    val phone: String,
    val offers: List<NativeHubServiceOffer>,
    val selectedOfferId: String?,
    val amount: String,
    val amountLabel: String,
    val selectedOffer: NativeHubServiceOffer?,
    val busy: Boolean,
    val error: String?,
    val order: NativeHubServiceOrder?,
    val canPay: Boolean
)

/** Display-only order values. Invoice and recovery credential never cross into Swift. */
data class NativeHubServiceOrder(
    val id: String,
    val title: String,
    val item: String,
    val phone: String,
    val amountText: String?,
    val lightningPrice: String?,
    val expiresAt: String?,
    val state: String,
    val status: String,
    val paymentStatus: String,
    val fulfillmentStatus: String,
    val unconfirmed: Boolean
)

data class NativeHubServiceCopy(
    val title: String,
    val topup: String,
    val packages: String,
    val topupBody: String,
    val packagesBody: String,
    val phone: String,
    val chooseOffer: String,
    val review: String,
    val pay: String,
    val checkStatus: String,
    val orderBanner: String,
    val recipient: String,
    val item: String,
    val lightningPrice: String,
    val quoteExpires: String,
    val orderStatus: String,
    val paymentStatus: String,
    val fulfillmentStatus: String,
    val orderReference: String,
    val unknownHint: String,
    val paymentHint: String
)

private fun loadServiceCopy() = NativeHubServiceCopy(
    title = localized("hub_service_title"),
    topup = localized("hub_service_topup"),
    packages = localized("hub_service_packages"),
    topupBody = localized("hub_service_topup_body"),
    packagesBody = localized("hub_service_packages_body"),
    phone = localized("hub_service_phone"),
    chooseOffer = localized("hub_service_choose_offer"),
    review = localized("hub_service_review"),
    pay = localized("hub_service_pay"),
    checkStatus = localized("hub_service_check_status"),
    orderBanner = localized("hub_service_order_banner"),
    recipient = localized("hub_service_recipient"),
    item = localized("hub_service_item"),
    lightningPrice = localized("hub_service_lightning_price"),
    quoteExpires = localized("hub_service_quote_expires"),
    orderStatus = localized("hub_service_order_status"),
    paymentStatus = localized("hub_service_payment_status"),
    fulfillmentStatus = localized("hub_service_fulfillment_status"),
    orderReference = localized("hub_service_order_reference"),
    unknownHint = localized("hub_service_unknown_hint"),
    paymentHint = localized("hub_service_payment_hint")
)

private fun HubServiceOffer.toNative() = NativeHubServiceOffer(
    id = id,
    title = title,
    detail = description,
    kind = kind,
    amountText = amount?.displayText(),
    rangeText = range?.let {
        localized("hub_service_amount_range")
            .replace("%1\$@", "${decimalText(it.minMinor, it.fractionDigits)} ${it.currency}")
            .replace("%2\$@", "${decimalText(it.maxMinor, it.fractionDigits)} ${it.currency}")
            .replace("%3\$@", "${decimalText(it.stepMinor, it.fractionDigits)} ${it.currency}")
    },
    currencyCode = range?.currency ?: amount?.currency,
    requiresAmount = range != null
)

private fun HubServicePurchaseState.toNative() = NativeHubServicePurchase(
    title = title,
    phone = phone,
    offers = offers.map(HubServiceOffer::toNative),
    selectedOfferId = selectedOfferId,
    amount = amountInput,
    amountLabel = nativeString(
        resource("hub_service_amount"),
        selectedOffer?.range?.currency ?: selectedOffer?.amount?.currency.orEmpty()
    ),
    selectedOffer = selectedOffer?.toNative(),
    busy = busy,
    error = error?.let {
        localized(
            when (it) {
                HubServiceError.InvalidPhone -> "hub_service_invalid_phone"
                HubServiceError.InvalidAmount -> "hub_service_invalid_amount"
                HubServiceError.SelectOffer -> "hub_service_select_offer"
                HubServiceError.Unavailable -> "hub_service_unavailable"
                HubServiceError.Changed -> "hub_service_changed"
                HubServiceError.SaveFailed -> "hub_service_save_failed"
                HubServiceError.InvalidInvoice -> "hub_service_invalid_invoice"
            }
        )
    },
    order = order?.let {
        NativeHubServiceOrder(
            id = it.orderId,
            title = it.serviceTitle,
            item = it.itemTitle,
            phone = it.phone,
            amountText = it.requestedAmount?.displayText(),
            lightningPrice = it.payment?.let { payment ->
                "${decimalText(payment.amountMsat, 3)} sat"
            },
            expiresAt = it.payment?.expiresAt,
            state = it.state,
            status = serviceStatus(it.state),
            paymentStatus = serviceStatus(it.paymentStatus),
            fulfillmentStatus = serviceStatus(it.fulfillmentStatus),
            unconfirmed = "unknown" in listOf(it.state, it.paymentStatus, it.fulfillmentStatus)
        )
    },
    canPay = canPay
)

private fun serviceStatus(value: String): String = localized(
    "hub_service_status_${value.takeIf {
        it in setOf(
            "preparing", "awaiting_payment", "processing", "delivered", "expired", "failed",
            "unknown", "unpaid", "pending", "paid"
        )
    } ?: "unknown"}"
)

private fun HubServiceMoney.displayText(): String =
    "${decimalText(minor, fractionDigits)} $currency"

private fun decimalText(minor: String, fractionDigits: Int): String {
    val number = NSDecimalNumber(string = minor)
        .decimalNumberByMultiplyingByPowerOf10((-fractionDigits).toShort())
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        minimumFractionDigits = 0u
        maximumFractionDigits = fractionDigits.toULong()
        usesGroupingSeparator = true
    }
    return formatter.stringFromNumber(number) ?: number.stringValue
}
