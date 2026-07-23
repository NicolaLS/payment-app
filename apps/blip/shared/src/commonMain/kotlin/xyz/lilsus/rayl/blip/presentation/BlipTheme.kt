package xyz.lilsus.rayl.blip.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import xyz.lilsus.rayl.blip.generated.resources.Res
import xyz.lilsus.rayl.blip.generated.resources.inter_var
import xyz.lilsus.rayl.blip.generated.resources.roboto_serif_var
import xyz.lilsus.rayl.blip.platform.AppThemePreference

private val lightColors = lightColorScheme(
    primary = Color(0xFFF7931A),
    onPrimary = Color(0xFF1F2A33),
    primaryContainer = Color(0xFFFFE2C2),
    onPrimaryContainer = Color(0xFF4A2600),
    secondary = Color(0xFF274C77),
    onSecondary = Color.White,
    tertiary = Color(0xFF006B52),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF1F2A33),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF1F2A33),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF425A6B)
)

private val darkColors = darkColorScheme(
    primary = Color(0xFFF7931A),
    onPrimary = Color(0xFF1F2A33),
    primaryContainer = Color(0xFF5C3700),
    onPrimaryContainer = Color(0xFFFFE2C2),
    secondary = Color(0xFFA6C8FF),
    onSecondary = Color(0xFF00315A),
    tertiary = Color(0xFF4DE2B2),
    background = Color(0xFF1F2A33),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF1F2A33),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF2B3A45),
    onSurfaceVariant = Color(0xFFB8C7D5)
)

@Composable
fun BlipTheme(preference: AppThemePreference, content: @Composable () -> Unit) {
    val dark = when (preference) {
        AppThemePreference.System -> isSystemInDarkTheme()
        AppThemePreference.Light -> false
        AppThemePreference.Dark -> true
    }
    val inter = FontFamily(
        Font(
            resource = Res.font.inter_var,
            weight = FontWeight.Normal
        )
    )
    val serif = FontFamily(
        Font(
            resource = Res.font.roboto_serif_var,
            weight = FontWeight.Normal
        )
    )
    MaterialTheme(
        colorScheme = if (dark) darkColors else lightColors,
        typography = MaterialTheme.typography.copy(
            displayLarge = MaterialTheme.typography.displayLarge.copy(
                fontFamily = serif,
                fontWeight = FontWeight.Bold,
                fontSize = 54.sp
            ),
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = serif,
                fontWeight = FontWeight.Bold
            ),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = serif,
                fontWeight = FontWeight.SemiBold
            ),
            titleLarge = MaterialTheme.typography.titleLarge.copy(
                fontFamily = serif,
                fontWeight = FontWeight.SemiBold
            ),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = inter),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = inter),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = inter)
        ),
        content = content
    )
}
