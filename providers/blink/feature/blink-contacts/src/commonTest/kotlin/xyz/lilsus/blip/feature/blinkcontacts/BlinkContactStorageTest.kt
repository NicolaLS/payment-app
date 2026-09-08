package xyz.lilsus.blip.feature.blinkcontacts

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.feature.paymenthub.DefaultPaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.HubWidgetDraft
import xyz.lilsus.raylsuite.feature.paymenthub.HubWidgetKind
import xyz.lilsus.raylsuite.feature.paymenthub.LocalHubWidgets

class BlinkContactStorageTest {
    @Test
    fun importingAndRemovingAWidgetDoesNotMutateContacts() = runTest {
        val settings = MapSettings()
        val hub = DefaultPaymentHubRepository(settings)
        val contact = hub.saveContact(assertNotNull(LightningAddress.parse("alice@example.com")), "Alice")
        assertTrue(hub.hub.value.widgets.isEmpty())
        assertTrue(hub.hub.value.targets.isEmpty())
        val widget = assertNotNull(
            hub.saveWidget(
                HubWidgetDraft(
                    "local.contacts",
                    HubWidgetKind.Contacts,
                    LocalHubWidgets.Single,
                    contactIds = listOf(contact.id)
                )
            )
        )
        hub.deleteWidget(widget.id)
        assertEquals(listOf(contact), DefaultPaymentHubRepository(settings).hub.value.contacts)
        assertTrue(hub.hub.value.widgets.isEmpty())
    }
}
