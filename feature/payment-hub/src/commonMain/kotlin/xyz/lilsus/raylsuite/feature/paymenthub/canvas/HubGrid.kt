package xyz.lilsus.raylsuite.feature.paymenthub.canvas

/**
 * Canvas grid geometry. These are plain values: Compose lays the grid out on Android and SwiftUI
 * lays the same grid out on iOS, so only the numbers are shared.
 */
object HubGrid {
    const val COLUMNS = 2
    const val ROW_HEIGHT = 92
    const val GAP = 8
    const val GUTTER = 16

    /** How long a canvas toast stays on screen, in milliseconds. */
    const val TOAST_MS = 2600L
}

data class HubGridSpan(val columns: Int, val rows: Int)

data class HubGridPlacement<T>(
    val value: T,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int
)

/**
 * Dense two-column packing: every span takes the first free block scanning rows top to bottom and
 * columns left to right, so a later 1x1 backfills the hole a full-width tile left behind.
 */
fun <T> packHubGrid(
    items: List<T>,
    columns: Int = HubGrid.COLUMNS,
    span: (T) -> HubGridSpan
): List<HubGridPlacement<T>> {
    val occupied = mutableListOf<BooleanArray>()

    fun row(index: Int): BooleanArray {
        while (occupied.size <= index) occupied += BooleanArray(columns)
        return occupied[index]
    }

    fun fits(top: Int, left: Int, width: Int, height: Int): Boolean {
        if (left + width > columns) return false
        for (r in top until top + height) {
            val cells = row(r)
            for (c in left until left + width) if (cells[c]) return false
        }
        return true
    }

    return items.map { item ->
        val requested = span(item)
        val width = requested.columns.coerceIn(1, columns)
        val height = requested.rows.coerceAtLeast(1)
        var top = 0
        var left = 0
        while (true) {
            val free = (0 until columns).firstOrNull { fits(top, it, width, height) }
            if (free != null) {
                left = free
                break
            }
            top++
        }
        for (r in top until top + height) {
            val cells = row(r)
            for (c in left until left + width) cells[c] = true
        }
        HubGridPlacement(item, column = left, row = top, columns = width, rows = height)
    }
}

/** Rows the packed grid occupies, which is the canvas height in grid units. */
fun List<HubGridPlacement<*>>.gridRowCount(): Int = maxOfOrNull { it.row + it.rows } ?: 0
