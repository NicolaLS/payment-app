package xyz.lilsus.blip

import xyz.lilsus.raylsuite.feature.settings.SettingsLegalLinks

internal const val BLIP_PREFERENCES = "blip_preferences"
internal const val BLIP_CREDENTIALS = "blip_wallet"

internal val BLIP_LEGAL_LINKS =
    SettingsLegalLinks(
        privacyPolicyUrl =
            "https://github.com/NicolaLS/rayl-suite/blob/main/docs/legal/blip/privacy.md",
        termsUrl =
            "https://github.com/NicolaLS/rayl-suite/blob/main/docs/legal/blip/terms.md",
        sourceCodeUrl = "https://github.com/NicolaLS/rayl-suite"
    )
