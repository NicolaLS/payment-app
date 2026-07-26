package xyz.lilsus.papp.presentation.settings

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
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.lnurl.LightningInputParser
import xyz.lilsus.papp.domain.model.Contact
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.model.PaymentShortcut
import xyz.lilsus.papp.domain.model.ShortcutAmount
import xyz.lilsus.papp.domain.usecases.DeleteContactUseCase
import xyz.lilsus.papp.domain.usecases.ObserveContactsUseCase
import xyz.lilsus.papp.domain.usecases.ObserveShortcutsUseCase
import xyz.lilsus.papp.domain.usecases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.usecases.SaveContactUseCase
import xyz.lilsus.papp.domain.usecases.UpdateContactUseCase
import xyz.lilsus.papp.presentation.common.ContactEditorError

class ContactsSettingsViewModel internal constructor(
    observeContacts: ObserveContactsUseCase,
    observeWalletConnection: ObserveWalletConnectionUseCase,
    observeShortcuts: ObserveShortcutsUseCase,
    private val saveContact: SaveContactUseCase,
    private val updateContact: UpdateContactUseCase,
    private val deleteContactUseCase: DeleteContactUseCase,
    private val lightningInputParser: LightningInputParser = LightningInputParser(),
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    autoSaveScope: CoroutineScope? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val durableAutoSaveScope = autoSaveScope ?: scope
    private var contacts: List<Contact> = emptyList()
    private var shortcuts: List<PaymentShortcut> = emptyList()
    private var hasBlinkWallet: Boolean = false
    private var editorAutoSaveRevision: Int = 0
    private var pendingContactEditorId: String? = null

    private val _uiState = MutableStateFlow(ContactsSettingsUiState())
    val uiState: StateFlow<ContactsSettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ContactsSettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ContactsSettingsEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            observeContacts().collectLatest {
                contacts = it
                pendingContactEditorId?.let { contactId ->
                    if (openContactEditor(contactId)) {
                        pendingContactEditorId = null
                    }
                }
                refresh()
            }
        }
        scope.launch {
            observeWalletConnection().collectLatest { wallet ->
                hasBlinkWallet = wallet?.isBlink == true
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

    fun startAddContact() {
        editorAutoSaveRevision++
        _uiState.value = _uiState.value.copy(
            contactEditor = ContactSettingsEditor(
                contactId = null,
                address = "",
                alias = "",
                roles = emptySet(),
                addressEditable = true
            )
        )
    }

    fun startBlinkContactsImport() {
        if (hasBlinkWallet) {
            scope.launch {
                _events.emit(ContactsSettingsEvent.OpenBlinkContactsImport)
            }
        }
    }

    fun startEditContact(id: String) {
        if (openContactEditor(id)) {
            pendingContactEditorId = null
        } else {
            pendingContactEditorId = id
        }
    }

    private fun openContactEditor(id: String): Boolean {
        val contact = contacts.firstOrNull { it.id == id } ?: return false
        editorAutoSaveRevision++
        _uiState.value = _uiState.value.copy(
            contactEditor = ContactSettingsEditor(
                contactId = contact.id,
                address = contact.address.full,
                alias = contact.alias.orEmpty(),
                roles = contact.roles,
                addressEditable = false,
                shortcuts = shortcutItemsForContact(contact.id)
            )
        )
        return true
    }

    fun deleteContactEditor() {
        val id = _uiState.value.contactEditor?.contactId ?: return
        editorAutoSaveRevision++
        scope.launch {
            deleteContactUseCase(id)
            _uiState.value = _uiState.value.copy(contactEditor = null)
            refresh()
            _events.emit(ContactsSettingsEvent.CloseContactEditor)
        }
    }

    fun updateContactEditorAddress(address: String) {
        updateContactEditor { it.copy(address = address, error = null) }
    }

    fun updateContactEditorAlias(alias: String) {
        updateContactEditor { it.copy(alias = alias, error = null) }
            ?.autoSaveExistingContact()
    }

    fun updateContactEditorRole(role: ContactRole?) {
        updateContactEditor { it.copy(roles = it.roles.toggleRole(role)) }
            ?.autoSaveExistingContact()
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        refresh()
    }

    fun saveContactEditor() {
        val editor = _uiState.value.contactEditor ?: return
        val address = parseAddress(editor.address)
        if (address == null) {
            updateContactEditor { it.copy(error = ContactEditorError.InvalidAddress) }
            return
        }
        scope.launch {
            if (editor.contactId == null) {
                saveContact(address, editor.alias, editor.roles)
            } else {
                updateContact(editor.contactId, editor.alias, editor.roles)
            }
            _uiState.value = _uiState.value.copy(contactEditor = null)
            refresh()
            _events.emit(ContactsSettingsEvent.CloseContactEditor)
        }
    }

    fun createShortcutForCurrentContact() {
        val contactId = _uiState.value.contactEditor?.contactId ?: return
        scope.launch {
            _events.emit(ContactsSettingsEvent.CreateShortcutForContact(contactId))
        }
    }

    fun clear() {
        scope.cancel()
    }

    private fun updateContactEditor(
        transform: (ContactSettingsEditor) -> ContactSettingsEditor
    ): ContactSettingsEditor? {
        val editor = _uiState.value.contactEditor ?: return null
        val updated = transform(editor)
        _uiState.value = _uiState.value.copy(contactEditor = updated)
        return updated
    }

    private fun ContactSettingsEditor.autoSaveExistingContact() {
        val id = contactId ?: return
        val revision = ++editorAutoSaveRevision
        val aliasSnapshot = alias
        val rolesSnapshot = roles
        durableAutoSaveScope.launch {
            if (revision == editorAutoSaveRevision) {
                updateContact(id, aliasSnapshot, rolesSnapshot)
            }
        }
    }

    private fun refresh() {
        val query = _uiState.value.query.trim().lowercase()
        val refreshedEditor = _uiState.value.contactEditor?.let { editor ->
            editor.copy(
                shortcuts = editor.contactId
                    ?.let(::shortcutItemsForContact)
                    .orEmpty()
            )
        }
        val contactItems = contacts
            .filter { contact ->
                query.isBlank() ||
                    contact.displayName.lowercase().contains(query) ||
                    contact.address.full.lowercase().contains(query) ||
                    contact.roles.any { it.name.lowercase().contains(query) }
            }
            .sortedWith(
                compareByDescending<Contact> {
                    if (ContactRole.Favorite in it.roles) 1 else 0
                }
                    .thenByDescending { it.stats.paymentCount }
                    .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
                    .thenBy { it.displayName.lowercase() }
            )
            .map { contact ->
                ContactSettingsItem(
                    id = contact.id,
                    displayName = contact.displayName,
                    address = contact.address.full,
                    roles = contact.roles
                )
            }
        _uiState.value = _uiState.value.copy(
            contacts = contactItems,
            contactEditor = refreshedEditor,
            hasBlinkWallet = hasBlinkWallet
        )
    }

    private fun shortcutItemsForContact(contactId: String): List<ContactShortcutItem> {
        val contact = contacts.firstOrNull { it.id == contactId } ?: return emptyList()
        return shortcuts
            .filter { shortcut ->
                shortcut.contactId == contactId ||
                    shortcut.address.sameAddressAs(contact.address)
            }
            .sortedWith(
                compareByDescending<PaymentShortcut> { it.stats.paymentCount }
                    .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
                    .thenBy { it.title.lowercase() }
            )
            .map { shortcut ->
                ContactShortcutItem(
                    id = shortcut.id,
                    title = shortcut.title,
                    amountText = shortcut.amount.displayText(),
                    comment = shortcut.comment
                )
            }
    }

    private fun parseAddress(raw: String): LightningAddress? =
        when (val result = lightningInputParser.parse(raw.trim())) {
            is LightningInputParser.ParseResult.Success ->
                (result.target as? LightningInputParser.Target.LightningAddressTarget)?.address

            is LightningInputParser.ParseResult.Failure -> null
        }

    private fun Set<ContactRole>.toggleRole(role: ContactRole?): Set<ContactRole> = when (role) {
        null -> emptySet()
        else -> if (role in this) this - role else this + role
    }

    private fun LightningAddress.sameAddressAs(other: LightningAddress): Boolean =
        full.equals(other.full, ignoreCase = true)

    private fun ShortcutAmount.displayText(): String {
        val info = CurrencyCatalog.infoFor(normalizedCurrencyCode)
        val unit = if (info.code == CurrencyCatalog.DEFAULT_CODE) {
            "sats"
        } else {
            info.code
        }
        return "${minor.formatMinorAmount(info.fractionDigits)} $unit"
    }

    private fun Long.formatMinorAmount(fractionDigits: Int): String {
        if (fractionDigits <= 0) return toString()
        val factor = decimalFactor(fractionDigits)
        val whole = this / factor
        val fraction = (this % factor).toString().padStart(fractionDigits, '0').trimEnd('0')
        return if (fraction.isEmpty()) {
            whole.toString()
        } else {
            "$whole.$fraction"
        }
    }

    private fun decimalFactor(fractionDigits: Int): Long {
        var factor = 1L
        repeat(fractionDigits) { factor *= 10L }
        return factor
    }
}

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
