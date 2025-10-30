package xyz.lilsus.papp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.presentation.settings.CurrencySettingsScreen
import xyz.lilsus.papp.presentation.settings.CurrencySettingsViewModel
import xyz.lilsus.papp.presentation.settings.LanguageSettingsScreen
import xyz.lilsus.papp.presentation.settings.ManageWalletsScreen
import xyz.lilsus.papp.presentation.settings.PaymentsSettingsScreen
import xyz.lilsus.papp.presentation.settings.PaymentsSettingsViewModel
import xyz.lilsus.papp.presentation.settings.SettingsScreen
import xyz.lilsus.papp.presentation.settings.wallet.WalletSettingsViewModel
import xyz.lilsus.papp.presentation.settings.wallet.WalletSettingsEvent
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.use_cases.ObserveCurrencyPreferenceUseCase

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

fun NavGraphBuilder.settingsScreen(
    navController: NavController,
    onBack: () -> Unit = {},
) {
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
            LanguageSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsManageWallets> {
            WalletSettingsEntry(navController = navController)
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

@Composable
private fun WalletSettingsEntry(navController: NavController) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = remember { koin.get<WalletSettingsViewModel>() }

    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }

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
        onAddWallet = { navController.navigateToConnectWallet() },
        onSelectWallet = { viewModel.selectWallet(it) },
        onRemoveWallet = { pubKey -> viewModel.removeWallet(pubKey) },
    )
}

@Composable
private fun CurrencySettingsEntry(onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = remember { koin.get<CurrencySettingsViewModel>() }

    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }

    val state by viewModel.uiState.collectAsState()

    CurrencySettingsScreen(
        state = state,
        onQueryChange = { viewModel.updateSearch(it) },
        onCurrencySelected = { viewModel.selectCurrency(it) },
        onBack = onBack,
    )
}

@Composable
private fun PaymentsSettingsEntry(onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = remember { koin.get<PaymentsSettingsViewModel>() }

    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }

    val state by viewModel.uiState.collectAsState()

    PaymentsSettingsScreen(
        state = state,
        onBack = onBack,
        onModeSelected = { viewModel.selectMode(it) },
        onThresholdChanged = { threshold -> viewModel.updateThreshold(threshold) },
        onConfirmManualEntryChanged = { enabled -> viewModel.setConfirmManualEntry(enabled) },
    )
}

@Composable
private fun SettingsOverviewEntry(navController: NavController, onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val observeWalletConnection = remember { koin.get<ObserveWalletConnectionUseCase>() }
    val observeCurrencyPreference = remember { koin.get<ObserveCurrencyPreferenceUseCase>() }
    val wallet by observeWalletConnection().collectAsState(initial = null)
    val subtitle = wallet?.let { formatWalletSubtitle(it) }
    val currency by observeCurrencyPreference().collectAsState(initial = CurrencyCatalog.infoFor("SAT").currency)
    val currencyLabel = stringResource(CurrencyCatalog.infoFor(currency).nameRes)

    SettingsScreen(
        onBack = onBack,
        onManageWallets = { navController.navigateToSettingsManageWallets() },
        onPayments = { navController.navigateToSettingsPayments() },
        onCurrency = { navController.navigateToSettingsCurrency() },
        onLanguage = { navController.navigateToSettingsLanguage() },
        walletSubtitle = subtitle,
        currencySubtitle = currencyLabel,
    )
}

private fun formatWalletSubtitle(connection: WalletConnection): String {
    val key = connection.walletPublicKey
    return if (key.length <= 12) key else buildString {
        append(key.take(6))
        append("…")
        append(key.takeLast(4))
    }
}
