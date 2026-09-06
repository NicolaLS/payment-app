package xyz.lilsus.lasr

import xyz.lilsus.raylsuite.feature.settings.SettingsLegalLinks

internal const val LASR_PREFERENCES = "lasr_preferences"
internal const val LASR_CREDENTIALS = "lasr_wallet"

internal val LASR_LEGAL_LINKS =
    SettingsLegalLinks(
        privacyPolicyUrl =
            "https://github.com/NicolaLS/rayl-suite/blob/main/docs/legal/lasr/privacy.md",
        termsUrl =
            "https://github.com/NicolaLS/rayl-suite/blob/main/docs/legal/lasr/terms.md",
        sourceCodeUrl = "https://github.com/NicolaLS/rayl-suite"
    )

internal val LASR_EXPERIENCE = NwcExperienceConfiguration(
    appName = "Lasr",
    preferencesName = LASR_PREFERENCES,
    walletPreferencesName = "lasr_connection",
    credentialsName = LASR_CREDENTIALS,
    legalLinks = LASR_LEGAL_LINKS
)
