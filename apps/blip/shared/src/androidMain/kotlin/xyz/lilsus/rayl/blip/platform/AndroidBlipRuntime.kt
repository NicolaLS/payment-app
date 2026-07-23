package xyz.lilsus.rayl.blip.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
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

class AndroidBlipRuntime(context: Context) : BlipRuntime {
    private val appContext = context.applicationContext
    private val clock = AndroidClock
    private val identifiers = AndroidIdentifiers()
    private val vault = AndroidCredentialVault(appContext)
    private val httpClient = HttpClient(OkHttp) {
        engine {
            config {
                followRedirects(false)
                followSslRedirects(false)
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
            override fun createDriver() = AndroidSqliteDriver(
                schema = BlipDatabase.Schema,
                context = appContext,
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

    override val preferences: UserPreferenceStore =
        AndroidUserPreferenceStore(appContext)

    override val platform: PlatformActions =
        AndroidPlatformActions(appContext)

    private companion object {
        const val DATABASE_NAME = "blip.db"
    }
}

private object AndroidClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

private class AndroidIdentifiers : IdentifierSource {
    override fun newConnectionId(): ConnectionId =
        ConnectionId.require(UUID.randomUUID().toString())

    override fun newAttemptId(): AttemptId = AttemptId.require(UUID.randomUUID().toString())

    override fun newContactId(): ContactId = ContactId.require(UUID.randomUUID().toString())

    override fun newShortcutId(): ShortcutId = ShortcutId.require(UUID.randomUUID().toString())
}

private class AndroidCredentialVault(context: Context) : CredentialVault {
    private val preferences = context.getSharedPreferences(VAULT_FILE, Context.MODE_PRIVATE)

    override suspend fun put(connectionId: ConnectionId, apiKey: BlinkApiKey) {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = apiKey.use { raw ->
            cipher.doFinal(raw.encodeToByteArray())
        }
        val payload = cipher.iv + encrypted
        preferences.edit()
            .putString(connectionId.value, Base64.encodeToString(payload, Base64.NO_WRAP))
            .commit()
            .also { require(it) }
    }

    override suspend fun get(connectionId: ConnectionId): BlinkApiKey? {
        val encoded = preferences.getString(connectionId.value, null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > IV_BYTES)
            val iv = payload.copyOfRange(0, IV_BYTES)
            val encrypted = payload.copyOfRange(IV_BYTES, payload.size)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            BlinkApiKey.parse(cipher.doFinal(encrypted).decodeToString())
        }.getOrNull()
    }

    override suspend fun remove(connectionId: ConnectionId) {
        preferences.edit().remove(connectionId.value).commit()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val VAULT_FILE = "blip_credentials"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "xyz.lilsus.blip.api-key.v1"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
    }
}

private class AndroidUserPreferenceStore(private val context: Context) : UserPreferenceStore {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
    private val mutableValues = MutableStateFlow(read())

    override val values: StateFlow<UserPreferences> = mutableValues.asStateFlow()

    override fun completeOnboarding() = update {
        copy(onboardingComplete = true)
    }

    override fun setTheme(value: AppThemePreference) = update {
        copy(theme = value)
    }

    override fun setLanguage(value: String) {
        update {
            copy(language = value)
        }
        AppCompatDelegate.setApplicationLocales(
            if (value == "system") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(value)
            }
        )
    }

    override fun setPrimaryCurrency(value: String) = update {
        copy(primaryCurrency = value)
    }

    override fun setSecondaryCurrency(value: String) = update {
        copy(secondaryCurrency = value)
    }

    override fun setAskToSaveNewContacts(value: Boolean) = update {
        copy(askToSaveNewContacts = value)
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
        preferences.edit()
            .putBoolean(ONBOARDING, updated.onboardingComplete)
            .putString(THEME, updated.theme.name)
            .putString(LANGUAGE, updated.language)
            .putString(PRIMARY_CURRENCY, updated.primaryCurrency)
            .putString(SECONDARY_CURRENCY, updated.secondaryCurrency)
            .putBoolean(ASK_SAVE_CONTACTS, updated.askToSaveNewContacts)
            .putString(CONFIRMATION_MODE, updated.payments.confirmationMode.name)
            .putLong(CONFIRMATION_THRESHOLD, updated.payments.thresholdSats)
            .putBoolean(CONFIRM_MANUAL, updated.payments.confirmManualEntry)
            .putBoolean(CONFIRM_SHORTCUTS, updated.payments.confirmShortcutPayments)
            .putBoolean(VIBRATE_SCAN, updated.payments.vibrateOnScan)
            .putBoolean(VIBRATE_PAYMENT, updated.payments.vibrateOnPayment)
            .apply()
        mutableValues.value = updated
    }

    private fun read(): UserPreferences = UserPreferences(
        onboardingComplete = preferences.getBoolean(ONBOARDING, false),
        theme = preferences.getString(THEME, null)
            ?.let { runCatching { AppThemePreference.valueOf(it) }.getOrNull() }
            ?: AppThemePreference.System,
        language = preferences.getString(LANGUAGE, "system") ?: "system",
        primaryCurrency = preferences.getString(PRIMARY_CURRENCY, "SAT") ?: "SAT",
        secondaryCurrency = preferences.getString(SECONDARY_CURRENCY, "USD") ?: "USD",
        askToSaveNewContacts = preferences.getBoolean(ASK_SAVE_CONTACTS, true),
        payments = PaymentPreferences(
            confirmationMode = preferences.getString(CONFIRMATION_MODE, null)
                ?.let { runCatching { ConfirmationMode.valueOf(it) }.getOrNull() }
                ?: ConfirmationMode.AboveThreshold,
            thresholdSats = preferences.getLong(CONFIRMATION_THRESHOLD, 10_000L),
            confirmManualEntry = preferences.getBoolean(CONFIRM_MANUAL, false),
            confirmShortcutPayments = preferences.getBoolean(CONFIRM_SHORTCUTS, false),
            vibrateOnScan = preferences.getBoolean(VIBRATE_SCAN, true),
            vibrateOnPayment = preferences.getBoolean(VIBRATE_PAYMENT, true)
        )
    )

    private companion object {
        const val PREFERENCES_FILE = "blip_preferences"
        const val ONBOARDING = "onboarding_complete"
        const val THEME = "theme"
        const val LANGUAGE = "language"
        const val PRIMARY_CURRENCY = "primary_currency"
        const val SECONDARY_CURRENCY = "secondary_currency"
        const val ASK_SAVE_CONTACTS = "ask_save_contacts"
        const val CONFIRMATION_MODE = "confirmation_mode"
        const val CONFIRMATION_THRESHOLD = "confirmation_threshold"
        const val CONFIRM_MANUAL = "confirm_manual"
        const val CONFIRM_SHORTCUTS = "confirm_shortcuts"
        const val VIBRATE_SCAN = "vibrate_scan"
        const val VIBRATE_PAYMENT = "vibrate_payment"
    }
}

private class AndroidPlatformActions(private val context: Context) : PlatformActions {
    private val clipboard =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    override suspend fun readClipboard(): String? = clipboard.primaryClip
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
        ?.takeIf(String::isNotBlank)

    override suspend fun writeClipboard(value: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("Blip", value))
    }

    override fun haptic() {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20L)
        }
    }
}
