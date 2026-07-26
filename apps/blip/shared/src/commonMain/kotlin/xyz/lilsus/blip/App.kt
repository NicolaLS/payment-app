package xyz.lilsus.blip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme

@Composable
fun App() {
    RaylSuiteTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Hello World")
        }
    }
}
