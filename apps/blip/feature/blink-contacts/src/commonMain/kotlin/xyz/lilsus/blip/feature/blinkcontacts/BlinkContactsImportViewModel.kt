package xyz.lilsus.blip.feature.blinkcontacts

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.lilsus.blip.integration.blink.BlinkApiException
import xyz.lilsus.blip.integration.blink.BlinkConnectionException
import xyz.lilsus.blip.integration.blink.BlinkContact
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.blip.ui.BlinkUiError
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository

class BlinkContactsImportViewModel(
    private val blinkWallet: BlinkWallet,
    private val contactsRepository: ContactsRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val mutableUiState = MutableStateFlow(BlinkContactsImportUiState())
    val uiState: StateFlow<BlinkContactsImportUiState> = mutableUiState.asStateFlow()

    private val mutableEvents =
        MutableSharedFlow<BlinkContactsImportEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BlinkContactsImportEvent> = mutableEvents.asSharedFlow()

    fun loadBlinkContacts() {
        if (
            mutableUiState.value.isLoading ||
            mutableUiState.value.isImporting ||
            mutableUiState.value.hasLoaded
        ) {
            return
        }

        scope.launch {
            mutableUiState.update {
                it.copy(
                    isLoading = true,
                    isImporting = false,
                    importedCount = null,
                    error = null
                )
            }
            try {
                val existingAddressKeys =
                    contactsRepository
                        .getContacts()
                        .map { it.address.importKey() }
                        .toSet()
                val items =
                    blinkWallet
                        .fetchContacts()
                        .mapNotNull { contact -> toImportItem(contact, existingAddressKeys) }
                        .distinctBy { it.id }
                        .sortedWith(
                            compareBy<BlinkContactImportItem> {
                                if (it.alreadyAdded) 1 else 0
                            }.thenByDescending { it.transactionsCount }
                                .thenBy { it.displayName.lowercase() }
                        )
                val selectableIds =
                    items
                        .filterNot { it.alreadyAdded }
                        .map(BlinkContactImportItem::id)
                        .toSet()
                mutableUiState.update {
                    it.copy(
                        items = items,
                        selectedIds = selectableIds,
                        isLoading = false,
                        hasLoaded = true
                    )
                }
            } catch (error: BlinkApiException) {
                finishLoadingWith(BlinkUiError.Api(error.error))
            } catch (error: BlinkConnectionException) {
                finishLoadingWith(BlinkUiError.Connection(error.error))
            } catch (error: Exception) {
                finishLoadingWith(BlinkUiError.Unexpected(error.message))
            }
        }
    }

    fun toggleBlinkContact(id: String) {
        mutableUiState.update { state ->
            val item = state.items.firstOrNull { it.id == id } ?: return@update state
            if (item.alreadyAdded || state.isImporting) return@update state
            val selectedIds =
                if (id in state.selectedIds) {
                    state.selectedIds - id
                } else {
                    state.selectedIds + id
                }
            state.copy(
                selectedIds = selectedIds,
                importedCount = null,
                error = null
            )
        }
    }

    fun toggleAllBlinkContacts() {
        mutableUiState.update { state ->
            if (!state.hasSelectableItems || state.isImporting) return@update state
            val selectableIds =
                state.items
                    .filterNot { it.alreadyAdded }
                    .map(BlinkContactImportItem::id)
                    .toSet()
            state.copy(
                selectedIds = if (state.allSelected) emptySet() else selectableIds,
                importedCount = null,
                error = null
            )
        }
    }

    fun updateSearchQuery(query: String) {
        mutableUiState.update {
            it.copy(searchQuery = query)
        }
    }

    fun importSelectedBlinkContacts() {
        val state = mutableUiState.value
        if (state.isImporting) return
        val selectedItems =
            state.items.filter {
                !it.alreadyAdded && it.id in state.selectedIds
            }
        if (selectedItems.isEmpty()) return

        scope.launch {
            mutableUiState.update {
                it.copy(
                    isImporting = true,
                    importedCount = null,
                    error = null
                )
            }
            try {
                selectedItems.forEach { item ->
                    val address =
                        LightningAddress.parse(item.address)
                            ?: error("Invalid contact address")
                    contactsRepository.saveContact(address, item.alias, emptySet())
                }
                val importedIds = selectedItems.map(BlinkContactImportItem::id).toSet()
                mutableUiState.update {
                    it.copy(
                        isImporting = false,
                        selectedIds = emptySet(),
                        items =
                            it.items.map { item ->
                                if (item.id in importedIds) {
                                    item.copy(alreadyAdded = true)
                                } else {
                                    item
                                }
                            },
                        importedCount = selectedItems.size
                    )
                }
                mutableEvents.emit(BlinkContactsImportEvent.Imported(selectedItems.size))
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isImporting = false,
                        error = BlinkUiError.Unexpected(error.message)
                    )
                }
            }
        }
    }

    fun clear() {
        scope.cancel()
    }

    private fun finishLoadingWith(error: BlinkUiError) {
        mutableUiState.update {
            it.copy(
                isLoading = false,
                hasLoaded = true,
                error = error
            )
        }
    }

    private fun toImportItem(
        contact: BlinkContact,
        existingAddressKeys: Set<String>
    ): BlinkContactImportItem? {
        val rawHandle = contact.handle.trim()
        if (rawHandle.isBlank()) return null

        val address =
            LightningAddress.parse(
                if ('@' in rawHandle) {
                    rawHandle
                } else {
                    "$rawHandle@${contact.lightningAddressDomain}"
                }
            ) ?: return null
        val alias = contact.alias?.trim()?.takeIf(String::isNotEmpty)
        val addressKey = address.importKey()
        return BlinkContactImportItem(
            id = addressKey,
            displayName = alias ?: address.username,
            address = address.full,
            alias = alias,
            transactionsCount = contact.transactionsCount,
            alreadyAdded = addressKey in existingAddressKeys
        )
    }
}

private fun LightningAddress.importKey(): String = full.trim().lowercase()

data class BlinkContactsImportUiState(
    val items: List<BlinkContactImportItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importedCount: Int? = null,
    val error: BlinkUiError? = null
) {
    val selectedCount: Int
        get() = items.count { !it.alreadyAdded && it.id in selectedIds }

    val filteredItems: List<BlinkContactImportItem>
        get() {
            val query = searchQuery.trim()
            if (query.isBlank()) return items
            return items.filter { it.matchesQuery(query) }
        }

    val selectableCount: Int
        get() = items.count { !it.alreadyAdded }

    val hasSelectableItems: Boolean
        get() = selectableCount > 0

    val allSelected: Boolean
        get() = hasSelectableItems && selectedCount == selectableCount
}

data class BlinkContactImportItem(
    val id: String,
    val displayName: String,
    val address: String,
    val alias: String?,
    val transactionsCount: Int,
    val alreadyAdded: Boolean
)

private fun BlinkContactImportItem.matchesQuery(query: String): Boolean =
    displayName.contains(query, ignoreCase = true) ||
        address.contains(query, ignoreCase = true) ||
        alias.orEmpty().contains(query, ignoreCase = true)

sealed interface BlinkContactsImportEvent {
    data class Imported(val count: Int) : BlinkContactsImportEvent
}
