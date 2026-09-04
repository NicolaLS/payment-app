package xyz.lilsus.raylsuite.feature.paymenthub.canvas

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId

class CanvasLayoutTest {
    private val alice = HubItemId("target:alice")
    private val bob = HubItemId("target:bob")
    private val carol = HubItemId("target:carol")

    @Test
    fun placeMoveResizeAndRemoveAffectOnlyPlacement() {
        val layout =
            CanvasLayout.Empty
                .place(alice)
                .place(bob, CanvasTileSize.Wide)
        assertEquals(
            listOf(
                CanvasTile(alice, CanvasTileSize.Small),
                CanvasTile(bob, CanvasTileSize.Wide)
            ),
            layout.tiles
        )

        val moved = layout.moveTo(bob, index = 0).resize(alice, CanvasTileSize.Large)
        assertEquals(
            listOf(CanvasTile(bob, CanvasTileSize.Wide), CanvasTile(alice, CanvasTileSize.Large)),
            moved.tiles
        )
        assertEquals(moved, moved.moveTo(bob, index = 0))
        assertEquals(listOf(CanvasTile(bob, CanvasTileSize.Wide)), moved.remove(alice).tiles)
    }

    @Test
    fun coveringDropsMissingItemsAndAppendsNewOnes() {
        val layout =
            CanvasLayout(
                tiles =
                    listOf(
                        CanvasTile(bob, CanvasTileSize.Wide),
                        CanvasTile(HubItemId("target:gone"), CanvasTileSize.Small)
                    )
            )
        assertEquals(
            listOf(
                CanvasTile(bob, CanvasTileSize.Wide),
                CanvasTile(alice, CanvasTileSize.Small),
                CanvasTile(carol, CanvasTileSize.Small)
            ),
            layout.covering(listOf(alice, bob, carol)).tiles
        )
    }

    @Test
    fun normalizedDropsDuplicatesAndDanglingIds() {
        val layout =
            CanvasLayout(
                tiles =
                    listOf(
                        CanvasTile(alice, CanvasTileSize.Small),
                        CanvasTile(HubItemId("target:gone"), CanvasTileSize.Wide),
                        CanvasTile(alice, CanvasTileSize.Wide)
                    )
            )
        assertEquals(
            listOf(CanvasTile(alice, CanvasTileSize.Small)),
            layout.normalized(existingIds = setOf(alice)).tiles
        )
    }

    @Test
    fun repositoryPersistsTypedPlacementAndResets() = runTest {
        val settings = MapSettings()
        val repository = DefaultCanvasLayoutRepository(settings)
        repository.update {
            it.place(bob).place(alice, CanvasTileSize.Large).moveTo(alice, index = 0)
        }

        val reloaded = DefaultCanvasLayoutRepository(settings)
        assertEquals(
            listOf(
                CanvasTile(alice, CanvasTileSize.Large),
                CanvasTile(bob, CanvasTileSize.Small)
            ),
            reloaded.layout.value.tiles
        )

        reloaded.reset()
        assertEquals(CanvasLayout.Empty, reloaded.layout.value)
        assertEquals(CanvasLayout.Empty, DefaultCanvasLayoutRepository(settings).layout.value)
    }
}
