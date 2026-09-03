package xyz.lilsus.raylsuite.feature.paymenthub.library

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.lens.HubItemDetail
import xyz.lilsus.raylsuite.feature.paymenthub.lens.HubItemRenderModel
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubRenderState
import xyz.lilsus.raylsuite.feature.paymenthub.lens.toRenderState

/** Full hub library: browse, search, pin, and reorder. Editing happens in the editors. */
class PaymentHubLibraryViewModel(
    private val repository: PaymentHubRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var render = PaymentHubRenderState()

    private val mutableUiState = MutableStateFlow(PaymentHubLibraryUiState())
    val uiState: StateFlow<PaymentHubLibraryUiState> = mutableUiState.asStateFlow()

    init {
        scope.launch {
            repository.hub.collectLatest { hub ->
                render = hub.toRenderState(recentLimit = RECENT_LIMIT)
                refresh()
            }
        }
    }

    fun updateSearch(query: String) {
        mutableUiState.update { it.copy(query = query) }
        refresh()
    }

    fun toggleArrangePins() {
        mutableUiState.update { it.copy(arrangingPins = !it.arrangingPins) }
    }

    fun setPinned(id: HubItemId, pinned: Boolean) {
        scope.launch { repository.setPinned(id, pinned) }
    }

    fun movePinned(id: HubItemId, offset: Int) {
        val order = render.pinnedItems.map { it.id }
        val index = order.indexOf(id)
        val target = index + offset
        if (index < 0 || target !in order.indices) return
        val reordered = order.toMutableList()
        reordered.removeAt(index)
        reordered.add(target, id)
        scope.launch { repository.reorderPinned(reordered) }
    }

    fun clear() {
        scope.cancel()
    }

    private fun refresh() {
        val query = mutableUiState.value.query.trim()
        val matches: (HubItemRenderModel) -> Boolean = { item ->
            query.isEmpty() ||
                item.title.contains(query, ignoreCase = true) ||
                (item.detail as? HubItemDetail.Target)
                    ?.address
                    ?.contains(query, ignoreCase = true) == true
        }
        mutableUiState.update { current ->
            current.copy(
                isEmpty = render.isEmpty,
                pinned = render.pinnedItems.filter(matches),
                groups = render.allItems.filter { it.isGroup }.filter(matches),
                recent = render.recentItems.filter(matches),
                targets = render.allItems.filterNot { it.isGroup }.filter(matches),
                arrangingPins = current.arrangingPins && render.pinnedItems.isNotEmpty()
            )
        }
    }

    private companion object {
        const val RECENT_LIMIT = 5
    }
}

data class PaymentHubLibraryUiState(
    val query: String = "",
    val isEmpty: Boolean = true,
    val pinned: List<HubItemRenderModel> = emptyList(),
    val groups: List<HubItemRenderModel> = emptyList(),
    val recent: List<HubItemRenderModel> = emptyList(),
    val targets: List<HubItemRenderModel> = emptyList(),
    val arrangingPins: Boolean = false
) {
    val hasMatches: Boolean
        get() =
            pinned.isNotEmpty() || groups.isNotEmpty() || recent.isNotEmpty() ||
                targets.isNotEmpty()
}
