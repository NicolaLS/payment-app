package xyz.lilsus.blip.feature.onboarding

import org.jetbrains.compose.resources.getString
import xyz.lilsus.blip.feature.onboarding.generated.resources.Res
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_dashboard_button
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_enter_key_button
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_intro
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_next_step
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_previous_step
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step1_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step1_title
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step2_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step2_title
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step3_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step3_title
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step4_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step4_title
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step_progress
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_title
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_agreement_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_autopay_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page1_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page1_subtitle
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page1_title
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page2_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page2_subtitle
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page2_title
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page3_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page3_subtitle
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page3_title
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_welcome_subtitle_line1
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_welcome_subtitle_line2
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_welcome_title

data class NativeBlipOnboardingPage(
    val title: String,
    val subtitle: String,
    val body: String,
    val imageName: String?
)

data class NativeBlipOnboardingText(
    val welcomeTitle: String,
    val welcomeSubtitle: String,
    val welcomeDescription: String,
    val features: List<NativeBlipOnboardingPage>,
    val autoPayBody: String,
    val agreementBody: String,
    val instructionsTitle: String,
    val instructionsIntro: String,
    val instructions: List<NativeBlipOnboardingPage>,
    val previousStep: String,
    val nextStep: String,
    val dashboardButton: String,
    val enterKeyButton: String
)

suspend fun nativeBlipOnboardingText(): NativeBlipOnboardingText = NativeBlipOnboardingText(
    welcomeTitle = getString(Res.string.onboarding_welcome_title),
    welcomeSubtitle = getString(Res.string.onboarding_welcome_subtitle_line1),
    welcomeDescription = getString(Res.string.onboarding_welcome_subtitle_line2),
    features =
        listOf(
            NativeBlipOnboardingPage(
                title = getString(Res.string.onboarding_features_page1_title),
                subtitle = getString(Res.string.onboarding_features_page1_subtitle),
                body = getString(Res.string.onboarding_features_page1_body),
                imageName = null
            ),
            NativeBlipOnboardingPage(
                title = getString(Res.string.onboarding_features_page2_title),
                subtitle = getString(Res.string.onboarding_features_page2_subtitle),
                body = getString(Res.string.onboarding_features_page2_body),
                imageName = null
            ),
            NativeBlipOnboardingPage(
                title = getString(Res.string.onboarding_features_page3_title),
                subtitle = getString(Res.string.onboarding_features_page3_subtitle),
                body = getString(Res.string.onboarding_features_page3_body),
                imageName = null
            )
        ),
    autoPayBody = getString(Res.string.onboarding_autopay_body),
    agreementBody = getString(Res.string.onboarding_agreement_body),
    instructionsTitle = getString(Res.string.onboarding_add_wallet_title),
    instructionsIntro = getString(Res.string.onboarding_add_wallet_intro),
    instructions =
        listOf(
            instruction(
                title = getString(Res.string.onboarding_add_wallet_step1_title),
                body = getString(Res.string.onboarding_add_wallet_step1_body),
                imageName = "blink_dashboard_email"
            ),
            instruction(
                title = getString(Res.string.onboarding_add_wallet_step2_title),
                body = getString(Res.string.onboarding_add_wallet_step2_body),
                imageName = "blink_dashboard_api_keys"
            ),
            instruction(
                title = getString(Res.string.onboarding_add_wallet_step3_title),
                body = getString(Res.string.onboarding_add_wallet_step3_body),
                imageName = "blink_dashboard_key_settings"
            ),
            instruction(
                title = getString(Res.string.onboarding_add_wallet_step4_title),
                body = getString(Res.string.onboarding_add_wallet_step4_body),
                imageName = "blink_dashboard_copy_key"
            )
        ),
    previousStep = getString(Res.string.onboarding_add_wallet_previous_step),
    nextStep = getString(Res.string.onboarding_add_wallet_next_step),
    dashboardButton = getString(Res.string.onboarding_add_wallet_dashboard_button),
    enterKeyButton = getString(Res.string.onboarding_add_wallet_enter_key_button)
)

suspend fun nativeBlipInstructionProgress(step: Int, total: Int): String =
    getString(Res.string.onboarding_add_wallet_step_progress, step, total)

private fun instruction(title: String, body: String, imageName: String) = NativeBlipOnboardingPage(
    title = title,
    subtitle = "",
    body = body,
    imageName = imageName
)
