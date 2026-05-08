package xyz.lilsus.papp.presentation.settings.wallet

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
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.lnurl.LightningInputParser
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.BlinkContact
import xyz.lilsus.papp.domain.usecases.FetchBlinkContactsUseCase
import xyz.lilsus.papp.domain.usecases.GetContactsUseCase
import xyz.lilsus.papp.domain.usecases.SaveContactUseCase

class BlinkContactsImportViewModel internal constructor(
    private val walletId: String,
    private val fetchBlinkContacts: FetchBlinkContactsUseCase,
    private val getContacts: GetContactsUseCase,
    private val saveContact: SaveContactUseCase,
    private val lightningInputParser: LightningInputParser = LightningInputParser(),
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(BlinkContactsImportUiState())
    val uiState: StateFlow<BlinkContactsImportUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BlinkContactsImportEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BlinkContactsImportEvent> = _events.asSharedFlow()

    fun loadBlinkContacts() {
        if (_uiState.value.isLoading || _uiState.value.isImporting || _uiState.value.hasLoaded) {
            return
        }

        scope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isImporting = false,
                    importedCount = null,
                    error = null
                )
            }
            try {
                val existingAddressKeys = getContacts()
                    .map { it.address.importKey() }
                    .toSet()
                val items = fetchBlinkContacts(walletId)
                    .mapNotNull { contact -> toImportItem(contact, existingAddressKeys) }
                    .distinctBy { it.id }
                    .sortedWith(
                        compareBy<BlinkContactImportItem> { if (it.alreadyAdded) 1 else 0 }
                            .thenByDescending { it.transactionsCount }
                            .thenBy { it.displayName.lowercase() }
                    )
                val selectableIds = items
                    .filterNot { it.alreadyAdded }
                    .map { item -> item.id }
                    .toSet()
                _uiState.update {
                    it.copy(
                        items = items,
                        selectedIds = selectableIds,
                        isLoading = false,
                        hasLoaded = true
                    )
                }
            } catch (e: AppErrorException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasLoaded = true,
                        error = e.error
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        hasLoaded = true,
                        error = AppError.Unexpected(e.message)
                    )
                }
            }
        }
    }

    fun toggleBlinkContact(id: String) {
        _uiState.update { state ->
            val item = state.items.firstOrNull { it.id == id } ?: return@update state
            if (item.alreadyAdded || state.isImporting) return@update state
            val selectedIds = if (id in state.selectedIds) {
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
        _uiState.update { state ->
            if (!state.hasSelectableItems || state.isImporting) return@update state
            val selectableIds = state.items
                .filterNot { it.alreadyAdded }
                .map { it.id }
                .toSet()
            val selectedIds = if (state.allSelected) emptySet() else selectableIds
            state.copy(
                selectedIds = selectedIds,
                importedCount = null,
                error = null
            )
        }
    }

    fun importSelectedBlinkContacts() {
        val state = _uiState.value
        if (state.isImporting) return
        val selectedItems = state.items.filter {
            !it.alreadyAdded && it.id in state.selectedIds
        }
        if (selectedItems.isEmpty()) return

        scope.launch {
            _uiState.update {
                it.copy(
                    isImporting = true,
                    importedCount = null,
                    error = null
                )
            }
            try {
                selectedItems.forEach { item ->
                    val address = parseAddress(item.address)
                        ?: throw AppErrorException(AppError.Unexpected("Invalid contact address"))
                    saveContact(address, item.alias, emptySet())
                }
                val importedIds = selectedItems.map { it.id }.toSet()
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        selectedIds = emptySet(),
                        items = it.items.map { item ->
                            if (item.id in importedIds) {
                                item.copy(alreadyAdded = true)
                            } else {
                                item
                            }
                        },
                        importedCount = selectedItems.size
                    )
                }
                _events.emit(BlinkContactsImportEvent.Imported(selectedItems.size))
            } catch (e: AppErrorException) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        error = e.error
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        error = AppError.Unexpected(e.message)
                    )
                }
            }
        }
    }

    fun clear() {
        scope.cancel()
    }

    private fun toImportItem(
        contact: BlinkContact,
        existingAddressKeys: Set<String>
    ): BlinkContactImportItem? {
        val rawHandle = contact.handle.trim()
        if (rawHandle.isBlank()) return null

        val address = parseAddress(
            if ('@' in rawHandle) rawHandle else "$rawHandle@${contact.lightningAddressDomain}"
        ) ?: return null
        val alias = contact.alias?.trim()?.takeIf { it.isNotEmpty() }
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

    private fun parseAddress(raw: String): LightningAddress? =
        when (val result = lightningInputParser.parse(raw.trim())) {
            is LightningInputParser.ParseResult.Success ->
                (result.target as? LightningInputParser.Target.LightningAddressTarget)?.address

            is LightningInputParser.ParseResult.Failure -> null
        }
}

private fun LightningAddress.importKey(): String = full.trim().lowercase()

data class BlinkContactsImportUiState(
    val items: List<BlinkContactImportItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importedCount: Int? = null,
    val error: AppError? = null
) {
    val selectedCount: Int
        get() = items.count { !it.alreadyAdded && it.id in selectedIds }

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

sealed interface BlinkContactsImportEvent {
    data class Imported(val count: Int) : BlinkContactsImportEvent
}
