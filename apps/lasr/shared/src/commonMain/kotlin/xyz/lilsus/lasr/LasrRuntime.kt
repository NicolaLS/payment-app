package xyz.lilsus.lasr

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.lasr.feature.onboarding.NwcConnectionDraft
import xyz.lilsus.lasr.feature.payment.PaymentCoordinator
import xyz.lilsus.lasr.feature.payment.PaymentUiState
import xyz.lilsus.lasr.integration.nwc.createNwcWallet
import xyz.lilsus.raylsuite.core.network.createNetworkConnectivity
import xyz.lilsus.raylsuite.core.settings.SecureStringStore
import xyz.lilsus.raylsuite.core.ui.platform.HapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.platform.createAppLifecycleObserver
import xyz.lilsus.raylsuite.feature.appshell.AppTabState
import xyz.lilsus.raylsuite.feature.currencysettings.DefaultCurrencyPreferences
import xyz.lilsus.raylsuite.feature.languagesettings.createLanguageRepository
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.DefaultPaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.DefaultCanvasLayoutRepository
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymentsettings.DefaultPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.themesettings.DefaultThemePreferences
import xyz.lilsus.raylsuite.integration.exchangerate.CoinGeckoBitcoinPriceProvider
import xyz.lilsus.raylsuite.integration.lnurl.KtorLnurlPayClient

internal class LasrRuntime(
    appSettings: Settings,
    secureSettings: SecureStringStore,
    haptics: HapticFeedbackManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val networkConnectivity = createNetworkConnectivity()
    private val appLifecycle = createAppLifecycleObserver()

    val themePreferences = DefaultThemePreferences(appSettings)
    val currencyPreferences = DefaultCurrencyPreferences(appSettings)
    val languageRepository = createLanguageRepository()
    val paymentPreferences = DefaultPaymentPreferencesRepository(appSettings)
    val paymentHubRepository = DefaultPaymentHubRepository(appSettings)
    val canvasLayout = DefaultCanvasLayoutRepository(appSettings)
    val tabState = AppTabState()

    /** A pasted or scanned NWC link that still needs confirmation, before a wallet exists. */
    private val mutableOnboardingWalletFlow = MutableStateFlow(false)
    val onboardingWalletFlow: StateFlow<Boolean> = mutableOnboardingWalletFlow.asStateFlow()

    /** The same, once a wallet is connected: the Settings tab confirms it. */
    private val mutableSettingsWalletFlow = MutableStateFlow(false)
    val settingsWalletFlow: StateFlow<Boolean> = mutableSettingsWalletFlow.asStateFlow()
    val paymentHub = PaymentHubController(paymentHubRepository, scope)
    val bitcoinPriceProvider = CoinGeckoBitcoinPriceProvider()
    val connectionDraft = NwcConnectionDraft()
    val nwcWallet =
        createNwcWallet(
            secureSettings = secureSettings,
            scope = scope,
            isNetworkAvailable = networkConnectivity::isNetworkAvailable
        )

    private val lnurlPayClient = KtorLnurlPayClient(networkConnectivity)
    val paymentCoordinator =
        PaymentCoordinator(
            nwcWallet = nwcWallet,
            lnurlPayClient = lnurlPayClient,
            bitcoinPriceProvider = bitcoinPriceProvider,
            currencyPreferences = currencyPreferences,
            paymentPreferences = paymentPreferences,
            paymentHub = paymentHub,
            haptics = haptics,
            paymentAttemptSettings = appSettings
        )

    val onboardingViewModel =
        OnboardingViewModel(
            paymentPreferences = paymentPreferences,
            currencyPreferences = currencyPreferences,
            bitcoinPriceProvider = bitcoinPriceProvider
        )

    init {
        scope.launch {
            appLifecycle.isInForeground.collectLatest(nwcWallet::onAppForegroundChanged)
        }
        scope.launch {
            LasrDeepLinks.events.collect(::routeDeepLink)
        }
        scope.launch {
            // A payment always presents on the Scan tab, wherever it was started from.
            paymentCoordinator.uiState.collect { state ->
                if (state != PaymentUiState.Active) tabState.requestScan()
            }
        }
    }

    fun requestSettingsWalletFlow() {
        mutableSettingsWalletFlow.value = true
    }

    fun walletFlowHandled() {
        mutableOnboardingWalletFlow.value = false
        mutableSettingsWalletFlow.value = false
    }

    fun resetPaymentSession() {
        paymentCoordinator.resetSession()
    }

    private fun routeDeepLink(uri: String) {
        val scheme = uri.substringBefore(":", missingDelimiterValue = "")
        if (scheme.equals(NWC_SCHEME, ignoreCase = true)) {
            connectionDraft.set(normalizeNwcUri(uri))
            if (nwcWallet.connection.value == null) {
                mutableOnboardingWalletFlow.value = true
            } else {
                mutableSettingsWalletFlow.value = true
                tabState.select(xyz.lilsus.raylsuite.feature.appshell.AppTab.Settings)
            }
            return
        }
        if (!isPaymentScheme(scheme) || nwcWallet.connection.value == null) return

        tabState.requestScan()
        paymentCoordinator.dispatch(PaymentIntent.DeepLinkReceived(uri))
    }

    fun clear() {
        onboardingViewModel.clear()
        paymentCoordinator.clear()
        languageRepository.close()
        appLifecycle.close()
        scope.launch {
            nwcWallet.close()
            scope.cancel()
        }
    }
}

internal const val NWC_SCHEME = "nostr+walletconnect"
private const val LIGHTNING_SCHEME = "lightning"
private const val BITCOIN_SCHEME = "bitcoin"
private const val LNURL_SCHEME = "lnurl"

private fun normalizeNwcUri(uri: String): String =
    if (uri.startsWith("$NWC_SCHEME://", ignoreCase = true)) {
        uri
    } else {
        val value = uri.substringAfter(":", missingDelimiterValue = "").trimStart('/')
        "$NWC_SCHEME://$value"
    }

private fun isPaymentScheme(scheme: String): Boolean =
    scheme.equals(LIGHTNING_SCHEME, ignoreCase = true) ||
        scheme.equals(BITCOIN_SCHEME, ignoreCase = true) ||
        scheme.equals(LNURL_SCHEME, ignoreCase = true)
