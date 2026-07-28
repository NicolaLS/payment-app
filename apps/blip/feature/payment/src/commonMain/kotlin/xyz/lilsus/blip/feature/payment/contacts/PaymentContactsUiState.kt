package xyz.lilsus.blip.feature.payment.contacts

import xyz.lilsus.raylsuite.core.model.ContactRole
import xyz.lilsus.raylsuite.feature.contacts.ContactEditorError

data class PaymentContactsUiState(
    val isOpen: Boolean = false,
    val selectedTab: PaymentSheetTab = PaymentSheetTab.Shortcuts,
    val selectedRoles: Set<ContactRole> = emptySet(),
    val shortcuts: List<PaymentShortcutListItem> = emptyList(),
    val hasContacts: Boolean = false,
    val contactCount: Int = 0,
    val contacts: List<PaymentContactListItem> = emptyList(),
    val savePrompt: ContactSavePromptUiState? = null
)

enum class PaymentSheetTab {
    Shortcuts,
    Contacts
}

data class PaymentShortcutListItem(
    val id: String,
    val title: String,
    val amountLabel: String,
    val recipientSummary: String,
    val commentSummary: String?,
    val paymentCount: Int,
    val lastPaidAtMs: Long?
)

data class PaymentContactListItem(
    val id: String,
    val displayName: String,
    val address: String,
    val roles: Set<ContactRole>,
    val paymentCount: Int,
    val lastPaidAtMs: Long?
)

data class ContactSavePromptUiState(
    val address: String,
    val alias: String,
    val selectedRoles: Set<ContactRole>,
    val error: ContactEditorError? = null
)
