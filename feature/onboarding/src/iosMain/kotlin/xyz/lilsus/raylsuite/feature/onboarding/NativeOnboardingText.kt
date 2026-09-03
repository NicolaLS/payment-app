package xyz.lilsus.raylsuite.feature.onboarding

import org.jetbrains.compose.resources.getString
import xyz.lilsus.raylsuite.core.ui.nativeBackActionText
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.Res
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_agreement_checkbox
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_agreement_continue
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_agreement_title
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_autopay_always
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_autopay_continue
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_autopay_hint
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_autopay_threshold
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_autopay_threshold_label
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_autopay_title
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_features_continue
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_welcome_get_started

data class NativeOnboardingText(
    val back: String,
    val getStarted: String,
    val featuresContinue: String,
    val autoPayTitle: String,
    val autoPayAlways: String,
    val autoPayThreshold: String,
    val autoPayHint: String,
    val autoPayContinue: String,
    val agreementTitle: String,
    val agreementCheckbox: String,
    val agreementContinue: String
)

suspend fun nativeOnboardingText(): NativeOnboardingText = NativeOnboardingText(
    back = nativeBackActionText(),
    getStarted = getString(Res.string.onboarding_welcome_get_started),
    featuresContinue = getString(Res.string.onboarding_features_continue),
    autoPayTitle = getString(Res.string.onboarding_autopay_title),
    autoPayAlways = getString(Res.string.onboarding_autopay_always),
    autoPayThreshold = getString(Res.string.onboarding_autopay_threshold),
    autoPayHint = getString(Res.string.onboarding_autopay_hint),
    autoPayContinue = getString(Res.string.onboarding_autopay_continue),
    agreementTitle = getString(Res.string.onboarding_agreement_title),
    agreementCheckbox = getString(Res.string.onboarding_agreement_checkbox),
    agreementContinue = getString(Res.string.onboarding_agreement_continue)
)

suspend fun nativeOnboardingThresholdLabel(amount: String): String =
    getString(Res.string.onboarding_autopay_threshold_label, amount)
