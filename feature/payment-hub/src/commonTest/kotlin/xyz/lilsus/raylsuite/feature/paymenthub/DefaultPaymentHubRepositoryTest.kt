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
    @Test
    fun contactDeletionPrunesCollectionsWhileRemovingWidgetsPreservesContactsAndHistory() = runTest {
        val settings = MapSettings()
        val repository = DefaultPaymentHubRepository(settings)
        val alice = repository.saveContact(address("alice"), "Alice")
        val bob = repository.saveContact(address("bob"), "Bob")
        val row = assertNotNull(
            repository.saveWidget(
                HubWidgetDraft(
                    "local.contacts",
                    HubWidgetKind.Contacts,
                    LocalHubWidgets.Row,
                    contactIds = listOf(alice.id, bob.id)
                )
            )
        )
        val single = assertNotNull(
            repository.saveWidget(
                HubWidgetDraft(
                    "local.contacts",
                    HubWidgetKind.Contacts,
                    LocalHubWidgets.Single,
                    contactIds = listOf(alice.id)
                )
            )
        )
        val shortcut = assertNotNull(
            repository.saveWidget(
                HubWidgetDraft(
                    "local.shortcut",
                    HubWidgetKind.Shortcut,
                    LocalHubWidgets.Single,
                    contactIds = listOf(alice.id),
                    amount = StoredAmount(500, "USD")
                )
            )
        )
        val bobAction = assertNotNull(repository.hub.value.contactTarget(bob.id))
        repository.recordSuccessfulPayment(bobAction.id, 100)
        repository.deleteContact(alice.id)
        val hub = repository.hub.value
        assertNull(hub.widget(single.id))
        assertNull(hub.widget(shortcut.id))
        assertEquals(listOf(bob.id), hub.widget(row.id)?.contactIds)
        repository.deleteWidget(row.id)
        assertEquals(listOf(bob), repository.hub.value.contacts)
        assertEquals(1, repository.hub.value.targets.size)
        assertEquals(1L, repository.hub.value.targets.single().stats.successfulPaymentCount)
        assertEquals(repository.hub.value, DefaultPaymentHubRepository(settings).hub.value)
    }

    @Test
    fun duplicatePlacementsShareActionIdentityAndInvalidEditsDoNotWrite() = runTest {
        val repository = DefaultPaymentHubRepository(MapSettings())
        val contact = repository.saveContact(address("alice"), "Alice")
        val draft = HubWidgetDraft(
            "local.shortcut",
            HubWidgetKind.Shortcut,
            LocalHubWidgets.Single,
            contactIds = listOf(contact.id),
            amount = StoredAmount(500, "USD")
        )
        val first = assertNotNull(repository.saveWidget(draft))
        val second = assertNotNull(repository.saveWidget(draft))
        assertEquals(first.targetId, second.targetId)
        val before = repository.hub.value
        assertNull(repository.saveWidget(draft.copy(variant = LocalHubWidgets.Row)))
        assertNull(repository.saveWidget(draft.copy(amount = StoredAmount(0, "USD"))))
        assertNull(repository.saveWidget(draft, "missing"))
        assertEquals(before, repository.hub.value)
        repository.moveWidget(second.id, 0)
        assertEquals(listOf(second.id, first.id), repository.hub.value.widgets.map { it.id })
    }

    @Test
    fun suggestionSavesBothWithoutOverwritingExistingContactNameOrDuplicatingItsWidget() = runTest {
        val settings = MapSettings()
        val repository = DefaultPaymentHubRepository(settings)
        val contact = repository.saveContact(address("alice"), "My friend")
        repository.saveContactAndWidget(address("alice"), "Alice")
        repository.saveContactAndWidget(address("alice"), "Alice")
        val hub = DefaultPaymentHubRepository(settings).hub.value
        assertEquals(contact, hub.contacts.single())
        assertEquals(listOf(contact.id), hub.widgets.single().contactIds)
        assertEquals(DirectTargetAmountRule.AskEveryTime, hub.targets.single().amountRule)
        repository.deleteContact(contact.id)
        assertTrue(repository.hub.value.widgets.isEmpty())
    }

    private fun address(name: String) = assertNotNull(LightningAddress.parse("$name@example.com"))
}
