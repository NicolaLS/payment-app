package xyz.lilsus.raylsuite.feature.languagesettings

import org.jetbrains.compose.resources.getString
import xyz.lilsus.raylsuite.feature.languagesettings.generated.resources.Res
import xyz.lilsus.raylsuite.feature.languagesettings.generated.resources.search_placeholder

suspend fun nativeLanguageSearchPlaceholder(): String = getString(Res.string.search_placeholder)
