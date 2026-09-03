package xyz.lilsus.blip.feature.blinkcontacts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.Res as BlinkContactsRes
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.import_contacts_no_matches
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.import_contacts_search
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_already_added
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_empty
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_import
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_loading
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_select_all
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_selected
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_skip
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_success
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_transactions
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.blip.ui.generated.resources.Res as BlipUiRes
import xyz.lilsus.blip.ui.generated.resources.blink_contacts_import
import xyz.lilsus.blip.ui.generated.resources.blink_contacts_import_hint
import xyz.lilsus.blip.ui.generated.resources.blink_contacts_title
import xyz.lilsus.blip.ui.nativeBlinkErrorMessageFor
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

/** Blip-owned native presentation boundary for importing provider contacts into the Hub. */
class BlipNativeContactsController(
    blinkWallet: BlinkWallet,
    paymentHub: PaymentHubRepository,
    private val languageChanges: Flow<*>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val viewModel = BlinkContactsImportViewModel(blinkWallet, paymentHub)

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
            getString(
                BlinkContactsRes.string.settings_wallet_details_import_contacts_already_added
            )
        return BlipNativeContactsSnapshot(
            title = getString(BlipUiRes.string.blink_contacts_title),
            hint = getString(BlipUiRes.string.blink_contacts_import_hint),
            importTitle = getString(BlipUiRes.string.blink_contacts_import),
            skipTitle =
                getString(
                    BlinkContactsRes.string.settings_wallet_details_import_contacts_skip
                ),
            loadingTitle =
                getString(
                    BlinkContactsRes.string.settings_wallet_details_import_contacts_loading
                ),
            emptyTitle =
                getString(
                    BlinkContactsRes.string.settings_wallet_details_import_contacts_empty
                ),
            selectAllTitle =
                getString(
                    BlinkContactsRes.string.settings_wallet_details_import_contacts_select_all
                ),
            selectedSummary =
                getString(
                    BlinkContactsRes.string.settings_wallet_details_import_contacts_selected,
                    selectedCount
                ),
            successMessage =
                importedCount?.let {
                    getString(
                        BlinkContactsRes.string.settings_wallet_details_import_contacts_success,
                        it
                    )
                },
            searchTitle = getString(BlinkContactsRes.string.import_contacts_search),
            noMatchesTitle = getString(BlinkContactsRes.string.import_contacts_no_matches),
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
                                getString(
                                    BlinkContactsRes.string
                                        .settings_wallet_details_import_contacts_transactions,
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
