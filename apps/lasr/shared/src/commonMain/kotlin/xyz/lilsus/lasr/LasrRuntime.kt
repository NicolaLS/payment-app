package xyz.lilsus.lasr

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.lasr.feature.payment.PaymentCoordinator
import xyz.lilsus.lasr.integration.nwc.createNwcWallet
import xyz.lilsus.raylsuite.core.network.createNetworkConnectivity
import xyz.lilsus.raylsuite.core.ui.platform.HapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.platform.createAppLifecycleObserver
import xyz.lilsus.raylsuite.feature.contacts.DefaultContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.DefaultCurrencyPreferences
import xyz.lilsus.raylsuite.feature.paymentsettings.DefaultPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.themesettings.DefaultThemePreferences
import xyz.lilsus.raylsuite.integration.exchangerate.CoinGeckoBitcoinPriceProvider
import xyz.lilsus.raylsuite.integration.lnurl.KtorLnurlPayClient

internal class LasrRuntime(
    appSettings: Settings,
    secureSettings: Settings,
    haptics: HapticFeedbackManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val networkConnectivity = createNetworkConnectivity()
    private val appLifecycle = createAppLifecycleObserver()

    val themePreferences = DefaultThemePreferences(appSettings)
    val currencyPreferences = DefaultCurrencyPreferences(appSettings)
    val paymentPreferences = DefaultPaymentPreferencesRepository(appSettings)
    val contactsRepository = DefaultContactsRepository(appSettings)
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
            contactsRepository = contactsRepository,
            haptics = haptics
        )

    init {
        scope.launch {
            appLifecycle.isInForeground.collectLatest(nwcWallet::onAppForegroundChanged)
        }
    }

    fun resetPaymentSession() {
        paymentCoordinator.resetSession()
    }

    fun clear() {
        paymentCoordinator.clear()
        appLifecycle.close()
        scope.launch {
            nwcWallet.close()
            scope.cancel()
        }
    }
}

internal class NwcConnectionDraft {
    var uri: String? = null
        private set

    fun set(uri: String) {
        this.uri = uri
    }

    fun clear() {
        uri = null
    }
}
