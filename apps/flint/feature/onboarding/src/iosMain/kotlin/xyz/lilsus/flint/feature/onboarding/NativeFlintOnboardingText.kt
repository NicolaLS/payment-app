package xyz.lilsus.flint.feature.onboarding

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

data class NativeFlintOnboardingPage(val title: String, val subtitle: String, val body: String)

data class NativeFlintOnboardingText(
    val welcomeTitle: String,
    val welcomeSubtitle: String,
    val welcomeDescription: String,
    val featurePages: List<NativeFlintOnboardingPage>,
    val autoPayBody: String,
    val agreementBody: String,
    val instructionsTitle: String,
    val instructionsIntro: String,
    val instructionSteps: List<String>
)

suspend fun nativeFlintOnboardingText(): NativeFlintOnboardingText = NativeFlintOnboardingText(
    welcomeTitle = nativeString(
        NativeStringResource(table = "FlintOnboarding", key = "onboarding_welcome_title")
    ),
    welcomeSubtitle = nativeString(
        NativeStringResource(table = "FlintOnboarding", key = "onboarding_welcome_subtitle_line1")
    ),
    welcomeDescription = nativeString(
        NativeStringResource(table = "FlintOnboarding", key = "onboarding_welcome_subtitle_line2")
    ),
    featurePages =
        listOf(
            NativeFlintOnboardingPage(
                title = nativeString(
                    NativeStringResource(
                        table = "FlintOnboarding",
                        key = "onboarding_features_page1_title"
                    )
                ),
                subtitle = nativeString(
                    NativeStringResource(
                        table = "FlintOnboarding",
                        key = "onboarding_features_page1_subtitle"
                    )
                ),
                body = nativeString(
                    NativeStringResource(
                        table = "FlintOnboarding",
                        key = "onboarding_features_page1_body"
                    )
                )
            ),
            NativeFlintOnboardingPage(
                title = nativeString(
                    NativeStringResource(
                        table = "FlintOnboarding",
                        key = "onboarding_features_page2_title"
                    )
                ),
                subtitle = nativeString(
                    NativeStringResource(
                        table = "FlintOnboarding",
                        key = "onboarding_features_page2_subtitle"
                    )
                ),
                body = nativeString(
                    NativeStringResource(
                        table = "FlintOnboarding",
                        key = "onboarding_features_page2_body"
                    )
                )
            ),
            NativeFlintOnboardingPage(
                title = nativeString(
                    NativeStringResource(
                        table = "FlintOnboarding",
                        key = "onboarding_features_page3_title"
                    )
                ),
                subtitle = nativeString(
                    NativeStringResource(
                        table = "FlintOnboarding",
                        key = "onboarding_features_page3_subtitle"
                    )
                ),
                body = nativeString(
                    NativeStringResource(
                        table = "FlintOnboarding",
                        key = "onboarding_features_page3_body"
                    )
                )
            )
        ),
    autoPayBody = nativeString(
        NativeStringResource(table = "FlintOnboarding", key = "onboarding_autopay_body")
    ),
    agreementBody = nativeString(
        NativeStringResource(table = "FlintOnboarding", key = "onboarding_agreement_body")
    ),
    instructionsTitle = nativeString(
        NativeStringResource(table = "FlintOnboarding", key = "onboarding_add_wallet_title")
    ),
    instructionsIntro = nativeString(
        NativeStringResource(table = "FlintOnboarding", key = "onboarding_add_wallet_intro")
    ),
    instructionSteps =
        listOf(
            nativeString(
                NativeStringResource(table = "FlintOnboarding", key = "onboarding_add_wallet_step1")
            ),
            nativeString(
                NativeStringResource(table = "FlintOnboarding", key = "onboarding_add_wallet_step2")
            ),
            nativeString(
                NativeStringResource(table = "FlintOnboarding", key = "onboarding_add_wallet_step3")
            )
        )
)
