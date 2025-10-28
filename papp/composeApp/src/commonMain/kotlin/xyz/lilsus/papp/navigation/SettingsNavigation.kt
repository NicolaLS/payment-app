package xyz.lilsus.papp.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import xyz.lilsus.papp.presentation.settings.CurrencySettingsScreen
import xyz.lilsus.papp.presentation.settings.LanguageSettingsScreen
import xyz.lilsus.papp.presentation.settings.ManageWalletsScreen
import xyz.lilsus.papp.presentation.settings.SettingsScreen
import xyz.lilsus.papp.presentation.settings.PaymentsSettingsScreen

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
            SettingsScreen(
                onBack = onBack,
                onManageWallets = { navController.navigateToSettingsManageWallets() },
                onPayments = { navController.navigateToSettingsPayments() },
                onCurrency = { navController.navigateToSettingsCurrency() },
                onLanguage = { navController.navigateToSettingsLanguage() },
            )
        }
        composable<SettingsPayments> {
            PaymentsSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsCurrency> {
            CurrencySettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsLanguage> {
            LanguageSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<SettingsManageWallets> {
            ManageWalletsScreen(onBack = { navController.popBackStack() })
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
