package xyz.lilsus.blip.feature.payment

import com.russhwolf.settings.Settings
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.currentTimestampSeconds
import fr.acinq.lightning.utils.msat
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.blip.integration.blink.BlinkApiError
import xyz.lilsus.blip.integration.blink.BlinkApiException
import xyz.lilsus.blip.integration.blink.BlinkConnectionException
import xyz.lilsus.blip.integration.blink.BlinkFundingWallet
import xyz.lilsus.blip.integration.blink.BlinkPaymentAmount
import xyz.lilsus.blip.integration.blink.BlinkPaymentOutcome
import xyz.lilsus.blip.integration.blink.BlinkPaymentRequest
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.blip.integration.blink.BlinkWalletCurrency
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.CurrencyInfo
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.convertMsatsToDisplayAmount
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.core.payment.DynamicPaymentSourceKey
import xyz.lilsus.raylsuite.core.payment.LightningInputParser
import xyz.lilsus.raylsuite.core.payment.LnurlError
import xyz.lilsus.raylsuite.core.payment.LnurlInvoiceResolution
import xyz.lilsus.raylsuite.core.payment.LnurlInvoiceResolutionError
import xyz.lilsus.raylsuite.core.payment.LnurlPayClient
import xyz.lilsus.raylsuite.core.payment.LnurlPayParams
import xyz.lilsus.raylsuite.core.payment.LnurlResult
import xyz.lilsus.raylsuite.core.payment.lightningAddressDynamicPaymentSourceKey
import xyz.lilsus.raylsuite.core.payment.lnurlDynamicPaymentSourceKey
import xyz.lilsus.raylsuite.core.payment.roundToFullSatoshis
import xyz.lilsus.raylsuite.core.ui.platform.HapticFeedbackManager
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences
import xyz.lilsus.raylsuite.feature.paymentcurrency.CurrencyManagerError
import xyz.lilsus.raylsuite.feature.paymentcurrency.CurrencyState
import xyz.lilsus.raylsuite.feature.paymentcurrency.PaymentAmountQuote
import xyz.lilsus.raylsuite.feature.paymentcurrency.PaymentCurrencyManager
import xyz.lilsus.raylsuite.feature.paymenthub.DirectTargetAmountRule
import xyz.lilsus.raylsuite.feature.paymenthub.host.DirectTargetPaymentIntent
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentConfirmationPolicy
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.paymentui.LnurlPayDisplay
import xyz.lilsus.raylsuite.feature.paymentui.PaymentConfirmationAmount
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.paymentui.PaymentToastMessage
import xyz.lilsus.raylsuite.feature.paymentui.amount.ManualAmountConfig
import xyz.lilsus.raylsuite.feature.paymentui.amount.ManualAmountKey

