package xyz.lilsus.lasr

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.lasr.ui.getLasrPaymentErrorMessageFor
import xyz.lilsus.lasr.ui.lasrPaymentErrorMessageFor
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences
import xyz.lilsus.raylsuite.feature.payment.PaymentCoordinator
import xyz.lilsus.raylsuite.feature.payment.PaymentFlow
import xyz.lilsus.raylsuite.feature.payment.PaymentIntent
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.settings.SettingsFlow
import xyz.lilsus.raylsuite.feature.settings.SettingsStartDestination
import xyz.lilsus.raylsuite.feature.themesettings.ThemePreferences
import xyz.lilsus.raylsuite.lasr.generated.resources.Res
import xyz.lilsus.raylsuite.lasr.generated.resources.app_name

internal fun NavGraphBuilder.lasrHome(
    navController: NavController,
    themePreferences: ThemePreferences,
    bitcoinPriceProvider: BitcoinPriceProvider,
    currencyPreferences: CurrencyPreferences,
    paymentPreferences: PaymentPreferencesRepository,
    contactsRepository: ContactsRepository,
    paymentCoordinator: PaymentCoordinator
) {
    composable<LasrDestination.Home> {
        PaymentFlow(
            coordinator = paymentCoordinator,
            appTitle = stringResource(Res.string.app_name),
            estimatedFeeHint = null,
            errorMessageFor = ::lasrPaymentErrorMessageFor,
            eventErrorMessageFor = ::getLasrPaymentErrorMessageFor,
            onNavigateSettings = {
                navController.navigate(LasrDestination.Settings)
            },
            onNavigateShortcutCreate = {
                navController.navigate(LasrDestination.ShortcutCreate)
            },
            onNavigateContacts = {
                navController.navigate(LasrDestination.Contacts)
            }
        )
    }
    composable<LasrDestination.Settings> {
        LasrSettings(
            startDestination = SettingsStartDestination.Overview,
            navController = navController,
            themePreferences = themePreferences,
            bitcoinPriceProvider = bitcoinPriceProvider,
            currencyPreferences = currencyPreferences,
            paymentPreferences = paymentPreferences,
            contactsRepository = contactsRepository,
            paymentCoordinator = paymentCoordinator
        )
    }
    composable<LasrDestination.Contacts> {
        LasrSettings(
            startDestination = SettingsStartDestination.Contacts,
            navController = navController,
            themePreferences = themePreferences,
            bitcoinPriceProvider = bitcoinPriceProvider,
            currencyPreferences = currencyPreferences,
            paymentPreferences = paymentPreferences,
            contactsRepository = contactsRepository,
            paymentCoordinator = paymentCoordinator
        )
    }
    composable<LasrDestination.ShortcutCreate> {
        LasrSettings(
            startDestination = SettingsStartDestination.ShortcutCreate,
            navController = navController,
            themePreferences = themePreferences,
            bitcoinPriceProvider = bitcoinPriceProvider,
            currencyPreferences = currencyPreferences,
            paymentPreferences = paymentPreferences,
            contactsRepository = contactsRepository,
            paymentCoordinator = paymentCoordinator
        )
    }
}

@Composable
private fun LasrSettings(
    startDestination: SettingsStartDestination,
    navController: NavController,
    themePreferences: ThemePreferences,
    bitcoinPriceProvider: BitcoinPriceProvider,
    currencyPreferences: CurrencyPreferences,
    paymentPreferences: PaymentPreferencesRepository,
    contactsRepository: ContactsRepository,
    paymentCoordinator: PaymentCoordinator
) {
    SettingsFlow(
        storageName = LASR_PREFERENCES,
        themePreferences = themePreferences,
        bitcoinPriceProvider = bitcoinPriceProvider,
        onBack = navController::navigateUp,
        modifier = Modifier,
        startDestination = startDestination,
        currencyPreferences = currencyPreferences,
        paymentPreferences = paymentPreferences,
        contactsRepository = contactsRepository,
        donationAppName = stringResource(Res.string.app_name),
        onDonate = { amountSats ->
            navController.navigate(LasrDestination.Home) {
                popUpTo<LasrDestination.Home> {
                    inclusive = false
                }
                launchSingleTop = true
            }
            paymentCoordinator.dispatch(
                PaymentIntent.StartDonation(
                    amountSats = amountSats,
                    address = LASR_DONATION_ADDRESS
                )
            )
        }
    )
}

private val LASR_DONATION_ADDRESS =
    LightningAddress(
        username = "lilsus",
        domain = "blink.sv"
    )
