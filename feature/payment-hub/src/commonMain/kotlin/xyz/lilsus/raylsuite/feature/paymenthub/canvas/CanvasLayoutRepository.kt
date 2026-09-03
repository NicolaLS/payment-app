package xyz.lilsus.raylsuite.feature.paymenthub.canvas

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId

/**
 * Presentation-only persistence for the hub arrangement. It is kept separate from the canonical
 * hub document so clearing it never affects targets, groups, pins, or statistics.
 */
interface CanvasLayoutRepository {
    val layout: StateFlow<CanvasLayout>

    suspend fun update(transform: (CanvasLayout) -> CanvasLayout)

    suspend fun reset()
}

class DefaultCanvasLayoutRepository(private val settings: Settings) : CanvasLayoutRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutationMutex = Mutex()
    private val mutableLayout = MutableStateFlow(load())

    override val layout: StateFlow<CanvasLayout> = mutableLayout.asStateFlow()

    override suspend fun update(transform: (CanvasLayout) -> CanvasLayout) {
        mutationMutex.withLock {
            val current = mutableLayout.value
            val updated = transform(current).normalized()
            if (updated == current) return
            settings.putString(KEY_LAYOUT, json.encodeToString(updated.toDocument()))
            mutableLayout.value = updated
        }
    }

    override suspend fun reset() {
        mutationMutex.withLock {
            settings.remove(KEY_LAYOUT)
            mutableLayout.value = CanvasLayout.Empty
        }
    }

    private fun load(): CanvasLayout = settings
        .getStringOrNull(KEY_LAYOUT)
        ?.let { encoded ->
            runCatching { json.decodeFromString<CanvasLayoutDocument>(encoded).toLayout() }
                .getOrNull()
        }
        ?.normalized()
        ?: CanvasLayout.Empty

    private companion object {
        const val KEY_LAYOUT = "paymentHub.canvas.layout"
    }
}

@Serializable
private data class CanvasLayoutDocument(val tiles: List<CanvasTileRecord> = emptyList())

@Serializable
private data class CanvasTileRecord(val id: String, val size: String? = null)

private fun CanvasLayout.toDocument(): CanvasLayoutDocument = CanvasLayoutDocument(
    tiles = tiles.map { CanvasTileRecord(id = it.id.value, size = it.size.storedValue) }
)

private fun CanvasLayoutDocument.toLayout(): CanvasLayout = CanvasLayout(
    tiles =
        tiles.mapNotNull { record ->
            record.id
                .takeIf(String::isNotBlank)
                ?.let { CanvasTile(HubItemId(it), CanvasTileSize.fromStoredValue(record.size)) }
        }
)
