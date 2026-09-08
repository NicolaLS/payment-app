package xyz.lilsus.raylsuite.feature.paymenthub

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetHubViewModelTest {
    @Test
    fun galleryContactCreationVariantValidationAndSuccessfulUseDriveComputedWidgets() = runTest {
        val repo = DefaultPaymentHubRepository(MapSettings())
        val host = PaymentHubController(repo, this)
        val vm = WidgetHubViewModel(repo, host, { "USD" }, dispatcher = StandardTestDispatcher(testScheduler))
        vm.openGallery()
        vm.selectDefinition("local.contacts")
        vm.selectVariant("row")
        vm.configureSelected()
        vm.addContact("Alice", "alice@example.com")
        advanceUntilIdle()
        val alice = repo.hub.value.contacts.single()
        val bob = repo.saveContact(assertNotNull(LightningAddress.parse("bob@example.com")), "Bob")
        advanceUntilIdle()
        vm.toggleContact(bob.id)
        vm.selectVariant("single")
        assertEquals(listOf(alice.id), vm.state.value.editor?.contactIds)
        vm.selectVariant("row")
        vm.toggleContact(bob.id)
        vm.saveWidget()
        vm.saveWidget()
        advanceUntilIdle()
        assertEquals(1, repo.hub.value.widgets.size)
        assertEquals(HubWidgetScreen.Hub, vm.state.value.screen)
        for (id in listOf("local.favorites", "local.recents")) {
            vm.selectDefinition(id)
            vm.configureSelected()
            vm.saveWidget()
            advanceUntilIdle()
        }
        val aliceAction = assertNotNull(repo.hub.value.contactTarget(alice.id)).id
        val bobAction = assertNotNull(repo.hub.value.contactTarget(bob.id)).id
        repo.recordSuccessfulPayment(aliceAction, 10)
        repo.recordSuccessfulPayment(aliceAction, 20)
        repo.recordSuccessfulPayment(bobAction, 30)
        advanceUntilIdle()
        assertEquals(alice.id, vm.state.value.widgets.first { it.kind == HubWidgetKind.Favorites }.people.first().contactId)
        assertEquals(bob.id, vm.state.value.widgets.first { it.kind == HubWidgetKind.Recents }.people.first().contactId)
        vm.editWidget(repo.hub.value.widgets.first { it.kind == HubWidgetKind.Contacts }.id)
        vm.deleteContact(alice.id)
        advanceUntilIdle()
        assertEquals(listOf(bob.id), vm.state.value.editor?.contactIds)
        vm.deleteContact(bob.id)
        advanceUntilIdle()
        assertEquals(HubWidgetScreen.Hub, vm.state.value.screen)
        vm.clear()
    }

    @Test
    fun contactSelectionReplacesSingleAndEnforcesEachLayoutCapacity() = runTest {
        val repo = DefaultPaymentHubRepository(MapSettings())
        val host = PaymentHubController(repo, this)
        val vm = WidgetHubViewModel(repo, host, { "USD" }, dispatcher = StandardTestDispatcher(testScheduler))
        val contacts = (1..7).map {
            repo.saveContact(assertNotNull(LightningAddress.parse("person$it@example.com")), "Person $it")
        }
        advanceUntilIdle()
        vm.selectDefinition("local.contacts")
        vm.toggleContact(contacts[0].id)
        vm.toggleContact(contacts[1].id)
        assertEquals(listOf(contacts[1].id), vm.state.value.editor?.contactIds)
        vm.addContact("New", "new@example.com")
        advanceUntilIdle()
        assertEquals(1, vm.state.value.editor?.contactIds?.size)
        for (variant in listOf(LocalHubWidgets.Row, LocalHubWidgets.Card)) {
            vm.selectDefinition("local.contacts")
            vm.selectVariant(variant.id)
            contacts.take(variant.capacity).forEach { vm.toggleContact(it.id) }
            vm.toggleContact(contacts[variant.capacity].id)
            assertEquals(variant.capacity, vm.state.value.editor?.contactIds?.size)
            assertEquals(HubWidgetError.TooManyContacts, vm.state.value.error)
            vm.toggleContact(contacts[0].id)
            vm.toggleContact(contacts[variant.capacity].id)
            assertEquals(variant.capacity, vm.state.value.editor?.contactIds?.size)
            assertNull(vm.state.value.editorValidationError())
        }
        vm.clear()
    }

    @Test
    fun shortcutRequiresOneContactAndPositivePresetThenSaves() = runTest {
        val repo = DefaultPaymentHubRepository(MapSettings())
        val host = PaymentHubController(repo, this)
        val vm = WidgetHubViewModel(repo, host, { "USD" }, dispatcher = StandardTestDispatcher(testScheduler))
        val alice = repo.saveContact(assertNotNull(LightningAddress.parse("alice@example.com")), "Alice")
        val bob = repo.saveContact(assertNotNull(LightningAddress.parse("bob@example.com")), "Bob")
        advanceUntilIdle()
        vm.selectDefinition("local.shortcut")
        vm.configureSelected()
        assertEquals(HubWidgetError.SelectContacts, vm.state.value.editorValidationError())
        vm.toggleContact(alice.id)
        vm.toggleContact(bob.id)
        assertEquals(listOf(bob.id), vm.state.value.editor?.contactIds)
        assertEquals(HubWidgetError.InvalidAmount, vm.state.value.editorValidationError())
        vm.updateAmount("0")
        assertEquals(HubWidgetError.InvalidAmount, vm.state.value.editorValidationError())
        vm.updateAmount("12.50")
        assertNull(vm.state.value.editorValidationError())
        vm.saveWidget()
        advanceUntilIdle()
        assertEquals(HubWidgetScreen.Hub, vm.state.value.screen)
        assertEquals(HubWidgetKind.Shortcut, repo.hub.value.widgets.single().kind)
        assertEquals(1250L, vm.state.value.widgets.single().people.single().amount?.minor)
        vm.clear()
    }
}
