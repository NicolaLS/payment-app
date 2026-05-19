package xyz.lilsus.papp.presentation.common

import androidx.compose.ui.text.input.PlatformImeOptions

expect fun numericKeyboardPlatformImeOptions(
    doneLabel: String,
    onDone: () -> Unit
): PlatformImeOptions?
