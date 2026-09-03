package xyz.lilsus.raylsuite.core.ui.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource

/** Resolves [LocalizedText] inside composition. iOS resolves it with `resolveInCoroutine`. */
@Composable
fun LocalizedText.resolve(): String =
    argument?.let { stringResource(resource, it) } ?: stringResource(resource)
