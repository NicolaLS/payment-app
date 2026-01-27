package xyz.lilsus.papp.presentation.main

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceParser
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceSummary
import xyz.lilsus.papp.domain.bolt11.Bolt11Memo
import xyz.lilsus.papp.domain.bolt11.Bolt11ParseResult
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.lnurl.LightningInputParser
import xyz.lilsus.papp.domain.lnurl.LnurlPayParams
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.model.CurrencyInfo
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.usecases.FetchLnurlPayParamsUseCase
import xyz.lilsus.papp.domain.usecases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObservePaymentPreferencesUseCase
import xyz.lilsus.papp.domain.usecases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.usecases.PayInvoiceUseCase
import xyz.lilsus.papp.domain.usecases.RequestLnurlInvoiceUseCase
import xyz.lilsus.papp.domain.usecases.ResolveLightningAddressUseCase
import xyz.lilsus.papp.domain.usecases.ShouldConfirmPaymentUseCase
import xyz.lilsus.papp.platform.HapticFeedbackManager
import xyz.lilsus.papp.presentation.main.amount.ManualAmountConfig
import xyz.lilsus.papp.presentation.main.amount.ManualAmountController
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey

class MainViewModel internal constructor(
    private val payInvoice: PayInvoiceUseCase,
    private val observeWalletConnection: ObserveWalletConnectionUseCase,
    private val observeCurrencyPreference: ObserveCurrencyPreferenceUseCase,
    private val currencyManager: CurrencyManager,
    private val pendingTracker: PendingPaymentTracker,
    private val bolt11Parser: Bolt11InvoiceParser,
    private val manualAmount: ManualAmountController,
    private val shouldConfirmPayment: ShouldConfirmPaymentUseCase,
    private val lightningInputParser: LightningInputParser,
    private val fetchLnurlPayParams: FetchLnurlPayParamsUseCase,
    private val resolveLightningAddressUseCase: ResolveLightningAddressUseCase,
    private val requestLnurlInvoice: RequestLnurlInvoiceUseCase,
    private val observePaymentPreferences: ObservePaymentPreferencesUseCase,
    private val haptics: HapticFeedbackManager,
    dispatcher: CoroutineDispatcher
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Active)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    val pendingPayments: StateFlow<List<PendingPaymentItem>> = pendingTracker.displayItems

    /** Tracks which pending item the user is currently viewing (loading or result) */
    private var openPendingId: String? = null

    private var pendingInvoice: Bolt11InvoiceSummary? = null
    private var manualEntryContext: ManualEntryContext? = null
    private var pendingPayment: PendingPayment? = null
    private var lastPaymentResult: CompletedPayment? = null
    private var parsedInvoiceCache: Pair<String, Bolt11InvoiceSummary>? = null
    private var vibrateOnScan: Boolean = true
    private var vibrateOnPayment: Boolean = true
    private val pendingCollectionJobs = mutableMapOf<String, Job>()
    private var currentWalletUri: String? = null
    private var currentWalletType: WalletType? = null

    init {
        currencyManager.onStateChanged = { refreshAllDisplays() }

        scope.launch {
            observeWalletConnection().collectLatest { connection ->
                currentWalletUri = connection?.uri
                currentWalletType = connection?.type
                if (connection == null && _uiState.value is MainUiState.Success) {
                    _uiState.value = MainUiState.Active
                }
            }
        }
        scope.launch {
            observePaymentPreferences().collectLatest { prefs ->
                vibrateOnScan = prefs.vibrateOnScan
                vibrateOnPayment = prefs.vibrateOnPayment
            }
        }
        scope.launch {
            observeCurrencyPreference().collectLatest { currency ->
                currencyManager.setPreferredCurrency(currency)
            }
        }
        scope.launch {
            currencyManager.errors.collect { error ->
                _events.tryEmit(MainEvent.ShowError(error))
            }
        }
        scope.launch {
            pendingTracker.events.collect { event ->
                handlePendingEvent(event)
            }
        }
    }

    private fun handlePendingEvent(event: PendingEvent) {
        when (event) {
            is PendingEvent.BecameVisible -> {
                // If we're still showing loading for this payment, return to Active
                // But NOT if user is explicitly watching this pending entry
                val currentState = _uiState.value
                val isWatchingThis = openPendingId == event.id &&
                    currentState is MainUiState.Loading &&
                    currentState.isWatchingPending
                if (currentState is MainUiState.Loading && !isWatchingThis) {
                    _uiState.value = MainUiState.Active
                }
            }

            is PendingEvent.Settled -> {
                // Only handle if user is viewing this pending entry
                if (openPendingId == event.id) {
                    if (vibrateOnPayment) haptics.notifyPaymentSuccess()
                    val currencyState = currencyManager.state.value
                    _uiState.value = MainUiState.Success(
                        amountPaid = currencyManager.convertMsatsToDisplay(
                            event.paidMsats,
                            currencyState
                        ),
                        feePaid = currencyManager.convertMsatsToDisplay(
                            event.feeMsats,
                            currencyState
                        ),
                        showBlinkFeeHint =
                            pendingTracker.get(event.id)?.walletType == WalletType.BLINK
                    )
                }
            }

            is PendingEvent.Failed -> {
                // Only handle if user is viewing this pending entry
                if (openPendingId == event.id) {
                    _uiState.value = MainUiState.Error(event.error)
                }
            }
        }
    }

    fun dispatch(intent: MainIntent) {
        when (intent) {
            MainIntent.DismissResult -> handleDismissResult()
            is MainIntent.InvoiceDetected -> handleInvoiceDetected(intent.invoice)
            MainIntent.ManualAmountDismiss -> handleManualAmountDismiss()
            MainIntent.ManualAmountSubmit -> handleManualAmountSubmit()
            is MainIntent.ManualAmountKeyPress -> handleManualAmountKeyPress(intent.key)
            is MainIntent.ManualAmountPreset -> handleManualAmountPreset(intent.amount)
            MainIntent.ConfirmPaymentDismiss -> handleConfirmPaymentDismiss()
            MainIntent.ConfirmPaymentSubmit -> handleConfirmPaymentSubmit()
            is MainIntent.StartDonation -> handleDonation(intent.amountSats, intent.address)
            is MainIntent.TapPending -> handlePendingTap(intent.id)
        }
    }

    private fun handleInvoiceDetected(rawInput: String) {
        if (_uiState.value !is MainUiState.Active) return

        manualEntryContext = null
        pendingInvoice = null

        when (val parse = lightningInputParser.parse(rawInput)) {
            is LightningInputParser.ParseResult.Failure -> emitError(
                AppError.InvalidWalletUri(parse.reason)
            )

            is LightningInputParser.ParseResult.Success -> {
                when (val target = parse.target) {
                    is LightningInputParser.Target.Bolt11Candidate -> {
                        // Check if already pending for current wallet - if so, show its status
                        val existingPending = findPendingForCurrentWallet(target.invoice)
                        if (existingPending != null) {
                            openPendingEntry(existingPending.id)
                            return
                        }
                        if (vibrateOnScan) haptics.notifyScanSuccess()
                        processBoltInvoice(target.invoice)
                    }

                    is LightningInputParser.Target.Lnurl -> {
                        if (vibrateOnScan) haptics.notifyScanSuccess()
                        fetchLnurl(target.endpoint, LnurlSource.Lnurl)
                    }

                    is LightningInputParser.Target.LightningAddressTarget -> {
                        if (vibrateOnScan) haptics.notifyScanSuccess()
                        resolveLightningAddress(target.address)
                    }
                }
            }
        }
    }

    /**
     * Checks if invoice is already pending for the current wallet.
     * Returns the pending record if found, null otherwise.
     */
    private fun findPendingForCurrentWallet(invoice: String): PendingRecord? {
        val summary = parseBolt11Invoice(invoice) ?: return null
        return pendingTracker.findByInvoiceAndWallet(summary.paymentRequest, currentWalletUri)
    }

    private fun handleDonation(amountSats: Long, address: LightningAddress) {
        if (amountSats <= 0) return
        scope.launch {
            pendingInvoice = null
            pendingPayment = null
            manualEntryContext = null
            val amountMsats = amountSats * MSATS_PER_SAT
            _uiState.value = MainUiState.Loading()
            when (val result = resolveLightningAddressUseCase(address)) {
                is Result.Success -> {
                    val satInfo = CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE)
                    handleLnurlParams(
                        params = result.data,
                        source = LnurlSource.LightningAddress,
                        forceManualEntry = true,
                        prefillMsats = amountMsats,
                        inputCurrencyOverride = satInfo
                    )
                }

                is Result.Error -> emitError(result.error)

                Result.Loading -> Unit
            }
        }
    }

    private fun parseBolt11Invoice(invoice: String): Bolt11InvoiceSummary? {
        // Check cache first
        parsedInvoiceCache?.let { (cachedInvoice, summary) ->
            if (cachedInvoice == invoice) return summary
        }

        // Parse and cache
        return when (val result = bolt11Parser.parse(invoice)) {
            is Bolt11ParseResult.Success -> {
                parsedInvoiceCache = invoice to result.invoice
                result.invoice
            }

            is Bolt11ParseResult.Failure -> null
        }
    }

    private fun processBoltInvoice(invoice: String) {
        val summary = parseBolt11Invoice(invoice)
        if (summary == null) {
            emitError(AppError.InvalidWalletUri("Failed to parse BOLT11 invoice"))
            return
        }

        // If this invoice is already pending for the current wallet, just ensure chip is visible
        pendingTracker.findByInvoiceAndWallet(summary.paymentRequest, currentWalletUri)
            ?.let { existing ->
                pendingTracker.makeVisible(existing.id)
                return
            }

        pendingInvoice = summary
        val entryState = manualAmount.reset(
            ManualAmountConfig(
                info = currencyManager.state.value.info,
                exchangeRate = currencyManager.state.value.exchangeRate,
                minMsats = null,
                maxMsats = null
            ),
            clearInput = true
        )
        if (summary.amountMsats == null) {
            manualEntryContext = ManualEntryContext.Bolt(summary)
            _uiState.value = MainUiState.EnterAmount(entryState)
        } else {
            requestPayment(
                summary = summary,
                amountOverrideMsats = null,
                origin = PendingOrigin.Invoice
            )
        }
    }

    private fun fetchLnurl(endpoint: String, source: LnurlSource) {
        _uiState.value = MainUiState.Loading()
        scope.launch {
            when (val result = fetchLnurlPayParams(endpoint)) {
                is Result.Success -> handleLnurlParams(result.data, source)
                is Result.Error -> emitError(result.error)
                Result.Loading -> Unit
            }
        }
    }

    private fun resolveLightningAddress(address: LightningAddress) {
        _uiState.value = MainUiState.Loading()
        scope.launch {
            when (val result = resolveLightningAddressUseCase(address)) {
                is Result.Success -> handleLnurlParams(result.data, LnurlSource.LightningAddress)
                is Result.Error -> emitError(result.error)
                Result.Loading -> Unit
            }
        }
    }

    private fun handleLnurlParams(
        params: LnurlPayParams,
        source: LnurlSource,
        forceManualEntry: Boolean = false,
        prefillMsats: Long? = null,
        inputCurrencyOverride: CurrencyInfo? = null
    ) {
        if (params.minSendable <= 0 || params.maxSendable < params.minSendable) {
            emitError(AppError.InvalidWalletUri("LNURL amount range is invalid"))
            return
        }
        val session = LnurlSession(params = params, source = source)
        val currencyState = currencyManager.state.value
        val inputInfo = inputCurrencyOverride ?: currencyState.info
        val manualState = CurrencyState(
            info = inputInfo,
            exchangeRate = currencyState.exchangeRate.takeIf {
                inputInfo.code.equals(currencyState.info.code, ignoreCase = true)
            }
        )

        if (currencyManager.needsExchangeRate(inputInfo) &&
            inputInfo.code.equals(currencyState.info.code, ignoreCase = true)
        ) {
            currencyManager.ensureExchangeRateIfNeeded(inputInfo)
        }

        val shouldForceManualEntry = forceManualEntry || params.minSendable != params.maxSendable
        if (!shouldForceManualEntry) {
            payLnurlInvoice(session, params.minSendable, isManualEntry = false)
            return
        }

        manualEntryContext = ManualEntryContext.Lnurl(session, inputInfo)
        val minDisplay = currencyManager.convertMsatsToDisplay(params.minSendable, manualState)
        val maxDisplay = currencyManager.convertMsatsToDisplay(params.maxSendable, manualState)
        val clampedPrefill = prefillMsats?.coerceIn(params.minSendable, params.maxSendable)
        val config = ManualAmountConfig(
            info = manualState.info,
            exchangeRate = manualState.exchangeRate,
            min = minDisplay,
            max = maxDisplay,
            minMsats = params.minSendable,
            maxMsats = params.maxSendable
        )
        val baseEntry = manualAmount.reset(config, clearInput = true)
        val entry = clampedPrefill?.let {
            manualAmount.presetAmount(currencyManager.convertMsatsToDisplay(it, manualState))
        } ?: baseEntry
        _uiState.value = MainUiState.EnterAmount(entry)
    }

    private fun payLnurlInvoice(session: LnurlSession, amountMsats: Long, isManualEntry: Boolean) {
        _uiState.value = MainUiState.Loading()
        scope.launch {
            // Round to full satoshis - many Lightning servers only accept full sat amounts
            val roundedAmount = roundToFullSatoshis(amountMsats)
            when (val result = requestLnurlInvoice(session.params.callback, roundedAmount)) {
                is Result.Success -> handleLnurlInvoice(
                    session,
                    roundedAmount,
                    result.data,
                    isManualEntry
                )

                is Result.Error -> {
                    manualEntryContext = null
                    emitError(result.error)
                }

                Result.Loading -> Unit
            }
        }
    }

    private fun handleLnurlInvoice(
        session: LnurlSession,
        amountMsats: Long,
        invoice: String,
        isManualEntry: Boolean
    ) {
        val parsed = parseBolt11Invoice(invoice)
        if (parsed == null) {
            manualEntryContext = null
            emitError(AppError.InvalidWalletUri("Failed to parse BOLT11 invoice"))
            return
        }

        if (parsed.amountMsats != amountMsats) {
            manualEntryContext = null
            emitError(
                AppError.InvalidWalletUri(
                    "LNURL server returned amount (${parsed.amountMsats} msat) that does not match requested amount ($amountMsats msat)"
                )
            )
            return
        }

        val memoValid = validateLnurlMemo(parsed.memo, session.params)
        if (!memoValid) {
            manualEntryContext = null
            emitError(AppError.InvalidWalletUri("LNURL invoice metadata mismatch"))
            return
        }

        pendingInvoice = parsed
        val origin = if (isManualEntry) PendingOrigin.LnurlManual else PendingOrigin.LnurlFixed
        // Don't pass amount override for LNURL invoices - they already have the amount embedded.
        // The amount parameter in NWC is only for zero-amount invoices.
        requestPayment(
            summary = parsed,
            amountOverrideMsats = null,
            origin = origin
        )
    }

    private fun validateLnurlMemo(memo: Bolt11Memo, params: LnurlPayParams): Boolean = when (memo) {
        is Bolt11Memo.Text -> {
            params.metadata.plainText?.let { it == memo.value } ?: true
        }

        is Bolt11Memo.HashOnly -> true

        Bolt11Memo.None -> true
    }

    private fun handleManualAmountKeyPress(key: ManualAmountKey) {
        if (_uiState.value !is MainUiState.EnterAmount) return
        manualEntryContext ?: return
        _uiState.value = MainUiState.EnterAmount(entry = manualAmount.handleKeyPress(key))
    }

    private fun handleManualAmountPreset(amount: DisplayAmount) {
        if (_uiState.value !is MainUiState.EnterAmount) return
        manualEntryContext ?: return
        _uiState.value = MainUiState.EnterAmount(entry = manualAmount.presetAmount(amount))
    }

    private fun handleManualAmountSubmit() {
        if (_uiState.value !is MainUiState.EnterAmount) return
        val context = manualEntryContext ?: return
        val amountMsats = manualAmount.enteredAmountMsats()
        if (amountMsats == null || amountMsats <= 0) {
            if (currencyManager.needsExchangeRate()) {
                currencyManager.ensureExchangeRateIfNeeded(currencyManager.state.value.info)
            }
            return
        }
        if (currencyManager.needsExchangeRate()) {
            currencyManager.ensureExchangeRateIfNeeded(currencyManager.state.value.info)
        }

        when (context) {
            is ManualEntryContext.Bolt -> {
                // Round to full satoshis - NWC wallets only accept full sat amounts
                val roundedAmount = roundToFullSatoshis(amountMsats)
                requestPayment(
                    summary = context.invoice,
                    amountOverrideMsats = roundedAmount,
                    origin = PendingOrigin.ManualEntry
                )
            }

            is ManualEntryContext.Lnurl -> {
                val params = context.session.params
                if (amountMsats < params.minSendable || amountMsats > params.maxSendable) {
                    _events.tryEmit(
                        MainEvent.ShowError(
                            AppError.InvalidWalletUri("Amount is outside the allowed range")
                        )
                    )
                    return
                }
                payLnurlInvoice(context.session, amountMsats, isManualEntry = true)
            }
        }
    }

    private fun handleManualAmountDismiss() {
        manualAmount.reset()
        manualEntryContext = null
        pendingInvoice = null
        _uiState.value = MainUiState.Active
    }

    private fun handleConfirmPaymentDismiss() {
        val pending = pendingPayment ?: return
        pendingPayment = null
        when (pending.origin) {
            PendingOrigin.Invoice -> {
                pendingInvoice = null
                manualEntryContext = null
                _uiState.value = MainUiState.Active
            }

            PendingOrigin.ManualEntry -> {
                _uiState.value = MainUiState.EnterAmount(entry = manualAmount.current())
            }

            PendingOrigin.LnurlManual -> {
                _uiState.value = MainUiState.EnterAmount(entry = manualAmount.current())
            }

            PendingOrigin.LnurlFixed -> {
                _uiState.value = MainUiState.Active
            }
        }
    }

    private fun handleConfirmPaymentSubmit() {
        val pending = pendingPayment ?: return
        pendingPayment = null
        startPayment(
            summary = pending.summary,
            amountOverrideMsats = pending.overrideAmountMsats,
            origin = pending.origin
        )
    }

    private fun handlePendingTap(id: String) {
        openPendingEntry(id)
    }

    private fun openPendingEntry(id: String) {
        val record = pendingTracker.get(id) ?: return
        val currencyState = currencyManager.state.value

        // Mark this entry as open
        openPendingId = id

        when (record.status) {
            PendingStatus.Waiting -> {
                // Still waiting - show loading with watching mode enabled
                // User can watch as long as they want and tap to dismiss
                _uiState.value = MainUiState.Loading(isWatchingPending = true)
            }

            PendingStatus.Success -> {
                showSuccessForPending(record, currencyState)
            }

            PendingStatus.Failure -> {
                val error = record.error ?: AppError.Unexpected(null)
                _uiState.value = MainUiState.Error(error)
            }
        }
    }

    /**
     * Shows the success screen for a pending entry.
     */
    private fun showSuccessForPending(record: PendingRecord, currencyState: CurrencyState) {
        val paid = record.paidMsats ?: record.amountMsats
        val fee = record.feeMsats ?: 0L
        val showBlinkFeeHint = record.walletType == WalletType.BLINK
        lastPaymentResult = CompletedPayment(
            amountMsats = paid,
            feeMsats = fee,
            showBlinkFeeHint = showBlinkFeeHint
        )
        _uiState.value = MainUiState.Success(
            amountPaid = currencyManager.convertMsatsToDisplay(paid, currencyState),
            feePaid = currencyManager.convertMsatsToDisplay(fee, currencyState),
            showBlinkFeeHint = showBlinkFeeHint
        )
    }

    private fun requestPayment(
        summary: Bolt11InvoiceSummary,
        amountOverrideMsats: Long?,
        origin: PendingOrigin
    ) {
        scope.launch {
            if (currencyManager.needsExchangeRate()) {
                currencyManager.ensureExchangeRateIfNeeded(currencyManager.state.value.info)
            }
            val amountMsats = amountOverrideMsats ?: summary.amountMsats
            val isManualEntry =
                origin == PendingOrigin.ManualEntry || origin == PendingOrigin.LnurlManual
            val requiresConfirmation =
                amountMsats != null && shouldConfirmPayment(amountMsats, isManualEntry)
            if (requiresConfirmation) {
                val display = currencyManager.convertMsatsToDisplay(
                    amountMsats,
                    currencyManager.state.value
                )
                pendingPayment = PendingPayment(
                    summary = summary,
                    overrideAmountMsats = amountOverrideMsats,
                    origin = origin
                )
                _uiState.value = MainUiState.Confirm(display)
            } else {
                startPayment(
                    summary = summary,
                    amountOverrideMsats = amountOverrideMsats,
                    origin = origin
                )
            }
        }
    }

    private fun emitError(error: AppError) {
        _uiState.value = MainUiState.Error(error)
        _events.tryEmit(MainEvent.ShowError(error))
    }

    private fun startPayment(
        summary: Bolt11InvoiceSummary,
        amountOverrideMsats: Long?,
        origin: PendingOrigin
    ) {
        val amountMsats = amountOverrideMsats ?: summary.amountMsats ?: 0L
        val pendingId = pendingTracker.register(
            summary = summary,
            amountMsats = amountMsats,
            origin = origin,
            walletUri = currentWalletUri,
            walletType = currentWalletType
        )
        cancelCollectionJob(pendingId)
        val job = scope.launch {
            val request = try {
                payInvoice(
                    invoice = summary.paymentRequest,
                    amountMsats = amountOverrideMsats
                )
            } catch (error: AppErrorException) {
                handlePaymentFailure(
                    pendingId = pendingId,
                    summary = summary,
                    amountOverrideMsats = amountOverrideMsats,
                    error = error.error
                )
                cancelCollectionJob(pendingId)
                return@launch
            } catch (error: Throwable) {
                handlePaymentFailure(
                    pendingId = pendingId,
                    summary = summary,
                    amountOverrideMsats = amountOverrideMsats,
                    error = AppError.Unexpected(error.message)
                )
                cancelCollectionJob(pendingId)
                return@launch
            }
            pendingTracker.setRequest(pendingId, request)
            try {
                request.state.collect { state ->
                    when (state) {
                        PayInvoiceRequestState.Loading -> {
                            // Only show Loading if not already returned to Active (pending chip visible)
                            val record = pendingTracker.get(pendingId)
                            if (record?.visible != true) {
                                _uiState.value = MainUiState.Loading()
                            }
                        }

                        is PayInvoiceRequestState.Success -> {
                            handlePaymentSuccess(
                                pendingId = pendingId,
                                summary = summary,
                                amountOverrideMsats = amountOverrideMsats,
                                result = state.invoice
                            )
                            cancelCollectionJob(pendingId)
                            this@launch.cancel()
                        }

                        is PayInvoiceRequestState.Failure -> {
                            handlePaymentFailure(
                                pendingId = pendingId,
                                summary = summary,
                                amountOverrideMsats = amountOverrideMsats,
                                error = state.error
                            )
                            cancelCollectionJob(pendingId)
                            this@launch.cancel()
                        }
                    }
                }
            } finally {
                pendingTracker.clearRequestIfMatches(pendingId, request)
            }
        }
        job.invokeOnCompletion {
            if (pendingCollectionJobs[pendingId] === job) {
                pendingCollectionJobs.remove(pendingId)
            }
        }
        pendingCollectionJobs[pendingId] = job
    }

    private fun handlePaymentSuccess(
        pendingId: String,
        summary: Bolt11InvoiceSummary,
        amountOverrideMsats: Long?,
        result: PaidInvoice
    ) {
        val record = pendingTracker.get(pendingId)
        val paidMsats = amountOverrideMsats ?: summary.amountMsats ?: 0L
        val feeMsats = result.feesPaidMsats ?: 0L
        clearPaymentSessionState()

        // Remove any other pending requests for the same invoice (from other wallets)
        // since the invoice can only be paid once
        pendingTracker.removeOthersForSameInvoice(pendingId, summary.paymentRequest)

        // Check if user is currently viewing this pending entry OR if chip isn't visible yet
        val isOpen = openPendingId == pendingId
        val chipVisible = record?.visible == true

        if (chipVisible && !isOpen) {
            // Chip is showing but user isn't viewing it - update in place, stay on Active
            if (vibrateOnPayment) haptics.notifyPaymentSuccess()
            pendingTracker.markSuccess(pendingId, paidMsats, feeMsats)
            return
        }

        // Either: fast response (chip not visible yet) OR user is viewing this entry
        // Show success screen and remove the pending record
        if (vibrateOnPayment) haptics.notifyPaymentSuccess()
        val paidDisplay = currencyManager.convertMsatsToDisplay(
            paidMsats,
            currencyManager.state.value
        )
        val feeDisplay = currencyManager.convertMsatsToDisplay(
            feeMsats,
            currencyManager.state.value
        )
        val showBlinkFeeHint = record?.walletType == WalletType.BLINK
        _uiState.value = MainUiState.Success(
            amountPaid = paidDisplay,
            feePaid = feeDisplay,
            showBlinkFeeHint = showBlinkFeeHint
        )
        lastPaymentResult = CompletedPayment(
            amountMsats = paidMsats,
            feeMsats = feeMsats,
            showBlinkFeeHint = showBlinkFeeHint
        )
        // Clear openPendingId since we're showing the result directly
        if (isOpen) openPendingId = null
        pendingTracker.remove(pendingId)
    }

    private fun handlePaymentFailure(
        pendingId: String,
        summary: Bolt11InvoiceSummary,
        amountOverrideMsats: Long?,
        error: AppError
    ) {
        // Handle PaymentUnconfirmed specially - the payment may have succeeded
        // Keep polling instead of treating as failure
        if (error is AppError.PaymentUnconfirmed) {
            handlePaymentUnconfirmed(pendingId, summary, amountOverrideMsats, error)
            return
        }

        // Timeout means request was sent but no response - treat as unconfirmed
        // (NetworkUnavailable means request wasn't sent, so that's a hard failure)
        if (error is AppError.Timeout) {
            val paymentHash = summary.paymentHash
            if (paymentHash != null) {
                handlePaymentUnconfirmed(
                    pendingId,
                    summary,
                    amountOverrideMsats,
                    AppError.PaymentUnconfirmed(paymentHash, "Connection lost during payment")
                )
                return
            }
        }

        val record = pendingTracker.get(pendingId)
        clearPaymentSessionState()

        // Check if user is currently viewing this pending entry OR if chip isn't visible yet
        val isOpen = openPendingId == pendingId
        val chipVisible = record?.visible == true

        if (chipVisible && !isOpen) {
            // Chip is showing but user isn't viewing it - update in place, stay on Active
            pendingTracker.markFailure(pendingId, error)
            return
        }

        // Either: fast failure (chip not visible yet) OR user is viewing this entry
        // Show error screen and remove the pending record
        _uiState.value = MainUiState.Error(error)
        _events.tryEmit(MainEvent.ShowError(error))
        if (isOpen) openPendingId = null
        pendingTracker.remove(pendingId)
    }

    /**
     * Handles PaymentUnconfirmed - the payment was sent but we don't know if it succeeded.
     * This is a "soft" error - we keep the payment as pending and poll for status.
     */
    private fun handlePaymentUnconfirmed(
        pendingId: String,
        summary: Bolt11InvoiceSummary,
        amountOverrideMsats: Long?,
        error: AppError.PaymentUnconfirmed
    ) {
        if (pendingTracker.get(pendingId) == null) return
        clearPaymentSessionState()

        // Make the chip visible immediately since we need user awareness
        pendingTracker.makeVisible(pendingId)

        // Only return to Active if user is NOT explicitly watching this pending entry
        val isWatchingThis = openPendingId == pendingId
        if (!isWatchingThis) {
            _uiState.value = MainUiState.Active
        }
        // If watching, stay in Loading(isWatchingPending=true) - user can dismiss when ready

        // Start background verification polling
        val paymentHash = error.paymentHash ?: summary.paymentHash
        if (paymentHash != null) {
            pendingTracker.startVerification(pendingId, summary, amountOverrideMsats, paymentHash)
        }
        // If no payment hash, the chip will stay as "Waiting" and user can re-scan to retry
    }

    private fun handleDismissResult() {
        val id = openPendingId
        openPendingId = null
        lastPaymentResult = null

        if (id != null) {
            val record = pendingTracker.get(id)
            if (record != null && record.status != PendingStatus.Waiting) {
                // Resolved (success/failure) - remove the pending entry
                pendingTracker.remove(id)
            }
            // If still waiting, keep the chip visible (don't remove)
        }

        _uiState.value = MainUiState.Active
    }

    private fun refreshManualAmountState(preserveInput: Boolean = false) {
        val currencyState = currencyManager.state.value
        val manualInfo = when (val context = manualEntryContext) {
            is ManualEntryContext.Lnurl -> context.inputInfo
            else -> currencyState.info
        }
        val manualExchangeRate = if (
            manualInfo.code.equals(currencyState.info.code, ignoreCase = true)
        ) {
            currencyState.exchangeRate
        } else {
            null
        }
        val manualCurrencyState = CurrencyState(
            info = manualInfo,
            exchangeRate = manualExchangeRate
        )
        val config = when (val context = manualEntryContext) {
            is ManualEntryContext.Lnurl -> {
                val params = context.session.params
                ManualAmountConfig(
                    info = manualCurrencyState.info,
                    exchangeRate = manualCurrencyState.exchangeRate,
                    min = currencyManager.convertMsatsToDisplay(
                        params.minSendable,
                        manualCurrencyState
                    ),
                    max = currencyManager.convertMsatsToDisplay(
                        params.maxSendable,
                        manualCurrencyState
                    ),
                    minMsats = params.minSendable,
                    maxMsats = params.maxSendable
                )
            }

            else -> ManualAmountConfig(
                info = manualCurrencyState.info,
                exchangeRate = manualCurrencyState.exchangeRate
            )
        }
        val entry = manualAmount.reset(config, clearInput = !preserveInput)
        if (_uiState.value is MainUiState.EnterAmount) {
            _uiState.value = MainUiState.EnterAmount(entry = entry)
        }
    }

    /** Clears in-progress payment state after a payment completes (success, failure, or unconfirmed). */
    private fun clearPaymentSessionState() {
        val state = currencyManager.state.value
        pendingInvoice = null
        manualEntryContext = null
        pendingPayment = null
        manualAmount.reset(
            ManualAmountConfig(info = state.info, exchangeRate = state.exchangeRate),
            clearInput = true
        )
    }

    /** Refreshes all currency-dependent displays after exchange rate or currency changes. */
    private fun refreshAllDisplays() {
        refreshManualAmountState(preserveInput = manualEntryContext != null)
        refreshResultState()
        pendingTracker.refreshDisplayItems()
    }

    private fun cancelCollectionJob(id: String) {
        pendingCollectionJobs.remove(id)?.cancel()
    }

    private fun refreshResultState() {
        val currencyState = currencyManager.state.value
        when (val state = _uiState.value) {
            is MainUiState.Success -> {
                val payment = lastPaymentResult ?: return
                _uiState.value = MainUiState.Success(
                    amountPaid = currencyManager.convertMsatsToDisplay(
                        payment.amountMsats,
                        currencyState
                    ),
                    feePaid = currencyManager.convertMsatsToDisplay(
                        payment.feeMsats,
                        currencyState
                    ),
                    showBlinkFeeHint = payment.showBlinkFeeHint
                )
            }

            is MainUiState.Confirm -> {
                val pending = pendingPayment ?: return
                val amountMsats =
                    pending.overrideAmountMsats ?: pending.summary.amountMsats ?: return
                val display = currencyManager.convertMsatsToDisplay(amountMsats, currencyState)
                _uiState.value = MainUiState.Confirm(display)
            }

            else -> Unit
        }
    }

    /**
     * Rounds msats to full satoshis (always rounds UP).
     * Many Lightning servers only accept full satoshi amounts.
     * Examples: 1 msat -> 1000 msat, 1500 msat -> 2000 msat, 968504 msat -> 969000 msat
     */
    private fun roundToFullSatoshis(msats: Long): Long =
        ((msats + MSATS_PER_SAT - 1) / MSATS_PER_SAT) * MSATS_PER_SAT

    fun clear() {
        pendingCollectionJobs.values.forEach { it.cancel() }
        pendingCollectionJobs.clear()
        pendingTracker.clear()
        scope.cancel()
    }
}

private const val MSATS_PER_SAT = 1_000L

private data class PendingPayment(
    val summary: Bolt11InvoiceSummary,
    val overrideAmountMsats: Long?,
    val origin: PendingOrigin
)

private data class CompletedPayment(
    val amountMsats: Long,
    val feeMsats: Long,
    val showBlinkFeeHint: Boolean
)

private data class LnurlSession(val params: LnurlPayParams, val source: LnurlSource)

private enum class LnurlSource {
    Lnurl,
    LightningAddress
}

private sealed class ManualEntryContext {
    data class Bolt(val invoice: Bolt11InvoiceSummary) : ManualEntryContext()
    data class Lnurl(val session: LnurlSession, val inputInfo: CurrencyInfo) :
        ManualEntryContext()
}