class PaymentCoordinator(
    private val blinkWallet: BlinkWallet,
    private val lnurlPayClient: LnurlPayClient,
    private val bitcoinPriceProvider: BitcoinPriceProvider,
    private val currencyPreferences: CurrencyPreferences,
    private val paymentPreferences: PaymentPreferencesRepository,
    private val paymentHub: PaymentHubController,
    private val haptics: HapticFeedbackManager,
    private val showEstimatedFeeHint: Boolean = false,
    paymentAttemptSettings: Settings,
    coroutineContext: CoroutineContext = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private val currencyManager = PaymentCurrencyManager(bitcoinPriceProvider, scope)
    private val confirmationPolicy = PaymentConfirmationPolicy(paymentPreferences)
    private val preparation = PaymentPreparation(lnurlPayClient)
    private val sessionState = PaymentSessionState(preparation)
    private val inputParser = preparation.inputParser
    private val manualAmount = preparation.manualAmount
    private val pendingTracker =
        PendingPaymentTracker(
            currencyManager = currencyManager,
            scope = scope,
            showEstimatedFeeHint = showEstimatedFeeHint,
            store = PendingPaymentStore(paymentAttemptSettings)
        )
    private val hubContexts = mutableMapOf<String, HubTargetContext>()

    private val mutableUiState = sessionState.uiState
    val uiState: StateFlow<PaymentUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<PaymentEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PaymentEvent> = mutableEvents.asSharedFlow()

    val sessionTransactions: StateFlow<List<SessionTransactionItem>> = pendingTracker.displayItems

    private val mutableTransactionDetailNavigationTarget =
        sessionState.transactionDetailNavigationTarget
    val transactionDetailNavigationTarget: StateFlow<String?> =
        mutableTransactionDetailNavigationTarget.asStateFlow()

    private val mutableNewSessionTransactionCount = sessionState.newSessionTransactionCount
    val newSessionTransactionCount: StateFlow<Int> =
        mutableNewSessionTransactionCount.asStateFlow()

    private var manualEntryContext: ManualEntryContext?
        get() = preparation.manualEntryContext
        set(value) {
            preparation.manualEntryContext = value
        }
    private var pendingPayment: PendingPayment?
        get() = preparation.pendingPayment
        set(value) {
            preparation.pendingPayment = value
        }
    private var pendingLnurlReview: PendingLnurlReview?
        get() = preparation.pendingLnurlReview
        set(value) {
            preparation.pendingLnurlReview = value
        }
    private var pendingRetry: PendingRetryChoice?
        get() = sessionState.pendingRetry
        set(value) {
            sessionState.pendingRetry = value
        }
    private var lastPaymentResult: CompletedPayment?
        get() = sessionState.lastPaymentResult
        set(value) {
            sessionState.lastPaymentResult = value
        }
    private var vibrateOnScan = true
    private var vibrateOnPayment = true
    private var showLnurlPayDetails = false
    private var offerToSaveNewTargets = true
    private val paymentJobs = sessionState.paymentJobs
    private var paymentAdmissionInProgress: Boolean
        get() = sessionState.paymentAdmissionInProgress
        set(value) {
            sessionState.paymentAdmissionInProgress = value
        }

    init {
        scope.launch {
            paymentPreferences.preferences.collectLatest { preferences ->
                vibrateOnScan = preferences.vibrateOnScan
                vibrateOnPayment = preferences.vibrateOnPayment
                showLnurlPayDetails = preferences.showLnurlPayDetails
                offerToSaveNewTargets = preferences.offerToSaveNewTargets
            }
        }
        scope.launch {
            paymentHub.paymentRequests.collect(::payTarget)
        }
        scope.launch {
            currencyPreferences.code.collectLatest { code ->
                currencyManager.setPreferredCurrency(CurrencyCatalog.infoFor(code).currency)
            }
        }
        scope.launch {
            currencyManager.state.collectLatest {
                refreshAllDisplays()
            }
        }
        scope.launch {
            currencyManager.errors.collect { error ->
                mutableEvents.tryEmit(PaymentEvent.ShowError(error.toPaymentUiError()))
            }
        }
        scope.launch {
            pendingTracker.events.collect(::handlePendingEvent)
        }
        scope.launch {
            pendingTracker.displayItems.collect { items ->
                sessionState.updateSessionTransactionIds(
                    items.mapTo(mutableSetOf(), SessionTransactionItem::id)
                )
            }
        }
    }

    fun dispatch(intent: PaymentIntent) {
        scope.launch { handleIntent(intent) }
    }

    fun clear() {
        paymentAdmissionInProgress = false
        val jobs = paymentJobs.values.toList()
        paymentJobs.clear()
        jobs.forEach(Job::cancel)
        pendingTracker.close()
        scope.cancel()
    }

    fun resetSession() {
        sessionState.reset(currencyManager.state.value)
        pendingTracker.resetSession()
        hubContexts.clear()
        paymentHub.resetSession()
    }

    private suspend fun handleIntent(intent: PaymentIntent) {
        when (intent) {
            PaymentIntent.DismissResult -> dismissResult()

            is PaymentIntent.TransactionDetailNavigationHandled ->
                transactionDetailNavigationHandled(intent.id)

            PaymentIntent.SessionTransactionsOpened -> sessionTransactionsOpened()

            is PaymentIntent.QrCodeScanned ->
                handlePaymentInput(intent.rawValue, PaymentRequestSource.Camera)

            is PaymentIntent.DeepLinkReceived ->
                handlePaymentInput(intent.rawValue, PaymentRequestSource.DeepLink)

            PaymentIntent.ManualAmountDismiss -> dismissManualAmount()

            PaymentIntent.ManualAmountSubmit -> submitManualAmount()

            is PaymentIntent.ManualAmountKeyPress -> updateManualAmount(intent.key)

            is PaymentIntent.ManualAmountPreset -> presetManualAmount(intent.amount)

            PaymentIntent.ConfirmPaymentDismiss -> dismissConfirmation()

            PaymentIntent.ConfirmPaymentSubmit -> submitConfirmation()

            PaymentIntent.PendingRetryCreateNewInvoice -> createNewPendingInvoice()

            PaymentIntent.PendingRetryRetryPrevious -> retryPendingPayment()

            PaymentIntent.PendingRetryViewPending -> viewPendingPayment()

            PaymentIntent.PendingRetryDismiss -> dismissPendingRetry()

            is PaymentIntent.RetryTransaction -> retryPayment(intent.id)

            is PaymentIntent.StartDonation -> startDonation(intent.amountSats, intent.address)

            is PaymentIntent.RawInputSubmitted ->
                handlePaymentInput(intent.rawValue, PaymentRequestSource.Camera)
        }
    }

    private suspend fun handlePaymentInput(rawInput: String, source: PaymentRequestSource) {
        if (paymentAdmissionInProgress || mutableUiState.value != PaymentUiState.Active) return
        manualEntryContext = null

        val parseResult =
            if (source == PaymentRequestSource.DeepLink) {
                inputParser.parseDeepLink(rawInput)
            } else {
                inputParser.parse(rawInput)
            }
        when (val result = parseResult) {
            is LightningInputParser.ParseResult.Failure -> handleParseFailure(result.reason)

            is LightningInputParser.ParseResult.Success ->
                when (val target = result.target) {
                    is LightningInputParser.Target.Bolt11 -> {
                        pendingTracker.findLatestByPaymentHash(
                            target.invoice.paymentHash.toHex()
                        )
                            ?.let { existing ->
                                requestTransactionDetailNavigation(existing.id)
                                return
                            }
                        if (rejectExpiredInvoice(target.invoice)) return
                        notifyScanSuccess()
                        processBoltInvoice(target.invoice, source)
                    }

                    is LightningInputParser.Target.Lnurl -> {
                        val sourceKey = lnurlDynamicPaymentSourceKey(target.endpoint)
                        val existing =
                            pendingTracker.findGuardingByDynamicSourceKey(sourceKey)
                        if (existing != null) {
                            showPendingRetryPrompt(
                                record = existing,
                                continuation =
                                    PendingRetryContinuation.Lnurl(
                                        endpoint = target.endpoint,
                                        sourceKey = sourceKey,
                                        paymentSource = source
                                    )
                            )
                            return
                        }
                        notifyScanSuccess()
                        fetchLnurl(target.endpoint, source, sourceKey)
                    }

                    is LightningInputParser.Target.LightningAddressTarget -> {
                        val sourceKey =
                            lightningAddressDynamicPaymentSourceKey(target.address)
                        val targetContext =
                            HubTargetContext(
                                targetId = null,
                                address = target.address,
                                isPreset = false
                            )
                        val existing =
                            pendingTracker.findGuardingByDynamicSourceKey(sourceKey)
                        if (existing != null) {
                            showPendingRetryPrompt(
                                record = existing,
                                continuation =
                                    PendingRetryContinuation.LightningAddress(
                                        address = target.address,
                                        sourceKey = sourceKey,
                                        paymentSource = source,
                                        targetContext = targetContext
                                    )
                            )
                            return
                        }
                        notifyScanSuccess()
                        resolveLightningAddress(
                            address = target.address,
                            paymentSource = source,
                            sourceKey = sourceKey,
                            targetContext = targetContext
                        )
                    }

                    else -> Unit
                }
        }
    }

    private fun handleParseFailure(reason: LightningInputParser.FailureReason) {
        when (reason) {
            LightningInputParser.FailureReason.BitcoinAddress ->
                mutableEvents.tryEmit(
                    PaymentEvent.ShowToast(PaymentToastMessage.BitcoinAddressNotSupported)
                )

            LightningInputParser.FailureReason.Bolt12 ->
                mutableEvents.tryEmit(
                    PaymentEvent.ShowToast(PaymentToastMessage.Bolt12NotSupported)
                )

            LightningInputParser.FailureReason.UnsupportedLnurl ->
                mutableEvents.tryEmit(
                    PaymentEvent.ShowToast(PaymentToastMessage.LnurlRequestNotSupported)
                )

            LightningInputParser.FailureReason.InvalidLnurl ->
                emitError(PaymentUiError.InvalidInvoice("Invalid LNURL request"))

            LightningInputParser.FailureReason.UnsupportedDeepLink ->
                mutableEvents.tryEmit(
                    PaymentEvent.ShowToast(PaymentToastMessage.PaymentLinkNotSupported)
                )

            is LightningInputParser.FailureReason.InvalidInvoice ->
                emitError(PaymentUiError.InvalidInvoice(reason.reason))

            LightningInputParser.FailureReason.Empty,
            LightningInputParser.FailureReason.Unrecognized -> Unit
        }
    }

    private fun processBoltInvoice(invoice: Bolt11Invoice, source: PaymentRequestSource) {
        val entry =
            manualAmount.reset(
                ManualAmountConfig(
                    info = currencyManager.state.value.info,
                    exchangeRate = currencyManager.state.value.exchangeRate
                ),
                clearInput = true
            )
        if (invoice.amount == null) {
            manualEntryContext = ManualEntryContext.Bolt(invoice, source)
            mutableUiState.value = PaymentUiState.EnterAmount(entry)
        } else {
            requestPayment(
                invoice = invoice,
                amountOverrideMsats = null,
                origin = PendingOrigin.Invoice,
                source = source
            )
        }
    }

    private fun fetchLnurl(
        endpoint: String,
        paymentSource: PaymentRequestSource,
        sourceKey: DynamicPaymentSourceKey?,
        replacesDynamicGuardId: String? = null
    ) {
        mutableUiState.value = PaymentUiState.Loading(LoadingKind.Resolving)
        scope.launch {
            when (val result = lnurlPayClient.fetchPayParams(endpoint)) {
                is LnurlResult.Success ->
                    handleLnurlParams(
                        params = result.data,
                        paymentSource = paymentSource,
                        sourceKey = sourceKey,
                        replacesDynamicGuardId = replacesDynamicGuardId
                    )

                is LnurlResult.Error -> emitError(result.error.toPaymentUiError())
            }
        }
    }

    private fun resolveLightningAddress(
        address: LightningAddress,
        paymentSource: PaymentRequestSource,
        sourceKey: DynamicPaymentSourceKey?,
        targetContext: HubTargetContext? = null,
        presetQuote: PaymentAmountQuote? = null,
        targetComment: String? = null,
        replacesDynamicGuardId: String? = null
    ) {
        mutableUiState.value = PaymentUiState.Loading(LoadingKind.Resolving)
        scope.launch {
            when (val result = lnurlPayClient.fetchPayParams(address)) {
                is LnurlResult.Success ->
                    handleLnurlParams(
                        params = result.data,
                        paymentSource = paymentSource,
                        sourceKey = sourceKey,
                        targetContext = targetContext,
                        presetQuote = presetQuote,
                        targetComment = targetComment,
                        replacesDynamicGuardId = replacesDynamicGuardId
                    )

                is LnurlResult.Error -> emitError(result.error.toPaymentUiError())
            }
        }
    }

    private fun resolveTargetPayment(
        address: LightningAddress,
        context: HubTargetContext,
        paymentQuote: PaymentAmountQuote?,
        comment: String?
    ) {
        val sourceKey = lightningAddressDynamicPaymentSourceKey(address)
        val existing = pendingTracker.findGuardingByDynamicSourceKey(sourceKey)
        if (existing != null) {
            showPendingRetryPrompt(
                record = existing,
                continuation =
                    PendingRetryContinuation.LightningAddress(
                        address = address,
                        sourceKey = sourceKey,
                        paymentSource = PaymentRequestSource.Camera,
                        targetContext = context,
                        presetQuote = paymentQuote,
                        targetComment = comment
                    )
            )
            return
        }
        notifyScanSuccess()
        resolveLightningAddress(
            address = address,
            paymentSource = PaymentRequestSource.Camera,
            sourceKey = sourceKey,
            targetContext = context,
            presetQuote = paymentQuote,
            targetComment = comment
        )
    }

    /** Maps a hub selection into this app's own resolution, quoting, and confirmation flow. */
    private fun payTarget(intent: DirectTargetPaymentIntent) {
        val preset = (intent.amountRule as? DirectTargetAmountRule.Preset)?.amount
        val context =
            HubTargetContext(
                targetId = intent.targetId,
                address = intent.address,
                isPreset = preset != null
            )
        if (preset == null) {
            resolveTargetPayment(intent.address, context, null, intent.comment)
            return
        }
        scope.launch {
            val paymentQuote = currencyManager.quoteStoredAmount(preset)
            if (paymentQuote == null) {
                val info = CurrencyCatalog.infoFor(preset.normalizedCurrencyCode)
                emitError(
                    if (info.currency is DisplayCurrency.Fiat) {
                        PaymentUiError.ExchangeRateUnavailable(info.code)
                    } else {
                        PaymentUiError.InvalidInvoice("Preset amount could not be converted")
                    }
                )
                return@launch
            }
            resolveTargetPayment(
                intent.address,
                context,
                paymentQuote,
                intent.comment
            )
        }
    }

    private suspend fun handleLnurlParams(
        params: LnurlPayParams,
        paymentSource: PaymentRequestSource,
        forceManualEntry: Boolean = false,
        prefillMsats: Long? = null,
        inputCurrencyOverride: CurrencyInfo? = null,
        sourceKey: DynamicPaymentSourceKey? = null,
        targetContext: HubTargetContext? = null,
        presetQuote: PaymentAmountQuote? = null,
        targetComment: String? = null,
        replacesDynamicGuardId: String? = null
    ) {
        if (params.minSendable <= 0 || params.maxSendable < params.minSendable) {
            emitError(PaymentUiError.InvalidInvoice("LNURL amount range is invalid"))
            return
        }
        val lnurlPayDisplay =
            if (showLnurlPayDetails) {
                LnurlPayDisplay.fromUntrusted(
                    domain = params.domain,
                    description = params.metadata.plainText,
                    imagePngBase64 = params.metadata.imagePng,
                    imageJpegBase64 = params.metadata.imageJpeg
                ) ?: run {
                    emitError(PaymentUiError.InvalidInvoice("LNURL payment details are invalid"))
                    return
                }
            } else {
                null
            }
        val session =
            LnurlSession(
                params = params,
                display = lnurlPayDisplay,
                sourceKey = sourceKey,
                paymentSource = paymentSource,
                targetContext = targetContext,
                comment = targetComment,
                replacesDynamicGuardId = replacesDynamicGuardId
            )
        val currencyState = currencyManager.state.value
        val inputInfo = inputCurrencyOverride ?: currencyState.info
        val manualCurrencyState =
            CurrencyState(
                info = inputInfo,
                exchangeRate =
                    currencyState.exchangeRate.takeIf {
                        inputInfo.code.equals(currencyState.info.code, ignoreCase = true)
                    }
            )

        if (
            inputInfo.currency is DisplayCurrency.Fiat &&
            inputInfo.code.equals(currencyState.info.code, ignoreCase = true)
        ) {
            currencyManager.ensureExchangeRateIfNeeded(inputInfo)
        }

        presetQuote?.let { paymentQuote ->
            val roundedAmount = paymentQuote.amountMsats
            if (
                roundedAmount < params.minSendable ||
                roundedAmount > params.maxSendable
            ) {
                emitError(
                    PaymentUiError.InvalidInvoice(
                        "Preset amount is outside the allowed range"
                    )
                )
                return
            }
            if (session.display != null) {
                reviewLnurlPayment(
                    session,
                    roundedAmount,
                    isManualEntry = false,
                    paymentQuote = paymentQuote
                )
            } else {
                payLnurlInvoice(
                    session,
                    roundedAmount,
                    isManualEntry = false,
                    paymentQuote = paymentQuote
                )
            }
            return
        }

        if (!forceManualEntry && params.minSendable == params.maxSendable) {
            if (session.display != null) {
                reviewLnurlPayment(session, params.minSendable, isManualEntry = false)
            } else {
                payLnurlInvoice(session, params.minSendable, isManualEntry = false)
            }
            return
        }

        manualEntryContext = ManualEntryContext.Lnurl(session, inputInfo)
        val config =
            ManualAmountConfig(
                info = manualCurrencyState.info,
                exchangeRate = manualCurrencyState.exchangeRate,
                min =
                    currencyManager.convertMsatsToDisplay(
                        params.minSendable,
                        manualCurrencyState
                    ),
                max =
                    currencyManager.convertMsatsToDisplay(
                        params.maxSendable,
                        manualCurrencyState
                    ),
                minMsats = params.minSendable,
                maxMsats = params.maxSendable
            )
        val baseEntry = manualAmount.reset(config, clearInput = true)
        val entry =
            prefillMsats
                ?.coerceIn(params.minSendable, params.maxSendable)
                ?.let { amount ->
                    manualAmount.presetAmount(
                        currencyManager.convertMsatsToDisplay(amount, manualCurrencyState)
                    )
                } ?: baseEntry
        mutableUiState.value = PaymentUiState.EnterAmount(entry, session.display)
    }

    private suspend fun reviewLnurlPayment(
        session: LnurlSession,
        amountMsats: Long,
        isManualEntry: Boolean,
        paymentQuote: PaymentAmountQuote? = null
    ) {
        val display = session.display ?: return
        val fundingWallet = snapshotFundingWallet() ?: return
        val roundedAmount = roundToFullSatoshis(amountMsats)
        val confirmationAmount = confirmationAmount(roundedAmount, paymentQuote)
        pendingLnurlReview =
            PendingLnurlReview(
                session = session,
                amountMsats = roundedAmount,
                isManualEntry = isManualEntry,
                fundingWallet = fundingWallet,
                paymentQuote = paymentQuote
            )
        mutableUiState.value =
            PaymentUiState.Confirm(
                amount = confirmationAmount,
                fundingWallet = fundingWallet,
                lnurlPayDisplay = display
            )
    }

    private fun payLnurlInvoice(
        session: LnurlSession,
        amountMsats: Long,
        isManualEntry: Boolean,
        paymentQuote: PaymentAmountQuote? = null,
        fundingWallet: BlinkFundingWallet? = null
    ) {
        mutableUiState.value = PaymentUiState.Loading()
        scope.launch {
            when (val result = preparation.resolveLnurlInvoice(session, amountMsats)) {
                is LnurlInvoiceResolution.Success ->
                    handleLnurlInvoice(
                        session = session,
                        amountMsats = result.amountMsats,
                        invoice = result.invoice,
                        isManualEntry = isManualEntry,
                        paymentQuote = paymentQuote,
                        fundingWallet = fundingWallet
                    )

                is LnurlInvoiceResolution.Failure -> {
                    manualEntryContext = null
                    emitError(result.error.toPaymentUiError())
                }
            }
        }
    }

    private fun handleLnurlInvoice(
        session: LnurlSession,
        amountMsats: Long,
        invoice: Bolt11Invoice,
        isManualEntry: Boolean,
        paymentQuote: PaymentAmountQuote?,
        fundingWallet: BlinkFundingWallet?
    ) {
        pendingTracker.findLatestByPaymentHash(invoice.paymentHash.toHex())?.let { existing ->
            manualEntryContext = null
            mutableUiState.value = PaymentUiState.Active
            requestTransactionDetailNavigation(existing.id)
            return
        }

        requestPayment(
            invoice = invoice,
            amountOverrideMsats = null,
            origin =
                if (isManualEntry) {
                    PendingOrigin.LnurlManual
                } else {
                    PendingOrigin.LnurlFixed
                },
            source = session.paymentSource,
            dynamicSourceKey = session.sourceKey,
            targetContext = session.targetContext,
            replacesDynamicGuardId = session.replacesDynamicGuardId,
            lnurlAuthorized = session.display != null,
            paymentQuote = paymentQuote,
            fundingWalletSnapshot = fundingWallet
        )
    }

    private fun rejectExpiredInvoice(invoice: Bolt11Invoice): Boolean {
        if (!invoice.isExpired(currentTimestampSeconds())) return false
        manualEntryContext = null
        emitError(PaymentUiError.InvalidInvoice("Invoice has expired"))
        return true
    }

    private fun updateManualAmount(key: ManualAmountKey) {
        val state = mutableUiState.value as? PaymentUiState.EnterAmount ?: return
        manualEntryContext ?: return
        mutableUiState.value =
            PaymentUiState.EnterAmount(
                manualAmount.handleKeyPress(key),
                state.lnurlPayDisplay
            )
    }

    private fun presetManualAmount(amount: DisplayAmount) {
        val state = mutableUiState.value as? PaymentUiState.EnterAmount ?: return
        manualEntryContext ?: return
        mutableUiState.value =
            PaymentUiState.EnterAmount(
                manualAmount.presetAmount(amount),
                state.lnurlPayDisplay
            )
    }

    private suspend fun submitManualAmount() {
        val entryState = mutableUiState.value as? PaymentUiState.EnterAmount ?: return
        val context = manualEntryContext ?: return
        val enteredAmount = manualAmount.enteredAmount() ?: return
        mutableUiState.value = PaymentUiState.Loading(LoadingKind.Resolving)
        val paymentQuote = currencyManager.quote(enteredAmount)
        if (paymentQuote == null) {
            mutableUiState.value = entryState
            val error = when (val currency = enteredAmount.currency) {
                is DisplayCurrency.Fiat ->
                    PaymentUiError.ExchangeRateUnavailable(currency.iso4217)

                else -> PaymentUiError.InvalidInvoice("Amount could not be converted")
            }
            mutableEvents.tryEmit(PaymentEvent.ShowError(error))
            return
        }

        when (context) {
            is ManualEntryContext.Bolt ->
                requestPayment(
                    invoice = context.invoice,
                    amountOverrideMsats = paymentQuote.amountMsats,
                    origin = PendingOrigin.ManualEntry,
                    source = context.source,
                    paymentQuote = paymentQuote
                )

            is ManualEntryContext.Lnurl -> {
                val roundedAmount = paymentQuote.amountMsats
                if (
                    roundedAmount < context.session.params.minSendable ||
                    roundedAmount > context.session.params.maxSendable
                ) {
                    mutableUiState.value = entryState
                    mutableEvents.tryEmit(
                        PaymentEvent.ShowError(
                            PaymentUiError.InvalidInvoice("Amount is outside the allowed range")
                        )
                    )
                    return
                }
                payLnurlInvoice(
                    context.session,
                    roundedAmount,
                    isManualEntry = true,
                    paymentQuote = paymentQuote
                )
            }
        }
    }

    private fun dismissManualAmount() {
        manualAmount.reset()
        manualEntryContext = null
        mutableUiState.value = PaymentUiState.Active
    }

    private fun dismissConfirmation() {
        pendingLnurlReview?.let { review ->
            pendingLnurlReview = null
            mutableUiState.value =
                if (review.isManualEntry) {
                    PaymentUiState.EnterAmount(manualAmount.current(), review.session.display)
                } else {
                    PaymentUiState.Active
                }
            return
        }
        val pending = pendingPayment ?: return
        pendingPayment = null
        mutableUiState.value =
            when (pending.origin) {
                PendingOrigin.Invoice,
                PendingOrigin.LnurlFixed -> PaymentUiState.Active

                PendingOrigin.ManualEntry,
                PendingOrigin.LnurlManual ->
                    PaymentUiState.EnterAmount(manualAmount.current())
            }
    }

    private fun submitConfirmation() {
        pendingLnurlReview?.let { review ->
            pendingLnurlReview = null
            payLnurlInvoice(
                review.session,
                review.amountMsats,
                review.isManualEntry,
                review.paymentQuote,
                review.fundingWallet
            )
            return
        }
        val pending = pendingPayment ?: return
        pendingPayment = null
        startPayment(
            invoice = pending.invoice,
            amountOverrideMsats = pending.amountOverrideMsats,
            fundingWallet = pending.fundingWallet,
            fundingAmountCents = pending.fundingAmountCents,
            origin = pending.origin,
            dynamicSourceKey = pending.dynamicSourceKey,
            targetContext = pending.targetContext,
            replacesDynamicGuardId = pending.replacesDynamicGuardId
        )
    }

    private fun requestPayment(
        invoice: Bolt11Invoice,
        amountOverrideMsats: Long?,
        origin: PendingOrigin,
        source: PaymentRequestSource,
        dynamicSourceKey: DynamicPaymentSourceKey? = null,
        targetContext: HubTargetContext? = null,
        replacesDynamicGuardId: String? = null,
        lnurlAuthorized: Boolean = false,
        paymentQuote: PaymentAmountQuote? = null,
        fundingWalletSnapshot: BlinkFundingWallet? = null
    ) {
        if (paymentAdmissionInProgress) return
        paymentAdmissionInProgress = true
        scope.launch {
            try {
                val amountMsats = amountOverrideMsats ?: invoice.amount?.msat
                if (paymentQuote != null && paymentQuote.amountMsats != amountMsats) {
                    emitError(PaymentUiError.InvalidInvoice("Quoted amount does not match invoice"))
                    return@launch
                }
                val fundingWallet =
                    fundingWalletSnapshot ?: snapshotFundingWallet() ?: return@launch
                val fundingAmountCents =
                    if (
                        amountOverrideMsats != null &&
                        fundingWallet.currency == BlinkWalletCurrency.USD
                    ) {
                        usdPaymentAmountCents(amountOverrideMsats, paymentQuote)
                            ?: run {
                                emitError(PaymentUiError.ExchangeRateUnavailable(USD_CODE))
                                return@launch
                            }
                    } else {
                        null
                    }
                val isManualEntry =
                    origin == PendingOrigin.ManualEntry || origin == PendingOrigin.LnurlManual
                val requiresConfirmation =
                    !lnurlAuthorized &&
                        (
                            source == PaymentRequestSource.DeepLink ||
                                (
                                    amountMsats != null &&
                                        confirmationPolicy.shouldConfirm(
                                            amountMsats = amountMsats,
                                            isManualEntry = isManualEntry,
                                            isPresetTarget = targetContext?.isPreset == true
                                        )
                                    )
                            )
                if (requiresConfirmation) {
                    val display = confirmationAmount(amountMsats ?: 0L, paymentQuote)
                    pendingPayment =
                        PendingPayment(
                            invoice = invoice,
                            amountOverrideMsats = amountOverrideMsats,
                            fundingWallet = fundingWallet,
                            fundingAmountCents = fundingAmountCents,
                            origin = origin,
                            dynamicSourceKey = dynamicSourceKey,
                            targetContext = targetContext,
                            replacesDynamicGuardId = replacesDynamicGuardId
                        )
                    mutableUiState.value =
                        PaymentUiState.Confirm(
                            amount = display,
                            fundingWallet = fundingWallet
                        )
                } else {
                    startPayment(
                        invoice,
                        amountOverrideMsats,
                        fundingWallet,
                        fundingAmountCents,
                        origin,
                        dynamicSourceKey,
                        targetContext,
                        replacesDynamicGuardId
                    )
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                emitError(cause.toPaymentUiError())
            } finally {
                paymentAdmissionInProgress = false
            }
        }
    }

    private fun startPayment(
        invoice: Bolt11Invoice,
        amountOverrideMsats: Long?,
        fundingWallet: BlinkFundingWallet,
        fundingAmountCents: Long?,
        origin: PendingOrigin,
        dynamicSourceKey: DynamicPaymentSourceKey?,
        targetContext: HubTargetContext?,
        replacesDynamicGuardId: String? = null
    ) {
        mutableUiState.value = PaymentUiState.Loading()
        val amountMsats = amountOverrideMsats ?: invoice.amount?.msat ?: 0L
        val pendingId =
            pendingTracker.register(
                summary = invoice,
                amountMsats = amountMsats,
                amountOverrideMsats = amountOverrideMsats,
                fundingWallet = fundingWallet,
                fundingAmountCents = fundingAmountCents,
                origin = origin,
                dynamicSourceKey = dynamicSourceKey,
                replacesDynamicGuardId = replacesDynamicGuardId
            )
        targetContext?.let { hubContexts[pendingId] = it }
        launchPayment(
            pendingId = pendingId,
            invoice = invoice,
            amountOverrideMsats = amountOverrideMsats,
            fundingWallet = fundingWallet,
            fundingAmountCents = fundingAmountCents
        )
    }

    private fun launchPayment(
        pendingId: String,
        invoice: Bolt11Invoice,
        amountOverrideMsats: Long?,
        fundingWallet: BlinkFundingWallet,
        fundingAmountCents: Long?
    ) {
        paymentJobs.remove(pendingId)?.cancel()
        val job =
            scope.launch {
                try {
                    when (
                        val outcome =
                            blinkWallet.submitPayment(
                                BlinkPaymentRequest(
                                    invoice = invoice.write(),
                                    fundingWallet = fundingWallet,
                                    amount =
                                        amountOverrideMsats?.let { amountMsats ->
                                            when (fundingWallet.currency) {
                                                BlinkWalletCurrency.BTC ->
                                                    BlinkPaymentAmount.Bitcoin(amountMsats)

                                                BlinkWalletCurrency.USD ->
                                                    BlinkPaymentAmount.Usd(
                                                        requireNotNull(fundingAmountCents)
                                                    )
                                            }
                                        }
                                )
                            )
                    ) {
                        is BlinkPaymentOutcome.Paid ->
                            handlePaymentSuccess(
                                pendingId = pendingId,
                                invoice = invoice,
                                amountOverrideMsats = amountOverrideMsats,
                                feesPaidMsats = outcome.feesPaidMsats,
                                preimageHex = outcome.preimageHex,
                                wasAlreadyPaid = false
                            )

                        BlinkPaymentOutcome.AlreadyPaid ->
                            handlePaymentSuccess(
                                pendingId = pendingId,
                                invoice = invoice,
                                amountOverrideMsats = amountOverrideMsats,
                                feesPaidMsats = null,
                                preimageHex = null,
                                wasAlreadyPaid = true
                            )

                        BlinkPaymentOutcome.Pending ->
                            handlePaymentPendingInBlink(pendingId)

                        is BlinkPaymentOutcome.DefinitiveFailure ->
                            handlePaymentFailure(
                                pendingId = pendingId,
                                error = PaymentUiError.Blink(outcome.error)
                            )

                        is BlinkPaymentOutcome.StatusUnknown ->
                            handlePaymentStatusUnknown(
                                pendingId = pendingId,
                                error = PaymentUiError.Blink(outcome.error)
                            )
                    }
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    handlePaymentStatusUnknown(
                        pendingId = pendingId,
                        error = cause.toPaymentUiError()
                    )
                }
            }
        job.invokeOnCompletion {
            if (paymentJobs[pendingId] === job) {
                paymentJobs.remove(pendingId)
            }
        }
        paymentJobs[pendingId] = job
    }

    private fun handlePaymentSuccess(
        pendingId: String,
        invoice: Bolt11Invoice,
        amountOverrideMsats: Long?,
        feesPaidMsats: Long?,
        preimageHex: String?,
        wasAlreadyPaid: Boolean
    ) {
        val record = pendingTracker.get(pendingId)
        val showDirectResult = shouldShowDirectPaymentResult(record?.visible == true)
        if (showDirectResult) sessionState.markTransactionSeen(pendingId)
        val paidMsats =
            if (wasAlreadyPaid) {
                0L
            } else {
                amountOverrideMsats ?: invoice.amount?.msat ?: 0L
            }
        val feeMsats =
            if (wasAlreadyPaid) {
                0L
            } else {
                feesPaidMsats ?: 0L
            }
        clearPaymentSessionState()
        pendingTracker.markSuccess(
            id = pendingId,
            paidMsats = paidMsats,
            feeMsats = feeMsats,
            wasAlreadyPaid = wasAlreadyPaid,
            preimage = preimageHex
        )
        if (!wasAlreadyPaid) {
            reportPaymentSuccess(pendingId, paidMsats)
        } else {
            hubContexts.remove(pendingId)
        }
        if (vibrateOnPayment) haptics.notifyPaymentSuccess()
        if (!showDirectResult) return

        showPaymentSuccess(
            CompletedPayment(
                amountMsats = paidMsats,
                feeMsats = feeMsats,
                showEstimatedFeeHint = showEstimatedFeeHint && !wasAlreadyPaid,
                wasAlreadyPaid = wasAlreadyPaid,
                preimage = preimageHex
            )
        )
    }

    private fun handlePaymentFailure(pendingId: String, error: PaymentUiError) {
        val record = pendingTracker.get(pendingId)
        val showDirectResult = shouldShowDirectPaymentResult(record?.visible == true)
        val clarificationOpen = pendingRetry?.recordId == pendingId
        if (showDirectResult || clarificationOpen) {
            sessionState.markTransactionSeen(pendingId)
        }
        clearPaymentSessionState()
        pendingTracker.markFailure(pendingId, error)
        hubContexts.remove(pendingId)
        if (clarificationOpen) pendingRetry = null
        if (!showDirectResult && !clarificationOpen) return
        showPaymentError(error, emitEvent = true)
    }

    private fun handlePaymentPendingInBlink(pendingId: String) {
        val record = pendingTracker.get(pendingId) ?: return
        val showDirectResult = shouldShowDirectPaymentResult(record.visible)
        if (showDirectResult) sessionState.markTransactionSeen(pendingId)
        clearPaymentSessionState()
        pendingTracker.markPendingInBlink(pendingId)
        hubContexts.remove(pendingId)
        if (mutableUiState.value is PaymentUiState.Loading) {
            mutableUiState.value = PaymentUiState.Active
        }
        if (showDirectResult) {
            sessionState.showTransactionDetail(pendingId)
        }
    }

    private fun handlePaymentStatusUnknown(pendingId: String, error: PaymentUiError) {
        val record = pendingTracker.get(pendingId) ?: return
        val showDirectResult = shouldShowDirectPaymentResult(record.visible)
        if (showDirectResult) sessionState.markTransactionSeen(pendingId)
        clearPaymentSessionState()
        pendingTracker.markStatusUnknown(pendingId, error)
        hubContexts.remove(pendingId)
        if (mutableUiState.value is PaymentUiState.Loading) {
            mutableUiState.value = PaymentUiState.Active
        }
        if (showDirectResult) {
            sessionState.showTransactionDetail(pendingId)
        }
    }

    private fun handlePendingEvent(event: PendingEvent) {
        if (event is PendingEvent.BecameVisible) {
            if (mutableUiState.value is PaymentUiState.Loading) {
                mutableUiState.value = PaymentUiState.Active
            }
        }
    }

    private fun startDonation(amountSats: Long, address: LightningAddress) {
        if (amountSats <= 0) return
        mutableUiState.value = PaymentUiState.Loading(LoadingKind.Resolving)
        scope.launch {
            when (val result = lnurlPayClient.fetchPayParams(address)) {
                is LnurlResult.Success ->
                    handleLnurlParams(
                        params = result.data,
                        paymentSource = PaymentRequestSource.Camera,
                        forceManualEntry = true,
                        prefillMsats = amountSats * MSATS_PER_SAT,
                        inputCurrencyOverride =
                            CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE),
                        sourceKey = lightningAddressDynamicPaymentSourceKey(address)
                    )

                is LnurlResult.Error -> emitError(result.error.toPaymentUiError())
            }
        }
    }

    private fun createNewPendingInvoice() {
        val choice = pendingRetry ?: return
        val continuation = choice.continuation
        pendingRetry = null
        notifyScanSuccess()
        when (continuation) {
            is PendingRetryContinuation.Lnurl ->
                fetchLnurl(
                    continuation.endpoint,
                    continuation.paymentSource,
                    continuation.sourceKey,
                    replacesDynamicGuardId = choice.recordId
                )

            is PendingRetryContinuation.LightningAddress ->
                resolveLightningAddress(
                    continuation.address,
                    continuation.paymentSource,
                    continuation.sourceKey,
                    continuation.targetContext,
                    continuation.presetQuote,
                    continuation.targetComment,
                    replacesDynamicGuardId = choice.recordId
                )
        }
    }

    private fun retryPendingPayment() {
        val id = pendingRetry?.recordId ?: return
        pendingRetry = null
        retryPayment(id)
    }

    private fun retryPayment(id: String) {
        val record =
            pendingTracker.get(id)
                ?.takeIf {
                    it.status == PendingStatus.StatusUnknown ||
                        it.status == PendingStatus.Failure
                }
                ?: return
        if (rejectExpiredInvoice(record.summary)) return
        if (
            record.amountOverrideMsats != null &&
            record.fundingWallet.currency == BlinkWalletCurrency.USD &&
            record.fundingAmountCents == null
        ) {
            emitError(PaymentUiError.Blink(BlinkApiError.FundingWalletUnavailable))
            return
        }
        mutableUiState.value = PaymentUiState.Loading()
        pendingTracker.markSending(id)
        launchPayment(
            pendingId = id,
            invoice = record.summary,
            amountOverrideMsats = record.amountOverrideMsats,
            fundingWallet = record.fundingWallet,
            fundingAmountCents = record.fundingAmountCents
        )
    }

    private fun viewPendingPayment() {
        val id = pendingRetry?.recordId ?: return
        pendingRetry = null
        requestTransactionDetailNavigation(id)
    }

    private fun dismissPendingRetry() {
        pendingRetry = null
        mutableUiState.value = PaymentUiState.Active
    }

    private fun showPendingRetryPrompt(
        record: PendingRecord,
        continuation: PendingRetryContinuation
    ) {
        pendingTracker.focus(record.id)
        pendingRetry = PendingRetryChoice(record.id, continuation)
        mutableUiState.value = PaymentUiState.PendingRetry(record.id)
    }

    private fun sessionTransactionsOpened() {
        sessionState.onSessionTransactionsOpened(
            pendingTracker.displayItems.value.map(SessionTransactionItem::id)
        )
    }

    private fun requestTransactionDetailNavigation(id: String) {
        if (pendingTracker.get(id) == null) return
        pendingTracker.focus(id)
        sessionState.requestTransactionDetailNavigation(id)
    }

    private fun transactionDetailNavigationHandled(id: String) {
        sessionState.onTransactionDetailNavigationHandled(id)
    }

    private fun dismissResult() {
        sessionState.dismissResult()
    }

    private fun reportPaymentSuccess(pendingId: String, paidMsats: Long) {
        val context = hubContexts.remove(pendingId) ?: return
        if (paidMsats <= 0L) return
        val targetId = context.targetId
        if (targetId != null) {
            paymentHub.recordSuccessfulPayment(targetId)
        } else if (offerToSaveNewTargets) {
            paymentHub.offerSave(context.address)
        }
    }

    private fun emitError(error: PaymentUiError) {
        mutableUiState.value = PaymentUiState.Error(error)
        mutableEvents.tryEmit(PaymentEvent.ShowError(error))
    }

    private fun showPaymentSuccess(payment: CompletedPayment) {
        lastPaymentResult = payment
        mutableUiState.value = payment.toUiState(currencyManager.state.value)
    }

    private fun showPaymentError(error: PaymentUiError, emitEvent: Boolean) {
        lastPaymentResult = null
        mutableUiState.value = PaymentUiState.Error(error)
        if (emitEvent) {
            mutableEvents.tryEmit(PaymentEvent.ShowError(error))
        }
    }

    private fun shouldShowDirectPaymentResult(recordVisible: Boolean): Boolean =
        mutableUiState.value is PaymentUiState.Loading && !recordVisible

    private fun refreshAllDisplays() {
        refreshManualAmountState(preserveInput = manualEntryContext != null)
        refreshResultState()
        pendingTracker.refreshDisplayItems()
    }

    private fun refreshManualAmountState(preserveInput: Boolean) {
        val currencyState = currencyManager.state.value
        val manualInfo =
            when (val context = manualEntryContext) {
                is ManualEntryContext.Lnurl -> context.inputInfo
                else -> currencyState.info
            }
        val manualCurrencyState =
            CurrencyState(
                info = manualInfo,
                exchangeRate =
                    currencyState.exchangeRate.takeIf {
                        manualInfo.code.equals(currencyState.info.code, ignoreCase = true)
                    }
            )
        val config =
            when (val context = manualEntryContext) {
                is ManualEntryContext.Lnurl ->
                    ManualAmountConfig(
                        info = manualCurrencyState.info,
                        exchangeRate = manualCurrencyState.exchangeRate,
                        min =
                            currencyManager.convertMsatsToDisplay(
                                context.session.params.minSendable,
                                manualCurrencyState
                            ),
                        max =
                            currencyManager.convertMsatsToDisplay(
                                context.session.params.maxSendable,
                                manualCurrencyState
                            ),
                        minMsats = context.session.params.minSendable,
                        maxMsats = context.session.params.maxSendable
                    )

                else ->
                    ManualAmountConfig(
                        info = manualCurrencyState.info,
                        exchangeRate = manualCurrencyState.exchangeRate
                    )
            }
        val entry = manualAmount.reset(config, clearInput = !preserveInput)
        if (mutableUiState.value is PaymentUiState.EnterAmount) {
            val display = (manualEntryContext as? ManualEntryContext.Lnurl)?.session?.display
            mutableUiState.value = PaymentUiState.EnterAmount(entry, display)
        }
    }

    private fun refreshResultState() {
        when (mutableUiState.value) {
            is PaymentUiState.Success -> {
                val payment = lastPaymentResult ?: return
                mutableUiState.value = payment.toUiState(currencyManager.state.value)
            }

            else -> Unit
        }
    }

    private suspend fun confirmationAmount(
        amountMsats: Long,
        paymentQuote: PaymentAmountQuote?
    ): PaymentConfirmationAmount {
        val exactSats = DisplayAmount(amountMsats / MSATS_PER_SAT, DisplayCurrency.Satoshi)
        paymentQuote?.let { quote ->
            return PaymentConfirmationAmount(
                primary = quote.requestedAmount,
                exactSats = exactSats.takeIf {
                    quote.requestedAmount.currency is DisplayCurrency.Fiat
                }
            )
        }
        val preferredAmount = currencyManager.convertMsatsToFreshDisplay(amountMsats)
        return PaymentConfirmationAmount(
            primary = preferredAmount,
            exactSats = exactSats.takeIf { preferredAmount.currency is DisplayCurrency.Fiat },
            primaryIsEstimate = preferredAmount.currency is DisplayCurrency.Fiat
        )
    }

    private suspend fun snapshotFundingWallet(): BlinkFundingWallet? = try {
        blinkWallet.prepareFundingWallet()
    } catch (cause: CancellationException) {
        throw cause
    } catch (error: BlinkApiException) {
        emitError(PaymentUiError.Blink(error.error))
        null
    } catch (_: BlinkConnectionException) {
        emitError(PaymentUiError.Blink(BlinkApiError.MissingWalletConnection))
        null
    }

    private suspend fun usdPaymentAmountCents(
        amountMsats: Long,
        paymentQuote: PaymentAmountQuote?
    ): Long? {
        val requestedAmount = paymentQuote?.requestedAmount
        val requestedCurrency = requestedAmount?.currency as? DisplayCurrency.Fiat
        if (
            requestedAmount != null &&
            requestedCurrency?.iso4217?.equals(USD_CODE, ignoreCase = true) == true
        ) {
            return requestedAmount.minor.takeIf { it > 0L }
        }

        val usdRate =
            bitcoinPriceProvider
                .pricePerBitcoin(USD_CODE)
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?: return null
        return convertMsatsToDisplayAmount(
            msats = amountMsats,
            info = CurrencyCatalog.infoFor(USD_CODE),
            fiatPricePerBitcoin = usdRate
        )?.minor?.takeIf { it > 0L }
    }

    private fun clearPaymentSessionState() {
        preparation.reset(currencyManager.state.value)
    }

    private fun notifyScanSuccess() {
        if (vibrateOnScan) haptics.notifyScanSuccess()
    }

    private fun CompletedPayment.toUiState(currencyState: CurrencyState): PaymentUiState.Success =
        PaymentUiState.Success(
            amountPaid = currencyManager.convertMsatsToDisplay(amountMsats, currencyState),
            feePaid = currencyManager.convertMsatsToDisplay(feeMsats, currencyState),
            showEstimatedFeeHint = showEstimatedFeeHint,
            wasAlreadyPaid = wasAlreadyPaid,
            preimage = preimage
        )
}

