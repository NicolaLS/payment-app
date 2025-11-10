package xyz.lilsus.papp.presentation.common

import androidx.compose.runtime.Composable

@Composable
expect fun <T : Any> rememberRetainedInstance(
    key: String? = null,
    factory: () -> T,
    onDispose: (T) -> Unit = {},
): T
