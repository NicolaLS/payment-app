package xyz.lilsus.raylsuite.feature.appshell

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

/**
 * Localized tab titles keyed by [AppTab.storedValue], for a platform shell that draws the tab
 * bar itself.
 */
suspend fun appTabTitles(): Map<String, String> = AppTab.entries.associate { tab ->
    tab.storedValue to
        nativeString(
            NativeStringResource(
                table = "AppShell",
                key = "app_tab_${tab.storedValue}"
            )
        )
}
