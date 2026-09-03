package xyz.lilsus.raylsuite.feature.settings

/** The legal and source URLs an app supplies to whichever platform renders its settings. */
data class SettingsLegalLinks(
    val privacyPolicyUrl: String? = null,
    val termsUrl: String? = null,
    val sourceCodeUrl: String
)
