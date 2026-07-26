package xyz.lilsus.raylsuite.core.ui.keyboard

import androidx.compose.ui.text.input.PlatformImeOptions

@Suppress("UNUSED_PARAMETER")
actual fun doneKeyboardPlatformImeOptions(
    doneLabel: String,
    onDone: () -> Unit
): PlatformImeOptions? = null
