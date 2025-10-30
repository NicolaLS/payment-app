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
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.model.CurrencyInfo
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceParser
import xyz.lilsus.papp.domain.bolt11.Bolt11ParseResult
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceSummary
import xyz.lilsus.papp.domain.use_cases.PayInvoiceUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.ShouldConfirmPaymentUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.GetExchangeRateUseCase
import xyz.lilsus.papp.presentation.main.amount.ManualAmountController
import xyz.lilsus.papp.presentation.main.amount.ManualAmountConfig
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey
import xyz.lilsus.papp.presentation.main.components.ManualAmountUiState

class MainViewModel internal constructor(
    private val payInvoice: PayInvoiceUseCase,
    private val observeWalletConnection: ObserveWalletConnectionUseCase,
    private val observeCurrencyPreference: ObserveCurrencyPreferenceUseCase,
    private val getExchangeRate: GetExchangeRateUseCase,
    private val bolt11Parser: Bolt11InvoiceParser,
    private val manualAmount: ManualAmountController,
    private val shouldConfirmPayment: ShouldConfirmPaymentUseCase,
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
    private var activePaymentJob: Job? = null
    private var pendingPayment: PendingPayment? = null
    private var exchangeRateJob: Job? = null

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
                _currencyState.value = CurrencyState(info = info, exchangeRate = current.exchangeRate.takeIf { info.code == current.info.code })
                ensureExchangeRateIfNeeded(info)
                refreshManualAmountState()
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

    private fun handleInvoiceDetected(invoice: String) {
        if (activePaymentJob?.isActive == true) return
        if (_uiState.value !is MainUiState.Active) return

        val summary = when (val parsed = bolt11Parser.parse(invoice)) {
            is Bolt11ParseResult.Success -> parsed.invoice
            is Bolt11ParseResult.Failure -> {
                val error = AppError.InvalidWalletUri(parsed.reason)
                _uiState.value = MainUiState.Error(error)
                _events.tryEmit(MainEvent.ShowError(error))
                return
            }
        }

        val currencyState = _currencyState.value
        val entryState = manualAmount.reset(
            ManualAmountConfig(
                info = currencyState.info,
                exchangeRate = currencyState.exchangeRate,
            )
        )
        pendingInvoice = summary

        if (summary.amountMsats == null) {
            _uiState.value = MainUiState.EnterAmount(
                entry = entryState,
            )
            return
        }

        requestPayment(
            summary = summary,
            amountOverrideMsats = null,
            origin = PendingOrigin.Invoice,
        )
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
                        val paidDisplay = convertMsatsToDisplay(amountOverrideMsats ?: summary.amountMsats ?: 0L, currencyState)
                        val feeDisplay = convertMsatsToDisplay(result.data.feesPaidMsats ?: 0L, currencyState)
                        _uiState.value = MainUiState.Success(
                            amountPaid = paidDisplay,
                            feePaid = feeDisplay,
                        )
                        pendingInvoice = null
                        pendingPayment = null
                        manualAmount.reset(
                            ManualAmountConfig(
                                info = currencyState.info,
                                exchangeRate = currencyState.exchangeRate,
                            )
                        )
                    }

                    is Result.Error -> {
                        pendingInvoice = null
                        pendingPayment = null
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
    }

    private fun handleManualAmountKeyPress(key: ManualAmountKey) {
        if (_uiState.value !is MainUiState.EnterAmount) return
        val invoice = pendingInvoice ?: return
        if (invoice.amountMsats != null) return

        _uiState.value = MainUiState.EnterAmount(
            entry = manualAmount.handleKeyPress(key),
        )
    }

    private fun handleManualAmountSubmit() {
        if (_uiState.value !is MainUiState.EnterAmount) return
        val invoice = pendingInvoice ?: return
        if (invoice.amountMsats != null) return
        val amountMsats = manualAmount.enteredAmountMsats()
        if (amountMsats == null || amountMsats <= 0) {
            if (needsExchangeRate()) {
                ensureExchangeRateIfNeeded(_currencyState.value.info)
            }
            return
        }

        requestPayment(
            summary = invoice,
            amountOverrideMsats = amountMsats,
            origin = PendingOrigin.ManualEntry,
        )
    }

    private fun handleManualAmountDismiss() {
        manualAmount.reset()
        pendingInvoice = null
        _uiState.value = MainUiState.Active
    }

    private fun handleConfirmPaymentDismiss() {
        val pending = pendingPayment ?: return
        pendingPayment = null
        when (pending.origin) {
            PendingOrigin.Invoice -> {
                pendingInvoice = null
                _uiState.value = MainUiState.Active
            }
            PendingOrigin.ManualEntry -> {
                _uiState.value = MainUiState.EnterAmount(entry = manualAmount.current())
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
            val isManualEntry = origin == PendingOrigin.ManualEntry
            val currencyState = _currencyState.value
            val requiresConfirmation = amountMsats != null && shouldConfirmPayment(amountMsats, isManualEntry)
            if (requiresConfirmation) {
                val display = convertMsatsToDisplay(amountMsats!!, currencyState)
                pendingPayment = PendingPayment(
                    summary = summary,
                    overrideAmountMsats = amountOverrideMsats,
                    displayAmount = display,
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

    private fun refreshManualAmountState(preserveInput: Boolean = false) {
        val currencyState = _currencyState.value
        val entry = manualAmount.reset(
            ManualAmountConfig(
                info = currencyState.info,
                exchangeRate = currencyState.exchangeRate,
            ),
            clearInput = !preserveInput,
        )
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
                    _currencyState.value = CurrencyState(info = info, exchangeRate = max(result.data.pricePerBitcoin, 0.0))
                    refreshManualAmountState(preserveInput = _uiState.value is MainUiState.EnterAmount)
                }
                is Result.Error -> {
                    _currencyState.value = CurrencyState(info = info, exchangeRate = null)
                    _events.tryEmit(MainEvent.ShowError(result.error))
                }
                Result.Loading -> Unit
            }
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
    val displayAmount: DisplayAmount,
    val origin: PendingOrigin,
)

private enum class PendingOrigin {
    Invoice,
    ManualEntry,
}

private data class CurrencyState(
    val info: CurrencyInfo,
    val exchangeRate: Double?,
)
