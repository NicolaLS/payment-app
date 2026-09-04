package xyz.lilsus.raylsuite.feature.paymentsettings

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

suspend fun nativePaymentSettingsStrings(): Map<String, String> = mapOf(
    "confirmTitle" to
        nativeString(
            NativeStringResource(table = "PaymentSettings", key = "settings_payments_confirm_label")
        ),
    "always" to
        nativeString(
            NativeStringResource(table = "PaymentSettings", key = "settings_payments_option_always")
        ),
    "above" to
        nativeString(
            NativeStringResource(table = "PaymentSettings", key = "settings_payments_option_above")
        ),
    "confirmManual" to
        nativeString(
            NativeStringResource(
                table = "PaymentSettings",
                key = "settings_payments_confirm_manual_entry"
            )
        ),
    "lnurlTitle" to
        nativeString(
            NativeStringResource(table = "PaymentSettings", key = "settings_payments_lnurl_review")
        ),
    "lnurlDescription" to
        nativeString(
            NativeStringResource(
                table = "PaymentSettings",
                key = "settings_payments_lnurl_review_description"
            )
        ),
    "hapticsTitle" to
        nativeString(
            NativeStringResource(table = "PaymentSettings", key = "settings_payments_haptics_title")
        ),
    "hapticsScan" to
        nativeString(
            NativeStringResource(table = "PaymentSettings", key = "settings_payments_haptics_scan")
        ),
    "hapticsPayment" to
        nativeString(
            NativeStringResource(
                table = "PaymentSettings",
                key = "settings_payments_haptics_payment"
            )
        ),
    "hubTitle" to
        nativeString(
            NativeStringResource(table = "PaymentSettings", key = "settings_payments_hub_title")
        ),
    "offerSaveTargets" to
        nativeString(
            NativeStringResource(
                table = "PaymentSettings",
                key = "settings_payments_offer_save_targets"
            )
        )
)
