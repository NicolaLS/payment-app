package xyz.lilsus.raylsuite.core.ui

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

suspend fun nativeBackActionText(): String =
    nativeString(NativeStringResource(table = "CoreUI", key = "action_back"))
