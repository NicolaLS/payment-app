package xyz.lilsus.raylsuite.feature.paymenthub

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.StoredAmount

class DefaultPaymentHubRepositoryTest {
    private val alice = requireNotNull(LightningAddress.parse("alice@example.com"))

    private fun repository(settings: MapSettings, ids: Iterator<String> = generateSequence(1) { it + 1 }.map { "id$it" }.iterator()) =
        DefaultPaymentHubRepository(
            settings = settings,
            clock = { 1_000L },
            idGenerator = ids::next
        )

    @Test
    fun payAliceTipAliceAndFavoriteFriendsAreIndependent() = runTest {
        val settings = MapSettings()
        val repository = repository(settings)

        val payAlice =
            assertNotNull(
                repository.createTarget(
                    DirectTargetDraft(
                        title = "Pay Alice",
                        address = alice,
                        amountRule = DirectTargetAmountRule.AskEveryTime,
                        pinned = true
                    )
                )
            )
        val tipAlice =
            assertNotNull(
                repository.createTarget(
                    DirectTargetDraft(
                        title = "Tip Alice",
                        address = alice,
                        amountRule = DirectTargetAmountRule.Preset(StoredAmount(100, "usd")),
                        comment = "  thanks "
                    )
                )
            )
        val friends =
            assertNotNull(
                repository.createGroup(
                    GroupDraft(
                        title = "Favorite Friends",
                        memberIds = listOf(tipAlice.id, payAlice.id, HubItemId("group:other"), payAlice.id),
                        pinned = true
                    )
                )
            )

        val hub = repository.hub.value
        assertEquals(listOf(payAlice.id, friends.id), hub.pinnedItemIds)
        assertEquals(listOf(tipAlice.id, payAlice.id), assertNotNull(hub.group(friends.id)).memberIds)
        assertEquals("thanks", assertNotNull(hub.target(tipAlice.id)).comment)
        assertEquals(
            DirectTargetAmountRule.Preset(StoredAmount(100, "USD")),
            assertNotNull(hub.target(tipAlice.id)).amountRule
        )

        repository.deleteTarget(payAlice.id)
        val afterDelete = repository.hub.value
        assertNull(afterDelete.target(payAlice.id))
        assertNotNull(afterDelete.target(tipAlice.id))
        assertEquals(listOf(tipAlice.id), assertNotNull(afterDelete.group(friends.id)).memberIds)
        assertEquals(listOf(friends.id), afterDelete.pinnedItemIds)

        repository.deleteGroup(friends.id)
        assertNotNull(repository.hub.value.target(tipAlice.id))
        assertTrue(repository.hub.value.groups.isEmpty())

        val reloaded = repository(settings)
        assertEquals(repository.hub.value, reloaded.hub.value)
    }

    @Test
    fun editingUpdatesPinAndMembershipWithoutChangingIdentity() = runTest {
        val repository = repository(MapSettings())
        val target =
            assertNotNull(
                repository.createTarget(
                    DirectTargetDraft("Alice", alice, DirectTargetAmountRule.AskEveryTime)
                )
            )
        val group = assertNotNull(repository.createGroup(GroupDraft(title = "Friends")))
        repository.recordSuccessfulPayment(target.id, paidAtMs = 5_000L)

        val updated =
            assertNotNull(
                repository.updateTarget(
                    target.id,
                    DirectTargetDraft(
                        title = "Tip Alice",
                        address = alice,
                        amountRule = DirectTargetAmountRule.Preset(StoredAmount(2_000, "SAT")),
                        pinned = true,
                        groupIds = setOf(group.id)
                    )
                )
            )
        assertEquals(target.id, updated.id)
        assertEquals(HubItemStats(1, 5_000L), updated.stats)
        val hub = repository.hub.value
        assertEquals(listOf(target.id), hub.pinnedItemIds)
        assertEquals(listOf(target.id), assertNotNull(hub.group(group.id)).memberIds)

        repository.updateTarget(
            target.id,
            DirectTargetDraft("Alice", alice, DirectTargetAmountRule.AskEveryTime)
        )
        val reverted = repository.hub.value
        assertEquals(DirectTargetAmountRule.AskEveryTime, assertNotNull(reverted.target(target.id)).amountRule)
        assertTrue(reverted.pinnedItemIds.isEmpty())
        assertTrue(assertNotNull(reverted.group(group.id)).memberIds.isEmpty())
        assertEquals(1, reverted.targets.size)
    }

    @Test
    fun reorderPinnedKeepsUnknownAndMissingIdsSafe() = runTest {
        val repository = repository(MapSettings())
        val a = assertNotNull(repository.createTarget(DirectTargetDraft("A", alice, DirectTargetAmountRule.AskEveryTime, pinned = true)))
        val b = assertNotNull(repository.createTarget(DirectTargetDraft("B", alice, DirectTargetAmountRule.AskEveryTime, pinned = true)))
        val c = assertNotNull(repository.createTarget(DirectTargetDraft("C", alice, DirectTargetAmountRule.AskEveryTime, pinned = true)))

        repository.reorderPinned(listOf(c.id, HubItemId("target:missing"), a.id))

        assertEquals(listOf(c.id, a.id, b.id), repository.hub.value.pinnedItemIds)
    }

    @Test
    fun invalidDraftsAreRejected() = runTest {
        val repository = repository(MapSettings())
        assertNull(repository.createTarget(DirectTargetDraft("  ", alice, DirectTargetAmountRule.AskEveryTime)))
        assertNull(
            repository.createTarget(
                DirectTargetDraft("Bad", alice, DirectTargetAmountRule.Preset(StoredAmount(0, "USD")))
            )
        )
        assertNull(
            repository.createTarget(
                DirectTargetDraft("Bad", alice, DirectTargetAmountRule.Preset(StoredAmount(10, "XXX")))
            )
        )
        assertNull(repository.createGroup(GroupDraft(title = "")))
        assertTrue(repository.hub.value.isEmpty)
    }

    @Test
    fun undecodableDocumentStartsEmptyAndIsReplacedOnWrite() = runTest {
        val settings = MapSettings()
        settings.putString("paymentHub.document", "{broken")

        val repository = repository(settings)
        assertTrue(repository.hub.value.isEmpty)
        assertNotNull(repository.createTarget(DirectTargetDraft("Alice", alice, DirectTargetAmountRule.AskEveryTime)))
        assertEquals(1, repository(settings).hub.value.targets.size)
    }
}
