package xyz.lilsus.blip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.blip.generated.resources.Res
import xyz.lilsus.raylsuite.blip.generated.resources.app_name
import xyz.lilsus.raylsuite.blip.generated.resources.open_settings
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.settings.SettingsFlow
import xyz.lilsus.raylsuite.feature.themesettings.rememberThemePreferences

@Composable
fun App() {
    val themePreferences = rememberThemePreferences(storageName = "blip_preferences")
    val themePreference by themePreferences.preference.collectAsState(
        initial = ThemePreference.System
    )
    var showSettings by remember { mutableStateOf(false) }

    RaylSuiteTheme(themePreference = themePreference) {
        if (showSettings) {
            SettingsFlow(
                storageName = "blip_preferences",
                themePreferences = themePreferences,
                bitcoinPriceProvider = unavailableBitcoinPriceProvider,
                onBack = { showSettings = false }
            )
        } else {
            AppHome(onOpenSettings = { showSettings = true })
        }
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

private val unavailableBitcoinPriceProvider = BitcoinPriceProvider { null }
