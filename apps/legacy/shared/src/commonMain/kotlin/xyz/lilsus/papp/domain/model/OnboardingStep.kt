package xyz.lilsus.papp.domain.model

/**
 * Represents the steps in the onboarding flow.
 * Used by OnboardingScaffold for the progress indicator.
 */
enum class OnboardingStep {
    Welcome,
    Features,
    AutoPaySettings,
    WalletTypeChoice,
    NoWalletHelp,
    Agreement,
    AddWallet
}
