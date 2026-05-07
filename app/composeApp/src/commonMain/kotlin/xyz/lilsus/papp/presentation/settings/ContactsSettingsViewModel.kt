package xyz.lilsus.papp.presentation.settings

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.lnurl.LightningInputParser
import xyz.lilsus.papp.domain.model.Contact
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.domain.model.PaymentShortcut
import xyz.lilsus.papp.domain.usecases.DeleteContactUseCase
import xyz.lilsus.papp.domain.usecases.DeleteShortcutUseCase
import xyz.lilsus.papp.domain.usecases.ObserveContactsUseCase
import xyz.lilsus.papp.domain.usecases.ObserveShortcutsUseCase
import xyz.lilsus.papp.domain.usecases.SaveContactUseCase
import xyz.lilsus.papp.domain.usecases.SaveShortcutUseCase
import xyz.lilsus.papp.domain.usecases.UpdateContactUseCase

class ContactsSettingsViewModel internal constructor(
    observeContacts: ObserveContactsUseCase,
    observeShortcuts: ObserveShortcutsUseCase,
    private val saveContact: SaveContactUseCase,
    private val updateContact: UpdateContactUseCase,
    private val deleteContactUseCase: DeleteContactUseCase,
    private val saveShortcut: SaveShortcutUseCase,
    private val deleteShortcutUseCase: DeleteShortcutUseCase,
    private val lightningInputParser: LightningInputParser = LightningInputParser(),
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var contacts: List<Contact> = emptyList()
    private var shortcuts: List<PaymentShortcut> = emptyList()

    private val _uiState = MutableStateFlow(ContactsSettingsUiState())
    val uiState: StateFlow<ContactsSettingsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            observeContacts().collectLatest {
                contacts = it
                refresh()
            }
        }
        scope.launch {
            observeShortcuts().collectLatest {
                shortcuts = it
                refresh()
            }
        }
    }

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        refresh()
    }

    fun startAddContact() {
        _uiState.value = _uiState.value.copy(
            contactEditor = ContactSettingsEditor(
                contactId = null,
                address = _uiState.value.query,
                alias = "",
                role = null,
                addressEditable = true
            ),
            shortcutEditor = null
        )
    }

    fun startEditContact(id: String) {
        val contact = contacts.firstOrNull { it.id == id } ?: return
        _uiState.value = _uiState.value.copy(
            contactEditor = ContactSettingsEditor(
                contactId = contact.id,
                address = contact.address.full,
                alias = contact.alias.orEmpty(),
                role = contact.role,
                addressEditable = false
            ),
            shortcutEditor = null
        )
    }

    fun startAddShortcut() {
        val options = shortcutContactOptions()
        _uiState.value = _uiState.value.copy(
            shortcutEditor = ShortcutSettingsEditor(
                shortcutId = null,
                title = "",
                selectedContactId = options.firstOrNull()?.id,
                amountSats = "",
                comment = "",
                contactOptions = options
            ),
            contactEditor = null
        )
    }

    fun startEditShortcut(id: String) {
        val shortcut = shortcuts.firstOrNull { it.id == id } ?: return
        val options = shortcutContactOptions()
        val selectedContactId = shortcut.contactId
            ?: contacts.firstOrNull { it.address.sameAddressAs(shortcut.address) }?.id
            ?: options.firstOrNull()?.id
        _uiState.value = _uiState.value.copy(
            shortcutEditor = ShortcutSettingsEditor(
                shortcutId = shortcut.id,
                title = shortcut.title,
                selectedContactId = selectedContactId,
                amountSats = (shortcut.amountMsats / MSATS_PER_SAT)
                    .takeIf { it > 0L }
                    ?.toString()
                    .orEmpty(),
                comment = shortcut.comment.orEmpty(),
                contactOptions = options
            ),
            contactEditor = null
        )
    }

    fun deleteContact(id: String) {
        scope.launch { deleteContactUseCase(id) }
    }

    fun deleteShortcut(id: String) {
        scope.launch { deleteShortcutUseCase(id) }
    }

    fun updateContactEditorAddress(address: String) {
        updateContactEditor { it.copy(address = address, error = null) }
    }

    fun updateContactEditorAlias(alias: String) {
        updateContactEditor { it.copy(alias = alias, error = null) }
    }

    fun updateContactEditorRole(role: ContactRole?) {
        updateContactEditor { it.copy(role = role) }
    }

    fun updateShortcutTitle(title: String) {
        updateShortcutEditor { it.copy(title = title, error = null) }
    }

    fun updateShortcutContact(contactId: String) {
        updateShortcutEditor { it.copy(selectedContactId = contactId, error = null) }
    }

    fun updateShortcutAmount(amount: String) {
        updateShortcutEditor { it.copy(amountSats = amount.filter(Char::isDigit), error = null) }
    }

    fun updateShortcutComment(comment: String) {
        updateShortcutEditor { it.copy(comment = comment, error = null) }
    }

    fun saveContactEditor() {
        val editor = _uiState.value.contactEditor ?: return
        val address = parseAddress(editor.address)
        if (address == null) {
            updateContactEditor { it.copy(error = "Enter a valid Lightning address.") }
            return
        }
        scope.launch {
            if (editor.contactId == null) {
                saveContact(address, editor.alias, editor.role)
            } else {
                updateContact(editor.contactId, editor.alias, editor.role)
            }
            _uiState.value = _uiState.value.copy(contactEditor = null, query = "")
            refresh()
        }
    }

    fun saveShortcutEditor() {
        val editor = _uiState.value.shortcutEditor ?: return
        val contactId = editor.selectedContactId
        if (editor.contactOptions.isEmpty() || contactId == null) {
            updateShortcutEditor { it.copy(error = "Add a contact before creating a shortcut.") }
            return
        }
        val amountSats = editor.amountSats.toLongOrNull()
        if (amountSats == null || amountSats <= 0L) {
            updateShortcutEditor { it.copy(error = "Enter an amount in sats.") }
            return
        }
        val contact = contacts.firstOrNull { it.id == contactId }
        if (contact == null) {
            updateShortcutEditor { it.copy(error = "Select a contact.") }
            return
        }
        val title = editor.title.ifBlank { "Pay ${contact.displayName}" }
        scope.launch {
            saveShortcut(
                id = editor.shortcutId,
                title = title,
                contactId = contact.id,
                amountMsats = amountSats * MSATS_PER_SAT,
                comment = editor.comment.takeIf { it.isNotBlank() }
            )
            _uiState.value = _uiState.value.copy(shortcutEditor = null, query = "")
            refresh()
        }
    }

    fun dismissEditor() {
        _uiState.value = _uiState.value.copy(contactEditor = null, shortcutEditor = null)
    }

    fun clear() {
        scope.cancel()
    }

    private fun updateContactEditor(transform: (ContactSettingsEditor) -> ContactSettingsEditor) {
        val editor = _uiState.value.contactEditor ?: return
        _uiState.value = _uiState.value.copy(contactEditor = transform(editor))
    }

    private fun updateShortcutEditor(
        transform: (ShortcutSettingsEditor) -> ShortcutSettingsEditor
    ) {
        val editor = _uiState.value.shortcutEditor ?: return
        _uiState.value = _uiState.value.copy(shortcutEditor = transform(editor))
    }

    private fun refresh() {
        refreshOpenShortcutEditor()
        val query = _uiState.value.query.trim()
        val shortcutItems = shortcuts
            .filter { shortcut ->
                query.isBlank() ||
                    shortcut.title.contains(query, ignoreCase = true) ||
                    shortcut.address.full.contains(query, ignoreCase = true) ||
                    shortcut.comment?.contains(query, ignoreCase = true) == true ||
                    shortcut.displayName().contains(query, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<PaymentShortcut> { it.stats.paymentCount }
                    .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
                    .thenBy { it.title.lowercase() }
            )
            .map { it.toSettingsItem() }
        val contactItems = contacts
            .filter { contact ->
                query.isBlank() ||
                    contact.displayName.contains(query, ignoreCase = true) ||
                    contact.address.full.contains(query, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<Contact> { it.stats.paymentCount }
                    .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
                    .thenBy { it.displayName.lowercase() }
            )
            .map { contact ->
                ContactSettingsItem(
                    id = contact.id,
                    displayName = contact.displayName,
                    address = contact.address.full,
                    role = contact.role
                )
            }
        _uiState.value = _uiState.value.copy(
            shortcuts = shortcutItems,
            contacts = contactItems
        )
    }

    private fun refreshOpenShortcutEditor() {
        val editor = _uiState.value.shortcutEditor ?: return
        val options = shortcutContactOptions()
        val selectedContactId = editor.selectedContactId?.takeIf { selected ->
            options.any { it.id == selected }
        } ?: options.firstOrNull()?.id
        _uiState.value = _uiState.value.copy(
            shortcutEditor = editor.copy(
                selectedContactId = selectedContactId,
                contactOptions = options
            )
        )
    }

    private fun shortcutContactOptions(): List<ShortcutContactOption> = contacts
        .sortedWith(
            compareByDescending<Contact> { it.stats.paymentCount }
                .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
                .thenBy { it.displayName.lowercase() }
        )
        .map { contact ->
            ShortcutContactOption(
                id = contact.id,
                displayName = contact.displayName,
                address = contact.address.full
            )
        }

    private fun PaymentShortcut.toSettingsItem(): ShortcutSettingsItem = ShortcutSettingsItem(
        id = id,
        title = title,
        amountSats = amountMsats / MSATS_PER_SAT,
        contactName = displayName(),
        comment = comment
    )

    private fun PaymentShortcut.displayName(): String =
        contactId?.let { id -> contacts.firstOrNull { it.id == id } }?.displayName
            ?: address.username

    private fun parseAddress(raw: String): LightningAddress? =
        when (val result = lightningInputParser.parse(raw.trim())) {
            is LightningInputParser.ParseResult.Success ->
                (result.target as? LightningInputParser.Target.LightningAddressTarget)?.address

            is LightningInputParser.ParseResult.Failure -> null
        }

    private fun LightningAddress.sameAddressAs(other: LightningAddress): Boolean =
        full.equals(other.full, ignoreCase = true)

    companion object {
        private const val MSATS_PER_SAT = 1_000L
    }
}

data class ContactsSettingsUiState(
    val query: String = "",
    val shortcuts: List<ShortcutSettingsItem> = emptyList(),
    val contacts: List<ContactSettingsItem> = emptyList(),
    val contactEditor: ContactSettingsEditor? = null,
    val shortcutEditor: ShortcutSettingsEditor? = null
)

data class ShortcutSettingsItem(
    val id: String,
    val title: String,
    val amountSats: Long,
    val contactName: String,
    val comment: String?
)

data class ContactSettingsItem(
    val id: String,
    val displayName: String,
    val address: String,
    val role: ContactRole?
)

data class ContactSettingsEditor(
    val contactId: String?,
    val address: String,
    val alias: String,
    val role: ContactRole?,
    val addressEditable: Boolean,
    val error: String? = null
)

data class ShortcutSettingsEditor(
    val shortcutId: String?,
    val title: String,
    val selectedContactId: String?,
    val amountSats: String,
    val comment: String,
    val contactOptions: List<ShortcutContactOption>,
    val error: String? = null
)

data class ShortcutContactOption(val id: String, val displayName: String, val address: String)
