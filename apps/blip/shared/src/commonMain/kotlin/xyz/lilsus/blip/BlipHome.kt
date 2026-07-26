package xyz.lilsus.blip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.blip.generated.resources.Res
import xyz.lilsus.raylsuite.blip.generated.resources.app_name
import xyz.lilsus.raylsuite.blip.generated.resources.open_settings
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.feature.settings.SettingsFlow
import xyz.lilsus.raylsuite.feature.themesettings.ThemePreferences

internal fun NavGraphBuilder.blipHome(
    navController: NavController,
    themePreferences: ThemePreferences,
    bitcoinPriceProvider: BitcoinPriceProvider
) {
    composable<BlipDestination.Home> {
        AppHome(
            onOpenSettings = {
                navController.navigate(BlipDestination.Settings)
            }
        )
    }
    composable<BlipDestination.Settings> {
        SettingsFlow(
            storageName = BLIP_PREFERENCES,
            themePreferences = themePreferences,
            bitcoinPriceProvider = bitcoinPriceProvider,
            onBack = navController::navigateUp
        )
    }
}

@Composable
private fun AppHome(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(Res.string.app_name))
        Button(onClick = onOpenSettings) {
            Text(stringResource(Res.string.open_settings))
        }
    }
}
