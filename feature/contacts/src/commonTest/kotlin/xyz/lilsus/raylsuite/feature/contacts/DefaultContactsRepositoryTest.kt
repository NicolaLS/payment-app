package xyz.lilsus.raylsuite.feature.contacts

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.ContactRole
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.ShortcutAmount

class DefaultContactsRepositoryTest {
    @Test
    fun contactAndShortcutRoundTripThroughNewStore() = runTest {
        val settings = MapSettings()
        val writer =
            DefaultContactsRepository(
                settings = settings,
                clock = { 100L },
                idGenerator = sequenceOf("contact-1", "shortcut-1").iterator()::next
            )
        val contact =
            writer.saveContact(
                address = LightningAddress("Alice", "EXAMPLE.COM"),
                alias = "Alice",
                roles = setOf(ContactRole.People)
            )
        writer.saveShortcut(
            id = null,
            title = "Pay Alice",
            contactId = contact.id,
            amount = ShortcutAmount(1_000, "sat"),
            comment = "Thanks"
        )

        val reader = DefaultContactsRepository(settings)

        assertEquals("Alice@example.com", reader.getContacts().single().address.full)
        assertEquals(
            ShortcutAmount(1_000, "SAT"),
            reader.getShortcuts().single().amount
        )
    }
}
