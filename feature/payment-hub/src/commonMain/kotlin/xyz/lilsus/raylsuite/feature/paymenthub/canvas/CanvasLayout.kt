package xyz.lilsus.raylsuite.feature.paymenthub.canvas

import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId

/**
 * A tile's stored, closed size. A container's open size follows how many members it holds, so the
 * only size anyone ever chooses is the closed one. Freeform dimensions stay deliberately excluded.
 */
enum class CanvasTileSize(val storedValue: String, val columns: Int, val rows: Int) {
    Small("small", 1, 1),
    Wide("wide", 2, 1),
    Large("large", 2, 2);

    companion object {
        fun fromStoredValue(value: String?): CanvasTileSize =
            entries.firstOrNull { it.storedValue == value } ?: Small

        fun of(columns: Int, rows: Int): CanvasTileSize =
            entries.firstOrNull { it.columns == columns && it.rows == rows } ?: Small
    }
}

/**
 * The user's arrangement of hub items, in logical order. Tiles reference hub items by ID and
 * never copy target or group data, so removing this state leaves the hub intact.
 */
data class CanvasLayout(val tiles: List<CanvasTile> = emptyList()) {
    val placedItemIds: Set<HubItemId>
        get() = tiles.mapTo(mutableSetOf()) { it.id }

    fun indexOf(id: HubItemId): Int = tiles.indexOfFirst { it.id == id }

    fun size(id: HubItemId): CanvasTileSize? = tiles.firstOrNull { it.id == id }?.size

    /** Appends [id], or resizes it when it is already placed. */
    fun place(id: HubItemId, size: CanvasTileSize = CanvasTileSize.Small): CanvasLayout =
        if (id in placedItemIds) resize(id, size) else copy(tiles = tiles + CanvasTile(id, size))

    /** Inserts [id] at [index], or moves it there when it is already placed. */
    fun insertAt(
        id: HubItemId,
        index: Int,
        size: CanvasTileSize = CanvasTileSize.Small
    ): CanvasLayout {
        if (id in placedItemIds) return moveTo(id, index)
        val position = index.coerceIn(0, tiles.size)
        val inserted = tiles.toMutableList()
        inserted.add(position, CanvasTile(id, size))
        return copy(tiles = inserted)
    }

    fun remove(id: HubItemId): CanvasLayout = copy(tiles = tiles.filterNot { it.id == id })

    fun resize(id: HubItemId, size: CanvasTileSize): CanvasLayout = copy(
        tiles = tiles.map { tile -> if (tile.id == id) tile.copy(size = size) else tile }
    )

    /** Splices [id] to [index] in logical order. This is how a drop resolves a reorder. */
    fun moveTo(id: HubItemId, index: Int): CanvasLayout {
        val from = indexOf(id)
        if (from < 0 || tiles.isEmpty()) return this
        val to = index.coerceIn(0, tiles.lastIndex)
        if (from == to) return this
        val reordered = tiles.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return copy(tiles = reordered)
    }

    /**
     * Drops tiles whose hub item is gone and appends the ones that are not placed yet, so every
     * saved target and group has exactly one tile.
     */
    fun covering(orderedIds: List<HubItemId>): CanvasLayout {
        val normalized = normalized(orderedIds.toSet())
        val placed = normalized.placedItemIds
        return normalized.copy(
            tiles =
                normalized.tiles +
                    orderedIds.filterNot {
                        it in placed
                    }.map { CanvasTile(it, CanvasTileSize.Small) }
        )
    }

    /** Drops duplicates and tiles whose hub item no longer exists. */
    fun normalized(existingIds: Set<HubItemId>? = null): CanvasLayout {
        val seen = mutableSetOf<HubItemId>()
        return copy(
            tiles =
                tiles.filter { tile ->
                    seen.add(tile.id) && (existingIds == null || tile.id in existingIds)
                }
        )
    }

    companion object {
        val Empty = CanvasLayout()
    }
}

data class CanvasTile(val id: HubItemId, val size: CanvasTileSize)
