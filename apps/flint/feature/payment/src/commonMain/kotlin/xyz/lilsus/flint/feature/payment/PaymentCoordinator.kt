package xyz.lilsus.flint.feature.payment

import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.lilsus.flint.application.payment.AmountRequiredPayment
import xyz.lilsus.flint.application.payment.ClaimedPaymentLink
import xyz.lilsus.flint.application.payment.ConfirmPaymentResult
import xyz.lilsus.flint.application.payment.FiatAmountQuoteResult
import xyz.lilsus.flint.application.payment.FiatMinorAmount
import xyz.lilsus.flint.application.payment.PaymentActivity
import xyz.lilsus.flint.application.payment.PaymentDraftHandle
import xyz.lilsus.flint.application.payment.PaymentEngine
import xyz.lilsus.flint.application.payment.PaymentLinkInbox
import xyz.lilsus.flint.application.payment.PaymentOrigin
import xyz.lilsus.flint.application.payment.PaymentOutcome
import xyz.lilsus.flint.application.payment.PaymentRejection
import xyz.lilsus.flint.application.payment.PreparePaymentResult
import xyz.lilsus.flint.application.payment.PreparedPayment
import xyz.lilsus.flint.feature.payment.amount.ManualAmountConfig
import xyz.lilsus.flint.feature.payment.amount.ManualAmountController
import xyz.lilsus.flint.feature.payment.amount.ManualAmountKey
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.Satoshi
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.core.ui.platform.HapticFeedbackManager
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentPreferencesRepository

class PaymentCoordinator(
    private val engine: PaymentEngine,
    private val paymentLinks: PaymentLinkInbox,
    bitcoinPriceProvider: BitcoinPriceProvider,
    private val currencyPreferences: CurrencyPreferences,
    private val paymentPreferences: PaymentPreferencesRepository,
    contactsRepository: ContactsRepository,
    private val haptics: HapticFeedbackManager,
    coroutineContext: CoroutineContext = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private val actionMutex = Mutex()
    private val currencyManager = PaymentCurrencyManager(bitcoinPriceProvider, scope)
    private val manualAmount =
        ManualAmountController(
            ManualAmountConfig(
                info = CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE),
                exchangeRate = null
            )
        )
    private val contactsController =
        PaymentContactsController(
            repository = contactsRepository,
            currencyManager = currencyManager,
            scope = scope,
            onPaymentRequested = ::resolveContactPayment,
            onError = ::showError
        )

    private val mutableUiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Active)
    val uiState: StateFlow<PaymentUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<PaymentEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PaymentEvent> = mutableEvents.asSharedFlow()

    private val mutableSessionTransactions =
        MutableStateFlow<List<SessionTransactionItem>>(emptyList())
    val sessionTransactions: StateFlow<List<SessionTransactionItem>> =
        mutableSessionTransactions.asStateFlow()

    val contactsState = contactsController.state

    private val mutableTransactionDetailNavigationTarget = MutableStateFlow<String?>(null)
    val transactionDetailNavigationTarget: StateFlow<String?> =
        mutableTransactionDetailNavigationTarget.asStateFlow()

    private val mutableNewSessionTransactionCount = MutableStateFlow(0)
    val newSessionTransactionCount: StateFlow<Int> =
        mutableNewSessionTransactionCount.asStateFlow()

    private val sessionAttemptIds = linkedSetOf<String>()
    private val knownSessionTransactionIds = mutableSetOf<String>()
    private val newSessionTransactionIds = mutableSetOf<String>()
    private val terminalContactUpdates = mutableSetOf<String>()
    private val successNotifications = mutableSetOf<String>()
    private var activeDraft: ActiveDraft? = null
    private var manualRequest: ManualRequest? = null
    private var activeAttemptId: String? = null
    private var visibleActivity: VisibleActivity? = null
    private var activeLinkId: String? = null
    private var vibrateOnScan = true
    private var vibrateOnPayment = true
    private var pendingPresentationJob: Job? = null

    init {
        scope.launch {
            paymentPreferences.preferences.collectLatest { preferences ->
                vibrateOnScan = preferences.vibrateOnScan
                vibrateOnPayment = preferences.vibrateOnPayment
            }
        }
        scope.launch {
            currencyPreferences.primaryCode.collectLatest { code ->
                currencyManager.setPreferredCurrency(CurrencyCatalog.infoFor(code).currency)
            }
        }
        scope.launch {
            currencyManager.state.collectLatest {
                refreshCurrencyDependentPresentation()
            }
        }
        scope.launch {
            currencyManager.errors.collectLatest { mutableEvents.emit(PaymentEvent.ShowError(it)) }
        }
        scope.launch {
            engine.activity.collectLatest(::handleActivityUpdate)
        }
        scope.launch {
            paymentLinks.revision.collect {
                actionMutex.withLock { handlePendingPaymentLink() }
            }
        }
    }

    fun dispatch(intent: PaymentIntent) {
        scope.launch {
            actionMutex.withLock {
                try {
                    handleIntent(intent)
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    showError(
                        PaymentUiError.Spark(
                            SparkPaymentError.Unexpected(cause.message)
                        )
                    )
                }
            }
        }
    }

    fun clear() {
        pendingPresentationJob?.cancel()
        scope.cancel()
    }

    fun resetSession() {
        pendingPresentationJob?.cancel()
        sessionAttemptIds.clear()
        knownSessionTransactionIds.clear()
        newSessionTransactionIds.clear()
        terminalContactUpdates.clear()
        successNotifications.clear()
        activeDraft = null
        manualRequest = null
        activeAttemptId = null
        visibleActivity = null
        mutableSessionTransactions.value = emptyList()
        mutableNewSessionTransactionCount.value = 0
        mutableTransactionDetailNavigationTarget.value = null
        mutableUiState.value = PaymentUiState.Active
    }

    private suspend fun handleIntent(intent: PaymentIntent) {
        when (intent) {
            PaymentIntent.DismissResult -> dismissResult()

            is PaymentIntent.TransactionDetailNavigationHandled ->
                transactionDetailNavigationHandled(intent.id)

            PaymentIntent.SessionTransactionsOpened -> sessionTransactionsOpened()

            is PaymentIntent.QrCodeScanned ->
                handlePaymentInput(
                    rawInput = intent.rawValue,
                    origin = PaymentOrigin.DETECTED_CONTENT,
                    notifyScan = true
                )

            is PaymentIntent.DeepLinkReceived ->
                handlePaymentInput(intent.rawValue, PaymentOrigin.DEEP_LINK)

            PaymentIntent.ManualAmountDismiss -> dismissManualAmount()

            PaymentIntent.ManualAmountSubmit -> submitManualAmount()

            is PaymentIntent.ManualAmountKeyPress -> updateManualAmount(intent.key)

            is PaymentIntent.ManualAmountPreset -> presetManualAmount(intent.amount)

            PaymentIntent.ConfirmPaymentDismiss -> dismissConfirmation()

            PaymentIntent.ConfirmPaymentSubmit -> submitConfirmation()

            PaymentIntent.PendingRetryCreateNewInvoice,
            PaymentIntent.PendingRetryRetryPrevious,
            PaymentIntent.PendingRetryDismiss -> dismissResult()

            PaymentIntent.PendingRetryViewPending -> {
                val id = (mutableUiState.value as? PaymentUiState.PendingRetry)?.id
                if (id != null) requestTransactionDetailNavigation(id)
            }

            is PaymentIntent.RetryTransaction -> retryTransaction(intent.id)

            is PaymentIntent.StartDonation ->
                startDonation(intent.amountSats, intent.address)

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

    private suspend fun handlePaymentInput(
        rawInput: String,
        origin: PaymentOrigin,
        notifyScan: Boolean = false,
        contactContext: PaymentContactContext? = reusableLightningAddress(rawInput)?.let {
            contactsController.contextFor(it, allowSavePrompt = true)
        },
        requestedAmountMsats: Long? = null
    ) {
        if (mutableUiState.value != PaymentUiState.Active) return
        clearTransientPaymentState()
        if (notifyScan && vibrateOnScan) ignoreHapticFailure(haptics::notifyScanSuccess)
        mutableUiState.value = PaymentUiState.Detected
        mutableUiState.value = PaymentUiState.Loading(LoadingKind.Resolving)
        applyPrepareResult(
            result = engine.prepare(rawInput, origin),
            contactContext = contactContext,
            requestedAmountMsats = requestedAmountMsats
        )
    }

    private suspend fun applyPrepareResult(
        result: PreparePaymentResult,
        contactContext: PaymentContactContext?,
        requestedAmountMsats: Long?
    ) {
        when (result) {
            is PreparePaymentResult.AmountRequired -> {
                manualRequest = ManualRequest(result.payment, contactContext)
                if (requestedAmountMsats != null) {
                    prepareRequestedAmount(result.payment, requestedAmountMsats, contactContext)
                } else {
                    showManualAmount(result.payment, clearInput = true)
                }
            }

            is PreparePaymentResult.Ready ->
                handlePreparedPayment(result.payment, contactContext)

            is PreparePaymentResult.Existing -> {
                sessionAttemptIds += result.activity.attemptId
                refreshSessionTransactions(engine.activity.value)
                showActivity(result.activity, wasAlreadyPaid = true)
            }

            is PreparePaymentResult.Rejected ->
                showError(result.reason.toPaymentUiError())

            PreparePaymentResult.WalletUnavailable ->
                showError(PaymentUiError.Spark(SparkPaymentError.WalletUnavailable))

            PreparePaymentResult.SdkFailure ->
                showError(PaymentUiError.Spark(SparkPaymentError.SdkUnavailable))

            PreparePaymentResult.StorageFailure ->
                showError(PaymentUiError.Spark(SparkPaymentError.StorageUnavailable))
        }
    }

    private suspend fun handlePreparedPayment(
        payment: PreparedPayment,
        contactContext: PaymentContactContext?
    ) {
        manualRequest = null
        val draft = ActiveDraft(payment, contactContext)
        activeDraft = draft
        mutableUiState.value =
            if (payment.requiresConfirmation) {
                PaymentUiState.Confirm(display(payment.amountSats))
            } else {
                PaymentUiState.Loading()
            }
        if (!payment.requiresConfirmation) {
            applyConfirmResult(engine.autoPay(payment.handle), draft)
        }
    }

    private suspend fun submitConfirmation() {
        val draft = activeDraft ?: return
        mutableUiState.value = PaymentUiState.Loading()
        applyConfirmResult(engine.confirm(draft.payment.handle), draft)
    }

    private fun applyConfirmResult(result: ConfirmPaymentResult, draft: ActiveDraft) {
        when (result) {
            is ConfirmPaymentResult.Submitted -> {
                activeDraft = null
                activeAttemptId = result.activity.attemptId
                sessionAttemptIds += result.activity.attemptId
                contactsController.bindPendingPayment(
                    result.activity.attemptId,
                    draft.contactContext
                )
                refreshSessionTransactions(engine.activity.value)
                showActivity(result.activity)
            }

            ConfirmPaymentResult.ConfirmationRequired -> {
                activeDraft = draft
                mutableUiState.value =
                    PaymentUiState.Confirm(display(draft.payment.amountSats))
            }

            ConfirmPaymentResult.DraftUnavailable ->
                showError(PaymentUiError.InvalidInvoice("The payment request expired"))

            ConfirmPaymentResult.WalletUnavailable ->
                showError(PaymentUiError.Spark(SparkPaymentError.WalletUnavailable))

            ConfirmPaymentResult.PersistenceFailed ->
                showError(PaymentUiError.Spark(SparkPaymentError.StorageUnavailable))

            ConfirmPaymentResult.CapacityReached ->
                showError(PaymentUiError.Spark(SparkPaymentError.CapacityReached))
        }
    }

    private suspend fun dismissManualAmount() {
        manualRequest?.payment?.handle?.let { engine.cancel(it) }
        manualRequest = null
        finishPaymentInteraction()
    }

    private suspend fun dismissConfirmation() {
        activeDraft?.payment?.handle?.let { engine.cancel(it) }
        activeDraft = null
        finishPaymentInteraction()
    }

    private fun updateManualAmount(key: ManualAmountKey) {
        if (mutableUiState.value !is PaymentUiState.EnterAmount) return
        mutableUiState.value = PaymentUiState.EnterAmount(manualAmount.handleKeyPress(key))
    }

    private fun presetManualAmount(amount: DisplayAmount) {
        if (mutableUiState.value !is PaymentUiState.EnterAmount) return
        mutableUiState.value = PaymentUiState.EnterAmount(manualAmount.presetAmount(amount))
    }

    private suspend fun submitManualAmount() {
        val request = manualRequest ?: return
        val entered = manualAmount.current().amount ?: return
        mutableUiState.value = PaymentUiState.Loading(LoadingKind.Resolving)
        val result =
            when (val currency = entered.currency) {
                is DisplayCurrency.Fiat ->
                    when (
                        val quote =
                            engine.amountAssistant.quoteFiatAmount(
                                FiatMinorAmount(currency.iso4217.uppercase(), entered.minor)
                            )
                    ) {
                        is FiatAmountQuoteResult.Quoted ->
                            engine.prepareAmount(request.payment.handle, quote.quote)

                        FiatAmountQuoteResult.WalletUnavailable ->
                            PreparePaymentResult.WalletUnavailable

                        FiatAmountQuoteResult.CurrencyUnavailable,
                        FiatAmountQuoteResult.RateUnavailable ->
                            PreparePaymentResult.SdkFailure

                        FiatAmountQuoteResult.InvalidAmount ->
                            PreparePaymentResult.Rejected(PaymentRejection.INVALID_AMOUNT)
                    }

                DisplayCurrency.Bitcoin,
                DisplayCurrency.Satoshi -> {
                    val amountMsats = manualAmount.enteredAmountMsats()
                    val sats = amountMsats?.let(::msatsToSatoshi)
                    if (sats == null) {
                        PreparePaymentResult.Rejected(PaymentRejection.INVALID_AMOUNT)
                    } else {
                        engine.prepareAmount(request.payment.handle, sats)
                    }
                }
            }
        applyPrepareResult(result, request.contactContext, requestedAmountMsats = null)
    }

    private suspend fun prepareRequestedAmount(
        payment: AmountRequiredPayment,
        amountMsats: Long,
        contactContext: PaymentContactContext?
    ) {
        val sats = msatsToSatoshi(amountMsats)
        if (
            sats == null ||
            sats.value < payment.minimumAmountSats.value ||
            payment.maximumAmountSats?.let { sats.value > it.value } == true
        ) {
            showError(PaymentUiError.InvalidInvoice("Amount is outside the allowed range"))
            return
        }
        applyPrepareResult(
            engine.prepareAmount(payment.handle, sats),
            contactContext,
            requestedAmountMsats = null
        )
    }

    private fun showManualAmount(payment: AmountRequiredPayment, clearInput: Boolean) {
        currencyManager.ensureExchangeRateIfNeeded()
        val currencyState = currencyManager.state.value
        val minMsats = payment.minimumAmountSats.toMsats()
        val maxMsats = payment.maximumAmountSats?.toMsats()
        val min = currencyManager.convertMsatsToDisplay(minMsats, currencyState)
            .takeIf { it.currency == currencyState.info.currency }
        val max = maxMsats
            ?.let { currencyManager.convertMsatsToDisplay(it, currencyState) }
            ?.takeIf { it.currency == currencyState.info.currency }
        mutableUiState.value =
            PaymentUiState.EnterAmount(
                manualAmount.reset(
                    config =
                        ManualAmountConfig(
                            info = currencyState.info,
                            exchangeRate = currencyState.exchangeRate,
                            min = min,
                            max = max,
                            minMsats = minMsats,
                            maxMsats = maxMsats
                        ),
                    clearInput = clearInput
                )
            )
    }

    private fun handleActivityUpdate(activity: List<PaymentActivity>) {
        refreshSessionTransactions(activity)
        activity.forEach(::updateTerminalContactState)
        val active = activeAttemptId?.let { id -> activity.firstOrNull { it.attemptId == id } }
        if (active != null) {
            when (active.outcome) {
                PaymentOutcome.COMPLETED,
                PaymentOutcome.FAILED,
                PaymentOutcome.SUBMISSION_UNRESOLVED,
                PaymentOutcome.STATUS_UNAVAILABLE -> showActivity(active)

                else -> Unit
            }
        }
    }

    private fun showActivity(activity: PaymentActivity, wasAlreadyPaid: Boolean = false) {
        visibleActivity = VisibleActivity(activity, wasAlreadyPaid)
        pendingPresentationJob?.cancel()
        when (activity.outcome) {
            PaymentOutcome.COMPLETED -> {
                notifyPaymentSuccess(activity.attemptId)
                mutableUiState.value =
                    PaymentUiState.Success(
                        amountPaid = display(activity.amountSats),
                        feePaid = display(activity.feeSats),
                        wasAlreadyPaid = wasAlreadyPaid
                    )
            }

            PaymentOutcome.FAILED ->
                showError(
                    PaymentUiError.Spark(
                        SparkPaymentError.Rejected("The Spark payment failed")
                    ),
                    keepVisibleActivity = true
                )

            PaymentOutcome.SUBMISSION_UNRESOLVED,
            PaymentOutcome.STATUS_UNAVAILABLE ->
                showError(
                    PaymentUiError.Spark(
                        SparkPaymentError.OutcomeUnknown(
                            "The payment may still complete"
                        )
                    ),
                    keepVisibleActivity = true
                )

            PaymentOutcome.CONFIRMATION_RECORDED,
            PaymentOutcome.PENDING -> {
                mutableUiState.value = PaymentUiState.Loading()
                pendingPresentationJob =
                    scope.launch {
                        delay(PENDING_PRESENTATION_TIMEOUT_MS)
                        if (
                            activeAttemptId == activity.attemptId &&
                            mutableUiState.value is PaymentUiState.Loading
                        ) {
                            mutableUiState.value = PaymentUiState.Active
                        }
                    }
            }
        }
    }

    private fun refreshSessionTransactions(activity: List<PaymentActivity>) {
        val items =
            activity
                .asSequence()
                .filter { it.attemptId in sessionAttemptIds }
                .map { it.toSessionTransactionItem() }
                .toList()
        mutableSessionTransactions.value = items
        val unseen = items.map { it.id }.filterNot(knownSessionTransactionIds::contains)
        if (unseen.isNotEmpty()) {
            knownSessionTransactionIds += unseen
            newSessionTransactionIds += unseen
            mutableNewSessionTransactionCount.value = newSessionTransactionIds.size
        }
    }

    private fun PaymentActivity.toSessionTransactionItem(): SessionTransactionItem {
        val error =
            when (outcome) {
                PaymentOutcome.SUBMISSION_UNRESOLVED,
                PaymentOutcome.STATUS_UNAVAILABLE ->
                    PaymentUiError.Spark(
                        SparkPaymentError.OutcomeUnknown("The payment may still complete")
                    )

                PaymentOutcome.FAILED ->
                    PaymentUiError.Spark(
                        SparkPaymentError.Rejected("The Spark payment failed")
                    )

                else -> null
            }
        return SessionTransactionItem(
            id = attemptId,
            amount = display(amountSats),
            status =
                when (outcome) {
                    PaymentOutcome.CONFIRMATION_RECORDED -> PendingStatus.Resolving

                    PaymentOutcome.PENDING -> PendingStatus.Sending

                    PaymentOutcome.SUBMISSION_UNRESOLVED,
                    PaymentOutcome.STATUS_UNAVAILABLE -> PendingStatus.OutcomeUnknown

                    PaymentOutcome.COMPLETED -> PendingStatus.Succeeded

                    PaymentOutcome.FAILED -> PendingStatus.Failed
                },
            createdAtMs = createdAtEpochSeconds * MILLIS_PER_SECOND,
            resultAmount = display(amountSats),
            fee = display(feeSats),
            error = error
        )
    }

    private fun updateTerminalContactState(activity: PaymentActivity) {
        if (activity.attemptId in terminalContactUpdates) return
        when (activity.outcome) {
            PaymentOutcome.COMPLETED -> {
                terminalContactUpdates += activity.attemptId
                contactsController.paymentSucceeded(
                    activity.attemptId,
                    activity.amountSats.toMsats()
                )
            }

            PaymentOutcome.FAILED -> {
                terminalContactUpdates += activity.attemptId
                contactsController.paymentFailed(activity.attemptId)
            }

            else -> Unit
        }
    }

    private fun notifyPaymentSuccess(id: String) {
        if (!vibrateOnPayment || !successNotifications.add(id)) return
        ignoreHapticFailure(haptics::notifyPaymentSuccess)
    }

    private suspend fun handlePendingPaymentLink() {
        if (activeLinkId != null || mutableUiState.value != PaymentUiState.Active) return
        when (val link = paymentLinks.claim() ?: return) {
            is ClaimedPaymentLink.Rejected -> {
                activeLinkId = link.id
                paymentLinks.consume(link.id)
                showError(PaymentUiError.InvalidInvoice("Unsupported payment link"))
            }

            is ClaimedPaymentLink.Request -> {
                activeLinkId = link.id
                val request = link.reveal()
                paymentLinks.consume(link.id)
                handlePaymentInput(request, PaymentOrigin.DEEP_LINK)
            }
        }
    }

    private fun resolveContactPayment(
        address: LightningAddress,
        context: PaymentContactContext,
        amountMsats: Long?,
        comment: String?
    ) {
        scope.launch {
            actionMutex.withLock {
                handlePaymentInput(
                    rawInput = address.full,
                    origin = PaymentOrigin.DETECTED_CONTENT,
                    contactContext = context.copy(comment = comment ?: context.comment),
                    requestedAmountMsats = amountMsats
                )
            }
        }
    }

    private suspend fun startDonation(amountSats: Long, address: LightningAddress) {
        if (amountSats <= 0) return
        handlePaymentInput(
            rawInput = address.full,
            origin = PaymentOrigin.DETECTED_CONTENT,
            contactContext = contactsController.contextFor(address, allowSavePrompt = false),
            requestedAmountMsats = Satoshi.positive(amountSats).toMsats()
        )
    }

    private suspend fun retryTransaction(id: String) {
        if (id !in sessionAttemptIds) return
        mutableUiState.value = PaymentUiState.Loading(LoadingKind.Resolving)
        engine.refresh()
        mutableUiState.value = PaymentUiState.Active
        requestTransactionDetailNavigation(id)
    }

    private fun sessionTransactionsOpened() {
        newSessionTransactionIds.clear()
        knownSessionTransactionIds += mutableSessionTransactions.value.map { it.id }
        mutableNewSessionTransactionCount.value = 0
    }

    private fun requestTransactionDetailNavigation(id: String) {
        if (mutableSessionTransactions.value.none { it.id == id }) return
        mutableUiState.value = PaymentUiState.Active
        mutableTransactionDetailNavigationTarget.value = id
    }

    private fun transactionDetailNavigationHandled(id: String) {
        if (mutableTransactionDetailNavigationTarget.value == id) {
            mutableTransactionDetailNavigationTarget.value = null
        }
    }

    private suspend fun dismissResult() {
        finishPaymentInteraction()
        handlePendingPaymentLink()
    }

    private fun finishPaymentInteraction() {
        pendingPresentationJob?.cancel()
        clearTransientPaymentState()
        activeLinkId?.let(paymentLinks::finish)
        activeLinkId = null
        mutableUiState.value = PaymentUiState.Active
    }

    private fun clearTransientPaymentState() {
        activeDraft = null
        manualRequest = null
        activeAttemptId = null
        visibleActivity = null
    }

    private fun showError(error: PaymentUiError, keepVisibleActivity: Boolean = false) {
        if (!keepVisibleActivity) visibleActivity = null
        mutableUiState.value = PaymentUiState.Error(error)
    }

    private fun refreshCurrencyDependentPresentation() {
        manualRequest?.let { request ->
            if (mutableUiState.value is PaymentUiState.EnterAmount) {
                showManualAmount(request.payment, clearInput = false)
            }
        }
        visibleActivity?.let { visible ->
            when (mutableUiState.value) {
                is PaymentUiState.Success,
                is PaymentUiState.Error -> showActivity(
                    visible.activity,
                    visible.wasAlreadyPaid
                )

                else -> Unit
            }
        }
        refreshSessionTransactions(engine.activity.value)
    }

    private fun display(amount: Satoshi): DisplayAmount =
        currencyManager.convertMsatsToDisplay(amount.toMsats())

    private data class ActiveDraft(
        val payment: PreparedPayment,
        val contactContext: PaymentContactContext?
    )

    private data class ManualRequest(
        val payment: AmountRequiredPayment,
        val contactContext: PaymentContactContext?
    )

    private data class VisibleActivity(val activity: PaymentActivity, val wasAlreadyPaid: Boolean)
}

private fun PaymentRejection.toPaymentUiError(): PaymentUiError = when (this) {
    PaymentRejection.UNSUPPORTED_INPUT,
    PaymentRejection.ON_CHAIN_NOT_ALLOWED,
    PaymentRejection.AMOUNT_REQUIRED,
    PaymentRejection.INVALID_AMOUNT,
    PaymentRejection.EXPIRED,
    PaymentRejection.WRONG_NETWORK,
    PaymentRejection.TOKEN_NOT_ALLOWED,
    PaymentRejection.SENDER_NOT_ALLOWED,
    PaymentRejection.METHOD_MISMATCH,
    PaymentRejection.CONVERSION_NOT_ALLOWED ->
        PaymentUiError.InvalidInvoice(toReadableMessage())

    PaymentRejection.INSUFFICIENT_FUNDS ->
        PaymentUiError.Spark(SparkPaymentError.Rejected("Insufficient balance"))
}

private fun PaymentRejection.toReadableMessage(): String = name.lowercase().replace('_', ' ')

private fun reusableLightningAddress(input: String): LightningAddress? =
    LightningAddress.parse(input)

private fun msatsToSatoshi(msats: Long): Satoshi? {
    if (msats <= 0) return null
    val sats = (msats + MSATS_PER_SAT - 1) / MSATS_PER_SAT
    return Satoshi.positive(sats)
}

private fun Satoshi.toMsats(): Long =
    value.coerceAtMost(Long.MAX_VALUE / MSATS_PER_SAT) * MSATS_PER_SAT

private inline fun ignoreHapticFailure(block: () -> Unit) {
    try {
        block()
    } catch (_: Throwable) {
        // Haptics are optional feedback.
    }
}

internal fun platformCurrentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

internal fun roundToFullSatoshis(msats: Long): Long =
    ((msats + MSATS_PER_SAT - 1) / MSATS_PER_SAT) * MSATS_PER_SAT

private const val MSATS_PER_SAT = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val PENDING_PRESENTATION_TIMEOUT_MS = 4_000L
