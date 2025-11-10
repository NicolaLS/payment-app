package xyz.lilsus.papp.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

@Composable
actual fun <T : Any> rememberRetainedInstance(
    key: String?,
    factory: () -> T,
    onDispose: (T) -> Unit,
): T {
    val instance = if (key != null) {
        remember(key) { factory() }
    } else {
        remember { factory() }
    }

    DisposableEffect(key1 = instance) {
        onDispose { onDispose(instance) }
    }

    return instance
}
