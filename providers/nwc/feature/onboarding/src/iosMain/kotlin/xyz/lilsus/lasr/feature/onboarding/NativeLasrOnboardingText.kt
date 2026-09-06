package xyz.lilsus.lasr.feature.onboarding

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

data class NativeLasrOnboardingPage(val title: String, val subtitle: String, val body: String)

data class NativeLasrOnboardingText(
    val welcomeTitle: String,
    val welcomeSubtitle: String,
    val welcomeDescription: String,
    val featurePages: List<NativeLasrOnboardingPage>,
    val autoPayBody: String,
    val agreementBody: String,
    val instructionsTitle: String,
    val instructionsIntro: String,
    val instructionSteps: List<String>
)

suspend fun nativeLasrOnboardingText(appName: String): NativeLasrOnboardingText =
    NativeLasrOnboardingText(
        welcomeTitle = nativeString(
            NativeStringResource(table = "LasrOnboarding", key = "onboarding_welcome_title"),
            appName
        ),
        welcomeSubtitle = nativeString(
            NativeStringResource(
                table = "LasrOnboarding",
                key = "onboarding_welcome_subtitle_line1"
            )
        ),
        welcomeDescription = nativeString(
            NativeStringResource(
                table = "LasrOnboarding",
                key = "onboarding_welcome_subtitle_line2"
            )
        ),
        featurePages =
            listOf(
                NativeLasrOnboardingPage(
                    title = nativeString(
                        NativeStringResource(
                            table = "LasrOnboarding",
                            key = "onboarding_features_page1_title"
                        )
                    ),
                    subtitle = nativeString(
                        NativeStringResource(
                            table = "LasrOnboarding",
                            key = "onboarding_features_page1_subtitle"
                        )
                    ),
                    body = nativeString(
                        NativeStringResource(
                            table = "LasrOnboarding",
                            key = "onboarding_features_page1_body"
                        ),
                        appName
                    )
                ),
                NativeLasrOnboardingPage(
                    title = nativeString(
                        NativeStringResource(
                            table = "LasrOnboarding",
                            key = "onboarding_features_page2_title"
                        )
                    ),
                    subtitle = nativeString(
                        NativeStringResource(
                            table = "LasrOnboarding",
                            key = "onboarding_features_page2_subtitle"
                        )
                    ),
                    body = nativeString(
                        NativeStringResource(
                            table = "LasrOnboarding",
                            key = "onboarding_features_page2_body"
                        ),
                        appName
                    )
                ),
                NativeLasrOnboardingPage(
                    title = nativeString(
                        NativeStringResource(
                            table = "LasrOnboarding",
                            key = "onboarding_features_page3_title"
                        )
                    ),
                    subtitle = nativeString(
                        NativeStringResource(
                            table = "LasrOnboarding",
                            key = "onboarding_features_page3_subtitle"
                        ),
                        appName
                    ),
                    body = nativeString(
                        NativeStringResource(
                            table = "LasrOnboarding",
                            key = "onboarding_features_page3_body"
                        ),
                        appName
                    )
                )
            ),
        autoPayBody = nativeString(
            NativeStringResource(table = "LasrOnboarding", key = "onboarding_autopay_body"),
            appName
        ),
        agreementBody = nativeString(
            NativeStringResource(table = "LasrOnboarding", key = "onboarding_agreement_body"),
            appName
        ),
        instructionsTitle = nativeString(
            NativeStringResource(table = "LasrOnboarding", key = "onboarding_add_wallet_title")
        ),
        instructionsIntro = nativeString(
            NativeStringResource(table = "LasrOnboarding", key = "onboarding_add_wallet_intro"),
            appName
        ),
        instructionSteps =
            listOf(
                nativeString(
                    NativeStringResource(
                        table = "LasrOnboarding",
                        key = "onboarding_add_wallet_step1"
                    ),
                    appName
                ),
                nativeString(
                    NativeStringResource(
                        table = "LasrOnboarding",
                        key = "onboarding_add_wallet_step2"
                    )
                ),
                nativeString(
                    NativeStringResource(
                        table = "LasrOnboarding",
                        key = "onboarding_add_wallet_step3"
                    )
                )
            )
    )
