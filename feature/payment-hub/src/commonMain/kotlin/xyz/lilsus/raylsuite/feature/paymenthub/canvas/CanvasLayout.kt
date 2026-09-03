package xyz.lilsus.raylsuite.feature.paymenthub.canvas

import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId

/** Supported tile sizes. Freeform dimensions are deliberately excluded. */
enum class CanvasTileSize(val storedValue: String) {
    Compact("compact"),
    Wide("wide");

    companion object {
        fun fromStoredValue(value: String?): CanvasTileSize =
            entries.firstOrNull { it.storedValue == value } ?: Compact
    }
}

/**
 * The user's arrangement of hub items, in logical order. Tiles reference hub items by ID and
 * never copy target or group data, so removing this state leaves the hub intact.
 */
data class CanvasLayout(val tiles: List<CanvasTile> = emptyList()) {
    val placedItemIds: Set<HubItemId>
        get() = tiles.mapTo(mutableSetOf()) { it.id }

    fun place(id: HubItemId, size: CanvasTileSize = CanvasTileSize.Compact): CanvasLayout =
        if (id in placedItemIds) this else copy(tiles = tiles + CanvasTile(id, size))

    fun remove(id: HubItemId): CanvasLayout = copy(tiles = tiles.filterNot { it.id == id })

    fun resize(id: HubItemId, size: CanvasTileSize): CanvasLayout = copy(
        tiles = tiles.map { tile -> if (tile.id == id) tile.copy(size = size) else tile }
    )

    /** Moves the tile at [index] by [offset] positions in logical order. */
    fun move(index: Int, offset: Int): CanvasLayout {
        val target = index + offset
        if (index !in tiles.indices || target !in tiles.indices) return this
        val reordered = tiles.toMutableList()
        val tile = reordered.removeAt(index)
        reordered.add(target, tile)
        return copy(tiles = reordered)
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
