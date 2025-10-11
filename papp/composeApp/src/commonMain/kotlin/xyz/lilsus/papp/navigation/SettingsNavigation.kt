package xyz.lilsus.papp.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.serialization.Serializable
import xyz.lilsus.papp.presentation.settings.SettingsScreen

@Serializable
internal object Settings

@Serializable
internal object SettingsSubNav


fun NavGraphBuilder.settingsScreen(onBack: () -> Unit = {}) {
    navigation<SettingsSubNav>(startDestination = Settings) {
        composable<Settings> {
            SettingsScreen(onBack)
        }
    }
}

fun NavController.navigateToSettings() {
    navigate(route = Settings)
}