package xyz.lilsus.papp.presentation.main.contacts

import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.presentation.common.ContactEditorError

data class ContactsUiState(
    val isOpen: Boolean = false,
    val selectedTab: PaySheetTab = PaySheetTab.Shortcuts,
    val selectedRoles: Set<ContactRole> = emptySet(),
    val shortcuts: List<ShortcutListItem> = emptyList(),
    val hasContacts: Boolean = false,
    val contactCount: Int = 0,
    val contacts: List<ContactListItem> = emptyList(),
    val editor: ContactEditorUiState? = null,
    val savePrompt: ContactSavePromptUiState? = null
)

enum class PaySheetTab {
    Shortcuts,
    Contacts
}

data class ShortcutListItem(
    val id: String,
    val title: String,
    val amountLabel: String,
    val recipientSummary: String,
    val commentSummary: String?,
    val paymentCount: Int,
    val lastPaidAtMs: Long?
)

data class ContactListItem(
    val id: String,
    val displayName: String,
    val address: String,
    val roles: Set<ContactRole>,
    val paymentCount: Int,
    val lastPaidAtMs: Long?
)

data class ContactEditorUiState(
    val contactId: String?,
    val address: String,
    val alias: String,
    val selectedRoles: Set<ContactRole>,
    val addressEditable: Boolean,
    val error: ContactEditorError? = null
)

data class ContactSavePromptUiState(
    val address: String,
    val alias: String,
    val selectedRoles: Set<ContactRole>,
    val error: ContactEditorError? = null
)
