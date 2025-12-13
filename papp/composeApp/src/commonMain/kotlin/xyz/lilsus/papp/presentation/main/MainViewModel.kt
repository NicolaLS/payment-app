package xyz.lilsus.papp.presentation.main

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
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
import xyz.lilsus.papp.data.exchange.currentTimeMillis
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
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.usecases.FetchLnurlPayParamsUseCase
import xyz.lilsus.papp.domain.usecases.GetExchangeRateUseCase
import xyz.lilsus.papp.domain.usecases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObservePaymentPreferencesUseCase
import xyz.lilsus.papp.domain.usecases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.usecases.PayInvoiceUseCase
import xyz.lilsus.papp.domain.usecases.RequestLnurlInvoiceUseCase
import xyz.lilsus.papp.domain.usecases.ResolveLightningAddressUseCase
import xyz.lilsus.papp.domain.usecases.ShouldConfirmPaymentUseCase
import xyz.lilsus.papp.platform.HapticFeedbackManager
import xyz.lilsus.papp.presentation.main.PendingPaymentItem
import xyz.lilsus.papp.presentation.main.PendingStatus
import xyz.lilsus.papp.presentation.main.amount.ManualAmountConfig
import xyz.lilsus.papp.presentation.main.amount.ManualAmountController
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey

