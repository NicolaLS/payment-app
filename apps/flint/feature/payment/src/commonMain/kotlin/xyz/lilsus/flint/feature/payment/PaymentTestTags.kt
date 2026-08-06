package xyz.lilsus.flint.feature.payment

object PaymentTestTags {
    const val SCREEN = "payment_screen"
    const val ACTIVE_CONTENT = "payment_active_content"
    const val SETTINGS_BUTTON = "payment_settings_button"
    const val SESSION_TRANSACTIONS_BUTTON = "payment_session_transactions_button"
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

    const val RESULT = "payment_result"
    const val RESULT_SUCCESS = "payment_result_success"
    const val RESULT_ALREADY_PAID = "payment_result_already_paid"
    const val RESULT_ERROR = "payment_result_error"
    const val RESULT_VIEW_RECEIPT = "payment_result_view_receipt"

    fun manualAmountDigitKey(value: Int): String = "payment_manual_amount_key_$value"

    fun contactRow(label: String): String = "payment_contact_row_${label.stableTagToken()}"
}

private fun String.stableTagToken(): String = lowercase()
    .map { character -> if (character.isLetterOrDigit()) character else '-' }
    .joinToString(separator = "")
    .trim('-')
    .ifBlank { "contact" }
