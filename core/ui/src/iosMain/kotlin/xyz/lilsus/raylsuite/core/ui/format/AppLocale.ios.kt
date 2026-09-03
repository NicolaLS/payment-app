package xyz.lilsus.raylsuite.core.ui.format

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages

actual fun currentAppLocale(): AppLocale {
    val preferredLanguage = NSLocale.preferredLanguages.firstOrNull() as? String
    val identifier = preferredLanguage ?: NSLocale.currentLocale().localeIdentifier
    return AppLocale(identifier.replace('_', '-'))
}
