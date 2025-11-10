package xyz.lilsus.papp.presentation.main

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceParser
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceSummary
import xyz.lilsus.papp.domain.bolt11.Bolt11Memo
import xyz.lilsus.papp.domain.bolt11.Bolt11ParseResult
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.lnurl.LightningInputParser
import xyz.lilsus.papp.domain.lnurl.LnurlPayParams
import xyz.lilsus.papp.domain.model.*
import xyz.lilsus.papp.domain.use_cases.*
import xyz.lilsus.papp.presentation.main.amount.ManualAmountConfig
import xyz.lilsus.papp.presentation.main.amount.ManualAmountController
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong

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
    dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Active)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    private val _currencyState = MutableStateFlow(
        CurrencyState(info = CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE), exchangeRate = null)
    )
    private var pendingInvoice: Bolt11InvoiceSummary? = null
    private var manualEntryContext: ManualEntryContext? = null
    private var activePaymentJob: Job? = null
    private var pendingPayment: PendingPayment? = null
    private var exchangeRateJob: Job? = null
    private var lastPaymentResult: CompletedPayment? = null

    init {
        scope.launch {
            observeWalletConnection().collectLatest { connection ->
                if (connection == null && _uiState.value is MainUiState.Success) {
                    _uiState.value = MainUiState.Active
                }
            }
        }
        scope.launch {
            observeCurrencyPreference().collectLatest { currency ->
                val info = CurrencyCatalog.infoFor(currency)
                val current = _currencyState.value
                _currencyState.value = CurrencyState(
                    info = info,
                    exchangeRate = current.exchangeRate.takeIf { info.code == current.info.code },
                )
                ensureExchangeRateIfNeeded(info)
                refreshManualAmountState(preserveInput = manualEntryContext != null)
                refreshResultState()
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
            MainIntent.ConfirmPaymentDismiss -> handleConfirmPaymentDismiss()
            MainIntent.ConfirmPaymentSubmit -> handleConfirmPaymentSubmit()
        }
    }

    private fun handleInvoiceDetected(rawInput: String) {
        if (activePaymentJob?.isActive == true) return
        if (_uiState.value !is MainUiState.Active) return

        manualEntryContext = null
        pendingInvoice = null

        when (val parse = lightningInputParser.parse(rawInput)) {
            is LightningInputParser.ParseResult.Failure -> emitError(AppError.InvalidWalletUri(parse.reason))
            is LightningInputParser.ParseResult.Success -> when (val target = parse.target) {
                is LightningInputParser.Target.Bolt11Candidate -> processBoltInvoice(target.invoice)
                is LightningInputParser.Target.Lnurl -> fetchLnurl(target.endpoint, LnurlSource.Lnurl)
                is LightningInputParser.Target.LightningAddressTarget -> resolveLightningAddress(target.address)
            }
        }
    }

    private fun processBoltInvoice(invoice: String) {
        val summary = when (val result = bolt11Parser.parse(invoice)) {
            is Bolt11ParseResult.Success -> result.invoice
            is Bolt11ParseResult.Failure -> {
                emitError(AppError.InvalidWalletUri(result.reason))
                return
            }
        }

        pendingInvoice = summary
        val currencyState = _currencyState.value
        val entryState = manualAmount.reset(
            ManualAmountConfig(
                info = currencyState.info,
                exchangeRate = currencyState.exchangeRate,
            ),
            clearInput = true,
        )
        if (summary.amountMsats == null) {
            manualEntryContext = ManualEntryContext.Bolt(summary)
            _uiState.value = MainUiState.EnterAmount(entryState)
        } else {
            requestPayment(
                summary = summary,
                amountOverrideMsats = null,
                origin = PendingOrigin.Invoice,
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

    private fun handleLnurlParams(params: LnurlPayParams, source: LnurlSource) {
        if (params.minSendable <= 0 || params.maxSendable < params.minSendable) {
            emitError(AppError.InvalidWalletUri("LNURL amount range is invalid"))
            return
        }
        val session = LnurlSession(params = params, source = source)
        val currencyState = _currencyState.value

        if (needsExchangeRate()) {
            ensureExchangeRateIfNeeded(currencyState.info)
        }

        if (params.minSendable == params.maxSendable) {
            payLnurlInvoice(session, params.minSendable, isManualEntry = false)
            return
        }

        manualEntryContext = ManualEntryContext.Lnurl(session)
        val minDisplay = convertMsatsToDisplay(params.minSendable, currencyState)
        val maxDisplay = convertMsatsToDisplay(params.maxSendable, currencyState)
        val entry = manualAmount.reset(
            ManualAmountConfig(
                info = currencyState.info,
                exchangeRate = currencyState.exchangeRate,
                min = minDisplay,
                max = maxDisplay,
            ),
            clearInput = true,
        )
        _uiState.value = MainUiState.EnterAmount(entry)
    }

    private fun payLnurlInvoice(session: LnurlSession, amountMsats: Long, isManualEntry: Boolean) {
        _uiState.value = MainUiState.Loading
        scope.launch {
            when (val result = requestLnurlInvoice(session.params.callback, amountMsats)) {
                is Result.Success -> handleLnurlInvoice(session, amountMsats, result.data, isManualEntry)
                is Result.Error -> {
                    if (isManualEntry) {
                        _events.tryEmit(MainEvent.ShowError(result.error))
                        _uiState.value = MainUiState.EnterAmount(manualAmount.current())
                    } else {
                        emitError(result.error)
                    }
                }

                Result.Loading -> Unit
            }
        }
    }

    private fun handleLnurlInvoice(
        session: LnurlSession,
        amountMsats: Long,
        invoice: String,
        isManualEntry: Boolean,
    ) {
        val parsed = when (val result = bolt11Parser.parse(invoice)) {
            is Bolt11ParseResult.Success -> result.invoice
            is Bolt11ParseResult.Failure -> {
                if (isManualEntry) {
                    _events.tryEmit(MainEvent.ShowError(AppError.InvalidWalletUri(result.reason)))
                    _uiState.value = MainUiState.EnterAmount(manualAmount.current())
                } else {
                    emitError(AppError.InvalidWalletUri(result.reason))
                }
                return
            }
        }

        if (parsed.amountMsats != amountMsats) {
            val error = AppError.InvalidWalletUri("LNURL invoice amount does not match requested amount")
            if (isManualEntry) {
                _events.tryEmit(MainEvent.ShowError(error))
                _uiState.value = MainUiState.EnterAmount(manualAmount.current())
            } else {
                emitError(error)
            }
            return
        }

        val memoValid = validateLnurlMemo(parsed.memo, session.params)
        if (!memoValid) {
            val error = AppError.InvalidWalletUri("LNURL invoice metadata mismatch")
            if (isManualEntry) {
                _events.tryEmit(MainEvent.ShowError(error))
                _uiState.value = MainUiState.EnterAmount(manualAmount.current())
            } else {
                emitError(error)
            }
            return
        }

        pendingInvoice = parsed
        val origin = if (isManualEntry) PendingOrigin.LnurlManual else PendingOrigin.LnurlFixed
        requestPayment(
            summary = parsed,
            amountOverrideMsats = amountMsats,
            origin = origin,
        )
    }

    private fun validateLnurlMemo(memo: Bolt11Memo, params: LnurlPayParams): Boolean {
        return when (memo) {
            is Bolt11Memo.Text -> {
                params.metadata.plainText?.let { it == memo.value } ?: true
            }

            is Bolt11Memo.HashOnly -> true
            Bolt11Memo.None -> true
        }
    }

    private fun handleManualAmountKeyPress(key: ManualAmountKey) {
        if (_uiState.value !is MainUiState.EnterAmount) return
        manualEntryContext ?: return
        _uiState.value = MainUiState.EnterAmount(entry = manualAmount.handleKeyPress(key))
    }

    private fun handleManualAmountSubmit() {
        if (_uiState.value !is MainUiState.EnterAmount) return
        val context = manualEntryContext ?: return
        val amountMsats = manualAmount.enteredAmountMsats()
        if (amountMsats == null || amountMsats <= 0) {
            if (needsExchangeRate()) {
                ensureExchangeRateIfNeeded(_currencyState.value.info)
            }
            return
        }

        when (context) {
            is ManualEntryContext.Bolt -> {
                requestPayment(
                    summary = context.invoice,
                    amountOverrideMsats = amountMsats,
                    origin = PendingOrigin.ManualEntry,
                )
            }

            is ManualEntryContext.Lnurl -> {
                val params = context.session.params
                if (amountMsats < params.minSendable || amountMsats > params.maxSendable) {
                    _events.tryEmit(
                        MainEvent.ShowError(
                            AppError.InvalidWalletUri("Amount is outside the allowed range"),
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
        )
    }

    private fun requestPayment(
        summary: Bolt11InvoiceSummary,
        amountOverrideMsats: Long?,
        origin: PendingOrigin,
    ) {
        scope.launch {
            val amountMsats = amountOverrideMsats ?: summary.amountMsats
            val isManualEntry = origin == PendingOrigin.ManualEntry || origin == PendingOrigin.LnurlManual
            val currencyState = _currencyState.value
            val requiresConfirmation = amountMsats != null && shouldConfirmPayment(amountMsats, isManualEntry)
            if (requiresConfirmation) {
                val display = convertMsatsToDisplay(amountMsats!!, currencyState)
                pendingPayment = PendingPayment(
                    summary = summary,
                    overrideAmountMsats = amountOverrideMsats,
                    origin = origin,
                )
                _uiState.value = MainUiState.Confirm(display)
            } else {
                startPayment(
                    summary = summary,
                    amountOverrideMsats = amountOverrideMsats,
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
    ) {
        activePaymentJob?.cancel()
        val job = scope.launch {
            payInvoice(
                invoice = summary.paymentRequest,
                amountMsats = amountOverrideMsats,
            ).collect { result ->
                when (result) {
                    Result.Loading -> _uiState.value = MainUiState.Loading
                    is Result.Success -> {
                        val currencyState = _currencyState.value
                        val paidMsats = amountOverrideMsats ?: summary.amountMsats ?: 0L
                        val paidDisplay = convertMsatsToDisplay(paidMsats, currencyState)
                        val feeDisplay = convertMsatsToDisplay(result.data.feesPaidMsats ?: 0L, currencyState)
                        _uiState.value = MainUiState.Success(
                            amountPaid = paidDisplay,
                            feePaid = feeDisplay,
                        )
                        lastPaymentResult = CompletedPayment(
                            amountMsats = paidMsats,
                            feeMsats = result.data.feesPaidMsats ?: 0L,
                        )
                        pendingInvoice = null
                        manualEntryContext = null
                        pendingPayment = null
                        manualAmount.reset(
                            ManualAmountConfig(
                                info = currencyState.info,
                                exchangeRate = currencyState.exchangeRate,
                            ),
                            clearInput = true,
                        )
                    }

                    is Result.Error -> {
                        pendingInvoice = null
                        _uiState.value = MainUiState.Error(result.error)
                        _events.tryEmit(MainEvent.ShowError(result.error))
                    }
                }
            }
        }
        job.invokeOnCompletion {
            if (activePaymentJob === job) {
                activePaymentJob = null
            }
        }
        activePaymentJob = job
    }

    private fun handleDismissResult() {
        _uiState.value = MainUiState.Active
        lastPaymentResult = null
    }

    private fun refreshManualAmountState(preserveInput: Boolean = false) {
        val currencyState = _currencyState.value
        val config = when (val context = manualEntryContext) {
            is ManualEntryContext.Lnurl -> {
                val params = context.session.params
                ManualAmountConfig(
                    info = currencyState.info,
                    exchangeRate = currencyState.exchangeRate,
                    min = convertMsatsToDisplay(params.minSendable, currencyState),
                    max = convertMsatsToDisplay(params.maxSendable, currencyState),
                )
            }

            else -> ManualAmountConfig(
                info = currencyState.info,
                exchangeRate = currencyState.exchangeRate,
            )
        }
        val entry = manualAmount.reset(config, clearInput = !preserveInput)
        if (_uiState.value is MainUiState.EnterAmount) {
            _uiState.value = MainUiState.EnterAmount(entry = entry)
        }
    }

    private fun ensureExchangeRateIfNeeded(info: CurrencyInfo) {
        if (info.currency !is DisplayCurrency.Fiat) {
            _currencyState.value = _currencyState.value.copy(exchangeRate = null, info = info)
            return
        }
        exchangeRateJob?.cancel()
        exchangeRateJob = scope.launch {
            when (val result = getExchangeRate(info.code)) {
                is Result.Success -> {
                    _currencyState.value =
                        CurrencyState(info = info, exchangeRate = max(result.data.pricePerBitcoin, 0.0))
                    refreshManualAmountState(preserveInput = manualEntryContext != null)
                    refreshResultState()
                }

                is Result.Error -> {
                    _currencyState.value = CurrencyState(info = info, exchangeRate = null)
                    _events.tryEmit(MainEvent.ShowError(result.error))
                }

                Result.Loading -> Unit
            }
        }
    }

    private fun refreshResultState() {
        val currencyState = _currencyState.value
        when (val state = _uiState.value) {
            is MainUiState.Success -> {
                val payment = lastPaymentResult ?: return
                _uiState.value = MainUiState.Success(
                    amountPaid = convertMsatsToDisplay(payment.amountMsats, currencyState),
                    feePaid = convertMsatsToDisplay(payment.feeMsats, currencyState),
                )
            }

            is MainUiState.Confirm -> {
                val pending = pendingPayment ?: return
                val amountMsats = pending.overrideAmountMsats ?: pending.summary.amountMsats ?: return
                val display = convertMsatsToDisplay(amountMsats, currencyState)
                _uiState.value = MainUiState.Confirm(display)
            }

            else -> Unit
        }
    }

    private fun needsExchangeRate(): Boolean {
        val info = _currencyState.value.info
        return info.currency is DisplayCurrency.Fiat && _currencyState.value.exchangeRate == null
    }

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
                    DisplayAmount(minor, currency)
                }
            }
        }
    }

    fun clear() {
        scope.cancel()
    }
}

private const val MSATS_PER_SAT = 1_000L
private const val MSATS_PER_BTC = 100_000_000_000L

private data class PendingPayment(
    val summary: Bolt11InvoiceSummary,
    val overrideAmountMsats: Long?,
    val origin: PendingOrigin,
)

private data class CompletedPayment(
    val amountMsats: Long,
    val feeMsats: Long,
)

private enum class PendingOrigin {
    Invoice,
    ManualEntry,
    LnurlFixed,
    LnurlManual,
}

private data class CurrencyState(
    val info: CurrencyInfo,
    val exchangeRate: Double?,
)

private data class LnurlSession(
    val params: LnurlPayParams,
    val source: LnurlSource,
)

private enum class LnurlSource {
    Lnurl,
    LightningAddress,
}

private sealed class ManualEntryContext {
    data class Bolt(val invoice: Bolt11InvoiceSummary) : ManualEntryContext()
    data class Lnurl(val session: LnurlSession) : ManualEntryContext()
}
