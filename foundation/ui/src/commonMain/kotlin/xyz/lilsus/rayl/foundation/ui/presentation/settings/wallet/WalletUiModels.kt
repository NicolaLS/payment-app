package xyz.lilsus.rayl.foundation.ui.presentation.settings.wallet

import xyz.lilsus.rayl.foundation.ui.domain.model.AppError
import xyz.lilsus.rayl.foundation.ui.domain.model.WalletType

data class WalletSettingsUiState(val wallet: WalletDisplay? = null) {
    val hasWallet: Boolean
        get() = wallet != null
}

data class WalletDisplay(
    val connectionId: String,
    val relay: String?,
    val lud16: String?,
    val alias: String?,
    val type: WalletType = WalletType.NWC
)

data class WalletDetailsUiState(
    val connectionId: String = "",
    val alias: String? = null,
    val walletType: WalletType = WalletType.NWC,
    val blinkDefaultWalletId: String? = null,
    val isRefreshing: Boolean = false,
    val isMissing: Boolean = false,
    val error: AppError? = null
) {
    val isBlink: Boolean
        get() = walletType == WalletType.BLINK
}

data class BlinkContactsImportUiState(
    val items: List<BlinkContactImportItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val searchQuery: String = "",
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importedCount: Int? = null,
    val error: AppError? = null
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
