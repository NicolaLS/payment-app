package xyz.lilsus.flint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.flint.application.wallet.WalletAccessState
import xyz.lilsus.flint.feature.payment.PaymentCoordinator
import xyz.lilsus.flint.feature.payment.PaymentFlow
import xyz.lilsus.flint.feature.payment.PaymentIntent
import xyz.lilsus.flint.feature.payment.flintPaymentErrorMessageFor
import xyz.lilsus.flint.feature.payment.getFlintPaymentErrorMessageFor
import xyz.lilsus.flint.feature.wallet.WalletAction
import xyz.lilsus.flint.feature.wallet.WalletViewModel
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.settings.SettingsEntry
import xyz.lilsus.raylsuite.feature.settings.SettingsFlow
import xyz.lilsus.raylsuite.feature.settings.SettingsLegalLinks
import xyz.lilsus.raylsuite.feature.settings.SettingsStartDestination
import xyz.lilsus.raylsuite.feature.themesettings.ThemePreferences
import xyz.lilsus.raylsuite.feature.walletmanagement.ManagedWallet
import xyz.lilsus.raylsuite.feature.walletmanagement.WalletManagementScreen
import xyz.lilsus.raylsuite.flint.generated.resources.Res
import xyz.lilsus.raylsuite.flint.generated.resources.app_name
import xyz.lilsus.raylsuite.flint.generated.resources.settings_wallet_subtitle
import xyz.lilsus.raylsuite.flint.generated.resources.settings_wallet_title

internal fun NavGraphBuilder.flintHome(
    navController: NavController,
    paymentCoordinator: PaymentCoordinator,
    themePreferences: ThemePreferences,
    bitcoinPriceProvider: BitcoinPriceProvider,
    currencyPreferences: CurrencyPreferences,
    paymentPreferences: PaymentPreferencesRepository,
    contactsRepository: ContactsRepository,
    walletViewModel: WalletViewModel,
    networkLabel: String
) {
    composable<FlintDestination.Home> {
        PaymentFlow(
            coordinator = paymentCoordinator,
            appTitle = stringResource(Res.string.app_name),
            estimatedFeeHint = null,
            errorMessageFor = ::flintPaymentErrorMessageFor,
            eventErrorMessageFor = ::getFlintPaymentErrorMessageFor,
            onNavigateSettings = { navController.navigate(FlintDestination.Settings) },
            onNavigateShortcutCreate = {
                navController.navigate(FlintDestination.ShortcutCreate)
            },
            onNavigateContacts = { navController.navigate(FlintDestination.Contacts) }
        )
    }
    composable<FlintDestination.Settings> {
        FlintSettings(
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
    composable<FlintDestination.Contacts> {
        FlintSettings(
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
    composable<FlintDestination.ShortcutCreate> {
        FlintSettings(
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
    composable<FlintDestination.WalletManagement> {
        val state by walletViewModel.state.collectAsState()
        LaunchedEffect(state.access) {
            if (state.access == WalletAccessState.NoWallet) {
                navController.navigate(FlintDestination.Welcome) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        WalletManagementScreen(
            wallet =
                if (state.access == WalletAccessState.Connected) {
                    ManagedWallet(
                        id = "spark",
                        title = stringResource(Res.string.settings_wallet_title),
                        details =
                            listOf(
                                stringResource(Res.string.settings_wallet_subtitle),
                                networkLabel
                            )
                    )
                } else {
                    null
                },
            onBack = navController::navigateUp,
            onAddWallet = { navController.navigate(FlintDestination.AddWalletFromSettings) },
            onRemoveWallet = { walletViewModel.dispatch(WalletAction.ConfirmRemoval) }
        )
    }
}

@Composable
private fun FlintSettings(
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
        storageName = FLINT_PREFERENCES,
        themePreferences = themePreferences,
        bitcoinPriceProvider = bitcoinPriceProvider,
        legalLinks = FLINT_LEGAL_LINKS,
        onBack = navController::navigateUp,
        modifier = Modifier,
        startDestination = startDestination,
        currencyPreferences = currencyPreferences,
        paymentPreferences = paymentPreferences,
        contactsRepository = contactsRepository,
        leadingEntries =
            listOf(
                SettingsEntry(
                    id = "wallet",
                    title = stringResource(Res.string.settings_wallet_title),
                    subtitle = stringResource(Res.string.settings_wallet_subtitle),
                    onClick = { navController.navigate(FlintDestination.WalletManagement) }
                )
            ),
        donationAppName = stringResource(Res.string.app_name),
        onDonate = { amountSats ->
            navController.navigate(FlintDestination.Home) {
                popUpTo<FlintDestination.Home> { inclusive = false }
                launchSingleTop = true
            }
            paymentCoordinator.dispatch(
                PaymentIntent.StartDonation(amountSats, FLINT_DONATION_ADDRESS)
            )
        }
    )
}

private val FLINT_DONATION_ADDRESS =
    checkNotNull(LightningAddress.parse("lilsus@blink.sv"))

private val FLINT_LEGAL_LINKS =
    SettingsLegalLinks(sourceCodeUrl = "https://github.com/NicolaLS/lasr")
