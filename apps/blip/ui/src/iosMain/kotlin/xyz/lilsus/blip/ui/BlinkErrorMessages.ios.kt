package xyz.lilsus.blip.ui

import xyz.lilsus.raylsuite.core.ui.resources.resolveNative

fun nativeBlinkErrorMessageFor(error: BlinkUiError): String = error.text().resolveNative()
