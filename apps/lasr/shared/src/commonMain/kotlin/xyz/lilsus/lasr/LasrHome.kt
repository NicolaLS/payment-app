package xyz.lilsus.lasr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.lasr.feature.walletdetails.NwcWalletDetailsScreen
import xyz.lilsus.lasr.integration.nwc.NwcWallet
import xyz.lilsus.lasr.integration.nwc.NwcWalletConnection
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
import xyz.lilsus.raylsuite.feature.settings.SettingsEntry
import xyz.lilsus.raylsuite.feature.settings.SettingsFlow
import xyz.lilsus.raylsuite.feature.settings.SettingsStartDestination
import xyz.lilsus.raylsuite.feature.themesettings.ThemePreferences
import xyz.lilsus.raylsuite.feature.walletmanagement.ManagedWallet
import xyz.lilsus.raylsuite.feature.walletmanagement.WalletManagementScreen
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.Res as WalletRes
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet_subtitle
import xyz.lilsus.raylsuite.lasr.generated.resources.Res
import xyz.lilsus.raylsuite.lasr.generated.resources.app_name

internal fun NavGraphBuilder.lasrHome(
    navController: NavController,
    themePreferences: ThemePreferences,
    bitcoinPriceProvider: BitcoinPriceProvider,
    currencyPreferences: CurrencyPreferences,
    paymentPreferences: PaymentPreferencesRepository,
    contactsRepository: ContactsRepository,
    paymentCoordinator: PaymentCoordinator,
    nwcWallet: NwcWallet
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
            paymentCoordinator = paymentCoordinator,
            nwcWallet = nwcWallet
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
            paymentCoordinator = paymentCoordinator,
            nwcWallet = nwcWallet
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
            paymentCoordinator = paymentCoordinator,
            nwcWallet = nwcWallet
        )
    }
    composable<LasrDestination.WalletManagement> {
        val connection by nwcWallet.connection.collectAsState()
        val scope = rememberCoroutineScope()
        WalletManagementScreen(
            wallet =
                connection?.let {
                    ManagedWallet(
                        id = it.walletPublicKey,
                        title =
                            it.alias?.takeIf(String::isNotBlank)
                                ?: it.walletPublicKey
                    )
                },
            onBack = navController::navigateUp,
            onAddWallet = {
                navController.navigate(LasrDestination.AddWalletFromSettings)
            },
            onRemoveWallet = {
                scope.launch {
                    nwcWallet.disconnect()
                }
            },
            onWalletDetails = {
                navController.navigate(LasrDestination.WalletDetails)
            }
        )
    }
    composable<LasrDestination.WalletDetails> {
        val connection by nwcWallet.connection.collectAsState()
        if (connection == null) {
            LaunchedEffect(Unit) {
                navController.navigateUp()
            }
        } else {
            NwcWalletDetailsScreen(
                connection = requireNotNull(connection),
                onBack = navController::navigateUp
            )
        }
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
    paymentCoordinator: PaymentCoordinator,
    nwcWallet: NwcWallet
) {
    val connection by nwcWallet.connection.collectAsState()
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
        leadingEntries =
            listOf(
                SettingsEntry(
                    id = "wallet",
                    title = stringResource(WalletRes.string.settings_manage_wallet),
                    subtitle =
                        connection?.let(::formatWalletSubtitle)
                            ?: stringResource(
                                WalletRes.string.settings_manage_wallet_subtitle
                            ),
                    onClick = {
                        navController.navigate(LasrDestination.WalletManagement)
                    }
                )
            ),
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

private fun formatWalletSubtitle(connection: NwcWalletConnection): String {
    connection.alias?.takeIf(String::isNotBlank)?.let { return it }
    val publicKey = connection.walletPublicKey
    return if (publicKey.length <= 12) {
        publicKey
    } else {
        "${publicKey.take(6)}…${publicKey.takeLast(4)}"
    }
}

private val LASR_DONATION_ADDRESS =
    LightningAddress(
        username = "lilsus",
        domain = "blink.sv"
    )
