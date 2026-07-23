@file:OptIn(com.russhwolf.settings.ExperimentalSettingsImplementation::class)

package xyz.lilsus.rayl.blip.platform

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UIPasteboard
import xyz.lilsus.rayl.blip.application.AddressBook
import xyz.lilsus.rayl.blip.application.PaymentCoordinator
import xyz.lilsus.rayl.blip.data.BlipStore
import xyz.lilsus.rayl.blip.data.DatabaseDriverFactory
import xyz.lilsus.rayl.blip.data.ExchangeRateService
import xyz.lilsus.rayl.blip.data.LightningInputResolver
import xyz.lilsus.rayl.blip.data.blink.BlinkApi
import xyz.lilsus.rayl.blip.data.blink.BlinkGateway
import xyz.lilsus.rayl.blip.data.db.BlipDatabase
import xyz.lilsus.rayl.blip.domain.AppClock
import xyz.lilsus.rayl.blip.domain.AttemptId
import xyz.lilsus.rayl.blip.domain.BlinkApiKey
import xyz.lilsus.rayl.blip.domain.ConfirmationMode
import xyz.lilsus.rayl.blip.domain.ConnectionId
import xyz.lilsus.rayl.blip.domain.ContactId
import xyz.lilsus.rayl.blip.domain.CredentialVault
import xyz.lilsus.rayl.blip.domain.IdentifierSource
import xyz.lilsus.rayl.blip.domain.PaymentPreferences
import xyz.lilsus.rayl.blip.domain.ShortcutId

internal class IosBlipRuntime : BlipRuntime {
    private val clock = IosClock
    private val identifiers = IosIdentifiers()
    private val vault = IosCredentialVault()
    private val httpClient = HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
            }
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                }
            )
        }
    }

    override val store = BlipStore(
        object : DatabaseDriverFactory {
            override fun createDriver() = NativeSqliteDriver(
                schema = BlipDatabase.Schema,
                name = DATABASE_NAME
            )
        }
    )

    override val gateway = BlinkGateway(
        api = BlinkApi(),
        store = store,
        vault = vault,
        identifiers = identifiers,
        clock = clock
    )

    override val inputResolver = LightningInputResolver(
        httpClient = httpClient,
        clock = clock
    )

    override val coordinator = PaymentCoordinator(
        store = store,
        backend = gateway,
        identifiers = identifiers,
        clock = clock
    )

    override val exchangeRates = ExchangeRateService(
        client = httpClient,
        clock = clock
    )

    override val addressBook = AddressBook(
        store = store,
        gateway = gateway,
        identifiers = identifiers,
        clock = clock
    )

    override val preferences: UserPreferenceStore = IosUserPreferenceStore()

    override val platform: PlatformActions = IosPlatformActions()

    private companion object {
        const val DATABASE_NAME = "blip.db"
    }
}

private object IosClock : AppClock {
    override fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000.0).toLong()
}

private class IosIdentifiers : IdentifierSource {
    private fun nextId(): String = NSUUID().UUIDString.lowercase()

    override fun newConnectionId(): ConnectionId = ConnectionId.require(nextId())

    override fun newAttemptId(): AttemptId = AttemptId.require(nextId())

    override fun newContactId(): ContactId = ContactId.require(nextId())

    override fun newShortcutId(): ShortcutId = ShortcutId.require(nextId())
}

private class IosCredentialVault : CredentialVault {
    private val keychain = KeychainSettings(service = KEYCHAIN_SERVICE)

    override suspend fun put(connectionId: ConnectionId, apiKey: BlinkApiKey) {
        apiKey.use { keychain.putString(connectionId.value, it) }
    }

    override suspend fun get(connectionId: ConnectionId): BlinkApiKey? =
        keychain.getStringOrNull(connectionId.value)?.let(BlinkApiKey::parse)

    override suspend fun remove(connectionId: ConnectionId) {
        keychain.remove(connectionId.value)
    }

    private companion object {
        const val KEYCHAIN_SERVICE = "xyz.lilsus.blip.api-key.v1"
    }
}

private class IosUserPreferenceStore : UserPreferenceStore {
    private val settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    private val mutableValues = MutableStateFlow(read())

    override val values: StateFlow<UserPreferences> = mutableValues.asStateFlow()

    override fun completeOnboarding() = update {
        copy(onboardingComplete = true)
    }

    override fun setTheme(value: AppThemePreference) = update {
        copy(theme = value)
    }

    override fun setLanguage(value: String) {
        update { copy(language = value) }
        val defaults = NSUserDefaults.standardUserDefaults
        if (value == "system") {
            defaults.removeObjectForKey(APPLE_LANGUAGES)
        } else {
            defaults.setObject(listOf(value), forKey = APPLE_LANGUAGES)
        }
        defaults.synchronize()
    }

