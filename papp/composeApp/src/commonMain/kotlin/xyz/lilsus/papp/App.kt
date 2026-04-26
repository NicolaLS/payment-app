package xyz.lilsus.papp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.flowOf
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.papp.domain.model.ThemePreference
import xyz.lilsus.papp.domain.usecases.ObserveOnboardingRequiredUseCase
import xyz.lilsus.papp.domain.usecases.ObserveThemePreferenceUseCase
import xyz.lilsus.papp.domain.util.decodeUrlComponent
import xyz.lilsus.papp.navigation.DeepLinkEvents
import xyz.lilsus.papp.navigation.Onboarding
import xyz.lilsus.papp.navigation.Pay
import xyz.lilsus.papp.navigation.PaymentDeepLinkEvents
import xyz.lilsus.papp.navigation.connectWalletDialog
import xyz.lilsus.papp.navigation.navigateToAddBlinkWallet
import xyz.lilsus.papp.navigation.navigateToAddWallet
import xyz.lilsus.papp.navigation.navigateToConnectWallet
import xyz.lilsus.papp.navigation.navigateToSettings
import xyz.lilsus.papp.navigation.onboardingScreen
import xyz.lilsus.papp.navigation.paymentScreen
import xyz.lilsus.papp.navigation.settingsScreen
import xyz.lilsus.papp.presentation.theme.AppTheme

private const val NWC_SCHEME = "nostr+walletconnect"
private const val LIGHTNING_SCHEME = "lightning"
private const val BITCOIN_SCHEME = "bitcoin"
private const val LNURL_SCHEME = "lnurl"

@Composable
@Preview
fun App() {
    val navController = rememberNavController()

    val koin = remember {
        runCatching { KoinPlatformTools.defaultContext().get() }.getOrNull()
    }

    val themePreferenceFlow = remember {
        koin?.let { k ->
            runCatching { k.get<ObserveThemePreferenceUseCase>()() }.getOrNull()
        } ?: flowOf(ThemePreference.System)
    }
    val themePreference by themePreferenceFlow.collectAsState(initial = ThemePreference.System)

    // Determine if onboarding should be shown
    val onboardingRequiredFlow = remember {
        koin?.let { k ->
            runCatching { k.get<ObserveOnboardingRequiredUseCase>()() }.getOrNull()
        } ?: flowOf(false)
    }
    val onboardingRequired by onboardingRequiredFlow.collectAsState(initial = null)

    // Wait until we know if onboarding is required
    val startDestination = when (onboardingRequired) {
        null -> return

        // Still loading, don't render NavHost yet
        true -> Onboarding

        false -> Pay
    }

    LaunchedEffect(navController, onboardingRequired) {
        DeepLinkEvents.events.collect { uri ->
            val normalized = uri.trim()
            val scheme = normalized.substringBefore(":", missingDelimiterValue = "")

            if (scheme.equals(NWC_SCHEME, ignoreCase = true)) {
                val normalizedUri = if (
                    normalized.startsWith("$NWC_SCHEME://", ignoreCase = true)
                ) {
                    normalized
                } else {
                    val afterScheme = normalized
                        .substringAfter(":", missingDelimiterValue = "")
                        .trimStart('/')
                    "$NWC_SCHEME://$afterScheme"
                }
                navController.navigateToConnectWallet(uri = normalizedUri)
                return@collect
            }

            val paymentInput = paymentInputFromDeepLink(normalized) ?: return@collect
            if (onboardingRequired != false) return@collect

            navController.navigate(Pay) {
                launchSingleTop = true
            }
            PaymentDeepLinkEvents.emit(paymentInput)
        }
    }

    AppTheme(themePreference = themePreference) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.enableMaestroTestTagsAsResourceId()
        ) {
            onboardingScreen(
                navController = navController,
                onNavigateToAddNwcWallet = {
                    navController.navigateToAddWallet()
                },
                onNavigateToAddBlinkWallet = {
                    navController.navigateToAddBlinkWallet()
                }
            )
            paymentScreen(
                onNavigateToSettings = { navController.navigateToSettings() },
                onNavigateToConnectWallet = { uri ->
                    navController.navigateToConnectWallet(uri = uri)
                }
            )
            settingsScreen(
                navController = navController,
                onBack = { navController.navigateUp() }
            )
            connectWalletDialog(navController)
        }
    }
}

private fun isPaymentDeepLinkScheme(scheme: String): Boolean =
    scheme.equals(LIGHTNING_SCHEME, ignoreCase = true) ||
        scheme.equals(BITCOIN_SCHEME, ignoreCase = true) ||
        scheme.equals(LNURL_SCHEME, ignoreCase = true)

internal fun paymentInputFromDeepLink(uri: String): String? {
    val normalized = uri.trim()
    val scheme = normalized.substringBefore(":", missingDelimiterValue = "")
    if (!isPaymentDeepLinkScheme(scheme)) return null

    return when {
        scheme.equals(BITCOIN_SCHEME, ignoreCase = true) ->
            extractBitcoinLightningParameter(normalized)

        else ->
            normalized
                .substringAfter(":", missingDelimiterValue = "")
                .trimStart('/')
                .takeIf { it.isNotBlank() }
    }
}

private fun extractBitcoinLightningParameter(uri: String): String? {
    val query = uri.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return null

    return query.split('&')
        .mapNotNull { pair ->
            if (pair.isEmpty()) return@mapNotNull null
            val parts = pair.split('=', limit = 2)
            val key = decodeUrlComponent(parts[0]).lowercase()
            val value = parts.getOrNull(1)?.let(::decodeUrlComponent).orEmpty()
            key to value
        }
        .firstOrNull { (key, value) ->
            key == "lightning" && value.isNotBlank()
        }
        ?.second
}
