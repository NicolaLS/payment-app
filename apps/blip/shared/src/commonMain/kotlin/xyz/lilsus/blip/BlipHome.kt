package xyz.lilsus.blip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.blinkcontacts.BlinkContactsImportButton
import xyz.lilsus.blip.feature.blinkcontacts.BlinkContactsImportScreen
import xyz.lilsus.blip.feature.blinkcontacts.BlinkContactsImportViewModel
import xyz.lilsus.blip.feature.payment.PaymentCoordinator
import xyz.lilsus.blip.feature.payment.PaymentFlow
import xyz.lilsus.blip.feature.payment.blipPaymentErrorMessageFor
import xyz.lilsus.blip.feature.payment.getBlipPaymentErrorMessageFor
import xyz.lilsus.blip.feature.walletsettings.BlinkWalletSettingsActions
import xyz.lilsus.blip.feature.walletsettings.BlinkWalletSettingsViewModel
import xyz.lilsus.blip.generated.resources.Res
import xyz.lilsus.blip.generated.resources.app_name
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.blip.ui.generated.resources.Res as BlipUiRes
import xyz.lilsus.blip.ui.generated.resources.result_paid_fee_blink_hint
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.settings.SettingsFlow
import xyz.lilsus.raylsuite.feature.settings.SettingsLegalLinks
import xyz.lilsus.raylsuite.feature.settings.SettingsStartDestination
import xyz.lilsus.raylsuite.feature.themesettings.ThemePreferences

internal fun NavGraphBuilder.blipHome(
    navController: NavController,
    themePreferences: ThemePreferences,
    bitcoinPriceProvider: BitcoinPriceProvider,
    currencyPreferences: CurrencyPreferences,
    paymentPreferences: PaymentPreferencesRepository,
    contactsRepository: ContactsRepository,
    paymentCoordinator: PaymentCoordinator,
    blinkWallet: BlinkWallet,
    onRemoveWallet: () -> Unit
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
            contactsRepository = contactsRepository,
            blinkWallet = blinkWallet,
            paymentCoordinator = paymentCoordinator,
            onRemoveWallet = onRemoveWallet
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
            contactsRepository = contactsRepository,
            blinkWallet = blinkWallet,
            paymentCoordinator = paymentCoordinator,
            onRemoveWallet = onRemoveWallet
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
            contactsRepository = contactsRepository,
            blinkWallet = blinkWallet,
            paymentCoordinator = paymentCoordinator,
            onRemoveWallet = onRemoveWallet
        )
    }
    composable<BlipDestination.BlinkContactsImport> {
        val viewModel =
            remember(blinkWallet, contactsRepository) {
                BlinkContactsImportViewModel(
                    blinkWallet = blinkWallet,
                    contactsRepository = contactsRepository
                )
            }
        val state by viewModel.uiState.collectAsState()
        DisposableEffect(viewModel) {
            onDispose(viewModel::clear)
        }
        LaunchedEffect(viewModel) {
            viewModel.loadBlinkContacts()
        }
        BlinkContactsImportScreen(
            state = state,
            onBack = navController::navigateUp,
            onToggleContact = viewModel::toggleBlinkContact,
            onToggleAll = viewModel::toggleAllBlinkContacts,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onImport = viewModel::importSelectedBlinkContacts,
            onSkip = null
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
    contactsRepository: ContactsRepository,
    blinkWallet: BlinkWallet,
    paymentCoordinator: PaymentCoordinator,
    onRemoveWallet: () -> Unit
) {
    val walletSettingsViewModel =
        remember(blinkWallet) {
            BlinkWalletSettingsViewModel(blinkWallet)
        }
    val walletSettingsState by walletSettingsViewModel.uiState.collectAsState()
    DisposableEffect(walletSettingsViewModel) {
        onDispose(walletSettingsViewModel::clear)
    }
    SettingsFlow(
        storageName = BLIP_PREFERENCES,
        themePreferences = themePreferences,
        bitcoinPriceProvider = bitcoinPriceProvider,
        legalLinks = BLIP_LEGAL_LINKS,
        onBack = navController::navigateUp,
        modifier = Modifier,
        startDestination = startDestination,
        currencyPreferences = currencyPreferences,
        paymentPreferences = paymentPreferences,
        contactsRepository = contactsRepository,
        overviewBottomContent = {
            BlinkWalletSettingsActions(
                state = walletSettingsState,
                onRefreshConnection = walletSettingsViewModel::refreshConnection,
                onRemoveWallet = onRemoveWallet
            )
        },
        additionalContactActions = {
            BlinkContactsImportButton(
                onClick = {
                    navController.navigate(BlipDestination.BlinkContactsImport)
                }
            )
        },
        donationAppName = stringResource(Res.string.app_name),
        onDonate = { amountSats ->
            navController.navigate(BlipDestination.Home) {
                popUpTo<BlipDestination.Home> {
                    inclusive = false
                }
                launchSingleTop = true
            }
            paymentCoordinator.dispatch(
                PaymentIntent.StartDonation(
                    amountSats = amountSats,
                    address = BLIP_DONATION_ADDRESS
                )
            )
        }
    )
}

private val BLIP_DONATION_ADDRESS =
    LightningAddress(
        username = "lilsus",
        domain = "blink.sv"
    )

private val BLIP_LEGAL_LINKS =
    SettingsLegalLinks(
        privacyPolicyUrl =
            "https://github.com/NicolaLS/lasr/blob/main/docs/legal/blip/privacy.md",
        termsUrl =
            "https://github.com/NicolaLS/lasr/blob/main/docs/legal/blip/terms.md",
        sourceCodeUrl = "https://github.com/NicolaLS/lasr"
    )