    override fun setPrimaryCurrency(value: String) = update {
        copy(primaryCurrency = value)
    }

    override fun setSecondaryCurrency(value: String) = update {
        copy(secondaryCurrency = value)
    }

    override fun setConfirmationMode(value: ConfirmationMode) = update {
        copy(payments = payments.copy(confirmationMode = value))
    }

    override fun setConfirmationThreshold(value: Long) = update {
        copy(payments = payments.copy(thresholdSats = value.coerceAtLeast(0L)))
    }

    override fun setConfirmManualEntry(value: Boolean) = update {
        copy(payments = payments.copy(confirmManualEntry = value))
    }

    override fun setConfirmShortcuts(value: Boolean) = update {
        copy(payments = payments.copy(confirmShortcutPayments = value))
    }

    override fun setVibrateOnScan(value: Boolean) = update {
        copy(payments = payments.copy(vibrateOnScan = value))
    }

    override fun setVibrateOnPayment(value: Boolean) = update {
        copy(payments = payments.copy(vibrateOnPayment = value))
    }

    private fun update(transform: UserPreferences.() -> UserPreferences) {
        val updated = mutableValues.value.transform()
        settings.putBoolean(ONBOARDING, updated.onboardingComplete)
        settings.putString(THEME, updated.theme.name)
        settings.putString(LANGUAGE, updated.language)
        settings.putString(PRIMARY_CURRENCY, updated.primaryCurrency)
        settings.putString(SECONDARY_CURRENCY, updated.secondaryCurrency)
        settings.putString(CONFIRMATION_MODE, updated.payments.confirmationMode.name)
        settings.putLong(CONFIRMATION_THRESHOLD, updated.payments.thresholdSats)
        settings.putBoolean(CONFIRM_MANUAL, updated.payments.confirmManualEntry)
        settings.putBoolean(CONFIRM_SHORTCUTS, updated.payments.confirmShortcutPayments)
        settings.putBoolean(VIBRATE_SCAN, updated.payments.vibrateOnScan)
        settings.putBoolean(VIBRATE_PAYMENT, updated.payments.vibrateOnPayment)
        mutableValues.value = updated
    }

    private fun read(): UserPreferences = UserPreferences(
        onboardingComplete = settings.getBoolean(ONBOARDING, false),
        theme = settings.getStringOrNull(THEME)
            ?.let { runCatching { AppThemePreference.valueOf(it) }.getOrNull() }
            ?: AppThemePreference.System,
        language = settings.getString(LANGUAGE, "system"),
        primaryCurrency = settings.getString(PRIMARY_CURRENCY, "SAT"),
        secondaryCurrency = settings.getString(SECONDARY_CURRENCY, "USD"),
        payments = PaymentPreferences(
            confirmationMode = settings.getStringOrNull(CONFIRMATION_MODE)
                ?.let { runCatching { ConfirmationMode.valueOf(it) }.getOrNull() }
                ?: ConfirmationMode.AboveThreshold,
            thresholdSats = settings.getLong(CONFIRMATION_THRESHOLD, 10_000L),
            confirmManualEntry = settings.getBoolean(CONFIRM_MANUAL, false),
            confirmShortcutPayments = settings.getBoolean(CONFIRM_SHORTCUTS, false),
            vibrateOnScan = settings.getBoolean(VIBRATE_SCAN, true),
            vibrateOnPayment = settings.getBoolean(VIBRATE_PAYMENT, true)
        )
    )

    private companion object {
        const val APPLE_LANGUAGES = "AppleLanguages"
        const val ONBOARDING = "onboarding_complete"
        const val THEME = "theme"
        const val LANGUAGE = "language"
        const val PRIMARY_CURRENCY = "primary_currency"
        const val SECONDARY_CURRENCY = "secondary_currency"
        const val CONFIRMATION_MODE = "confirmation_mode"
        const val CONFIRMATION_THRESHOLD = "confirmation_threshold"
        const val CONFIRM_MANUAL = "confirm_manual"
        const val CONFIRM_SHORTCUTS = "confirm_shortcuts"
        const val VIBRATE_SCAN = "vibrate_scan"
        const val VIBRATE_PAYMENT = "vibrate_payment"
    }
}

private class IosPlatformActions : PlatformActions {
    override suspend fun readClipboard(): String? =
        UIPasteboard.generalPasteboard.string?.takeIf(String::isNotBlank)

    override suspend fun writeClipboard(value: String) {
        UIPasteboard.generalPasteboard.string = value
    }

    override fun haptic() {
        UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).apply {
            prepare()
            impactOccurred()
        }
    }
}
