package xyz.lilsus.raylsuite.core.ui.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** Product name used to format reusable, localized native presentation. */
val LocalProductName = staticCompositionLocalOf<String> { error("Product name was not supplied") }
