package xyz.lilsus.raylsuite.feature.languagesettings

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

suspend fun nativeLanguageSearchPlaceholder(): String =
    nativeString(NativeStringResource(table = "LanguageSettings", key = "search_placeholder"))
