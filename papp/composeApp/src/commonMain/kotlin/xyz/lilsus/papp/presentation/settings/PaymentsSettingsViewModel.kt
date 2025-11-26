package xyz.lilsus.papp.presentation.settings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import xyz.lilsus.papp.domain.model.PaymentConfirmationMode
import xyz.lilsus.papp.domain.model.PaymentPreferences
import xyz.lilsus.papp.domain.use_cases.ObservePaymentPreferencesUseCase
import xyz.lilsus.papp.domain.use_cases.SetConfirmManualEntryUseCase
import xyz.lilsus.papp.domain.use_cases.SetPaymentConfirmationModeUseCase
import xyz.lilsus.papp.domain.use_cases.SetPaymentConfirmationThresholdUseCase
import xyz.lilsus.papp.domain.use_cases.SetVibrateOnPaymentUseCase
import xyz.lilsus.papp.domain.use_cases.SetVibrateOnScanUseCase

class PaymentsSettingsViewModel internal constructor(
    observePreferences: ObservePaymentPreferencesUseCase,
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
        scope.launch {
            observePreferences().collectLatest { preferences ->
                _uiState.value = PaymentsSettingsUiState(
                    confirmationMode = preferences.confirmationMode,
                    thresholdSats = preferences.thresholdSats,
                    confirmManualEntry = preferences.confirmManualEntry,
                    vibrateOnScan = preferences.vibrateOnScan,
                    vibrateOnPayment = preferences.vibrateOnPayment
                )
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
}

data class PaymentsSettingsUiState(
    val confirmationMode: PaymentConfirmationMode = PaymentPreferences().confirmationMode,
    val thresholdSats: Long = PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS,
    val confirmManualEntry: Boolean = PaymentPreferences().confirmManualEntry,
    val vibrateOnScan: Boolean = PaymentPreferences().vibrateOnScan,
    val vibrateOnPayment: Boolean = PaymentPreferences().vibrateOnPayment
) {
    val minThreshold: Long get() = PaymentPreferences.MIN_CONFIRMATION_THRESHOLD_SATS
    val maxThreshold: Long get() = PaymentPreferences.MAX_CONFIRMATION_THRESHOLD_SATS
}
