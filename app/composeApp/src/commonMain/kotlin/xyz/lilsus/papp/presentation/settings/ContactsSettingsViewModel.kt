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
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.usecases.DeleteContactUseCase
import xyz.lilsus.papp.domain.usecases.ObserveContactsUseCase
import xyz.lilsus.papp.domain.usecases.ObserveWalletsUseCase
import xyz.lilsus.papp.domain.usecases.SaveContactUseCase
import xyz.lilsus.papp.domain.usecases.UpdateContactUseCase

class ContactsSettingsViewModel internal constructor(
    observeContacts: ObserveContactsUseCase,
    observeWallets: ObserveWalletsUseCase,
    private val saveContact: SaveContactUseCase,
    private val updateContact: UpdateContactUseCase,
    private val deleteContactUseCase: DeleteContactUseCase,
    private val lightningInputParser: LightningInputParser = LightningInputParser(),
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var contacts: List<Contact> = emptyList()
    private var blinkWallets: List<BlinkWalletImportOption> = emptyList()
    private var editorAutoSaveRevision: Int = 0

    private val _uiState = MutableStateFlow(ContactsSettingsUiState())
    val uiState: StateFlow<ContactsSettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ContactsSettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ContactsSettingsEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            observeContacts().collectLatest {
                contacts = it
                refresh()
            }
        }
        scope.launch {
            observeWallets().collectLatest { wallets ->
                blinkWallets = wallets
                    .filter { it.isBlink }
                    .map { it.toBlinkImportOption() }
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
        when (blinkWallets.size) {
            0 -> Unit

            1 -> openBlinkContactsImport(blinkWallets.first().walletId)

            else -> {
                _uiState.value = _uiState.value.copy(
                    blinkWalletChooser = BlinkWalletChooser(blinkWallets)
                )
            }
        }
    }

    fun selectBlinkWalletForImport(walletId: String) {
        if (blinkWallets.none { it.walletId == walletId }) return
        _uiState.value = _uiState.value.copy(blinkWalletChooser = null)
        openBlinkContactsImport(walletId)
    }

    fun dismissBlinkWalletChooser() {
        _uiState.value = _uiState.value.copy(blinkWalletChooser = null)
    }

    fun startEditContact(id: String) {
        val contact = contacts.firstOrNull { it.id == id } ?: return
        editorAutoSaveRevision++
        _uiState.value = _uiState.value.copy(
            contactEditor = ContactSettingsEditor(
                contactId = contact.id,
                address = contact.address.full,
                alias = contact.alias.orEmpty(),
                roles = contact.roles,
                addressEditable = false
            )
        )
    }

    fun deleteContactEditor() {
        val id = _uiState.value.contactEditor?.contactId ?: return
        editorAutoSaveRevision++
        scope.launch {
            deleteContactUseCase(id)
            _uiState.value = _uiState.value.copy(contactEditor = null)
            refresh()
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
            updateContactEditor { it.copy(error = "Enter a valid Lightning address.") }
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
        }
    }

    fun dismissEditor() {
        editorAutoSaveRevision++
        _uiState.value = _uiState.value.copy(contactEditor = null)
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
        scope.launch {
            if (revision == editorAutoSaveRevision) {
                updateContact(id, aliasSnapshot, rolesSnapshot)
            }
        }
    }

    private fun refresh() {
        val query = _uiState.value.query.trim().lowercase()
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
            blinkWallets = blinkWallets,
            blinkWalletChooser = _uiState.value.blinkWalletChooser
                ?.takeIf { blinkWallets.size > 1 }
                ?.copy(wallets = blinkWallets)
        )
    }

    private fun openBlinkContactsImport(walletId: String) {
        scope.launch {
            _events.emit(ContactsSettingsEvent.OpenBlinkContactsImport(walletId))
        }
    }

    private fun WalletConnection.toBlinkImportOption(): BlinkWalletImportOption =
        BlinkWalletImportOption(
            walletId = walletPublicKey,
            displayName = alias?.takeIf { it.isNotBlank() } ?: "Blink wallet",
            subtitle = walletPublicKey
        )

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
}

data class ContactsSettingsUiState(
    val contacts: List<ContactSettingsItem> = emptyList(),
    val contactEditor: ContactSettingsEditor? = null,
    val query: String = "",
    val blinkWallets: List<BlinkWalletImportOption> = emptyList(),
    val blinkWalletChooser: BlinkWalletChooser? = null
) {
    val hasBlinkWallets: Boolean get() = blinkWallets.isNotEmpty()
}

data class BlinkWalletChooser(val wallets: List<BlinkWalletImportOption>)

data class BlinkWalletImportOption(
    val walletId: String,
    val displayName: String,
    val subtitle: String
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
    val error: String? = null
)

sealed interface ContactsSettingsEvent {
    data class OpenBlinkContactsImport(val walletId: String) : ContactsSettingsEvent
}
