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
        val mode = when (settings.stringOrNullCompat(KEY_CONFIRM_MODE)?.lowercase()) {
            "always" -> PaymentConfirmationMode.Always
            "above" -> PaymentConfirmationMode.Above
            else -> PaymentConfirmationMode.Above
        }
        val threshold = settings.longOrNullCompat(KEY_CONFIRM_THRESHOLD_SATS)
            ?: PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS
        val confirmManual = settings.booleanOrNullCompat(KEY_CONFIRM_MANUAL_ENTRY) ?: false
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

private fun Settings.booleanOrNullCompat(key: String): Boolean? = when {
    hasKey(key) -> getBoolean(key, false)
    else -> null
}

private fun Settings.stringOrNullCompat(key: String): String? = when {
    hasKey(key) -> getString(key, "")
    else -> null
}

private fun Settings.longOrNullCompat(key: String): Long? = when {
    hasKey(key) -> getLong(key, 0L)
    else -> null
}
