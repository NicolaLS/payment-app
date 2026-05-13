package xyz.lilsus.papp.e2e

import android.content.Intent
import android.os.Bundle
import android.util.Log
import io.github.nicolals.nwc.NwcConnectionUri
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.papp.data.blink.BlinkCredentialStore
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.OnboardingRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

private const val TAG = "LasrE2E"
private const val ARG_PROFILE = "e2eProfile"
private const val ARG_RESET = "e2eReset"
private const val ARG_FIXTURE_JSON = "e2eFixtureJson"
private const val ARG_PAYMENT_INPUT = "e2ePaymentInput"

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun applyE2eLaunchArguments(intent: Intent?) {
    val launch = E2eLaunch.from(intent?.extras ?: Bundle.EMPTY)
    runBlocking {
        E2eFixtureSeeder.apply(launch)
    }
}

fun Intent?.e2ePaymentInput(): String? =
    this?.extras?.string(ARG_PAYMENT_INPUT)?.takeIf { it.isNotBlank() }

private data class E2eLaunch(val profile: E2eProfile, val reset: Boolean, val fixture: E2eFixture) {
    companion object {
        fun from(extras: Bundle): E2eLaunch {
            val profile = E2eProfile.from(extras.string(ARG_PROFILE) ?: E2eProfile.NEW_USER.id)
            val fixture = extras.string(ARG_FIXTURE_JSON)
                ?.takeIf { it.isNotBlank() }
                ?.let { raw -> json.decodeFromString<E2eFixture>(raw) }
                ?: E2eFixture()
            return E2eLaunch(
                profile = profile,
                reset = extras.boolean(ARG_RESET),
                fixture = fixture
            )
        }
    }
}

private enum class E2eProfile(val id: String) {
    NEW_USER("new_user"),
    NWC_USER("nwc_user"),
    BLINK_USER("blink_user"),
    MULTI_USER("multi_user"),
    SLOW_INTERNET_USER("slow_internet_user");

    companion object {
        fun from(raw: String): E2eProfile = entries.firstOrNull { it.id == raw.trim() }
            ?: error("Unknown E2E profile: $raw")
    }
}

@Serializable
private data class E2eFixture(
    val wallets: List<E2eWalletFixture> = emptyList(),
    val activeWallet: String? = null,
    val completeOnboarding: Boolean = true,
    val network: E2eNetworkFixture? = null
)

@Serializable
private data class E2eNetworkFixture(val policy: String? = null, val latencyMillis: Long? = null)

@Serializable
private data class E2eWalletFixture(
    val type: String,
    val alias: String? = null,
    val uri: String? = null,
    val walletPublicKey: String? = null,
    val apiKey: String? = null,
    val defaultWalletId: String? = null,
    @SerialName("active")
    val activeHint: Boolean = false
)

private object E2eFixtureSeeder {
    suspend fun apply(launch: E2eLaunch) {
        val koin = KoinPlatformTools.defaultContext().get()
        val wallets = koin.get<WalletSettingsRepository>()
        val onboarding = koin.get<OnboardingRepository>()
        val blinkCredentials = koin.get<BlinkCredentialStore>()

        if (launch.reset) {
            wallets.clearWalletConnection()
        }

        validate(launch)

        val activeHint = launch.fixture.activeWallet
        var firstWalletKey: String? = null
        var explicitActiveKey: String? = null

        launch.fixture.wallets.forEach { wallet ->
            val connection = when (wallet.type.trim().lowercase()) {
                "nwc" -> wallet.toNwcConnection()
                "blink" -> wallet.toBlinkConnection(blinkCredentials)
                else -> error("Unsupported E2E wallet type: ${wallet.type}")
            }

            if (firstWalletKey == null) {
                firstWalletKey = connection.walletPublicKey
            }
            if (
                wallet.activeHint ||
                activeHint == connection.walletPublicKey ||
                activeHint == connection.alias
            ) {
                explicitActiveKey = connection.walletPublicKey
            }

            wallets.saveWalletConnection(
                connection = connection,
                activate = launch.fixture.wallets.size == 1
            )
        }

        val resolvedActive = explicitActiveKey ?: firstWalletKey
        if (resolvedActive != null) {
            wallets.setActiveWallet(resolvedActive)
        }

        if (launch.fixture.wallets.isNotEmpty() && launch.fixture.completeOnboarding) {
            onboarding.markOnboardingCompleted()
        }

        launch.fixture.network?.let { network ->
            Log.i(
                TAG,
                "Profile ${launch.profile.id} requested network policy " +
                    "${network.policy ?: "default"} (${network.latencyMillis ?: 0}ms latency)"
            )
        }
        Log.i(
            TAG,
            "Applied profile ${launch.profile.id} with ${launch.fixture.wallets.size} wallet(s)"
        )
    }

    private fun validate(launch: E2eLaunch) {
        val types = launch.fixture.wallets.map { it.type.trim().lowercase() }
        when (launch.profile) {
            E2eProfile.NEW_USER -> Unit

            E2eProfile.NWC_USER -> require("nwc" in types) {
                "Profile nwc_user requires an nwc wallet fixture"
            }

            E2eProfile.BLINK_USER -> require("blink" in types) {
                "Profile blink_user requires a blink wallet fixture"
            }

            E2eProfile.MULTI_USER -> require(launch.fixture.wallets.size > 1) {
                "Profile multi_user requires at least two wallet fixtures"
            }

            E2eProfile.SLOW_INTERNET_USER -> require(launch.fixture.network != null) {
                "Profile slow_internet_user requires a network fixture"
            }
        }
    }
}

private fun E2eWalletFixture.toNwcConnection(): WalletConnection {
    val parsed = NwcConnectionUri.parse(uri?.trim().orEmpty())
        ?: error("NWC wallet fixture requires a valid uri")
    return WalletConnection(
        uri = parsed.raw,
        walletPublicKey = parsed.walletPubkey.hex,
        relayUrl = parsed.relays.firstOrNull(),
        lud16 = parsed.lud16,
        alias = alias?.trim()?.takeIf { it.isNotBlank() },
        type = WalletType.NWC
    )
}

private fun E2eWalletFixture.toBlinkConnection(
    credentials: BlinkCredentialStore
): WalletConnection {
    val walletId = walletPublicKey?.trim()?.takeIf { it.isNotBlank() }
        ?: "blink-e2e-${alias.orEmpty().ifBlank { "wallet" }.stableToken()}"
    val trimmedApiKey = apiKey?.trim().orEmpty()
    require(trimmedApiKey.isNotBlank()) {
        "Blink wallet fixture requires apiKey"
    }

    credentials.storeApiKey(walletId, trimmedApiKey)
    defaultWalletId
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { credentials.storeDefaultWalletId(walletId, it) }

    return WalletConnection(
        walletPublicKey = walletId,
        alias = alias?.trim()?.takeIf { it.isNotBlank() } ?: "Blink E2E",
        type = WalletType.BLINK
    )
}

@Suppress("DEPRECATION")
private fun Bundle.string(key: String): String? = get(key)?.toString()

@Suppress("DEPRECATION")
private fun Bundle.boolean(key: String): Boolean = when (val raw = get(key)) {
    is Boolean -> raw
    is String -> raw.equals("true", ignoreCase = true)
    else -> false
}

private fun String.stableToken(): String = lowercase()
    .map { char -> if (char.isLetterOrDigit()) char else '-' }
    .joinToString(separator = "")
    .trim('-')
    .ifBlank { "wallet" }
