package xyz.lilsus.raylsuite.feature.paymenthub.canvas

import kotlin.test.Test
import kotlin.test.assertEquals

class HubGridTest {
    @Test
    fun denseFlowBackfillsTheHoleAFullWidthTileLeaves() {
        val placements =
            packHubGrid(
                items = listOf("a" to (1 to 1), "wide" to (2 to 1), "b" to (1 to 1)),
                span = { HubGridSpan(it.second.first, it.second.second) }
            ).associate { it.value.first to (it.column to it.row) }

        assertEquals(0 to 0, placements.getValue("a"))
        assertEquals(0 to 1, placements.getValue("wide"))
        // The 1x1 backfills the free cell beside "a" instead of starting a third row.
        assertEquals(1 to 0, placements.getValue("b"))
    }

    @Test
    fun tallTilesReserveBothRowsAndCountTowardsTheCanvasHeight() {
        val placements =
            packHubGrid(
                items = listOf("large" to (2 to 2), "small" to (1 to 1)),
                span = { HubGridSpan(it.second.first, it.second.second) }
            )

        assertEquals(listOf(0 to 0, 0 to 2), placements.map { it.column to it.row })
        assertEquals(3, placements.gridRowCount())
    }
}
