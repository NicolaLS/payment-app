package xyz.lilsus.lasr.feature.onboarding

enum class LasrWalletConnectionOutcome {
    ResumeOnboarding,
    CompleteOnboarding,
    FinishSettings
}

fun lasrWalletConnectionOutcome(
    fromSettings: Boolean,
    hasAgreed: Boolean
): LasrWalletConnectionOutcome = when {
    fromSettings -> LasrWalletConnectionOutcome.FinishSettings
    hasAgreed -> LasrWalletConnectionOutcome.CompleteOnboarding
    else -> LasrWalletConnectionOutcome.ResumeOnboarding
}
