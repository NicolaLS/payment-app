package xyz.lilsus.blip.feature.blinkcontacts

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.feature.paymenthub.DefaultPaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.DirectTargetAmountRule
import xyz.lilsus.raylsuite.feature.paymenthub.DirectTargetDraft

class BlinkContactsRepositoryTest {
    @Test
    fun importingAndRemovingAHubTargetDoesNotMutateContacts() = runTest {
        val settings = MapSettings()
        val contacts =
            BlinkContactsRepository(
                settings = settings,
                clock = { 1_000L },
                idGenerator = { "contact-1" }
            )
        val hub =
            DefaultPaymentHubRepository(
                settings = settings,
                clock = { 2_000L },
                idGenerator = { "target-1" }
            )
        val address = assertNotNull(LightningAddress.parse("alice@example.com"))

        val contact = contacts.saveContact(address, "Alice")

        assertTrue(hub.hub.value.isEmpty)
        val target =
            assertNotNull(
                hub.createTarget(
                    DirectTargetDraft(
                        title = contact.displayName,
                        address = contact.address,
                        amountRule = DirectTargetAmountRule.AskEveryTime
                    )
                )
            )

        hub.deleteTarget(target.id)

        assertTrue(hub.hub.value.isEmpty)
        assertEquals(listOf(contact), contacts.contacts.value)
        assertEquals(listOf(contact), BlinkContactsRepository(settings).contacts.value)
    }
}
