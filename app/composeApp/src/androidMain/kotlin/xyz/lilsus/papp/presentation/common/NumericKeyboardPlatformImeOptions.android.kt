package xyz.lilsus.papp.presentation.common

import androidx.compose.ui.text.input.PlatformImeOptions

@Suppress("UNUSED_PARAMETER")
actual fun numericKeyboardPlatformImeOptions(
    doneLabel: String,
    onDone: () -> Unit
): PlatformImeOptions? = null
