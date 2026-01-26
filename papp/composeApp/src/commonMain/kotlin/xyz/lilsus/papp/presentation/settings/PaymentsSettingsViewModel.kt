package xyz.lilsus.papp.presentation.settings

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.domain.model.PaymentConfirmationMode
import xyz.lilsus.papp.domain.model.PaymentPreferences
import xyz.lilsus.papp.domain.usecases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObservePaymentPreferencesUseCase
import xyz.lilsus.papp.domain.usecases.SetConfirmManualEntryUseCase
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationModeUseCase
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationThresholdUseCase
import xyz.lilsus.papp.domain.usecases.SetVibrateOnPaymentUseCase
import xyz.lilsus.papp.domain.usecases.SetVibrateOnScanUseCase
import xyz.lilsus.papp.presentation.main.CurrencyManager

class PaymentsSettingsViewModel internal constructor(
    observePreferences: ObservePaymentPreferencesUseCase,
    private val observeCurrencyPreference: ObserveCurrencyPreferenceUseCase,
    private val currencyManager: CurrencyManager,
    private val setConfirmationMode: SetPaymentConfirmationModeUseCase,
    private val setConfirmationThreshold: SetPaymentConfirmationThresholdUseCase,
    private val setConfirmManualEntryPreference: SetConfirmManualEntryUseCase,
    private val setVibrateOnScanUseCase: SetVibrateOnScanUseCase,
    private val setVibrateOnPaymentUseCase: SetVibrateOnPaymentUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(PaymentsSettingsUiState())
    val uiState: StateFlow<PaymentsSettingsUiState> = _uiState.asStateFlow()

    init {
        currencyManager.onStateChanged = { updateFiatEquivalent() }

        scope.launch {
            observePreferences().collectLatest { preferences ->
                _uiState.value = _uiState.value.copy(
                    confirmationMode = preferences.confirmationMode,
                    thresholdSats = preferences.thresholdSats,
                    confirmManualEntry = preferences.confirmManualEntry,
                    vibrateOnScan = preferences.vibrateOnScan,
                    vibrateOnPayment = preferences.vibrateOnPayment
                )
                updateFiatEquivalent()
            }
        }

        scope.launch {
            observeCurrencyPreference().collectLatest { currency ->
                currencyManager.setPreferredCurrency(currency)
            }
        }
    }

    fun selectMode(mode: PaymentConfirmationMode) {
        scope.launch {
            setConfirmationMode(mode)
        }
    }

    fun updateThreshold(thresholdSats: Long) {
        scope.launch {
            setConfirmationThreshold(thresholdSats)
        }
    }

    fun setConfirmManualEntry(enabled: Boolean) {
        scope.launch {
            setConfirmManualEntryPreference(enabled)
        }
    }

    fun setVibrateOnScan(enabled: Boolean) {
        scope.launch { setVibrateOnScanUseCase(enabled) }
    }

    fun setVibrateOnPayment(enabled: Boolean) {
        scope.launch { setVibrateOnPaymentUseCase(enabled) }
    }

    fun clear() {
        scope.cancel()
    }

    private fun updateFiatEquivalent() {
        val currencyState = currencyManager.state.value
        val isFiat = currencyState.info.currency is DisplayCurrency.Fiat
        if (!isFiat || currencyState.exchangeRate == null) {
            _uiState.value = _uiState.value.copy(thresholdFiatEquivalent = null)
            return
        }
        val thresholdMsats = _uiState.value.thresholdSats * MSATS_PER_SAT
        val fiatAmount = currencyManager.convertMsatsToDisplay(thresholdMsats, currencyState)
        _uiState.value = _uiState.value.copy(thresholdFiatEquivalent = fiatAmount)
    }

    companion object {
        private const val MSATS_PER_SAT = 1_000L
    }
}

data class PaymentsSettingsUiState(
    val confirmationMode: PaymentConfirmationMode = PaymentPreferences().confirmationMode,
    val thresholdSats: Long = PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS,
    val confirmManualEntry: Boolean = PaymentPreferences().confirmManualEntry,
    val vibrateOnScan: Boolean = PaymentPreferences().vibrateOnScan,
    val vibrateOnPayment: Boolean = PaymentPreferences().vibrateOnPayment,
    val thresholdFiatEquivalent: DisplayAmount? = null
)
