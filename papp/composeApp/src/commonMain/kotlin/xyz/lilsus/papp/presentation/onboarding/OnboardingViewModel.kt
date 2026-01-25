package xyz.lilsus.papp.presentation.onboarding

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.model.PaymentConfirmationMode
import xyz.lilsus.papp.domain.model.PaymentPreferences
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationModeUseCase
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationThresholdUseCase

/**
 * Shared state for onboarding flow.
 * Navigation is handled by the navigation graph, this just holds user choices.
 */
data class OnboardingState(
    val featuresPage: Int = 0,
    val confirmationMode: PaymentConfirmationMode = PaymentConfirmationMode.Above,
    val thresholdSats: Long = PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS,
    val hasAgreed: Boolean = false
)

class OnboardingViewModel internal constructor(
    private val persistConfirmationMode: SetPaymentConfirmationModeUseCase,
    private val persistConfirmationThreshold: SetPaymentConfirmationThresholdUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(OnboardingState())
    val uiState: StateFlow<OnboardingState> = _uiState.asStateFlow()

    fun setFeaturesPage(page: Int) {
        _uiState.update { it.copy(featuresPage = page.coerceIn(0, 2)) }
    }

    fun setConfirmationMode(mode: PaymentConfirmationMode) {
        _uiState.update { it.copy(confirmationMode = mode) }
    }

    fun setThreshold(thresholdSats: Long) {
        val clamped = thresholdSats.coerceIn(
            PaymentPreferences.MIN_CONFIRMATION_THRESHOLD_SATS,
            PaymentPreferences.MAX_CONFIRMATION_THRESHOLD_SATS
        )
        _uiState.update { it.copy(thresholdSats = clamped) }
    }

    fun persistAutoPaySettings() {
        val state = _uiState.value
        scope.launch {
            persistConfirmationMode(state.confirmationMode)
            persistConfirmationThreshold(state.thresholdSats)
        }
    }

    fun setAgreement(agreed: Boolean) {
        _uiState.update { it.copy(hasAgreed = agreed) }
    }

    fun clear() {
        scope.cancel()
    }
}
