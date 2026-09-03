package xyz.lilsus.raylsuite.feature.appshell

import org.jetbrains.compose.resources.getString

/**
 * Localized tab titles keyed by [AppTab.storedValue], for a platform shell that draws the tab
 * bar itself. It keeps one set of translations rather than a second copy per platform.
 */
suspend fun appTabTitles(): Map<String, String> =
    AppTab.entries.associate { tab -> tab.storedValue to getString(tab.label) }
