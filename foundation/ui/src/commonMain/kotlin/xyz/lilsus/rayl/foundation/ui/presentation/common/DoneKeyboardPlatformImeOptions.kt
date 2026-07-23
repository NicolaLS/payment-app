package xyz.lilsus.rayl.foundation.ui.presentation.common

import androidx.compose.ui.text.input.PlatformImeOptions

expect fun doneKeyboardPlatformImeOptions(
    doneLabel: String,
    onDone: () -> Unit
): PlatformImeOptions?
