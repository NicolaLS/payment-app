package xyz.lilsus.raylsuite.feature.paymentsettings

import org.jetbrains.compose.resources.getString
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_confirm_label
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_confirm_manual_entry
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_confirm_presets
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_haptics_payment
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_haptics_scan
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_haptics_title
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_hub_title
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_lnurl_review
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_lnurl_review_description
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_offer_save_targets
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_option_above
import xyz.lilsus.raylsuite.feature.paymentsettings.generated.resources.settings_payments_option_always

suspend fun nativePaymentSettingsStrings(): Map<String, String> = mapOf(
    "confirmTitle" to getString(Res.string.settings_payments_confirm_label),
    "always" to getString(Res.string.settings_payments_option_always),
    "above" to getString(Res.string.settings_payments_option_above),
    "confirmManual" to getString(Res.string.settings_payments_confirm_manual_entry),
    "lnurlTitle" to getString(Res.string.settings_payments_lnurl_review),
    "lnurlDescription" to getString(Res.string.settings_payments_lnurl_review_description),
    "hapticsTitle" to getString(Res.string.settings_payments_haptics_title),
    "hapticsScan" to getString(Res.string.settings_payments_haptics_scan),
    "hapticsPayment" to getString(Res.string.settings_payments_haptics_payment),
    "hubTitle" to getString(Res.string.settings_payments_hub_title),
    "confirmPresets" to getString(Res.string.settings_payments_confirm_presets),
    "offerSaveTargets" to getString(Res.string.settings_payments_offer_save_targets)
)
