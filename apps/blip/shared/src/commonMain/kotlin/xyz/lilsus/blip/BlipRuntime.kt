package xyz.lilsus.blip

import com.russhwolf.settings.Settings
import xyz.lilsus.blip.feature.payment.BlipPaymentPreferences
import xyz.lilsus.blip.feature.payment.PaymentCoordinator
import xyz.lilsus.blip.integration.blink.createBlinkWallet
import xyz.lilsus.raylsuite.core.network.createNetworkConnectivity
import xyz.lilsus.raylsuite.core.ui.platform.HapticFeedbackManager
import xyz.lilsus.raylsuite.feature.contacts.DefaultContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.DefaultCurrencyPreferences
import xyz.lilsus.raylsuite.feature.paymentsettings.DefaultPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.themesettings.DefaultThemePreferences
import xyz.lilsus.raylsuite.integration.exchangerate.CoinGeckoBitcoinPriceProvider
import xyz.lilsus.raylsuite.integration.lnurl.KtorLnurlPayClient

internal class BlipRuntime(
    appSettings: Settings,
    secureSettings: Settings,
    haptics: HapticFeedbackManager
) {
    val themePreferences = DefaultThemePreferences(appSettings)
    val currencyPreferences = DefaultCurrencyPreferences(appSettings)
    val paymentPreferences = DefaultPaymentPreferencesRepository(appSettings)
    val blipPaymentPreferences = BlipPaymentPreferences(appSettings)
    val contactsRepository = DefaultContactsRepository(appSettings)

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
            contactsRepository = contactsRepository,
            haptics = haptics,
            showEstimatedFeeHint = true
        )

    fun resetPaymentSession() {
        paymentCoordinator.resetSession()
    }

    fun clear() {
        paymentCoordinator.clear()
    }
}
