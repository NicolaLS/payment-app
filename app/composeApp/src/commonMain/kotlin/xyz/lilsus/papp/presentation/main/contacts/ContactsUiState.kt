package xyz.lilsus.papp.presentation.main.contacts

import xyz.lilsus.papp.domain.model.ContactRole

data class ContactsUiState(
    val isOpen: Boolean = false,
    val selectedTab: PaySheetTab = PaySheetTab.Shortcuts,
    val query: String = "",
    val selectedRole: ContactRole? = null,
    val shortcuts: List<ShortcutListItem> = emptyList(),
    val contacts: List<ContactListItem> = emptyList(),
    val addCandidate: String? = null,
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
    val amountSats: Long,
    val recipientSummary: String,
    val commentSummary: String?,
    val paymentCount: Int,
    val lastPaidAtMs: Long?
)

data class ContactListItem(
    val id: String,
    val displayName: String,
    val address: String,
    val role: ContactRole?,
    val paymentCount: Int,
    val lastPaidAtMs: Long?
)

data class ContactEditorUiState(
    val contactId: String?,
    val address: String,
    val alias: String,
    val selectedRole: ContactRole?,
    val addressEditable: Boolean,
    val error: String? = null
)

data class ContactSavePromptUiState(
    val address: String,
    val alias: String,
    val selectedRole: ContactRole?,
    val error: String? = null
)
