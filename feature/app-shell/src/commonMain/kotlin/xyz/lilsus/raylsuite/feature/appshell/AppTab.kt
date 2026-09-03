package xyz.lilsus.raylsuite.feature.appshell

import org.jetbrains.compose.resources.StringResource
import xyz.lilsus.raylsuite.feature.appshell.generated.resources.Res
import xyz.lilsus.raylsuite.feature.appshell.generated.resources.app_tab_hub
import xyz.lilsus.raylsuite.feature.appshell.generated.resources.app_tab_recent
import xyz.lilsus.raylsuite.feature.appshell.generated.resources.app_tab_scan
import xyz.lilsus.raylsuite.feature.appshell.generated.resources.app_tab_settings

/**
 * The top-level destinations of an app. The order is the tab-bar order and the stored value is
 * the stable identity platform shells use to name a tab.
 */
enum class AppTab(val storedValue: String) {
    Scan("scan"),
    Recent("recent"),
    Hub("hub"),
    Settings("settings");

    internal val label: StringResource
        get() =
            when (this) {
                Scan -> Res.string.app_tab_scan
                Recent -> Res.string.app_tab_recent
                Hub -> Res.string.app_tab_hub
                Settings -> Res.string.app_tab_settings
            }

    companion object {
        val Default: AppTab = Scan

        fun fromStoredValue(value: String?): AppTab =
            entries.firstOrNull { it.storedValue == value } ?: Default
    }
}