class MainViewModel internal constructor(
    private val payInvoice: PayInvoiceUseCase,
    private val observeWalletConnection: ObserveWalletConnectionUseCase,
    private val observeCurrencyPreference: ObserveCurrencyPreferenceUseCase,
    private val getExchangeRate: GetExchangeRateUseCase,
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

    private val _pendingPayments = MutableStateFlow<List<PendingPaymentItem>>(emptyList())
    val pendingPayments: StateFlow<List<PendingPaymentItem>> = _pendingPayments.asStateFlow()

    private val pendingRequests = LinkedHashMap<String, PendingRequest>()

    /** Tracks which pending item is currently showing its result screen */
    private var pendingSelectionId: String? = null

    private val currencyState = MutableStateFlow(
        CurrencyState(
            info = CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE),
            exchangeRate = null
        )
    )
    private var pendingInvoice: Bolt11InvoiceSummary? = null
    private var manualEntryContext: ManualEntryContext? = null
    private var pendingPayment: PendingPayment? = null
    private var exchangeRateJob: Job? = null
    private var exchangeRateRequestId: Int = 0
    private var lastPaymentResult: CompletedPayment? = null
    private var parsedInvoiceCache: Pair<String, Bolt11InvoiceSummary>? = null
    private var vibrateOnScan: Boolean = true
    private var vibrateOnPayment: Boolean = true
    private var lastExchangeRateRefreshMs: Long? = null
    private val pendingCollectionJobs = mutableMapOf<String, Job>()
    private var currentWalletUri: String? = null

    private val _highlightedPendingId = MutableStateFlow<String?>(null)
    val highlightedPendingId: StateFlow<String?> = _highlightedPendingId.asStateFlow()

    init {
        scope.launch {
            observeWalletConnection().collectLatest { connection ->
                currentWalletUri = connection?.uri
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
                val info = CurrencyCatalog.infoFor(currency)
                invalidateExchangeRateJob()

                if (info.currency is DisplayCurrency.Fiat) {
                    // Fetch rate first, then update state once with complete data
                    currencyState.value = CurrencyState(info = info, exchangeRate = null)
                    val requestId = exchangeRateRequestId
                    exchangeRateJob = launch {
                        when (val result = getExchangeRate(info.code)) {
                            is Result.Success -> {
                                if (!shouldApplyExchangeRateResult(requestId)) {
                                    return@launch
                                }
                                currencyState.value = CurrencyState(
                                    info = info,
                                    exchangeRate = max(result.data.pricePerBitcoin, 0.0)
                                )
                                markExchangeRateFresh()
                                refreshManualAmountState(preserveInput = manualEntryContext != null)
                                refreshResultState()
                                refreshPendingDisplays()
                            }

                            is Result.Error -> {
                                if (!shouldApplyExchangeRateResult(requestId)) {
                                    return@launch
                                }
                                currencyState.value =
                                    CurrencyState(info = info, exchangeRate = null)
                                lastExchangeRateRefreshMs = null
                                _events.tryEmit(MainEvent.ShowError(result.error))
                                refreshManualAmountState(preserveInput = manualEntryContext != null)
                                refreshResultState()
                                refreshPendingDisplays()
                            }

                            Result.Loading -> Unit
                        }
                    }
                } else {
                    // Non-fiat: update immediately
                    currencyState.value = CurrencyState(info = info, exchangeRate = null)
                    lastExchangeRateRefreshMs = null
                    refreshManualAmountState(preserveInput = manualEntryContext != null)
                    refreshResultState()
                    refreshPendingDisplays()
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
                        // Check if already pending for current wallet
                        val existingPendingId = findPendingForCurrentWallet(target.invoice)
                        if (existingPendingId != null) {
                            // Highlight the existing chip to give user feedback
                            highlightPendingChip(existingPendingId)
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

    private fun highlightPendingChip(id: String) {
        scope.launch {
            _highlightedPendingId.value = id
            kotlinx.coroutines.delay(300)
            _highlightedPendingId.value = null
        }
    }

    /**
     * Checks if invoice is already pending for the current wallet.
     * Returns the pending ID if found (for highlighting), null otherwise.
     */
    private fun findPendingForCurrentWallet(invoice: String): String? {
        val summary = parseBolt11Invoice(invoice) ?: return null
        return pendingRequests.values
            .firstOrNull {
                it.summary.paymentRequest == summary.paymentRequest &&
                    it.walletUri == currentWalletUri
            }?.id
    }

    private fun handleDonation(amountSats: Long, address: LightningAddress) {
        if (amountSats <= 0) return
        scope.launch {
            pendingInvoice = null
            pendingPayment = null
            manualEntryContext = null
            val amountMsats = amountSats * MSATS_PER_SAT
            _uiState.value = MainUiState.Loading
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
        pendingRequests.values
            .firstOrNull {
                it.summary.paymentRequest == summary.paymentRequest &&
                    it.walletUri == currentWalletUri
            }
            ?.let { existing ->
                existing.visible = true
                refreshPendingDisplays()
                return
            }

        pendingInvoice = summary
        val entryState = manualAmount.reset(
            ManualAmountConfig(
                info = currencyState.value.info,
                exchangeRate = currencyState.value.exchangeRate,
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
        _uiState.value = MainUiState.Loading
        scope.launch {
            when (val result = fetchLnurlPayParams(endpoint)) {
                is Result.Success -> handleLnurlParams(result.data, source)
                is Result.Error -> emitError(result.error)
                Result.Loading -> Unit
            }
        }
    }

    private fun resolveLightningAddress(address: LightningAddress) {
        _uiState.value = MainUiState.Loading
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
        val currencyState = currencyState.value
        val inputInfo = inputCurrencyOverride ?: currencyState.info
        val manualState = CurrencyState(
            info = inputInfo,
            exchangeRate = currencyState.exchangeRate.takeIf {
                inputInfo.code.equals(currencyState.info.code, ignoreCase = true)
            }
        )

        if (needsExchangeRate(inputInfo) &&
            inputInfo.code.equals(currencyState.info.code, ignoreCase = true)
        ) {
            ensureExchangeRateIfNeeded(inputInfo)
        }

        val shouldForceManualEntry = forceManualEntry || params.minSendable != params.maxSendable
        if (!shouldForceManualEntry) {
            payLnurlInvoice(session, params.minSendable, isManualEntry = false)
            return
        }

        manualEntryContext = ManualEntryContext.Lnurl(session, inputInfo)
        val minDisplay = convertMsatsToDisplay(params.minSendable, manualState)
        val maxDisplay = convertMsatsToDisplay(params.maxSendable, manualState)
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
            manualAmount.presetAmount(convertMsatsToDisplay(it, manualState))
        } ?: baseEntry
        _uiState.value = MainUiState.EnterAmount(entry)
    }

    private fun payLnurlInvoice(session: LnurlSession, amountMsats: Long, isManualEntry: Boolean) {
        _uiState.value = MainUiState.Loading
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
            if (needsExchangeRate()) {
                ensureExchangeRateIfNeeded(currencyState.value.info)
            }
            return
        }
        if (needsExchangeRate()) {
            ensureExchangeRateIfNeeded(currencyState.value.info)
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

    /**
     * Handle tap on a pending payment chip.
     * - If still waiting/timed out: do nothing
     * - If success/failure: show result screen, remove chip on dismiss
     */
    private fun handlePendingTap(id: String) {
        val record = pendingRequests[id] ?: return
        val currencyState = currencyState.value

        when (record.status) {
            PendingStatus.Waiting -> {
                // Still pending - do nothing on tap
            }

            PendingStatus.Success -> {
                val paid = record.paidMsats ?: record.amountMsats
                val fee = record.feeMsats ?: 0L
                pendingSelectionId = id
                lastPaymentResult = CompletedPayment(amountMsats = paid, feeMsats = fee)
                _uiState.value = MainUiState.Success(
                    amountPaid = convertMsatsToDisplay(paid, currencyState),
                    feePaid = convertMsatsToDisplay(fee, currencyState)
                )
            }

            PendingStatus.Failure -> {
                val error = record.error ?: AppError.Unexpected(null)
                pendingSelectionId = id
                _uiState.value = MainUiState.Error(error)
            }
        }
    }

    private fun requestPayment(
        summary: Bolt11InvoiceSummary,
        amountOverrideMsats: Long?,
        origin: PendingOrigin
    ) {
        scope.launch {
            if (needsExchangeRate()) {
                ensureExchangeRateIfNeeded(currencyState.value.info)
            }
            val amountMsats = amountOverrideMsats ?: summary.amountMsats
            val isManualEntry =
                origin == PendingOrigin.ManualEntry || origin == PendingOrigin.LnurlManual
            val requiresConfirmation =
                amountMsats != null && shouldConfirmPayment(amountMsats, isManualEntry)
            if (requiresConfirmation) {
                val display = convertMsatsToDisplay(amountMsats, currencyState.value)
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
        val pendingId = registerPending(summary, amountOverrideMsats, origin)
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
                cancelRequestForPending(pendingId)
                return@launch
            } catch (error: Throwable) {
                handlePaymentFailure(
                    pendingId = pendingId,
                    summary = summary,
                    amountOverrideMsats = amountOverrideMsats,
                    error = AppError.Unexpected(error.message)
                )
                cancelRequestForPending(pendingId)
                return@launch
            }
            pendingRequests[pendingId]?.request = request
            try {
                request.state.collect { state ->
                    when (state) {
                        PayInvoiceRequestState.Loading -> {
                            // Only show Loading if not already returned to Active (pending chip visible)
                            val record = pendingRequests[pendingId]
                            if (record?.visible != true) {
                                _uiState.value = MainUiState.Loading
                            }
                        }

                        is PayInvoiceRequestState.Success -> {
                            handlePaymentSuccess(
                                pendingId = pendingId,
                                summary = summary,
                                amountOverrideMsats = amountOverrideMsats,
                                result = state.invoice
                            )
                            cancelRequestForPending(pendingId)
                            this@launch.cancel()
                        }

                        is PayInvoiceRequestState.Failure -> {
                            handlePaymentFailure(
                                pendingId = pendingId,
                                summary = summary,
                                amountOverrideMsats = amountOverrideMsats,
                                error = state.error
                            )
                            cancelRequestForPending(pendingId)
                            this@launch.cancel()
                        }
                    }
                }
            } finally {
                if (pendingRequests[pendingId]?.request === request) {
                    pendingRequests[pendingId]?.request = null
                }
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
        val record = pendingRequests[pendingId]
        val currencyState = currencyState.value
        val paidMsats = amountOverrideMsats ?: summary.amountMsats ?: 0L
        val feeMsats = result.feesPaidMsats ?: 0L
        pendingInvoice = null
        manualEntryContext = null
        pendingPayment = null
        manualAmount.reset(
            ManualAmountConfig(
                info = currencyState.info,
                exchangeRate = currencyState.exchangeRate
            ),
            clearInput = true
        )

        // Remove any other pending requests for the same invoice (from other wallets)
        // since the invoice can only be paid once
        removeOtherPendingForSameInvoice(pendingId, summary.paymentRequest)

        if (record?.visible == true) {
            // Chip was already showing - update it in place, stay on Active
            if (vibrateOnPayment) haptics.notifyPaymentSuccess()
            updatePendingStatus(
                id = pendingId,
                status = PendingStatus.Success,
                paidMsats = paidMsats,
                feeMsats = feeMsats
            )
            return
        }

        // Fast response - show success screen
        if (vibrateOnPayment) haptics.notifyPaymentSuccess()
        val paidDisplay = convertMsatsToDisplay(paidMsats, currencyState)
        val feeDisplay = convertMsatsToDisplay(feeMsats, currencyState)
        _uiState.value = MainUiState.Success(
            amountPaid = paidDisplay,
            feePaid = feeDisplay
        )
        lastPaymentResult = CompletedPayment(
            amountMsats = paidMsats,
            feeMsats = feeMsats
        )
        removePendingRecord(pendingId)
    }

    private fun handlePaymentFailure(
        pendingId: String,
        summary: Bolt11InvoiceSummary,
        amountOverrideMsats: Long?,
        error: AppError
    ) {
        val record = pendingRequests[pendingId]
        val currencyState = currencyState.value
        pendingInvoice = null
        pendingPayment = null
        manualEntryContext = null
        manualAmount.reset(
            ManualAmountConfig(
                info = currencyState.info,
                exchangeRate = currencyState.exchangeRate
            ),
            clearInput = true
        )

        if (record?.visible == true) {
            // Chip was already showing - update it in place, stay on Active
            updatePendingStatus(
                id = pendingId,
                status = PendingStatus.Failure,
                error = error
            )
            return
        }

        // Fast failure - show error screen
        _uiState.value = MainUiState.Error(error)
        _events.tryEmit(MainEvent.ShowError(error))
        removePendingRecord(pendingId)
    }

    private fun handleDismissResult() {
        // If viewing a pending item's result, remove it from the list
        pendingSelectionId?.let { id ->
            removePendingRecord(id)
            pendingSelectionId = null
        }
        lastPaymentResult = null
        _uiState.value = MainUiState.Active
    }

    private fun refreshManualAmountState(preserveInput: Boolean = false) {
        val currencyState = currencyState.value
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
                    min = convertMsatsToDisplay(params.minSendable, manualCurrencyState),
                    max = convertMsatsToDisplay(params.maxSendable, manualCurrencyState),
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

    private fun registerPending(
        summary: Bolt11InvoiceSummary,
        amountOverrideMsats: Long?,
        origin: PendingOrigin
    ): String {
        val amountMsats = amountOverrideMsats ?: summary.amountMsats ?: 0L
        val id = "pending-${currentTimeMillis()}-${pendingRequests.size}"
        val record = PendingRequest(
            id = id,
            summary = summary,
            amountMsats = amountMsats,
            origin = origin,
            createdAtMs = currentTimeMillis(),
            walletUri = currentWalletUri
        )
        pendingRequests[id] = record

        // After delay, if still waiting, show chip and return to Active screen
        record.visibilityJob = scope.launch {
            kotlinx.coroutines.delay(PENDING_NOTICE_DELAY_MS)
            if (record.status == PendingStatus.Waiting) {
                record.visible = true
                refreshPendingDisplays()
                _uiState.value = MainUiState.Active
            }
        }
        return id
    }

    private fun updatePendingStatus(
        id: String,
        status: PendingStatus,
        error: AppError? = null,
        paidMsats: Long? = null,
        feeMsats: Long? = null
    ) {
        val record = pendingRequests[id] ?: return
        record.status = status
        record.error = error ?: record.error
        paidMsats?.let { record.paidMsats = it }
        feeMsats?.let { record.feeMsats = it }
        if (status != PendingStatus.Waiting) {
            record.visibilityJob?.cancel()
        }
        refreshPendingDisplays()
    }

    private fun refreshPendingDisplays() {
        val currencyState = currencyState.value
        _pendingPayments.value = pendingRequests.values
            .filter { it.visible }
            .map { record ->
                PendingPaymentItem(
                    id = record.id,
                    amount = convertMsatsToDisplay(record.amountMsats, currencyState),
                    status = record.status,
                    createdAtMs = record.createdAtMs,
                    fee = record.feeMsats?.let { convertMsatsToDisplay(it, currencyState) },
                    errorMessage = record.error?.let { errorMessageFor(it) }
                )
            }
    }

    private fun errorMessageFor(error: AppError): String = when (error) {
        is AppError.PaymentRejected -> error.message ?: error.code ?: "Rejected"
        AppError.NetworkUnavailable -> "Network error"
        AppError.Timeout -> "Timed out"
        is AppError.Unexpected -> error.message ?: "Error"
        else -> "Error"
    }

    private fun removePendingRecord(id: String) {
        val record = pendingRequests.remove(id) ?: return
        cancelRequestForPending(id, record)
        record.visibilityJob?.cancel()
        refreshPendingDisplays()
    }

    /**
     * Removes any other pending requests for the same invoice (from other wallets).
     * Called when one wallet successfully pays - the invoice can only be paid once.
     */
    private fun removeOtherPendingForSameInvoice(excludeId: String, paymentRequest: String) {
        val toRemove = pendingRequests.values
            .filter { it.id != excludeId && it.summary.paymentRequest == paymentRequest }
            .map { it.id }
        toRemove.forEach { removePendingRecord(it) }
    }

    private fun cancelRequestForPending(id: String, record: PendingRequest? = pendingRequests[id]) {
        cancelCollectionJob(id)
        record?.let { cancelRequest(it) }
    }

    private fun cancelRequest(record: PendingRequest) {
        record.request?.cancel()
        record.request = null
    }

    private fun cancelCollectionJob(id: String) {
        pendingCollectionJobs.remove(id)?.cancel()
    }

    private fun ensureExchangeRateIfNeeded(info: CurrencyInfo) {
        if (info.currency !is DisplayCurrency.Fiat) {
            invalidateExchangeRateJob()
            currencyState.value = currencyState.value.copy(exchangeRate = null, info = info)
            lastExchangeRateRefreshMs = null
            refreshManualAmountState(preserveInput = manualEntryContext != null)
            refreshResultState()
            refreshPendingDisplays()
            return
        }
        val current = currencyState.value
        if (current.info.code.equals(info.code, ignoreCase = true) &&
            current.exchangeRate != null &&
            !isExchangeRateStale()
        ) {
            if (current.info != info) {
                currencyState.value = current.copy(info = info)
                refreshManualAmountState(preserveInput = manualEntryContext != null)
                refreshResultState()
                refreshPendingDisplays()
            }
            return
        }
        invalidateExchangeRateJob()
        val requestId = exchangeRateRequestId
        exchangeRateJob = scope.launch {
            when (val result = getExchangeRate(info.code)) {
                is Result.Success -> {
                    if (!shouldApplyExchangeRateResult(requestId)) return@launch
                    currencyState.value =
                        CurrencyState(
                            info = info,
                            exchangeRate = max(result.data.pricePerBitcoin, 0.0)
                        )
                    markExchangeRateFresh()
                    refreshManualAmountState(preserveInput = manualEntryContext != null)
                    refreshResultState()
                    refreshPendingDisplays()
                }

                is Result.Error -> {
                    if (!shouldApplyExchangeRateResult(requestId)) return@launch
                    currencyState.value = CurrencyState(info = info, exchangeRate = null)
                    lastExchangeRateRefreshMs = null
                    _events.tryEmit(MainEvent.ShowError(result.error))
                    refreshManualAmountState(preserveInput = manualEntryContext != null)
                    refreshResultState()
                    refreshPendingDisplays()
                }

                Result.Loading -> Unit
            }
        }
    }

    private fun invalidateExchangeRateJob() {
        exchangeRateJob?.cancel()
        exchangeRateJob = null
        exchangeRateRequestId += 1
    }

    private fun shouldApplyExchangeRateResult(requestId: Int): Boolean =
        requestId == exchangeRateRequestId

    private fun refreshResultState() {
        val currencyState = currencyState.value
        when (val state = _uiState.value) {
            is MainUiState.Success -> {
                val payment = lastPaymentResult ?: return
                _uiState.value = MainUiState.Success(
                    amountPaid = convertMsatsToDisplay(payment.amountMsats, currencyState),
                    feePaid = convertMsatsToDisplay(payment.feeMsats, currencyState)
                )
            }

            is MainUiState.Confirm -> {
                val pending = pendingPayment ?: return
                val amountMsats =
                    pending.overrideAmountMsats ?: pending.summary.amountMsats ?: return
                val display = convertMsatsToDisplay(amountMsats, currencyState)
                _uiState.value = MainUiState.Confirm(display)
            }

            else -> Unit
        }
    }

    private fun markExchangeRateFresh() {
        lastExchangeRateRefreshMs = currentTimeMillis()
    }

    private fun isExchangeRateStale(): Boolean {
        val last = lastExchangeRateRefreshMs ?: return true
        return (currentTimeMillis() - last) >= EXCHANGE_RATE_MAX_AGE_MS
    }

    private fun needsExchangeRate(info: CurrencyInfo = currencyState.value.info): Boolean {
        if (info.currency !is DisplayCurrency.Fiat) return false
        val current = currencyState.value
        if (!current.info.code.equals(info.code, ignoreCase = true)) return true
        return current.exchangeRate == null || isExchangeRateStale()
    }

    /**
     * Rounds msats to full satoshis (always rounds UP).
     * Many Lightning servers only accept full satoshi amounts.
     * Examples: 1 msat -> 1000 msat, 1500 msat -> 2000 msat, 968504 msat -> 969000 msat
     */
    private fun roundToFullSatoshis(msats: Long): Long =
        ((msats + MSATS_PER_SAT - 1) / MSATS_PER_SAT) * MSATS_PER_SAT

    private fun convertMsatsToDisplay(msats: Long, state: CurrencyState): DisplayAmount {
        val info = state.info
        return when (val currency = info.currency) {
            DisplayCurrency.Satoshi -> DisplayAmount(msats / MSATS_PER_SAT, currency)

            DisplayCurrency.Bitcoin -> DisplayAmount(msats / MSATS_PER_SAT, currency)

            is DisplayCurrency.Fiat -> {
                val rate = state.exchangeRate
                if (rate == null) {
                    DisplayAmount(msats / MSATS_PER_SAT, DisplayCurrency.Satoshi)
                } else {
                    val btc = msats.toDouble() / MSATS_PER_BTC
                    val fiatMajor = btc * rate
                    val factor = 10.0.pow(info.fractionDigits)
                    val minor = (fiatMajor * factor).roundToLong()
                    val clamped = if (minor <= 0 && msats > 0) 1 else minor
                    DisplayAmount(clamped, currency)
                }
            }
        }
    }

    fun clear() {
        pendingCollectionJobs.values.forEach { it.cancel() }
        pendingCollectionJobs.clear()
        pendingRequests.values.forEach { it.request?.cancel() }
        pendingRequests.clear()
        scope.cancel()
    }
}

private const val MSATS_PER_SAT = 1_000L
private const val MSATS_PER_BTC = 100_000_000_000L
private const val EXCHANGE_RATE_MAX_AGE_MS = 60_000L
private const val PENDING_NOTICE_DELAY_MS = 5_000L

private data class PendingPayment(
    val summary: Bolt11InvoiceSummary,
    val overrideAmountMsats: Long?,
    val origin: PendingOrigin
)

private data class CompletedPayment(val amountMsats: Long, val feeMsats: Long)

private enum class PendingOrigin {
    Invoice,
    ManualEntry,
    LnurlFixed,
    LnurlManual
}

private data class CurrencyState(val info: CurrencyInfo, val exchangeRate: Double?)

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

private data class PendingRequest(
    val id: String,
    val summary: Bolt11InvoiceSummary,
    val amountMsats: Long,
    val origin: PendingOrigin,
    val createdAtMs: Long,
    /** Wallet URI that initiated this payment */
    val walletUri: String?,
    var status: PendingStatus = PendingStatus.Waiting,
    var error: AppError? = null,
    var paidMsats: Long? = null,
    var feeMsats: Long? = null,
    /** Whether the chip should be shown in the UI */
    var visible: Boolean = false,
    var request: PayInvoiceRequest? = null,
    /** Job that makes chip visible after delay */
    var visibilityJob: Job? = null
)
