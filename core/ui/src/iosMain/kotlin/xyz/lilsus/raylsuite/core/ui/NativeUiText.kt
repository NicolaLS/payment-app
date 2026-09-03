package xyz.lilsus.raylsuite.core.ui

import org.jetbrains.compose.resources.getString
import xyz.lilsus.raylsuite.core.ui.generated.resources.Res
import xyz.lilsus.raylsuite.core.ui.generated.resources.action_back

suspend fun nativeBackActionText(): String = getString(Res.string.action_back)
