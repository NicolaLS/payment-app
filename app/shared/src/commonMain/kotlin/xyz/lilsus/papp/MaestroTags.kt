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
        const val ACTIVE_CONTENT = "payment_active_content"
        const val SETTINGS_BUTTON = "payment_settings_button"
        const val SESSION_TRANSACTIONS_BUTTON = "payment_session_transactions_button"
        const val ACTIVE_WALLET_NAME = "payment_active_wallet_name"
        const val CONTACTS_SHEET = "payment_contacts_sheet"
        const val CONTACTS_SEARCH = "payment_contacts_search"
        const val CONTACTS_ADD_BUTTON = "payment_contacts_add_button"
        const val CONTACTS_SAVE_PROMPT = "payment_contacts_save_prompt"

        const val CONFIRMATION_SHEET = "payment_confirmation_sheet"
        const val CONFIRMATION_PAY_BUTTON = "payment_confirmation_pay_button"
        const val CONFIRMATION_DISMISS_BUTTON = "payment_confirmation_dismiss_button"

        const val MANUAL_AMOUNT_SHEET = "payment_manual_amount_sheet"
        const val MANUAL_AMOUNT_DISPLAY = "payment_manual_amount_display"
        const val MANUAL_AMOUNT_PAY_BUTTON = "payment_manual_amount_pay_button"
        const val MANUAL_AMOUNT_DECIMAL_KEY = "payment_manual_amount_key_decimal"
        const val MANUAL_AMOUNT_BACKSPACE_KEY = "payment_manual_amount_key_backspace"

        const val PENDING_RETRY_SHEET = "payment_pending_retry_sheet"
        const val PENDING_RETRY_SAME_INVOICE_BUTTON =
            "payment_pending_retry_same_invoice_button"
        const val PENDING_RETRY_CREATE_NEW_INVOICE_BUTTON =
            "payment_pending_retry_create_new_invoice_button"
        const val PENDING_RETRY_VIEW_PENDING_BUTTON =
            "payment_pending_retry_view_pending_button"

        const val RESULT = "payment_result"
        const val RESULT_SUCCESS = "payment_result_success"
        const val RESULT_ALREADY_PAID = "payment_result_already_paid"
        const val RESULT_ERROR = "payment_result_error"

        fun manualAmountDigitKey(value: Int): String = "payment_manual_amount_key_$value"

        fun contactRow(label: String): String = "payment_contact_row_${label.stableTagToken()}"
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

    object Settings {
        const val BACK_BUTTON = "settings_back_button"

        const val SCREEN = "settings_screen"
        const val MANAGE_WALLETS_ROW = "settings_manage_wallets_row"
        const val PAYMENTS_ROW = "settings_payments_row"
        const val CONTACTS_ROW = "settings_contacts_row"
        const val CURRENCY_ROW = "settings_currency_row"
        const val LANGUAGE_ROW = "settings_language_row"
        const val THEME_ROW = "settings_theme_row"

        const val PAYMENTS_SCREEN = "settings_payments_screen"
        const val PAYMENTS_CONFIRMATION_MODE_ALWAYS =
            "settings_payments_confirmation_mode_always"
        const val PAYMENTS_CONFIRMATION_MODE_ABOVE =
            "settings_payments_confirmation_mode_above"
        const val PAYMENTS_CONFIRM_MANUAL_ENTRY =
            "settings_payments_confirm_manual_entry"
        const val PAYMENTS_ASK_SAVE_CONTACTS =
            "settings_payments_ask_save_contacts"

        const val CONTACTS_SCREEN = "settings_contacts_screen"

        const val MANAGE_WALLETS_SCREEN = "settings_manage_wallets_screen"
        const val MANAGE_WALLETS_ADD_BUTTON = "settings_manage_wallets_add_button"
        const val MANAGE_WALLETS_EMPTY = "settings_manage_wallets_empty"

        fun walletRow(label: String): String = "settings_wallet_row_${label.stableTagToken()}"
        fun walletActiveBadge(label: String): String =
            "settings_wallet_active_${label.stableTagToken()}"

        fun walletRemoveButton(label: String): String =
            "settings_wallet_remove_${label.stableTagToken()}"

        fun walletSetActiveButton(label: String): String =
            "settings_wallet_set_active_${label.stableTagToken()}"
    }
}

private fun String.stableTagToken(): String = lowercase()
    .map { char -> if (char.isLetterOrDigit()) char else '-' }
    .joinToString(separator = "")
    .trim('-')
    .ifBlank { "wallet" }
