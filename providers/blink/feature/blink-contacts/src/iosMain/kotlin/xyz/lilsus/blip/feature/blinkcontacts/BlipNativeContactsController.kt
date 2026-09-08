package xyz.lilsus.blip.feature.blinkcontacts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.blip.ui.nativeBlinkErrorMessageFor
import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubRepository

data class BlipNativeContactsSnapshot(
    val title: String,
    val hint: String,
    val importTitle: String,
    val skipTitle: String,
    val loadingTitle: String,
    val emptyTitle: String,
    val selectAllTitle: String,
    val selectedSummary: String,
    val successMessage: String?,
    val searchTitle: String,
    val noMatchesTitle: String,
    val items: List<BlipNativeContactItem>,
    val hasAnyItems: Boolean,
    val allSelected: Boolean,
    val canSelectAll: Boolean,
    val canImport: Boolean,
    val isLoading: Boolean,
    val isImporting: Boolean,
    val errorMessage: String?
)

data class BlipNativeContactItem(
    val id: String,
    val title: String,
    val address: String,
    val status: String,
    val selected: Boolean,
    val enabled: Boolean
)

/** Blip-owned native presentation boundary for importing provider contacts into its contact book. */
class BlipNativeContactsController(
    blinkWallet: BlinkWallet,
    hubRepository: PaymentHubRepository,
    private val languageChanges: Flow<*>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val viewModel = BlinkContactsImportViewModel(blinkWallet, hubRepository)

    fun observe(onChange: (BlipNativeContactsSnapshot) -> Unit): () -> Unit {
        val job =
            scope.launch {
                combine(viewModel.uiState, languageChanges) { state, _ -> state }
                    .collect { onChange(it.toNativeSnapshot()) }
            }
        return { job.cancel() }
    }

    fun observeImported(onImported: (Int) -> Unit): () -> Unit {
        val job =
            scope.launch {
                viewModel.events.collect { event ->
                    when (event) {
                        is BlinkContactsImportEvent.Imported -> onImported(event.count)
                    }
                }
            }
        return { job.cancel() }
    }

    fun load() {
        viewModel.loadBlinkContacts()
    }

    fun updateSearch(query: String) {
        viewModel.updateSearchQuery(query)
    }

    fun toggleContact(id: String) {
        viewModel.toggleBlinkContact(id)
    }

    fun toggleAll() {
        viewModel.toggleAllBlinkContacts()
    }

    fun importSelected() {
        viewModel.importSelectedBlinkContacts()
    }

    fun clear() {
        viewModel.clear()
        scope.cancel()
    }

    private suspend fun BlinkContactsImportUiState.toNativeSnapshot(): BlipNativeContactsSnapshot {
        val alreadyAdded =
            nativeString(
                NativeStringResource(
                    table = "BlipContacts",
                    key = "settings_wallet_details_import_contacts_already_added"
                )
            )
        return BlipNativeContactsSnapshot(
            title = nativeString(
                NativeStringResource(table = "BlipUI", key = "blink_contacts_title")
            ),
            hint = nativeString(
                NativeStringResource(table = "BlipUI", key = "blink_contacts_import_hint")
            ),
            importTitle = nativeString(
                NativeStringResource(table = "BlipUI", key = "blink_contacts_import")
            ),
            skipTitle =
                nativeString(
                    NativeStringResource(
                        table = "BlipContacts",
                        key = "settings_wallet_details_import_contacts_skip"
                    )
                ),
            loadingTitle =
                nativeString(
                    NativeStringResource(
                        table = "BlipContacts",
                        key = "settings_wallet_details_import_contacts_loading"
                    )
                ),
            emptyTitle =
                nativeString(
                    NativeStringResource(
                        table = "BlipContacts",
                        key = "settings_wallet_details_import_contacts_empty"
                    )
                ),
            selectAllTitle =
                nativeString(
                    NativeStringResource(
                        table = "BlipContacts",
                        key = "settings_wallet_details_import_contacts_select_all"
                    )
                ),
            selectedSummary =
                nativeString(
                    NativeStringResource(
                        table = "BlipContacts",
                        key = "settings_wallet_details_import_contacts_selected"
                    ),
                    selectedCount
                ),
            successMessage =
                importedCount?.let {
                    nativeString(
                        NativeStringResource(
                            table = "BlipContacts",
                            key = "settings_wallet_details_import_contacts_success"
                        ),
                        it
                    )
                },
            searchTitle = nativeString(
                NativeStringResource(table = "BlipContacts", key = "import_contacts_search")
            ),
            noMatchesTitle = nativeString(
                NativeStringResource(table = "BlipContacts", key = "import_contacts_no_matches")
            ),
            items =
                filteredItems.map { item ->
                    BlipNativeContactItem(
                        id = item.id,
                        title = item.displayName,
                        address = item.address,
                        status =
                            if (item.alreadyAdded) {
                                alreadyAdded
                            } else {
                                nativeString(
                                    NativeStringResource(
                                        table = "BlipContacts",
                                        key = "settings_wallet_details_import_contacts_transactions"
                                    ),
                                    item.transactionsCount
                                )
                            },
                        selected = item.id in selectedIds,
                        enabled = !item.alreadyAdded && !isImporting
                    )
                },
            hasAnyItems = items.isNotEmpty(),
            allSelected = allSelected,
            canSelectAll = hasSelectableItems && !isImporting,
            canImport = selectedCount > 0 && !isLoading && !isImporting,
            isLoading = isLoading,
            isImporting = isImporting,
            errorMessage = error?.let { nativeBlinkErrorMessageFor(it) }
        )
    }
}
