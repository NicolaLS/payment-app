package xyz.lilsus.blip

import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import xyz.lilsus.blip.feature.blinkcontacts.BlinkContactsRepository
import xyz.lilsus.blip.feature.payment.BlipPaymentPreferences
import xyz.lilsus.blip.feature.payment.PaymentCoordinator
import xyz.lilsus.blip.feature.payment.PaymentDeepLinkEvents
import xyz.lilsus.blip.feature.payment.PaymentUiState
import xyz.lilsus.blip.integration.blink.createBlinkWallet
import xyz.lilsus.raylsuite.core.network.createNetworkConnectivity
import xyz.lilsus.raylsuite.core.settings.SecureStringStore
import xyz.lilsus.raylsuite.core.ui.platform.HapticFeedbackManager
import xyz.lilsus.raylsuite.feature.appshell.AppTabState
import xyz.lilsus.raylsuite.feature.currencysettings.DefaultCurrencyPreferences
import xyz.lilsus.raylsuite.feature.languagesettings.createLanguageRepository
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.DefaultPaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.DirectTargetAmountRule
import xyz.lilsus.raylsuite.feature.paymenthub.HubIcon
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.DefaultCanvasLayoutRepository
import xyz.lilsus.raylsuite.feature.paymenthub.create.HubContact
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymentsettings.DefaultPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.themesettings.DefaultThemePreferences
import xyz.lilsus.raylsuite.integration.exchangerate.CoinGeckoBitcoinPriceProvider
import xyz.lilsus.raylsuite.integration.lnurl.KtorLnurlPayClient

internal class BlipRuntime(
    appSettings: Settings,
    secureSettings: SecureStringStore,
    haptics: HapticFeedbackManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val themePreferences = DefaultThemePreferences(appSettings)
    val currencyPreferences = DefaultCurrencyPreferences(appSettings)
    val languageRepository = createLanguageRepository()
    val paymentPreferences = DefaultPaymentPreferencesRepository(appSettings)
    val blipPaymentPreferences = BlipPaymentPreferences(appSettings)
    val contactsRepository = BlinkContactsRepository(appSettings)
    val paymentHubContacts =
        contactsRepository.contacts.map { contacts ->
            contacts.map { contact ->
                HubContact(
                    id = contact.id,
                    title = contact.displayName,
                    address = contact.address
                )
            }
        }
    val paymentHubRepository = DefaultPaymentHubRepository(appSettings)
    val canvasLayout = DefaultCanvasLayoutRepository(appSettings)
    val tabState = AppTabState()
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

    val onboardingViewModel =
        OnboardingViewModel(
            paymentPreferences = paymentPreferences,
            currencyPreferences = currencyPreferences,
            bitcoinPriceProvider = bitcoinPriceProvider
        )

    init {
        scope.launch {
            if (!appSettings.getBoolean(CONTACT_SEPARATION_MIGRATION_KEY, false)) {
                // The first Payment Hub implementation stored Blink imports as Hub targets.
                // Preserve those people before users remove the unwanted tiles.
                paymentHubRepository.hub.value.targets
                    .filter { target ->
                        target.amountRule == DirectTargetAmountRule.AskEveryTime &&
                            target.comment == null &&
                            target.appearance.icon == HubIcon.Person &&
                            target.appearance.accent == null
                    }.forEach { target ->
                        contactsRepository.saveContact(
                            address = target.address,
                            alias = target.title.takeUnless {
                                it.equals(target.address.username, ignoreCase = true)
                            }
                        )
                    }
                appSettings.putBoolean(CONTACT_SEPARATION_MIGRATION_KEY, true)
            }
        }
        scope.launch {
            PaymentDeepLinkEvents.events.collect { uri ->
                if (blinkWallet.connection.value == null) return@collect
                tabState.requestScan()
                paymentCoordinator.dispatch(PaymentIntent.DeepLinkReceived(uri))
            }
        }
        scope.launch {
            // A payment always presents on the Scan tab, wherever it was started from.
            paymentCoordinator.uiState.collect { state ->
                if (state != PaymentUiState.Active) tabState.requestScan()
            }
        }
    }

    fun resetPaymentSession() {
        paymentCoordinator.resetSession()
    }

    fun clear() {
        onboardingViewModel.clear()
        paymentCoordinator.clear()
        languageRepository.close()
        scope.cancel()
    }
}

private const val CONTACT_SEPARATION_MIGRATION_KEY = "blip.contacts.separatedFromPaymentHub"
