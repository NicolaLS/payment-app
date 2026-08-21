package xyz.lilsus.blip.feature.payment

import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toByteVector32
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.blip.integration.blink.BlinkApiError
import xyz.lilsus.blip.integration.blink.BlinkPaymentOutcome
import xyz.lilsus.blip.integration.blink.BlinkPaymentRequest
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.CurrencyInfo
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.core.payment.DynamicPaymentSourceKey
import xyz.lilsus.raylsuite.core.payment.LightningInputParser
import xyz.lilsus.raylsuite.core.payment.LnurlError
import xyz.lilsus.raylsuite.core.payment.LnurlPayClient
import xyz.lilsus.raylsuite.core.payment.LnurlPayParams
import xyz.lilsus.raylsuite.core.payment.LnurlResult
import xyz.lilsus.raylsuite.core.payment.lightningAddressDynamicPaymentSourceKey
import xyz.lilsus.raylsuite.core.payment.lnurlDynamicPaymentSourceKey
import xyz.lilsus.raylsuite.core.ui.platform.HapticFeedbackManager
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentConfirmationPolicy
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.paymentui.PaymentToastMessage
import xyz.lilsus.raylsuite.feature.paymentui.amount.ManualAmountConfig
import xyz.lilsus.raylsuite.feature.paymentui.amount.ManualAmountController
import xyz.lilsus.raylsuite.feature.paymentui.amount.ManualAmountKey
import xyz.lilsus.raylsuite.feature.paymentui.contacts.PaymentContactContext
import xyz.lilsus.raylsuite.feature.paymentui.contacts.PaymentContactSelection
import xyz.lilsus.raylsuite.feature.paymentui.contacts.PaymentContactsController

