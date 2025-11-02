package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.papp.domain.model.PaymentConfirmationMode
import xyz.lilsus.papp.domain.model.PaymentPreferences
import xyz.lilsus.papp.domain.repository.PaymentPreferencesRepository

private const val KEY_CONFIRM_MODE = "payment.confirmation.mode"
private const val KEY_CONFIRM_THRESHOLD_SATS = "payment.confirmation.threshold.sats"
private const val KEY_CONFIRM_MANUAL_ENTRY = "payment.confirmation.manual"

class PaymentPreferencesRepositoryImpl(
    private val settings: Settings,
) : PaymentPreferencesRepository {

    private val state = MutableStateFlow(loadPreferences())

    override val preferences: Flow<PaymentPreferences> = state.asStateFlow()

    override suspend fun getPreferences(): PaymentPreferences = state.value

    override suspend fun setConfirmationMode(mode: PaymentConfirmationMode) {
        update { it.copy(confirmationMode = mode) }
    }

    override suspend fun setConfirmationThreshold(thresholdSats: Long) {
        update { it.copy(thresholdSats = thresholdSats) }
    }

    override suspend fun setConfirmManualEntry(enabled: Boolean) {
        update { it.copy(confirmManualEntry = enabled) }
    }

    private fun update(transform: (PaymentPreferences) -> PaymentPreferences) {
        val current = state.value
        val updated = transform(current).normalise()
        if (updated == current) return
        persist(updated)
        state.value = updated
    }

    private fun loadPreferences(): PaymentPreferences {
        val modeRaw = settings.getStringOrNull(KEY_CONFIRM_MODE)
        val mode = when (modeRaw?.lowercase()) {
            "always" -> PaymentConfirmationMode.Always
            "above" -> PaymentConfirmationMode.Above
            else -> PaymentConfirmationMode.Above
        }
        val threshold = if (settings.hasKey(KEY_CONFIRM_THRESHOLD_SATS)) {
            settings.getLong(KEY_CONFIRM_THRESHOLD_SATS, PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS)
        } else {
            PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS
        }
        val confirmManual = if (settings.hasKey(KEY_CONFIRM_MANUAL_ENTRY)) {
            settings.getBoolean(KEY_CONFIRM_MANUAL_ENTRY, false)
        } else {
            false
        }
        return PaymentPreferences(
            confirmationMode = mode,
            thresholdSats = threshold,
            confirmManualEntry = confirmManual,
        ).normalise()
    }

    private fun persist(preferences: PaymentPreferences) {
        settings.putString(
            KEY_CONFIRM_MODE,
            when (preferences.confirmationMode) {
                PaymentConfirmationMode.Always -> "always"
                PaymentConfirmationMode.Above -> "above"
            },
        )
        settings.putLong(KEY_CONFIRM_THRESHOLD_SATS, preferences.thresholdSats)
        settings.putBoolean(KEY_CONFIRM_MANUAL_ENTRY, preferences.confirmManualEntry)
    }
}
