package xyz.lilsus.blip

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.ui.blipPaymentErrorMessageFor
import xyz.lilsus.blip.ui.generated.resources.Res as BlipUiRes
import xyz.lilsus.blip.ui.generated.resources.result_paid_fee_blink_hint
import xyz.lilsus.blip.ui.getBlipPaymentErrorMessageFor
import xyz.lilsus.raylsuite.blip.generated.resources.Res
import xyz.lilsus.raylsuite.blip.generated.resources.app_name
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences
import xyz.lilsus.raylsuite.feature.payment.PaymentCoordinator
import xyz.lilsus.raylsuite.feature.payment.PaymentFlow
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.settings.SettingsFlow
import xyz.lilsus.raylsuite.feature.settings.SettingsStartDestination
import xyz.lilsus.raylsuite.feature.themesettings.ThemePreferences

internal fun NavGraphBuilder.blipHome(
    navController: NavController,
    themePreferences: ThemePreferences,
    bitcoinPriceProvider: BitcoinPriceProvider,
    currencyPreferences: CurrencyPreferences,
    paymentPreferences: PaymentPreferencesRepository,
    contactsRepository: ContactsRepository,
    paymentCoordinator: PaymentCoordinator
) {
    composable<BlipDestination.Home> {
        PaymentFlow(
            coordinator = paymentCoordinator,
            appTitle = stringResource(Res.string.app_name),
            estimatedFeeHint =
                stringResource(BlipUiRes.string.result_paid_fee_blink_hint),
            errorMessageFor = ::blipPaymentErrorMessageFor,
            eventErrorMessageFor = ::getBlipPaymentErrorMessageFor,
            onNavigateSettings = {
                navController.navigate(BlipDestination.Settings)
            },
            onNavigateShortcutCreate = {
                navController.navigate(BlipDestination.ShortcutCreate)
            },
            onNavigateContacts = {
                navController.navigate(BlipDestination.Contacts)
            }
        )
    }
    composable<BlipDestination.Settings> {
        BlipSettings(
            startDestination = SettingsStartDestination.Overview,
            navController = navController,
            themePreferences = themePreferences,
            bitcoinPriceProvider = bitcoinPriceProvider,
            currencyPreferences = currencyPreferences,
            paymentPreferences = paymentPreferences,
            contactsRepository = contactsRepository
        )
    }
    composable<BlipDestination.Contacts> {
        BlipSettings(
            startDestination = SettingsStartDestination.Contacts,
            navController = navController,
            themePreferences = themePreferences,
            bitcoinPriceProvider = bitcoinPriceProvider,
            currencyPreferences = currencyPreferences,
            paymentPreferences = paymentPreferences,
            contactsRepository = contactsRepository
        )
    }
    composable<BlipDestination.ShortcutCreate> {
        BlipSettings(
            startDestination = SettingsStartDestination.ShortcutCreate,
            navController = navController,
            themePreferences = themePreferences,
            bitcoinPriceProvider = bitcoinPriceProvider,
            currencyPreferences = currencyPreferences,
            paymentPreferences = paymentPreferences,
            contactsRepository = contactsRepository
        )
    }
}

@Composable
private fun BlipSettings(
    startDestination: SettingsStartDestination,
    navController: NavController,
    themePreferences: ThemePreferences,
    bitcoinPriceProvider: BitcoinPriceProvider,
    currencyPreferences: CurrencyPreferences,
    paymentPreferences: PaymentPreferencesRepository,
    contactsRepository: ContactsRepository
) {
    SettingsFlow(
        storageName = BLIP_PREFERENCES,
        themePreferences = themePreferences,
        bitcoinPriceProvider = bitcoinPriceProvider,
        onBack = navController::navigateUp,
        modifier = Modifier,
        startDestination = startDestination,
        currencyPreferences = currencyPreferences,
        paymentPreferences = paymentPreferences,
        contactsRepository = contactsRepository
    )
}
