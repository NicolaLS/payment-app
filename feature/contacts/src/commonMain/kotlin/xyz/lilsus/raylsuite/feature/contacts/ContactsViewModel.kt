package xyz.lilsus.raylsuite.feature.contacts

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.core.model.Contact
import xyz.lilsus.raylsuite.core.model.ContactRole
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.PaymentShortcut
import xyz.lilsus.raylsuite.core.model.ShortcutAmount

class ContactsViewModel(
    private val repository: ContactsRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var contacts: List<Contact> = emptyList()
    private var shortcuts: List<PaymentShortcut> = emptyList()
    private var pendingEditorContactId: String? = null

    private val mutableUiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<ContactsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ContactsEvent> = mutableEvents.asSharedFlow()

    init {
        scope.launch {
            repository.contacts.collectLatest { updatedContacts ->
                contacts = updatedContacts
                pendingEditorContactId?.let { contactId ->
                    if (openEditor(contactId)) {
                        pendingEditorContactId = null
                    }
                }
                refresh()
            }
        }
        scope.launch {
            repository.shortcuts.collectLatest { updatedShortcuts ->
                shortcuts = updatedShortcuts
                refresh()
            }
        }
    }

    fun updateSearch(query: String) {
        mutableUiState.value = mutableUiState.value.copy(query = query)
        refresh()
    }

    fun startAddContact() {
        mutableUiState.value =
            mutableUiState.value.copy(
                editor =
                    ContactEditorState(
                        contactId = null,
                        address = "",
                        alias = "",
                        roles = emptySet(),
                        addressEditable = true
                    )
            )
    }

    fun startEditContact(id: String) {
        if (openEditor(id)) {
            pendingEditorContactId = null
        } else {
            pendingEditorContactId = id
        }
    }

    fun updateEditorAddress(address: String) {
        updateEditor { editor ->
            editor.copy(
                address = address,
                error = null
            )
        }
    }

    fun updateEditorAlias(alias: String) {
        updateEditor { editor ->
            editor.copy(
                alias = alias,
                error = null
            )
        }?.saveExistingContact()
    }

    fun toggleEditorRole(role: ContactRole?) {
        updateEditor { editor ->
            editor.copy(roles = editor.roles.toggle(role))
        }?.saveExistingContact()
    }

    fun saveNewContact() {
        val editor = mutableUiState.value.editor ?: return
        if (editor.contactId != null) return

        val address = LightningAddress.parse(editor.address)
        if (address == null) {
            updateEditor { it.copy(error = ContactEditorError.InvalidAddress) }
            return
        }

        scope.launch {
            repository.saveContact(
                address = address,
                alias = editor.alias,
                roles = editor.roles
            )
            closeEditor()
        }
    }

    fun deleteEditedContact() {
        val contactId = mutableUiState.value.editor?.contactId ?: return
        scope.launch {
            repository.deleteContact(contactId)
            closeEditor()
        }
    }

    fun createShortcutForEditedContact() {
        val contactId = mutableUiState.value.editor?.contactId ?: return
        mutableEvents.tryEmit(ContactsEvent.CreateShortcut(contactId))
    }

    fun dismissEditor() {
        closeEditor()
    }

    fun clear() {
        scope.cancel()
    }

    private fun openEditor(contactId: String): Boolean {
        val contact = contacts.firstOrNull { it.id == contactId } ?: return false
        mutableUiState.value =
            mutableUiState.value.copy(
                editor =
                    ContactEditorState(
                        contactId = contact.id,
                        address = contact.address.full,
                        alias = contact.alias.orEmpty(),
                        roles = contact.roles,
                        addressEditable = false,
                        shortcuts = shortcuts.forContact(contact.id)
                    )
            )
        return true
    }

    private fun updateEditor(
        transform: (ContactEditorState) -> ContactEditorState
    ): ContactEditorState? {
        val editor = mutableUiState.value.editor ?: return null
        val updated = transform(editor)
        mutableUiState.value = mutableUiState.value.copy(editor = updated)
        return updated
    }

    private fun ContactEditorState.saveExistingContact() {
        val id = contactId ?: return
        scope.launch {
            repository.updateContact(
                id = id,
                alias = alias,
                roles = roles
            )
        }
    }

    private fun closeEditor() {
        mutableUiState.value = mutableUiState.value.copy(editor = null)
        mutableEvents.tryEmit(ContactsEvent.CloseEditor)
    }

    private fun refresh() {
        val query = mutableUiState.value.query.trim().lowercase()
        val filteredContacts =
            contacts
                .asSequence()
                .filter { contact ->
                    query.isBlank() ||
                        contact.displayName.lowercase().contains(query) ||
                        contact.address.full.lowercase().contains(query) ||
                        contact.roles.any { role -> role.name.lowercase().contains(query) }
                }.sortedWith(CONTACT_ORDER)
                .map(Contact::toListEntry)
                .toList()
        val refreshedEditor =
            mutableUiState.value.editor?.let { editor ->
                editor.copy(
                    shortcuts =
                        editor.contactId
                            ?.let(shortcuts::forContact)
                            .orEmpty()
                )
            }
        mutableUiState.value =
            mutableUiState.value.copy(
                contacts = filteredContacts,
                editor = refreshedEditor
            )
    }
}

data class ContactsUiState(
    val contacts: List<ContactListEntry> = emptyList(),
    val editor: ContactEditorState? = null,
    val query: String = ""
)

data class ContactEditorState(
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
    val amount: ShortcutAmount,
    val comment: String?
)

enum class ContactEditorError {
    InvalidAddress
}

sealed interface ContactsEvent {
    data class CreateShortcut(val contactId: String) : ContactsEvent

    data object CloseEditor : ContactsEvent
}

private val CONTACT_ORDER =
    compareByDescending<Contact> { ContactRole.Favorite in it.roles }
        .thenByDescending { it.stats.paymentCount }
        .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
        .thenBy { it.displayName.lowercase() }

private val SHORTCUT_ORDER =
    compareByDescending<PaymentShortcut> { it.stats.paymentCount }
        .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
        .thenBy { it.title.lowercase() }

private fun Contact.toListEntry(): ContactListEntry = ContactListEntry(
    id = id,
    displayName = displayName,
    address = address.full,
    roles = roles
)

private fun List<PaymentShortcut>.forContact(contactId: String): List<ContactShortcutItem> =
    asSequence()
        .filter { shortcut -> shortcut.contactId == contactId }
        .sortedWith(SHORTCUT_ORDER)
        .map { shortcut ->
            ContactShortcutItem(
                id = shortcut.id,
                title = shortcut.title,
                amount = shortcut.amount,
                comment = shortcut.comment
            )
        }.toList()

private fun Set<ContactRole>.toggle(role: ContactRole?): Set<ContactRole> = when {
    role == null -> emptySet()
    role in this -> this - role
    else -> this + role
}
