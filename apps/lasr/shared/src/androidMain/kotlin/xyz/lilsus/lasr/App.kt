package xyz.lilsus.lasr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import xyz.lilsus.raylsuite.feature.settings.PerformanceDiagnostics

@Composable
fun App(performanceDiagnostics: PerformanceDiagnostics? = null) {
    var connectionGeneration by rememberSaveable { mutableIntStateOf(0) }
    key(connectionGeneration) {
        NwcExperience(
            configuration = LASR_EXPERIENCE,
            performanceDiagnostics = performanceDiagnostics,
            onRemoved = { connectionGeneration += 1 }
        )
    }
}
