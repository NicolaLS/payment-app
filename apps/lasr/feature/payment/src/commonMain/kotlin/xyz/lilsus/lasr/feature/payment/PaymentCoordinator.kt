package xyz.lilsus.lasr.feature.payment

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
import xyz.lilsus.lasr.integration.nwc.NwcPayOutcome
import xyz.lilsus.lasr.integration.nwc.NwcWallet
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.CurrencyInfo
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.LightningAddress
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
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences
import xyz.lilsus.raylsuite.feature.paymentcurrency.CurrencyManagerError
import xyz.lilsus.raylsuite.feature.paymentcurrency.CurrencyState
import xyz.lilsus.raylsuite.feature.paymentcurrency.PaymentCurrencyManager
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentConfirmationPolicy
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.paymentui.LnurlPayDisplay
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.paymentui.PaymentToastMessage
import xyz.lilsus.raylsuite.feature.paymentui.amount.ManualAmountConfig
import xyz.lilsus.raylsuite.feature.paymentui.amount.ManualAmountKey
import xyz.lilsus.raylsuite.feature.paymentui.contacts.PaymentContactContext
import xyz.lilsus.raylsuite.feature.paymentui.contacts.PaymentContactSelection
import xyz.lilsus.raylsuite.feature.paymentui.contacts.PaymentContactsController

