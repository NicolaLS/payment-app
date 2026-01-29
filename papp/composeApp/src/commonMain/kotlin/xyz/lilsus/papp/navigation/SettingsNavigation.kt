package xyz.lilsus.papp.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatformTools
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.settings_language_system_default
import papp.composeapp.generated.resources.settings_theme_dark
import papp.composeapp.generated.resources.settings_theme_light
import papp.composeapp.generated.resources.settings_theme_system_default
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.model.LanguageCatalog
import xyz.lilsus.papp.domain.model.LanguagePreference
import xyz.lilsus.papp.domain.model.ThemePreference
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.usecases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObserveLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObserveThemePreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.navigation.DonationNavigation.donationAddress
import xyz.lilsus.papp.navigation.DonationNavigation.emit
import xyz.lilsus.papp.navigation.DonationRequest
import xyz.lilsus.papp.navigation.Pay
import xyz.lilsus.papp.platform.readPlainText
import xyz.lilsus.papp.presentation.common.rememberRetainedInstance
import xyz.lilsus.papp.presentation.main.scan.rememberCameraPermissionState
import xyz.lilsus.papp.presentation.main.scan.rememberQrScannerController
import xyz.lilsus.papp.presentation.settings.ChooseWalletTypeScreen
import xyz.lilsus.papp.presentation.settings.CurrencySettingsScreen
import xyz.lilsus.papp.presentation.settings.CurrencySettingsViewModel
import xyz.lilsus.papp.presentation.settings.LanguageSettingsScreen
import xyz.lilsus.papp.presentation.settings.LanguageSettingsViewModel
import xyz.lilsus.papp.presentation.settings.ManageWalletsScreen
import xyz.lilsus.papp.presentation.settings.PaymentsSettingsScreen
import xyz.lilsus.papp.presentation.settings.PaymentsSettingsViewModel
import xyz.lilsus.papp.presentation.settings.SettingsScreen
import xyz.lilsus.papp.presentation.settings.ThemeSettingsScreen
import xyz.lilsus.papp.presentation.settings.ThemeSettingsViewModel
import xyz.lilsus.papp.presentation.settings.addblink.AddBlinkWalletEvent
import xyz.lilsus.papp.presentation.settings.addblink.AddBlinkWalletScreen
import xyz.lilsus.papp.presentation.settings.addblink.AddBlinkWalletViewModel
import xyz.lilsus.papp.presentation.settings.addwallet.AddWalletEvent
import xyz.lilsus.papp.presentation.settings.addwallet.AddWalletScreen
import xyz.lilsus.papp.presentation.settings.addwallet.AddWalletViewModel
import xyz.lilsus.papp.presentation.settings.wallet.WalletDetailsScreen
import xyz.lilsus.papp.presentation.settings.wallet.WalletDetailsViewModel
import xyz.lilsus.papp.presentation.settings.wallet.WalletSettingsEvent
import xyz.lilsus.papp.presentation.settings.wallet.WalletSettingsViewModel

@Serializable
internal object Settings

@Serializable
internal object SettingsSubNav

@Serializable
internal object SettingsPayments

@Serializable
internal object SettingsCurrency

@Serializable
internal object SettingsLanguage

@Serializable
internal object SettingsTheme

@Serializable
internal object SettingsManageWallets

@Serializable
internal object SettingsAddWallet

@Serializable
internal object SettingsChooseWalletType

@Serializable
internal object SettingsAddBlinkWallet

@Serializable
internal data class SettingsWalletDetails(val walletId: String)

fun NavGraphBuilder.settingsScreen(navController: NavController, onBack: () -> Unit = {}) {
    navigation<SettingsSubNav>(startDestination = Settings) {
        composable<Settings> {
            SettingsOverviewEntry(navController = navController, onBack = onBack)
        }
        composable<SettingsPayments> {
            PaymentsSettingsEntry(onBack = { navController.popBackStack() })
        }
        composable<SettingsCurrency> {
            CurrencySettingsEntry(onBack = { navController.popBackStack() })
        }
        composable<SettingsLanguage> {
            LanguageSettingsEntry(onBack = { navController.popBackStack() })
        }
        composable<SettingsTheme> {
            ThemeSettingsEntry(onBack = { navController.popBackStack() })
        }
        composable<SettingsManageWallets> {
            WalletSettingsEntry(navController = navController)
        }
        composable<SettingsAddWallet> {
            AddWalletEntry(navController = navController)
        }
        composable<SettingsChooseWalletType> {
            ChooseWalletTypeEntry(navController = navController)
        }
        composable<SettingsAddBlinkWallet> {
            AddBlinkWalletEntry(navController = navController)
        }
        composable<SettingsWalletDetails> { backStackEntry ->
            val route = backStackEntry.toRoute<SettingsWalletDetails>()
            WalletDetailsEntry(
                navController = navController,
                walletId = route.walletId
            )
        }
    }
}

