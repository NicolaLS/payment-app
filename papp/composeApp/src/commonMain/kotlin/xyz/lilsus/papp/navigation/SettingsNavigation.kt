package xyz.lilsus.papp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatformTools
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.settings_language_system_default
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.model.LanguageCatalog
import xyz.lilsus.papp.domain.model.LanguagePreference
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.use_cases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.presentation.common.rememberRetainedInstance
import xyz.lilsus.papp.presentation.main.scan.rememberCameraPermissionState
import xyz.lilsus.papp.presentation.main.scan.rememberQrScannerController
import xyz.lilsus.papp.presentation.settings.*
import xyz.lilsus.papp.presentation.settings.add_wallet.AddWalletEvent
import xyz.lilsus.papp.presentation.settings.add_wallet.AddWalletScreen
import xyz.lilsus.papp.presentation.settings.add_wallet.AddWalletViewModel
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
internal object SettingsManageWallets

@Serializable
internal object SettingsAddWallet

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
        composable<SettingsManageWallets> {
            WalletSettingsEntry(navController = navController)
        }
        composable<SettingsAddWallet> {
            AddWalletEntry(navController = navController)
        }
    }
}

fun NavController.navigateToSettings() {
    navigate(route = Settings)
}

fun NavController.navigateToSettingsPayments() {
    navigate(route = SettingsPayments)
}

fun NavController.navigateToSettingsCurrency() {
    navigate(route = SettingsCurrency)
}

fun NavController.navigateToSettingsLanguage() {
    navigate(route = SettingsLanguage)
}

fun NavController.navigateToSettingsManageWallets() {
    navigate(route = SettingsManageWallets)
}

fun NavController.navigateToSettingsAddWallet() {
    navigate(route = SettingsAddWallet)
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
                is WalletSettingsEvent.WalletActivated -> Unit // TODO surface feedback when snackbars are available
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    ManageWalletsScreen(
        state = uiState,
        onBack = { navController.popBackStack() },
        onAddWallet = { navController.navigateToSettingsAddWallet() },
        onSelectWallet = { viewModel.selectWallet(it) },
        onRemoveWallet = { pubKey -> viewModel.removeWallet(pubKey) }
    )
}

@Composable
private fun AddWalletEntry(navController: NavController) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<AddWalletViewModel>() },
        onDispose = { it.clear() }
    )
    val clipboard = LocalClipboardManager.current
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
        viewModel.prefillUriIfValid(clipboard.getText()?.text)
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

    SettingsScreen(
        onBack = onBack,
        onManageWallets = { navController.navigateToSettingsManageWallets() },
        onPayments = { navController.navigateToSettingsPayments() },
        onCurrency = { navController.navigateToSettingsCurrency() },
        onLanguage = { navController.navigateToSettingsLanguage() },
        walletSubtitle = subtitle,
        currencySubtitle = currencyLabel,
        languageSubtitle = languageLabel
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