class PaymentCoordinator(
    private val nwcWallet: NwcWallet,
    private val lnurlPayClient: LnurlPayClient,
    bitcoinPriceProvider: BitcoinPriceProvider,
    private val currencyPreferences: CurrencyPreferences,
    private val paymentPreferences: PaymentPreferencesRepository,
    contactsRepository: ContactsRepository,
    private val haptics: HapticFeedbackManager,
    private val showEstimatedFeeHint: Boolean = false,
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
            lookupInvoice = nwcWallet::lookupInvoice,
            isInForeground = nwcWallet.isInForeground,
            currencyManager = currencyManager,
            scope = scope,
            showEstimatedFeeHint = showEstimatedFeeHint
        )
    private val contactsController =
        PaymentContactsController(
            repository = contactsRepository,
            scope = scope,
            onPaymentRequested = ::requestContactPayment,
            clock = ::currentTimeMillis
        )

    private val mutableUiState = sessionState.uiState
    val uiState: StateFlow<PaymentUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<PaymentEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PaymentEvent> = mutableEvents.asSharedFlow()

    val sessionTransactions: StateFlow<List<SessionTransactionItem>> = pendingTracker.displayItems
    val contactsState = contactsController.state

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
            }
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
                    items.map(SessionTransactionItem::id)
                )
            }
        }
        scope.launch {
            nwcWallet.sentPayments.collect(pendingTracker::applySentPayment)
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
        contactsController.resetSession()
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

            PaymentIntent.OpenContacts -> contactsController.open()

            PaymentIntent.DismissContacts -> contactsController.dismiss()

            is PaymentIntent.PaymentSheetTabSelected -> contactsController.selectTab(intent.tab)

            is PaymentIntent.ContactRoleSelected -> contactsController.selectRole(intent.role)

            is PaymentIntent.SelectShortcut -> contactsController.selectShortcut(intent.id)

            is PaymentIntent.SelectContact -> contactsController.selectContact(intent.id)

            is PaymentIntent.SaveContactPromptAliasChanged ->
                contactsController.updateSavePromptAlias(intent.alias)

            is PaymentIntent.SaveContactPromptRoleSelected ->
                contactsController.updateSavePromptRole(intent.role)

            PaymentIntent.SaveContactPromptSave -> contactsController.savePrompt()

            PaymentIntent.SaveContactPromptDismiss -> contactsController.dismissSavePrompt()
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
                        if (rejectExpiredInvoice(target.invoice)) return
                        pendingTracker.findUnresolvedByPaymentRequest(target.invoice.write())
                            ?.let { existing ->
                                requestTransactionDetailNavigation(existing.id)
                                return
                            }
                        notifyScanSuccess()
                        processBoltInvoice(target.invoice, source)
                    }

                    is LightningInputParser.Target.Lnurl -> {
                        val sourceKey = lnurlDynamicPaymentSourceKey(target.endpoint)
                        val existing =
                            pendingTracker.findUnresolvedByDynamicSourceKey(sourceKey)
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
                        val sourceKey = lightningAddressDynamicPaymentSourceKey(target.address)
                        val contactContext =
                            contactsController.contextFor(
                                target.address,
                                allowSavePrompt = true
                            )
                        val existing =
                            pendingTracker.findUnresolvedByDynamicSourceKey(sourceKey)
                        if (existing != null) {
                            showPendingRetryPrompt(
                                record = existing,
                                continuation =
                                    PendingRetryContinuation.LightningAddress(
                                        address = target.address,
                                        sourceKey = sourceKey,
                                        paymentSource = source,
                                        contactContext = contactContext
                                    )
                            )
                            return
                        }
                        notifyScanSuccess()
                        resolveLightningAddress(
                            address = target.address,
                            paymentSource = source,
                            sourceKey = sourceKey,
                            contactContext = contactContext
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
        contactContext: PaymentContactContext? = null,
        shortcutAmountMsats: Long? = null,
        shortcutComment: String? = null,
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
                        contactContext = contactContext,
                        shortcutAmountMsats = shortcutAmountMsats,
                        shortcutComment = shortcutComment,
                        replacesDynamicGuardId = replacesDynamicGuardId
                    )

                is LnurlResult.Error -> emitError(result.error.toPaymentUiError())
            }
        }
    }

    private fun resolveContactPayment(
        address: LightningAddress,
        context: PaymentContactContext,
        amountMsats: Long?,
        comment: String?
    ) {
        val sourceKey = lightningAddressDynamicPaymentSourceKey(address)
        val existing = pendingTracker.findUnresolvedByDynamicSourceKey(sourceKey)
        if (existing != null) {
            showPendingRetryPrompt(
                record = existing,
                continuation =
                    PendingRetryContinuation.LightningAddress(
                        address = address,
                        sourceKey = sourceKey,
                        paymentSource = PaymentRequestSource.Camera,
                        contactContext = context,
                        shortcutAmountMsats = amountMsats,
                        shortcutComment = comment
                    )
            )
            return
        }
        notifyScanSuccess()
        resolveLightningAddress(
            address = address,
            paymentSource = PaymentRequestSource.Camera,
            sourceKey = sourceKey,
            contactContext = context,
            shortcutAmountMsats = amountMsats,
            shortcutComment = comment
        )
    }

    private fun requestContactPayment(selection: PaymentContactSelection) {
        val context = selection.context
        val shortcutAmount = selection.shortcutAmount
        if (shortcutAmount == null) {
            resolveContactPayment(context.address, context, null, context.comment)
            return
        }
        scope.launch {
            val amountMsats = currencyManager.convertShortcutAmountToMsats(shortcutAmount)
            if (amountMsats == null || amountMsats <= 0L) {
                emitError(PaymentUiError.InvalidInvoice("Shortcut amount could not be converted"))
                return@launch
            }
            resolveContactPayment(
                context.address,
                context,
                roundToFullSatoshis(amountMsats),
                context.comment
            )
        }
    }

    private fun handleLnurlParams(
        params: LnurlPayParams,
        paymentSource: PaymentRequestSource,
        forceManualEntry: Boolean = false,
        prefillMsats: Long? = null,
        inputCurrencyOverride: CurrencyInfo? = null,
        sourceKey: DynamicPaymentSourceKey? = null,
        contactContext: PaymentContactContext? = null,
        shortcutAmountMsats: Long? = null,
        shortcutComment: String? = null,
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
                contactContext = contactContext?.copy(comment = shortcutComment),
                comment = shortcutComment,
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
            currencyManager.needsExchangeRate(inputInfo) &&
            inputInfo.code.equals(currencyState.info.code, ignoreCase = true)
        ) {
            currencyManager.ensureExchangeRateIfNeeded(inputInfo)
        }

        shortcutAmountMsats?.let { requestedAmount ->
            val roundedAmount = roundToFullSatoshis(requestedAmount)
            if (
                roundedAmount < params.minSendable ||
                roundedAmount > params.maxSendable
            ) {
                emitError(
                    PaymentUiError.InvalidInvoice(
                        "Shortcut amount is outside the allowed range"
                    )
                )
                return
            }
            if (session.display != null) {
                reviewLnurlPayment(session, roundedAmount, isManualEntry = false)
            } else {
                payLnurlInvoice(session, roundedAmount, isManualEntry = false)
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

    private fun reviewLnurlPayment(
        session: LnurlSession,
        amountMsats: Long,
        isManualEntry: Boolean
    ) {
        val display = session.display ?: return
        val roundedAmount = roundToFullSatoshis(amountMsats)
        pendingLnurlReview = PendingLnurlReview(session, roundedAmount, isManualEntry)
        mutableUiState.value =
            PaymentUiState.Confirm(
                amount = currencyManager.convertMsatsToDisplay(roundedAmount),
                lnurlPayDisplay = display
            )
    }

    private fun payLnurlInvoice(session: LnurlSession, amountMsats: Long, isManualEntry: Boolean) {
        mutableUiState.value = PaymentUiState.Loading()
        scope.launch {
            when (val result = preparation.resolveLnurlInvoice(session, amountMsats)) {
                is LnurlInvoiceResolution.Success ->
                    handleLnurlInvoice(
                        session = session,
                        amountMsats = result.amountMsats,
                        invoice = result.invoice,
                        isManualEntry = isManualEntry
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
        isManualEntry: Boolean
    ) {
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
            contactContext = session.contactContext,
            replacesDynamicGuardId = session.replacesDynamicGuardId,
            lnurlAuthorized = session.display != null
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

    private fun submitManualAmount() {
        if (mutableUiState.value !is PaymentUiState.EnterAmount) return
        val context = manualEntryContext ?: return
        val amountMsats = manualAmount.enteredAmountMsats()
        if (amountMsats == null || amountMsats <= 0) {
            currencyManager.ensureExchangeRateIfNeeded()
            return
        }
        if (currencyManager.needsExchangeRate()) {
            currencyManager.ensureExchangeRateIfNeeded()
        }

        when (context) {
            is ManualEntryContext.Bolt ->
                requestPayment(
                    invoice = context.invoice,
                    amountOverrideMsats = roundToFullSatoshis(amountMsats),
                    origin = PendingOrigin.ManualEntry,
                    source = context.source
                )

            is ManualEntryContext.Lnurl -> {
                val roundedAmount = roundToFullSatoshis(amountMsats)
                if (
                    roundedAmount < context.session.params.minSendable ||
                    roundedAmount > context.session.params.maxSendable
                ) {
                    mutableEvents.tryEmit(
                        PaymentEvent.ShowError(
                            PaymentUiError.InvalidInvoice("Amount is outside the allowed range")
                        )
                    )
                    return
                }
                payLnurlInvoice(context.session, roundedAmount, isManualEntry = true)
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
            payLnurlInvoice(review.session, review.amountMsats, review.isManualEntry)
            return
        }
        val pending = pendingPayment ?: return
        pendingPayment = null
        startPayment(
            invoice = pending.invoice,
            amountOverrideMsats = pending.amountOverrideMsats,
            origin = pending.origin,
            dynamicSourceKey = pending.dynamicSourceKey,
            contactContext = pending.contactContext,
            replacesDynamicGuardId = pending.replacesDynamicGuardId
        )
    }

    private fun requestPayment(
        invoice: Bolt11Invoice,
        amountOverrideMsats: Long?,
        origin: PendingOrigin,
        source: PaymentRequestSource,
        dynamicSourceKey: DynamicPaymentSourceKey? = null,
        contactContext: PaymentContactContext? = null,
        replacesDynamicGuardId: String? = null,
        lnurlAuthorized: Boolean = false
    ) {
        if (paymentAdmissionInProgress) return
        paymentAdmissionInProgress = true
        scope.launch {
            try {
                if (currencyManager.needsExchangeRate()) {
                    currencyManager.ensureExchangeRateIfNeeded()
                }
                val amountMsats = amountOverrideMsats ?: invoice.amount?.msat
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
                                            isShortcut = contactContext?.shortcutId != null
                                        )
                                    )
                            )
                if (requiresConfirmation) {
                    val display =
                        currencyManager.convertMsatsToDisplay(
                            amountMsats ?: 0L,
                            currencyManager.state.value
                        )
                    pendingPayment =
                        PendingPayment(
                            invoice = invoice,
                            amountOverrideMsats = amountOverrideMsats,
                            origin = origin,
                            dynamicSourceKey = dynamicSourceKey,
                            contactContext = contactContext,
                            replacesDynamicGuardId = replacesDynamicGuardId
                        )
                    mutableUiState.value = PaymentUiState.Confirm(display)
                } else {
                    startPayment(
                        invoice,
                        amountOverrideMsats,
                        origin,
                        dynamicSourceKey,
                        contactContext,
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
        origin: PendingOrigin,
        dynamicSourceKey: DynamicPaymentSourceKey?,
        contactContext: PaymentContactContext?,
        replacesDynamicGuardId: String? = null
    ) {
        mutableUiState.value = PaymentUiState.Loading()
        val amountMsats = amountOverrideMsats ?: invoice.amount?.msat ?: 0L
        val pendingId =
            pendingTracker.register(
                summary = invoice,
                amountMsats = amountMsats,
                amountOverrideMsats = amountOverrideMsats,
                origin = origin,
                dynamicSourceKey = dynamicSourceKey,
                replacesDynamicGuardId = replacesDynamicGuardId
            )
        contactsController.bindPendingPayment(pendingId, contactContext)
        paymentJobs.remove(pendingId)?.cancel()
        val job =
            scope.launch {
                try {
                    val outcome =
                        nwcWallet.payInvoice(
                            invoice = invoice.write(),
                            amountMsats = amountOverrideMsats,
                            timeoutMs = PAY_RESPONSE_TIMEOUT_MS
                        )
                    pendingTracker.applyPayOutcome(pendingId, outcome)
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    pendingTracker.applyPayOutcome(
                        pendingId,
                        NwcPayOutcome.Uncertain(cause.message)
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

    private fun handlePendingEvent(event: PendingEvent) {
        when (event) {
            is PendingEvent.BecameVisible -> {
                clearPaymentSessionState()
                if (mutableUiState.value is PaymentUiState.Loading) {
                    mutableUiState.value = PaymentUiState.Active
                }
            }

            is PendingEvent.Settled -> {
                clearPaymentSessionState()
                contactsController.paymentSucceeded(event.id, event.paidMsats)
                if (vibrateOnPayment) haptics.notifyPaymentSuccess()
                if (!event.wasVisible && mutableUiState.value is PaymentUiState.Loading) {
                    showPaymentSuccess(
                        CompletedPayment(
                            amountMsats = event.paidMsats,
                            feeMsats = event.feeMsats,
                            showEstimatedFeeHint = showEstimatedFeeHint,
                            wasAlreadyPaid = false,
                            preimage = event.preimage
                        )
                    )
                    sessionState.showTransactionDetail(event.id)
                }
            }

            is PendingEvent.Failed -> {
                clearPaymentSessionState()
                contactsController.paymentFinishedWithoutSuccess(event.id)
                if (pendingRetry?.recordId == event.id) {
                    pendingRetry = null
                    mutableUiState.value = PaymentUiState.Active
                }
                if (!event.wasVisible && mutableUiState.value is PaymentUiState.Loading) {
                    showPaymentError(event.error, emitEvent = true)
                    sessionState.showTransactionDetail(event.id)
                }
            }

            is PendingEvent.OutcomeUnknown -> {
                clearPaymentSessionState()
                contactsController.paymentFinishedWithoutSuccess(event.id)
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
                    continuation.contactContext,
                    continuation.shortcutAmountMsats,
                    continuation.shortcutComment,
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
        val record = pendingTracker.retryUnknown(id) ?: return
        mutableUiState.value = PaymentUiState.Loading()
        paymentJobs.remove(id)?.cancel()
        paymentJobs[id] =
            scope.launch {
                try {
                    val outcome =
                        nwcWallet.payInvoice(
                            invoice = record.summary.write(),
                            amountMsats = record.amountOverrideMsats,
                            timeoutMs = PAY_RESPONSE_TIMEOUT_MS
                        )
                    pendingTracker.applyPayOutcome(id, outcome)
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    pendingTracker.applyPayOutcome(
                        id,
                        NwcPayOutcome.Uncertain(cause.message)
                    )
                }
            }
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
        sessionState.requestTransactionDetailNavigation(id)
    }

    private fun transactionDetailNavigationHandled(id: String) {
        sessionState.onTransactionDetailNavigationHandled(id)
    }

    private fun dismissResult() {
        sessionState.dismissResult()
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
        when (val state = mutableUiState.value) {
            is PaymentUiState.Success -> {
                val payment = lastPaymentResult ?: return
                mutableUiState.value = payment.toUiState(currencyManager.state.value)
            }

            is PaymentUiState.Confirm -> {
                pendingLnurlReview?.let { review ->
                    mutableUiState.value =
                        PaymentUiState.Confirm(
                            currencyManager.convertMsatsToDisplay(review.amountMsats),
                            review.session.display
                        )
                    return
                }
                val pending = pendingPayment ?: return
                val amount =
                    pending.amountOverrideMsats ?: pending.invoice.amount?.msat ?: return
                mutableUiState.value =
                    PaymentUiState.Confirm(
                        currencyManager.convertMsatsToDisplay(amount)
                    )
            }

            else -> Unit
        }
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
        val contactContext: PaymentContactContext? = null,
        val shortcutAmountMsats: Long? = null,
        val shortcutComment: String? = null
    ) : PendingRetryContinuation
}

private fun Throwable.toPaymentUiError(): PaymentUiError = PaymentUiError.Unexpected(message)

private fun CurrencyManagerError.toPaymentUiError(): PaymentUiError = when (this) {
    is CurrencyManagerError.ExchangeRateUnavailable ->
        PaymentUiError.ExchangeRateUnavailable(currencyCode)
}

private fun LnurlError.toPaymentUiError(): PaymentUiError = when (this) {
    LnurlError.NetworkUnavailable ->
        PaymentUiError.Nwc(NwcPaymentError.NetworkUnavailable)

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
private const val PAY_RESPONSE_TIMEOUT_MS = 15_000L
