package xyz.lilsus.raylsuite.feature.paymenthub.canvas

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHub
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.isGroupId
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubTileRenderModel
import xyz.lilsus.raylsuite.feature.paymenthub.render.toCanvasTiles

/**
 * The canvas: which tiles exist, which container is open, and whether the user is arranging. It
 * owns arrangement; paying is still the host controller's job.
 */
class HubCanvasViewModel(
    private val repository: PaymentHubRepository,
    private val layoutRepository: CanvasLayoutRepository,
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val editing = MutableStateFlow(false)
    private val expanded = MutableStateFlow<HubItemId?>(null)
    private val message = MutableStateFlow<HubCanvasMessage?>(null)
    private var messageJob: Job? = null

    private val mutableUiState = MutableStateFlow(HubCanvasUiState())
    val uiState: StateFlow<HubCanvasUiState> = mutableUiState.asStateFlow()

    init {
        scope.launch {
            combine(
                repository.hub,
                layoutRepository.layout,
                editing,
                expanded,
                message
            ) { hub, layout, arranging, expandedId, toast ->
                HubCanvasUiState(
                    tiles = hub.toCanvasTiles(layout, if (arranging) null else expandedId),
                    editing = arranging,
                    expandedId = if (arranging) null else expandedId,
                    hasItems = !hub.isEmpty,
                    message = toast
                )
            }.collect { mutableUiState.value = it }
        }
        scope.launch {
            combine(repository.hub, layoutRepository.layout, ::Pair).collect { (hub, layout) ->
                val ordered = hub.orderedIds()
                if (layout.covering(ordered) != layout) {
                    layoutRepository.update { it.covering(ordered) }
                }
            }
        }
    }

    fun startEditing() {
        expanded.value = null
        editing.value = true
    }

    fun stopEditing() {
        editing.value = false
    }

    /** Tapping a closed container opens it in place; only one is ever open. */
    fun toggleExpanded(id: HubItemId) {
        expanded.value = if (expanded.value == id) null else id
    }

    fun collapse() {
        expanded.value = null
    }

    fun resize(id: HubItemId, size: CanvasTileSize) {
        scope.launch { layoutRepository.update { it.resize(id, size) } }
    }

    /** Removes the item itself: on this canvas a tile and its target are the same thing. */
    fun delete(id: HubItemId) {
        scope.launch {
            if (id.isGroupId()) repository.deleteGroup(id) else repository.deleteTarget(id)
            layoutRepository.update { it.remove(id) }
            if (expanded.value == id) expanded.value = null
            announce(HubCanvasMessage.Deleted)
        }
    }

    /** Moves one tile to the position occupied by another tile. */
    fun move(draggedId: HubItemId, targetId: HubItemId) {
        if (draggedId == targetId) return
        scope.launch {
            layoutRepository.update { layout ->
                val index = layout.indexOf(targetId)
                if (index < 0) layout else layout.moveTo(draggedId, index)
            }
        }
    }

    fun dismissMessage() {
        messageJob?.cancel()
        message.value = null
    }

    fun clear() {
        scope.cancel()
    }

    private fun announce(value: HubCanvasMessage) {
        message.value = value
        messageJob?.cancel()
        messageJob = scope.launch {
            delay(HubGrid.TOAST_MS)
            message.value = null
        }
    }

    private fun PaymentHub.orderedIds(): List<HubItemId> =
        targets.map { it.id } + groups.map { it.id }
}

data class HubCanvasUiState(
    val tiles: List<HubTileRenderModel> = emptyList(),
    val editing: Boolean = false,
    val expandedId: HubItemId? = null,
    /** False only while the hub holds nothing at all, which is the first-run state. */
    val hasItems: Boolean = false,
    val message: HubCanvasMessage? = null
)

/** What the canvas just did. Each platform renders these as its own short-lived notice. */
enum class HubCanvasMessage {
    Deleted
}
