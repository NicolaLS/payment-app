package xyz.lilsus.papp

/**
 * Shared contract between composables and Maestro flows.
 *
 * Keep Maestro selectors centralized here so UI changes and test flow updates
 * stay traceable in one place.
 */
object MaestroTags {
    object Payment {
        const val SCREEN = "payment_screen"
    }

    object Onboarding {
        const val WELCOME_SCREEN = "onboarding_welcome_screen"
        const val WELCOME_CONTINUE_BUTTON = "onboarding_welcome_continue_button"

        const val FEATURES_SCREEN = "onboarding_features_screen"
        const val FEATURES_CONTINUE_BUTTON = "onboarding_features_continue_button"

        const val AUTO_PAY_SCREEN = "onboarding_autopay_screen"
        const val AUTO_PAY_CONTINUE_BUTTON = "onboarding_autopay_continue_button"

        const val WALLET_CHOICE_SCREEN = "onboarding_wallet_choice_screen"
        const val WALLET_CHOICE_BLINK_OPTION = "onboarding_wallet_choice_blink_option"
        const val WALLET_CHOICE_NWC_OPTION = "onboarding_wallet_choice_nwc_option"
        const val WALLET_CHOICE_NO_WALLET_BUTTON = "onboarding_wallet_choice_no_wallet_button"

        const val AGREEMENT_SCREEN = "onboarding_agreement_screen"
        const val AGREEMENT_CHECKBOX = "onboarding_agreement_checkbox"
        const val AGREEMENT_CONTINUE_BUTTON = "onboarding_agreement_continue_button"

        const val ADD_BLINK_WALLET_SCREEN = "onboarding_add_blink_wallet_screen"
        const val ADD_BLINK_WALLET_CONTINUE_BUTTON = "onboarding_add_blink_wallet_continue_button"
        const val ADD_NWC_WALLET_SCREEN = "onboarding_add_nwc_wallet_screen"
        const val ADD_NWC_WALLET_CONTINUE_BUTTON = "onboarding_add_nwc_wallet_continue_button"

        const val NO_WALLET_HELP_SCREEN = "onboarding_no_wallet_help_screen"
        const val NO_WALLET_HAS_WALLET_BUTTON = "onboarding_no_wallet_has_wallet_button"
        const val NO_WALLET_START_AGAIN_BUTTON = "onboarding_no_wallet_start_again_button"
    }

    object BlinkWallet {
        const val SCREEN = "blink_wallet_screen"
        const val ALIAS_FIELD = "blink_wallet_alias_field"
        const val API_KEY_FIELD = "blink_wallet_api_key_field"
        const val CONNECT_BUTTON = "blink_wallet_connect_button"
    }

    object NwcWallet {
        const val SCREEN = "nwc_wallet_screen"
        const val URI_FIELD = "nwc_wallet_uri_field"
        const val CAMERA_CARD = "nwc_wallet_camera_card"
        const val CAMERA_PREVIEW = "nwc_wallet_camera_preview"

        const val CONFIRM_DIALOG = "nwc_wallet_confirm_dialog"
        const val DIALOG_LOADING = "nwc_wallet_dialog_loading"
        const val DIALOG_DETAILS = "nwc_wallet_dialog_details"
        const val DIALOG_WARNING = "nwc_wallet_dialog_warning"
        const val DIALOG_ALIAS_FIELD = "nwc_wallet_dialog_alias_field"
        const val DIALOG_SET_ACTIVE_CHECKBOX = "nwc_wallet_dialog_set_active_checkbox"
        const val DIALOG_RETRY_BUTTON = "nwc_wallet_dialog_retry_button"
        const val DIALOG_CONFIRM_BUTTON = "nwc_wallet_dialog_confirm_button"
        const val DIALOG_CANCEL_BUTTON = "nwc_wallet_dialog_cancel_button"
    }
}
