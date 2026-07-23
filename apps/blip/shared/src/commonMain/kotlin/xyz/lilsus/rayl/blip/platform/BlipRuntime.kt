package xyz.lilsus.rayl.blip.platform

import kotlinx.coroutines.flow.StateFlow
import xyz.lilsus.rayl.blip.application.AddressBook
import xyz.lilsus.rayl.blip.application.PaymentCoordinator
import xyz.lilsus.rayl.blip.data.BlipStore
import xyz.lilsus.rayl.blip.data.LightningInputResolver
import xyz.lilsus.rayl.blip.data.blink.BlinkGateway
import xyz.lilsus.rayl.blip.domain.ConfirmationMode
import xyz.lilsus.rayl.blip.domain.ExchangeRates
import xyz.lilsus.rayl.blip.domain.PaymentPreferences

enum class AppThemePreference {
    System,
    Light,
    Dark
}

data class UserPreferences(
    val onboardingComplete: Boolean = false,
    val theme: AppThemePreference = AppThemePreference.System,
    val language: String = "system",
    val primaryCurrency: String = "SAT",
    val secondaryCurrency: String = "USD",
    val askToSaveNewContacts: Boolean = true,
    val payments: PaymentPreferences = PaymentPreferences()
)

interface UserPreferenceStore {
    val values: StateFlow<UserPreferences>

    fun completeOnboarding()
    fun setTheme(value: AppThemePreference)
    fun setLanguage(value: String)
    fun setPrimaryCurrency(value: String)
    fun setSecondaryCurrency(value: String)
    fun setAskToSaveNewContacts(value: Boolean)
    fun setConfirmationMode(value: ConfirmationMode)
    fun setConfirmationThreshold(value: Long)
    fun setConfirmManualEntry(value: Boolean)
    fun setConfirmShortcuts(value: Boolean)
    fun setVibrateOnScan(value: Boolean)
    fun setVibrateOnPayment(value: Boolean)
}

interface PlatformActions {
    suspend fun readClipboard(): String?
    suspend fun writeClipboard(value: String)
    fun haptic()
}

interface BlipRuntime {
    val store: BlipStore
    val gateway: BlinkGateway
    val inputResolver: LightningInputResolver
    val coordinator: PaymentCoordinator
    val exchangeRates: ExchangeRates
    val addressBook: AddressBook
    val preferences: UserPreferenceStore
    val platform: PlatformActions
}
