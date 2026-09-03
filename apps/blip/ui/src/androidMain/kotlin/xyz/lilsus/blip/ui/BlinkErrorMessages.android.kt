package xyz.lilsus.blip.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource

@Composable
fun blinkErrorMessageFor(error: BlinkUiError): String = when (val text = error.text()) {
    is BlinkErrorText.Plain -> stringResource(text.resource)
    is BlinkErrorText.Formatted -> stringResource(text.resource, text.argument)
}