internal sealed interface PendingRetryContinuation {
    data class Lnurl(
        val endpoint: String,
        val sourceKey: DynamicPaymentSourceKey,
        val paymentSource: PaymentRequestSource
    ) : PendingRetryContinuation

    data class LightningAddress(
        val address: xyz.lilsus.raylsuite.core.model.LightningAddress,
        val sourceKey: DynamicPaymentSourceKey,
        val paymentSource: PaymentRequestSource,
        val targetContext: HubTargetContext? = null,
        val presetQuote: PaymentAmountQuote? = null,
        val targetComment: String? = null
    ) : PendingRetryContinuation
}

private fun Throwable.toPaymentUiError(): PaymentUiError = when (this) {
    is BlinkApiException -> PaymentUiError.Blink(error)
    is BlinkConnectionException -> PaymentUiError.Blink(BlinkApiError.MissingWalletConnection)
    else -> PaymentUiError.Unexpected(message)
}

private fun CurrencyManagerError.toPaymentUiError(): PaymentUiError = when (this) {
    is CurrencyManagerError.ExchangeRateUnavailable ->
        PaymentUiError.ExchangeRateUnavailable(currencyCode)
}

private fun LnurlError.toPaymentUiError(): PaymentUiError = when (this) {
    LnurlError.NetworkUnavailable ->
        PaymentUiError.Blink(BlinkApiError.NetworkUnavailable)

    is LnurlError.Protocol -> PaymentUiError.Lnurl(reason)

    is LnurlError.Unexpected -> PaymentUiError.Lnurl(detail)
}

private fun LnurlInvoiceResolutionError.toPaymentUiError(): PaymentUiError = when (this) {
    is LnurlInvoiceResolutionError.Client -> error.toPaymentUiError()

    LnurlInvoiceResolutionError.CommentRejected ->
        PaymentUiError.InvalidInvoice("Description is too long for this address")

    LnurlInvoiceResolutionError.MalformedInvoice ->
        PaymentUiError.InvalidInvoice("Failed to parse BOLT11 invoice")

    LnurlInvoiceResolutionError.ExpiredInvoice ->
        PaymentUiError.InvalidInvoice("Invoice has expired")

    is LnurlInvoiceResolutionError.AmountMismatch ->
        PaymentUiError.InvalidInvoice("LNURL invoice amount does not match")

    LnurlInvoiceResolutionError.MetadataMismatch ->
        PaymentUiError.InvalidInvoice("LNURL invoice metadata mismatch")
}

private const val MSATS_PER_SAT = 1_000L
private const val USD_CODE = "USD"