class PaymentCoordinator(
    private val blinkWallet: BlinkWallet,
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
    private val inputParser = LightningInputParser()
    private val manualAmount =
        ManualAmountController(
            ManualAmountConfig(
                info = CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE),
                exchangeRate = null
            )
        )
    private val pendingTracker =
        PendingPaymentTracker(
            currencyManager = currencyManager,
            scope = scope,
            showEstimatedFeeHint = showEstimatedFeeHint
        )
    private val contactsController =
        PaymentContactsController(
            repository = contactsRepository,
            scope = scope,
            onPaymentRequested = ::requestContactPayment,
            clock = ::platformCurrentTimeMillis
        )

    private val mutableUiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Active)
    val uiState: StateFlow<PaymentUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<PaymentEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PaymentEvent> = mutableEvents.asSharedFlow()

    val sessionTransactions: StateFlow<List<SessionTransactionItem>> = pendingTracker.displayItems
    val contactsState = contactsController.state

    private val mutableTransactionDetailNavigationTarget = MutableStateFlow<String?>(null)
    val transactionDetailNavigationTarget: StateFlow<String?> =
        mutableTransactionDetailNavigationTarget.asStateFlow()

    private val mutableNewSessionTransactionCount = MutableStateFlow(0)
    val newSessionTransactionCount: StateFlow<Int> =
        mutableNewSessionTransactionCount.asStateFlow()

    private var manualEntryContext: ManualEntryContext? = null
    private var pendingPayment: PendingPayment? = null
    private var pendingRetry: PendingRetryChoice? = null
    private var lastPaymentResult: CompletedPayment? = null
    private val knownSessionTransactionIds = mutableSetOf<String>()
    private val newSessionTransactionIds = mutableSetOf<String>()
    private var vibrateOnScan = true
    private var vibrateOnPayment = true
    private val paymentJobs = mutableMapOf<String, Job>()
    private var paymentAdmissionInProgress = false

    init {
        scope.launch {
            paymentPreferences.preferences.collectLatest { preferences ->
                vibrateOnScan = preferences.vibrateOnScan
                vibrateOnPayment = preferences.vibrateOnPayment
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
                mutableEvents.tryEmit(PaymentEvent.ShowError(error))
            }
        }
        scope.launch {
            pendingTracker.events.collect(::handlePendingEvent)
        }
        scope.launch {
            pendingTracker.displayItems.collect(::refreshNewSessionTransactionCount)
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
        paymentAdmissionInProgress = false
        val jobs = paymentJobs.values.toList()
        paymentJobs.clear()
        jobs.forEach(Job::cancel)
        pendingTracker.resetSession()
        contactsController.resetSession()
        manualEntryContext = null
        pendingPayment = null
        pendingRetry = null
        lastPaymentResult = null
        knownSessionTransactionIds.clear()
        newSessionTransactionIds.clear()
        mutableNewSessionTransactionCount.value = 0
        mutableTransactionDetailNavigationTarget.value = null
        clearPaymentSessionState()
        mutableUiState.value = PaymentUiState.Active
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

    private fun handlePaymentInput(rawInput: String, source: PaymentRequestSource) {
        if (paymentAdmissionInProgress || mutableUiState.value != PaymentUiState.Active) return
        manualEntryContext = null

        when (val result = inputParser.parse(rawInput)) {
            is LightningInputParser.ParseResult.Failure -> handleParseFailure(result.reason)

            is LightningInputParser.ParseResult.Success ->
                when (val target = result.target) {
                    is LightningInputParser.Target.Bolt11 -> {
                        pendingTracker.findUnresolvedByPaymentHash(
                            target.invoice.paymentHash.toHex()
                        )
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
                        val sourceKey =
                            lightningAddressDynamicPaymentSourceKey(target.address)
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

            is LightningInputParser.FailureReason.InvalidInvoice ->
                emitError(PaymentUiError.InvalidInvoice(reason.reason))

            LightningInputParser.FailureReason.ExpiredInvoice ->
                emitError(PaymentUiError.InvalidInvoice("Invoice has expired"))

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
            // TODO: Use Blink-native conversion when the deferred currency refactor begins.
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
        val session =
            LnurlSession(
                params = params,
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
            payLnurlInvoice(session, roundedAmount, isManualEntry = false)
            return
        }

        if (!forceManualEntry && params.minSendable == params.maxSendable) {
            payLnurlInvoice(session, params.minSendable, isManualEntry = false)
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
        mutableUiState.value = PaymentUiState.EnterAmount(entry)
    }

    private fun payLnurlInvoice(session: LnurlSession, amountMsats: Long, isManualEntry: Boolean) {
        mutableUiState.value = PaymentUiState.Loading()
        scope.launch {
            val roundedAmount = roundToFullSatoshis(amountMsats)
            // Shortcut comments currently double as local notes and receiver-facing LNURL
            // messages. TODO: Clarify whether those should remain one concept or be separated.
            val comment = session.comment?.takeIf(String::isNotBlank)
            val commentAllowed = session.params.commentAllowed
            if (
                comment != null &&
                (
                    commentAllowed == null ||
                        comment.length > commentAllowed
                    )
            ) {
                manualEntryContext = null
                emitError(
                    PaymentUiError.InvalidInvoice(
                        "Description is too long for this address"
                    )
                )
                return@launch
            }
            when (
                val result =
                    lnurlPayClient.requestInvoice(
                        callback = session.params.callback,
                        amountMsats = roundedAmount,
                        comment = comment
                    )
            ) {
                is LnurlResult.Success ->
                    handleLnurlInvoice(session, roundedAmount, result.data, isManualEntry)

                is LnurlResult.Error -> {
                    manualEntryContext = null
                    emitError(result.error.toPaymentUiError())
                }
            }
        }
    }

    private fun handleLnurlInvoice(
        session: LnurlSession,
        amountMsats: Long,
        encodedInvoice: String,
        isManualEntry: Boolean
    ) {
        val invoice =
            when (val result = inputParser.parse(encodedInvoice)) {
                is LightningInputParser.ParseResult.Success ->
                    (result.target as? LightningInputParser.Target.Bolt11)?.invoice

                is LightningInputParser.ParseResult.Failure -> null
            }
        if (invoice == null) {
            manualEntryContext = null
            emitError(PaymentUiError.InvalidInvoice("Failed to parse BOLT11 invoice"))
            return
        }
        if (invoice.amount?.msat != amountMsats) {
            manualEntryContext = null
            emitError(PaymentUiError.InvalidInvoice("LNURL invoice amount does not match"))
            return
        }
        if (!validateLnurlDescription(invoice, session.params)) {
            manualEntryContext = null
            emitError(PaymentUiError.InvalidInvoice("LNURL invoice metadata mismatch"))
            return
        }
        pendingTracker.findUnresolvedByPaymentHash(invoice.paymentHash.toHex())?.let { existing ->
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
            contactContext = session.contactContext,
            replacesDynamicGuardId = session.replacesDynamicGuardId
        )
    }

    private fun validateLnurlDescription(invoice: Bolt11Invoice, params: LnurlPayParams): Boolean {
        invoice.description?.let { description ->
            return params.metadata.plainText?.let { it == description } ?: true
        }
        invoice.descriptionHash?.let { hash ->
            return Crypto.sha256(params.metadataRaw.encodeToByteArray()).toByteVector32() == hash
        }
        return false
    }

    private fun updateManualAmount(key: ManualAmountKey) {
        if (mutableUiState.value !is PaymentUiState.EnterAmount) return
        manualEntryContext ?: return
        mutableUiState.value =
            PaymentUiState.EnterAmount(manualAmount.handleKeyPress(key))
    }

    private fun presetManualAmount(amount: DisplayAmount) {
        if (mutableUiState.value !is PaymentUiState.EnterAmount) return
        manualEntryContext ?: return
        mutableUiState.value =
            PaymentUiState.EnterAmount(manualAmount.presetAmount(amount))
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
        replacesDynamicGuardId: String? = null
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
                    source == PaymentRequestSource.DeepLink ||
                        (
                            amountMsats != null &&
                                confirmationPolicy.shouldConfirm(
                                    amountMsats = amountMsats,
                                    isManualEntry = isManualEntry,
                                    isShortcut = contactContext?.shortcutId != null
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
        launchPayment(
            pendingId = pendingId,
            invoice = invoice,
            amountOverrideMsats = amountOverrideMsats
        )
    }

    private fun launchPayment(
        pendingId: String,
        invoice: Bolt11Invoice,
        amountOverrideMsats: Long?
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
                                    amountMsats = amountOverrideMsats
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
        if (showDirectResult) markTransactionSeen(pendingId)
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
            contactsController.paymentSucceeded(pendingId, paidMsats)
        } else {
            contactsController.paymentFinishedWithoutSuccess(pendingId)
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
        if (showDirectResult || clarificationOpen) markTransactionSeen(pendingId)
        clearPaymentSessionState()
        pendingTracker.markFailure(pendingId, error)
        contactsController.paymentFinishedWithoutSuccess(pendingId)
        if (clarificationOpen) pendingRetry = null
        if (!showDirectResult && !clarificationOpen) return
        showPaymentError(error, emitEvent = true)
    }

    private fun handlePaymentPendingInBlink(pendingId: String) {
        val record = pendingTracker.get(pendingId) ?: return
        val showDirectResult = shouldShowDirectPaymentResult(record.visible)
        if (showDirectResult) markTransactionSeen(pendingId)
        clearPaymentSessionState()
        pendingTracker.markPendingInBlink(pendingId)
        contactsController.paymentFinishedWithoutSuccess(pendingId)
        if (mutableUiState.value is PaymentUiState.Loading) {
            mutableUiState.value = PaymentUiState.Active
        }
        if (showDirectResult) {
            mutableTransactionDetailNavigationTarget.value = pendingId
        }
    }

    private fun handlePaymentStatusUnknown(pendingId: String, error: PaymentUiError) {
        val record = pendingTracker.get(pendingId) ?: return
        val showDirectResult = shouldShowDirectPaymentResult(record.visible)
        if (showDirectResult) markTransactionSeen(pendingId)
        clearPaymentSessionState()
        pendingTracker.markStatusUnknown(pendingId, error)
        contactsController.paymentFinishedWithoutSuccess(pendingId)
        if (mutableUiState.value is PaymentUiState.Loading) {
            mutableUiState.value = PaymentUiState.Active
        }
        if (showDirectResult) {
            mutableTransactionDetailNavigationTarget.value = pendingId
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
        val record = pendingTracker.get(id)
            ?.takeIf { it.status == PendingStatus.StatusUnknown }
            ?: return
        mutableUiState.value = PaymentUiState.Loading()
        pendingTracker.markSending(id)
        launchPayment(
            pendingId = id,
            invoice = record.summary,
            amountOverrideMsats = record.amountOverrideMsats
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
        pendingRetry = PendingRetryChoice(record.id, continuation)
        mutableUiState.value = PaymentUiState.PendingRetry(record.id)
    }

    private fun sessionTransactionsOpened() {
        newSessionTransactionIds.clear()
        knownSessionTransactionIds += pendingTracker.displayItems.value.map { it.id }
        mutableNewSessionTransactionCount.value = 0
    }

    private fun markTransactionSeen(id: String) {
        knownSessionTransactionIds += id
        newSessionTransactionIds -= id
        mutableNewSessionTransactionCount.value = newSessionTransactionIds.size
    }

    private fun refreshNewSessionTransactionCount(items: List<SessionTransactionItem>) {
        val itemIds = items.mapTo(mutableSetOf(), SessionTransactionItem::id)
        knownSessionTransactionIds.retainAll(itemIds)
        newSessionTransactionIds.retainAll(itemIds)
        val unseenIds = itemIds.filterNot(knownSessionTransactionIds::contains)
        if (unseenIds.isNotEmpty()) {
            knownSessionTransactionIds += unseenIds
            newSessionTransactionIds += unseenIds
        }
        mutableNewSessionTransactionCount.value = newSessionTransactionIds.size
    }

    private fun requestTransactionDetailNavigation(id: String) {
        if (pendingTracker.get(id) == null) return
        markTransactionSeen(id)
        mutableUiState.value = PaymentUiState.Active
        mutableTransactionDetailNavigationTarget.value = id
    }

    private fun transactionDetailNavigationHandled(id: String) {
        if (mutableTransactionDetailNavigationTarget.value == id) {
            mutableTransactionDetailNavigationTarget.value = null
        }
    }

    private fun dismissResult() {
        mutableTransactionDetailNavigationTarget.value = null
        mutableUiState.value = PaymentUiState.Active
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
            mutableUiState.value = PaymentUiState.EnterAmount(entry)
        }
    }

    private fun refreshResultState() {
        when (val state = mutableUiState.value) {
            is PaymentUiState.Success -> {
                val payment = lastPaymentResult ?: return
                mutableUiState.value = payment.toUiState(currencyManager.state.value)
            }

            is PaymentUiState.Confirm -> {
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
        val currencyState = currencyManager.state.value
        manualEntryContext = null
        pendingPayment = null
        manualAmount.reset(
            ManualAmountConfig(
                info = currencyState.info,
                exchangeRate = currencyState.exchangeRate
            ),
            clearInput = true
        )
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

private data class PendingPayment(
    val invoice: Bolt11Invoice,
    val amountOverrideMsats: Long?,
    val origin: PendingOrigin,
    val dynamicSourceKey: DynamicPaymentSourceKey?,
    val contactContext: PaymentContactContext?,
    val replacesDynamicGuardId: String?
)

private data class PendingRetryChoice(
    val recordId: String,
    val continuation: PendingRetryContinuation
)

private data class CompletedPayment(
    val amountMsats: Long,
    val feeMsats: Long,
    val showEstimatedFeeHint: Boolean,
    val wasAlreadyPaid: Boolean,
    val preimage: String?
)

private data class LnurlSession(
    val params: LnurlPayParams,
    val sourceKey: DynamicPaymentSourceKey?,
    val paymentSource: PaymentRequestSource,
    val contactContext: PaymentContactContext?,
    val comment: String?,
    val replacesDynamicGuardId: String?
)

private enum class PaymentRequestSource {
    Camera,
    DeepLink
}

private sealed interface ManualEntryContext {
    data class Bolt(val invoice: Bolt11Invoice, val source: PaymentRequestSource) :
        ManualEntryContext

    data class Lnurl(val session: LnurlSession, val inputInfo: CurrencyInfo) :
        ManualEntryContext
}

private sealed interface PendingRetryContinuation {
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

private fun LnurlError.toPaymentUiError(): PaymentUiError = when (this) {
    LnurlError.NetworkUnavailable ->
        PaymentUiError.Blink(BlinkApiError.NetworkUnavailable)

    is LnurlError.Protocol -> PaymentUiError.Lnurl(reason)

    is LnurlError.Unexpected -> PaymentUiError.Lnurl(detail)
}

internal fun roundToFullSatoshis(msats: Long): Long =
    ((msats + MSATS_PER_SAT - 1) / MSATS_PER_SAT) * MSATS_PER_SAT

private const val MSATS_PER_SAT = 1_000L
