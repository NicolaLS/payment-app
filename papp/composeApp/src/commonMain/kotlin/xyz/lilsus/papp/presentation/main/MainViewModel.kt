package xyz.lilsus.papp.presentation.main

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceParser
import xyz.lilsus.papp.domain.bolt11.Bolt11ParseResult
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceSummary
import xyz.lilsus.papp.domain.use_cases.PayInvoiceUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.presentation.main.amount.ManualAmountController
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey
import xyz.lilsus.papp.presentation.main.components.ManualAmountUiState

class MainViewModel internal constructor(
    private val payInvoice: PayInvoiceUseCase,
    private val observeWalletConnection: ObserveWalletConnectionUseCase,
    private val bolt11Parser: Bolt11InvoiceParser,
    private val manualAmount: ManualAmountController = ManualAmountController(),
    dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Active)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

    private var pendingInvoice: Bolt11InvoiceSummary? = null
    private var activePaymentJob: Job? = null

    init {
        scope.launch {
            observeWalletConnection().collect { connection ->
                if (connection == null && _uiState.value is MainUiState.Success) {
                    _uiState.value = MainUiState.Active
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

        val entryState = manualAmount.reset()
        pendingInvoice = summary

        if (summary.amountMsats == null) {
            _uiState.value = MainUiState.EnterAmount(
                entry = entryState,
            )
            return
        }

        startPayment(
            summary = summary,
            amountOverrideMsats = null,
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
                        val invoiceMsats = amountOverrideMsats ?: summary.amountMsats
                        val invoiceSats = invoiceMsats?.div(MSATS_PER_SAT)
                        val feeSats = result.data.feesPaidMsats?.div(MSATS_PER_SAT) ?: 0L
                        val paidSats = invoiceSats ?: feeSats
                        val amountDisplay = DisplayAmount(paidSats, DisplayCurrency.Satoshi)
                        val feeDisplay = DisplayAmount(feeSats, DisplayCurrency.Satoshi)
                        _uiState.value = MainUiState.Success(
                            amountPaid = amountDisplay,
                            feePaid = feeDisplay,
                        )
                        pendingInvoice = null
                        manualAmount.reset()
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
            ?.takeIf { it > 0 }
            ?: return

        startPayment(
            summary = invoice,
            amountOverrideMsats = amountMsats,
        )
    }

    private fun handleManualAmountDismiss() {
        manualAmount.reset()
        pendingInvoice = null
        _uiState.value = MainUiState.Active
    }

    fun clear() {
        scope.cancel()
    }
}

private const val MSATS_PER_SAT = 1_000L
