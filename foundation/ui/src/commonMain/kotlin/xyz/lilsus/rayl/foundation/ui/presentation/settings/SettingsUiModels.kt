package xyz.lilsus.rayl.foundation.ui.presentation.settings

import xyz.lilsus.rayl.foundation.ui.domain.model.ContactRole
import xyz.lilsus.rayl.foundation.ui.domain.model.CurrencyCatalog
import xyz.lilsus.rayl.foundation.ui.domain.model.DisplayAmount
import xyz.lilsus.rayl.foundation.ui.domain.model.PaymentConfirmationMode
import xyz.lilsus.rayl.foundation.ui.domain.model.PaymentPreferences
import xyz.lilsus.rayl.foundation.ui.domain.model.ThemePreference
import xyz.lilsus.rayl.foundation.ui.presentation.common.ContactEditorError
import xyz.lilsus.rayl.foundation.ui.presentation.common.ShortcutEditorError

data class ContactsSettingsUiState(
    val contacts: List<ContactSettingsItem> = emptyList(),
    val contactEditor: ContactSettingsEditor? = null,
    val query: String = "",
    val hasBlinkWallet: Boolean = false
)

data class ContactSettingsItem(
    val id: String,
    val displayName: String,
    val address: String,
    val roles: Set<ContactRole>
)

data class ContactSettingsEditor(
    val contactId: String?,
    val address: String,
    val alias: String,
    val roles: Set<ContactRole>,
    val addressEditable: Boolean,
    val shortcuts: List<ContactShortcutItem> = emptyList(),
    val error: ContactEditorError? = null
)

data class ContactShortcutItem(
    val id: String,
    val title: String,
    val amountText: String,
    val comment: String?
)

sealed interface ContactsSettingsEvent {
    data object OpenBlinkContactsImport : ContactsSettingsEvent

    data class CreateShortcutForContact(val contactId: String) : ContactsSettingsEvent

    data object CloseContactEditor : ContactsSettingsEvent
}

data class CurrencySettingsUiState(
    val selectedPrimaryCode: String = CurrencyCatalog.DEFAULT_CODE,
    val selectedSecondaryCode: String = CurrencyCatalog.DEFAULT_SECONDARY_CODE,
    val activePreference: CurrencyPreference = CurrencyPreference.Primary,
    val searchQuery: String = "",
    val options: List<CurrencyOption> = emptyList()
) {
    val selectedCode: String
        get() = when (activePreference) {
            CurrencyPreference.Primary -> selectedPrimaryCode
            CurrencyPreference.Secondary -> selectedSecondaryCode
        }
}

enum class CurrencyPreference {
    Primary,
    Secondary
}

data class CurrencyOption(val code: String, val label: String)

data class LanguageSettingsUiState(
    val searchQuery: String = "",
    val selectedCode: String = "",
    val deviceCode: String = "",
    val options: List<LanguageOption> = emptyList()
)

data class LanguageOption(val id: String, val title: String, val tag: String?)

data class ThemeSettingsUiState(val selected: ThemePreference = ThemePreference.System)

data class PaymentsSettingsUiState(
    val confirmationMode: PaymentConfirmationMode = PaymentPreferences().confirmationMode,
    val thresholdSats: Long = PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS,
    val confirmManualEntry: Boolean = PaymentPreferences().confirmManualEntry,
    val confirmShortcutPayments: Boolean = PaymentPreferences().confirmShortcutPayments,
    val vibrateOnScan: Boolean = PaymentPreferences().vibrateOnScan,
    val vibrateOnPayment: Boolean = PaymentPreferences().vibrateOnPayment,
    val askToSaveNewContacts: Boolean = true,
    val thresholdSecondaryEquivalent: DisplayAmount? = null,
    val shortcuts: List<ShortcutSettingsItem> = emptyList(),
    val shortcutEditor: ShortcutSettingsEditor? = null
)

data class ShortcutSettingsItem(
    val id: String,
    val title: String,
    val amountText: String,
    val contactName: String,
    val comment: String?
)

data class ShortcutSettingsEditor(
    val shortcutId: String?,
    val title: String,
    val selectedContactId: String?,
    val selectedContact: ShortcutContactOption? = null,
    val amount: String,
    val currencyCode: String,
    val comment: String,
    val error: ShortcutEditorError? = null
)

data class ShortcutContactOption(val id: String, val displayName: String, val address: String)

sealed interface PaymentsSettingsEvent {
    data object CloseShortcutEditor : PaymentsSettingsEvent
}

data class ShortcutContactPickerUiState(
    val query: String = "",
    val options: List<ShortcutContactOption> = emptyList()
)
