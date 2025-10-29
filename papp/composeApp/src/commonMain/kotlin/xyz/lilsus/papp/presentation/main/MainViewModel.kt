package xyz.lilsus.papp.presentation.main

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
import xyz.lilsus.papp.domain.use_cases.PayInvoiceUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey

class MainViewModel internal constructor(
    private val payInvoice: PayInvoiceUseCase,
    private val observeWalletConnection: ObserveWalletConnectionUseCase,
    dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Active)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<MainEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<MainEvent> = _events.asSharedFlow()

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
            MainIntent.RequestScan -> handleRequestScan()
        }
    }

    private fun handleRequestScan() {
        _events.tryEmit(MainEvent.OpenScanner)
    }

    private fun handleInvoiceDetected(invoice: String) {
        scope.launch {
            payInvoice(invoice).collect { result ->
                when (result) {
                    Result.Loading -> _uiState.value = MainUiState.Loading
                    is Result.Success -> {
                        val feesPaidSats = result.data.feesPaidMsats?.div(1_000) ?: 0L
                        val displayAmount = DisplayAmount(feesPaidSats, DisplayCurrency.Satoshi)
                        _uiState.value = MainUiState.Success(displayAmount)
                    }

                    is Result.Error -> {
                        _uiState.value = MainUiState.Error(result.error)
                        result.error.let { _events.tryEmit(MainEvent.ShowError(it)) }
                    }
                }
            }
        }
    }

    private fun handleDismissResult() {
        _uiState.value = MainUiState.Active
    }

    private fun handleManualAmountKeyPress(@Suppress("UNUSED_PARAMETER") key: ManualAmountKey) {
        // TODO: Implement manual amount entry flow.
    }

    private fun handleManualAmountSubmit() {
        // TODO: Implement manual amount submission when flow is defined.
    }

    private fun handleManualAmountDismiss() {
        _uiState.value = MainUiState.Active
    }

    fun clear() {
        scope.cancel()
    }
}
