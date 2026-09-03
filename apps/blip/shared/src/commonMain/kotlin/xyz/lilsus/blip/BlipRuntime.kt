package xyz.lilsus.blip

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import xyz.lilsus.blip.feature.payment.BlipPaymentPreferences
import xyz.lilsus.blip.feature.payment.PaymentCoordinator
import xyz.lilsus.blip.integration.blink.createBlinkWallet
import xyz.lilsus.raylsuite.core.network.createNetworkConnectivity
import xyz.lilsus.raylsuite.core.ui.platform.HapticFeedbackManager
import xyz.lilsus.raylsuite.feature.currencysettings.DefaultCurrencyPreferences
import xyz.lilsus.raylsuite.feature.languagesettings.createLanguageRepository
import xyz.lilsus.raylsuite.feature.paymenthub.DefaultPaymentHubLensPreferences
import xyz.lilsus.raylsuite.feature.paymenthub.DefaultPaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymenthub.lenses.paymentHubLenses
import xyz.lilsus.raylsuite.feature.paymentsettings.DefaultPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.themesettings.DefaultThemePreferences
import xyz.lilsus.raylsuite.integration.exchangerate.CoinGeckoBitcoinPriceProvider
import xyz.lilsus.raylsuite.integration.lnurl.KtorLnurlPayClient

internal class BlipRuntime(
    appSettings: Settings,
    secureSettings: Settings,
    haptics: HapticFeedbackManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val themePreferences = DefaultThemePreferences(appSettings)
    val currencyPreferences = DefaultCurrencyPreferences(appSettings)
    val languageRepository = createLanguageRepository()
    val paymentPreferences = DefaultPaymentPreferencesRepository(appSettings)
    val blipPaymentPreferences = BlipPaymentPreferences(appSettings)
    val paymentHubRepository = DefaultPaymentHubRepository(appSettings)
    val lensPreferences = DefaultPaymentHubLensPreferences(appSettings)
    val lensDefinitions = paymentHubLenses(appSettings)
    val paymentHub = PaymentHubController(paymentHubRepository, scope)

    private val networkConnectivity = createNetworkConnectivity()
    val blinkWallet =
        createBlinkWallet(
            secureSettings = secureSettings,
            isNetworkAvailable = networkConnectivity::isNetworkAvailable
        )

    val bitcoinPriceProvider = CoinGeckoBitcoinPriceProvider()
    private val lnurlPayClient = KtorLnurlPayClient(networkConnectivity)

    val paymentCoordinator =
        PaymentCoordinator(
            blinkWallet = blinkWallet,
            lnurlPayClient = lnurlPayClient,
            bitcoinPriceProvider = bitcoinPriceProvider,
            currencyPreferences = currencyPreferences,
            paymentPreferences = paymentPreferences,
            paymentHub = paymentHub,
            haptics = haptics,
            showEstimatedFeeHint = true,
            paymentAttemptSettings = appSettings
        )

    fun resetPaymentSession() {
        paymentCoordinator.resetSession()
    }

    fun clear() {
        paymentCoordinator.clear()
        languageRepository.close()
        scope.cancel()
    }
}