fun NavController.navigateToSettings() {
    navigate(route = Settings) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsPayments() {
    navigate(route = SettingsPayments) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsCurrency() {
    navigate(route = SettingsCurrency) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsLanguage() {
    navigate(route = SettingsLanguage) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsTheme() {
    navigate(route = SettingsTheme) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsManageWallets() {
    navigate(route = SettingsManageWallets) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsWalletDetails(walletId: String) {
    navigate(route = SettingsWalletDetails(walletId)) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsAddWallet() {
    navigate(route = SettingsAddWallet) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsChooseWalletType() {
    navigate(route = SettingsChooseWalletType) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsAddBlinkWallet() {
    navigate(route = SettingsAddBlinkWallet) {
        launchSingleTop = true
    }
}

// Public navigation functions for access from outside settings (e.g., onboarding)
fun NavController.navigateToAddWallet() {
    navigate(route = SettingsAddWallet) {
        launchSingleTop = true
    }
}

fun NavController.navigateToAddBlinkWallet() {
    navigate(route = SettingsAddBlinkWallet) {
        launchSingleTop = true
    }
}

@Composable
private fun WalletSettingsEntry(navController: NavController) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<WalletSettingsViewModel>() },
        onDispose = { it.clear() }
    )

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WalletSettingsEvent.WalletRemoved,
                is WalletSettingsEvent.WalletActivated -> {
                    // TODO surface feedback when snackbars are available
                    Unit
                }
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    ManageWalletsScreen(
        state = uiState,
        onBack = { navController.popBackStack() },
        onAddWallet = { navController.navigateToSettingsChooseWalletType() },
        onSelectWallet = { viewModel.selectWallet(it) },
        onRemoveWallet = { pubKey -> viewModel.removeWallet(pubKey) },
        onWalletDetails = { pubKey ->
            navController.navigateToSettingsWalletDetails(pubKey)
        }
    )
}

@Composable
private fun WalletDetailsEntry(navController: NavController, walletId: String) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = {
            WalletDetailsViewModel(
                walletId = walletId,
                walletSettingsRepository = koin.get(),
                credentialStore = koin.get(),
                apiClient = koin.get(),
                dispatcher = koin.get()
            )
        },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    WalletDetailsScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onRefreshBlinkDefaultWallet = viewModel::refreshDefaultWalletId
    )
}

@Composable
private fun AddWalletEntry(navController: NavController) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<AddWalletViewModel>() },
        onDispose = { it.clear() }
    )

    val clipboard = LocalClipboard.current
    val state by viewModel.uiState.collectAsState()
    val scannerController = rememberQrScannerController()
    val cameraPermission = rememberCameraPermissionState()
    var scannerStarted by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    DisposableEffect(scannerController) {
        onDispose { scannerController.stop() }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddWalletEvent.NavigateToConfirm -> {
                    navController.popBackStack()
                    navController.navigateToConnectWallet(uri = event.uri)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val text = clipboard.getClipEntry()?.readPlainText()
        viewModel.prefillUriIfValid(text)
    }

    LaunchedEffect(cameraPermission.hasPermission) {
        if (!cameraPermission.hasPermission) {
            if (scannerStarted) {
                scannerController.stop()
                scannerStarted = false
            }
            if (!permissionRequested) {
                permissionRequested = true
                cameraPermission.request()
            }
            return@LaunchedEffect
        }

        permissionRequested = false
        if (!scannerStarted) {
            scannerController.start { value ->
                viewModel.handleScannedValue(value)
            }
            scannerStarted = true
        } else {
            scannerController.resume()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AddWalletScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onUriChange = viewModel::updateUri,
            onSubmit = viewModel::submit,
            controller = scannerController,
            isCameraPermissionGranted = cameraPermission.hasPermission
        )
    }
}

@Composable
private fun CurrencySettingsEntry(onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<CurrencySettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    CurrencySettingsScreen(
        state = state,
        onQueryChange = { viewModel.updateSearch(it) },
        onCurrencySelected = { viewModel.selectCurrency(it) },
        onBack = onBack
    )
}

@Composable
private fun LanguageSettingsEntry(onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<LanguageSettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    LanguageSettingsScreen(
        state = state,
        onQueryChange = { viewModel.updateSearch(it) },
        onOptionSelected = { viewModel.selectOption(it) },
        onBack = onBack
    )
}

@Composable
private fun ThemeSettingsEntry(onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<ThemeSettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    ThemeSettingsScreen(
        state = state,
        onThemeSelected = { viewModel.selectTheme(it) },
        onBack = onBack
    )
}

@Composable
private fun PaymentsSettingsEntry(onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<PaymentsSettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    PaymentsSettingsScreen(
        state = state,
        onBack = onBack,
        onModeSelected = { viewModel.selectMode(it) },
        onThresholdChanged = { threshold -> viewModel.updateThreshold(threshold) },
        onConfirmManualEntryChanged = { enabled -> viewModel.setConfirmManualEntry(enabled) },
        onVibrateOnScanChanged = { enabled -> viewModel.setVibrateOnScan(enabled) },
        onVibrateOnPaymentChanged = { enabled -> viewModel.setVibrateOnPayment(enabled) }
    )
}

@Composable
private fun SettingsOverviewEntry(navController: NavController, onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val observeWalletConnection = remember { koin.get<ObserveWalletConnectionUseCase>() }
    val observeCurrencyPreference = remember { koin.get<ObserveCurrencyPreferenceUseCase>() }
    val observeLanguagePreference = remember { koin.get<ObserveLanguagePreferenceUseCase>() }
    val observeThemePreference = remember { koin.get<ObserveThemePreferenceUseCase>() }
    val wallet by observeWalletConnection().collectAsState(initial = null)
    val subtitle = wallet?.let { formatWalletSubtitle(it) }
    val currency by observeCurrencyPreference().collectAsState(
        initial = CurrencyCatalog.infoFor("SAT").currency
    )
    val currencyLabel = stringResource(CurrencyCatalog.infoFor(currency).nameRes)
    val languagePreference by observeLanguagePreference().collectAsState(
        initial = LanguagePreference.System(LanguageCatalog.fallback.tag)
    )
    val languageLabel = formatLanguageSubtitle(languagePreference)
    val themePreference by observeThemePreference().collectAsState(initial = ThemePreference.System)
    val themeLabel = formatThemeSubtitle(themePreference)

    SettingsScreen(
        onBack = onBack,
        onManageWallets = { navController.navigateToSettingsManageWallets() },
        onPayments = { navController.navigateToSettingsPayments() },
        onCurrency = { navController.navigateToSettingsCurrency() },
        onLanguage = { navController.navigateToSettingsLanguage() },
        onTheme = { navController.navigateToSettingsTheme() },
        onDonate = { amount ->
            emit(DonationRequest(amountSats = amount, address = donationAddress))
            navController.navigate(route = Pay) {
                popUpTo(Pay) { inclusive = false }
                launchSingleTop = true
            }
        },
        walletSubtitle = subtitle,
        currencySubtitle = currencyLabel,
        languageSubtitle = languageLabel,
        themeSubtitle = themeLabel
    )
}

private fun formatWalletSubtitle(connection: WalletConnection): String {
    connection.alias?.takeIf { it.isNotBlank() }?.let { return it }
    val key = connection.walletPublicKey
    return if (key.length <= 12) {
        key
    } else {
        buildString {
            append(key.take(6))
            append("…")
            append(key.takeLast(4))
        }
    }
}

@Composable
private fun formatLanguageSubtitle(preference: LanguagePreference): String {
    val resolvedName = resolveLanguageName(preference.resolvedTag)
    return when (preference) {
        is LanguagePreference.System -> stringResource(
            Res.string.settings_language_system_default,
            resolvedName
        )

        is LanguagePreference.Override -> resolveLanguageName(preference.overrideTag)
    }
}

@Composable
private fun resolveLanguageName(tag: String): String {
    LanguageCatalog.infoForTag(tag)?.let { info ->
        return info.displayName
    }
    val fallbackCode = tag.substringBefore('-')
    LanguageCatalog.infoForCode(fallbackCode)?.let { info ->
        return info.displayName
    }
    return tag
}

@Composable
private fun formatThemeSubtitle(preference: ThemePreference): String = when (preference) {
    ThemePreference.System -> stringResource(
        Res.string.settings_theme_system_default,
        stringResource(
            if (isSystemInDarkTheme()) {
                Res.string.settings_theme_dark
            } else {
                Res.string.settings_theme_light
            }
        )
    )

    ThemePreference.Light -> stringResource(Res.string.settings_theme_light)

    ThemePreference.Dark -> stringResource(Res.string.settings_theme_dark)
}

@Composable
private fun ChooseWalletTypeEntry(navController: NavController) {
    ChooseWalletTypeScreen(
        onBack = { navController.popBackStack() },
        onNwcSelected = {
            navController.popBackStack()
            navController.navigateToSettingsAddWallet()
        },
        onBlinkSelected = {
            navController.popBackStack()
            navController.navigateToSettingsAddBlinkWallet()
        }
    )
}

@Composable
private fun AddBlinkWalletEntry(navController: NavController) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<AddBlinkWalletViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddBlinkWalletEvent.Success -> {
                    // Try to pop to settings manage wallets
                    val popped = navController.popBackStack(
                        route = SettingsManageWallets,
                        inclusive = false
                    )
                    if (!popped) {
                        // Coming from onboarding - navigate to Pay
                        navController.navigate(Pay) {
                            popUpTo(Onboarding) { inclusive = true }
                        }
                    }
                }

                AddBlinkWalletEvent.Cancelled -> {
                    navController.popBackStack()
                }
            }
        }
    }

    AddBlinkWalletScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onAliasChange = viewModel::updateAlias,
        onApiKeyChange = viewModel::updateApiKey,
        onSubmit = viewModel::submit
    )
}
