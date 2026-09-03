package xyz.lilsus.lasr.feature.onboarding

import org.jetbrains.compose.resources.getString
import xyz.lilsus.lasr.feature.onboarding.generated.resources.Res
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_add_wallet_intro
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_add_wallet_step1
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_add_wallet_step2
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_add_wallet_step3
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_add_wallet_title
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_agreement_body
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_autopay_body
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_features_page1_body
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_features_page1_subtitle
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_features_page1_title
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_features_page2_body
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_features_page2_subtitle
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_features_page2_title
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_features_page3_body
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_features_page3_subtitle
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_features_page3_title
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_welcome_subtitle_line1
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_welcome_subtitle_line2
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_welcome_title

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

suspend fun nativeLasrOnboardingText(): NativeLasrOnboardingText = NativeLasrOnboardingText(
    welcomeTitle = getString(Res.string.onboarding_welcome_title),
    welcomeSubtitle = getString(Res.string.onboarding_welcome_subtitle_line1),
    welcomeDescription = getString(Res.string.onboarding_welcome_subtitle_line2),
    featurePages =
        listOf(
            NativeLasrOnboardingPage(
                title = getString(Res.string.onboarding_features_page1_title),
                subtitle = getString(Res.string.onboarding_features_page1_subtitle),
                body = getString(Res.string.onboarding_features_page1_body)
            ),
            NativeLasrOnboardingPage(
                title = getString(Res.string.onboarding_features_page2_title),
                subtitle = getString(Res.string.onboarding_features_page2_subtitle),
                body = getString(Res.string.onboarding_features_page2_body)
            ),
            NativeLasrOnboardingPage(
                title = getString(Res.string.onboarding_features_page3_title),
                subtitle = getString(Res.string.onboarding_features_page3_subtitle),
                body = getString(Res.string.onboarding_features_page3_body)
            )
        ),
    autoPayBody = getString(Res.string.onboarding_autopay_body),
    agreementBody = getString(Res.string.onboarding_agreement_body),
    instructionsTitle = getString(Res.string.onboarding_add_wallet_title),
    instructionsIntro = getString(Res.string.onboarding_add_wallet_intro),
    instructionSteps =
        listOf(
            getString(Res.string.onboarding_add_wallet_step1),
            getString(Res.string.onboarding_add_wallet_step2),
            getString(Res.string.onboarding_add_wallet_step3)
        )
)
