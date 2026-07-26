package xyz.lilsus.raylsuite.core.ui.keyboard

import androidx.compose.ui.text.input.PlatformImeOptions

expect fun doneKeyboardPlatformImeOptions(
    doneLabel: String,
    onDone: () -> Unit
): PlatformImeOptions?
