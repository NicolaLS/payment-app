package xyz.lilsus.raylsuite.feature.paymenthub

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
        vm.saveWidget()
        assertEquals(HubWidgetError.TooManyContacts, vm.state.value.error)
        assertTrue(repo.hub.value.widgets.isEmpty())
        vm.selectVariant("row")
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
}
