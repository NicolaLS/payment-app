package xyz.lilsus.raylsuite.feature.paymentsettings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.raylsuite.core.model.PaymentConfirmationMode
import xyz.lilsus.raylsuite.core.model.PaymentPreferences

interface PaymentPreferencesRepository {
    val preferences: Flow<PaymentPreferences>

    suspend fun current(): PaymentPreferences

    suspend fun setConfirmationMode(mode: PaymentConfirmationMode)

    suspend fun setConfirmationThreshold(thresholdSats: Long)

    suspend fun setConfirmManualEntry(enabled: Boolean)

    suspend fun setConfirmShortcutPayments(enabled: Boolean)

    suspend fun setShowLnurlPayDetails(enabled: Boolean)

    suspend fun setVibrateOnScan(enabled: Boolean)

    suspend fun setVibrateOnPayment(enabled: Boolean)
}

class DefaultPaymentPreferencesRepository(private val settings: Settings) :
    PaymentPreferencesRepository {
    private val state = MutableStateFlow(loadPreferences())

    override val preferences: Flow<PaymentPreferences> = state.asStateFlow()

    override suspend fun current(): PaymentPreferences = state.value

    override suspend fun setConfirmationMode(mode: PaymentConfirmationMode) {
        update { it.copy(confirmationMode = mode) }
    }

    override suspend fun setConfirmationThreshold(thresholdSats: Long) {
        update { it.copy(thresholdSats = thresholdSats) }
    }

    override suspend fun setConfirmManualEntry(enabled: Boolean) {
        update { it.copy(confirmManualEntry = enabled) }
    }

    override suspend fun setConfirmShortcutPayments(enabled: Boolean) {
        update { it.copy(confirmShortcutPayments = enabled) }
    }

    override suspend fun setShowLnurlPayDetails(enabled: Boolean) {
        update { it.copy(showLnurlPayDetails = enabled) }
    }

    override suspend fun setVibrateOnScan(enabled: Boolean) {
        update { it.copy(vibrateOnScan = enabled) }
    }

    override suspend fun setVibrateOnPayment(enabled: Boolean) {
        update { it.copy(vibrateOnPayment = enabled) }
    }

    private fun update(transform: (PaymentPreferences) -> PaymentPreferences) {
        val current = state.value
        val updated = transform(current).normalise()
        if (updated == current) return

        persist(updated)
        state.value = updated
    }

    private fun loadPreferences(): PaymentPreferences {
        val confirmationMode =
            when (settings.getStringOrNull(KEY_CONFIRM_MODE)?.lowercase()) {
                MODE_ALWAYS -> PaymentConfirmationMode.Always
                else -> PaymentConfirmationMode.Above
            }
        val thresholdSats =
            settings.getLong(
                KEY_CONFIRM_THRESHOLD_SATS,
                PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS
            )
        val confirmShortcutPayments =
            settings.getBoolean(KEY_CONFIRM_SHORTCUT_PAYMENTS, false)
        return PaymentPreferences(
            confirmationMode = confirmationMode,
            thresholdSats = thresholdSats,
            confirmManualEntry = settings.getBoolean(KEY_CONFIRM_MANUAL_ENTRY, false),
            confirmShortcutPayments = confirmShortcutPayments,
            showLnurlPayDetails = settings.getBoolean(KEY_SHOW_LNURL_PAY_DETAILS, false),
            vibrateOnScan = settings.getBoolean(KEY_VIBRATE_SCAN, true),
            vibrateOnPayment = settings.getBoolean(KEY_VIBRATE_PAYMENT, true)
        ).normalise()
    }

    private fun persist(preferences: PaymentPreferences) {
        settings.putString(
            KEY_CONFIRM_MODE,
            when (preferences.confirmationMode) {
                PaymentConfirmationMode.Always -> MODE_ALWAYS
                PaymentConfirmationMode.Above -> MODE_ABOVE
            }
        )
        settings.putLong(KEY_CONFIRM_THRESHOLD_SATS, preferences.thresholdSats)
        settings.putBoolean(KEY_CONFIRM_MANUAL_ENTRY, preferences.confirmManualEntry)
        settings.putBoolean(
            KEY_CONFIRM_SHORTCUT_PAYMENTS,
            preferences.confirmShortcutPayments
        )
        settings.putBoolean(KEY_SHOW_LNURL_PAY_DETAILS, preferences.showLnurlPayDetails)
        settings.putBoolean(KEY_VIBRATE_SCAN, preferences.vibrateOnScan)
        settings.putBoolean(KEY_VIBRATE_PAYMENT, preferences.vibrateOnPayment)
    }

    private companion object {
        const val KEY_CONFIRM_MODE = "payments.confirmationMode"
        const val KEY_CONFIRM_THRESHOLD_SATS = "payments.confirmationThresholdSats"
        const val KEY_CONFIRM_MANUAL_ENTRY = "payments.confirmManualEntry"
        const val KEY_CONFIRM_SHORTCUT_PAYMENTS = "payments.confirmShortcuts"
        const val KEY_SHOW_LNURL_PAY_DETAILS = "payments.showLnurlPayDetails"
        const val KEY_VIBRATE_SCAN = "payments.vibrateOnScan"
        const val KEY_VIBRATE_PAYMENT = "payments.vibrateOnPayment"
        const val MODE_ALWAYS = "always"
        const val MODE_ABOVE = "above"
    }
}
