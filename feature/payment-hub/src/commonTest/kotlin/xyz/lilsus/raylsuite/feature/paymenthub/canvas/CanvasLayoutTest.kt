package xyz.lilsus.raylsuite.feature.paymenthub.canvas

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId

class CanvasLayoutTest {
    private val alice = HubItemId("target:alice")
    private val bob = HubItemId("target:bob")

    @Test
    fun placeMoveResizeAndRemoveAffectOnlyPlacement() {
        val layout =
            CanvasLayout.Empty
                .place(alice)
                .place(bob, CanvasTileSize.Wide)
                .place(alice)
        assertEquals(
            listOf(
                CanvasTile(alice, CanvasTileSize.Compact),
                CanvasTile(bob, CanvasTileSize.Wide)
            ),
            layout.tiles
        )

        val moved = layout.move(index = 1, offset = -1).resize(alice, CanvasTileSize.Wide)
        assertEquals(
            listOf(CanvasTile(bob, CanvasTileSize.Wide), CanvasTile(alice, CanvasTileSize.Wide)),
            moved.tiles
        )
        assertEquals(moved, moved.move(index = 0, offset = -1))
        assertEquals(listOf(CanvasTile(bob, CanvasTileSize.Wide)), moved.remove(alice).tiles)
    }

    @Test
    fun normalizedDropsDuplicatesAndDanglingIds() {
        val layout =
            CanvasLayout(
                tiles =
                    listOf(
                        CanvasTile(alice, CanvasTileSize.Compact),
                        CanvasTile(HubItemId("target:gone"), CanvasTileSize.Wide),
                        CanvasTile(alice, CanvasTileSize.Wide)
                    )
            )
        assertEquals(
            listOf(CanvasTile(alice, CanvasTileSize.Compact)),
            layout.normalized(existingIds = setOf(alice)).tiles
        )
    }

    @Test
    fun repositoryPersistsTypedPlacementAndResets() = runTest {
        val settings = MapSettings()
        val repository = DefaultCanvasLayoutRepository(settings)
        repository.update { it.place(bob).place(alice, CanvasTileSize.Wide).move(index = 1, offset = -1) }

        val reloaded = DefaultCanvasLayoutRepository(settings)
        assertEquals(
            listOf(CanvasTile(alice, CanvasTileSize.Wide), CanvasTile(bob, CanvasTileSize.Compact)),
            reloaded.layout.value.tiles
        )

        reloaded.reset()
        assertEquals(CanvasLayout.Empty, reloaded.layout.value)
        assertEquals(CanvasLayout.Empty, DefaultCanvasLayoutRepository(settings).layout.value)
    }
}
