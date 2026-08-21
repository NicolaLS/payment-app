package xyz.lilsus.raylsuite.feature.contacts

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.first
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
        writer.setAskToSaveNewContacts(false)
        writer.saveShortcut(
            id = null,
            title = "Pay Alice",
            contactId = contact.id,
            amount = ShortcutAmount(1_000, "sat"),
            comment = "Thanks"
        )

        val storedDocument = settings.getString("contacts.document", "")
        assertContains(storedDocument, "\"schemaVersion\":1")
        assertContains(storedDocument, "\"roles\":[\"people\"]")

        val reader = DefaultContactsRepository(settings)

        assertEquals("Alice@example.com", reader.getContacts().single().address.full)
        assertEquals(setOf(ContactRole.People), reader.getContacts().single().roles)
        assertEquals(
            ShortcutAmount(1_000, "SAT"),
            reader.getShortcuts().single().amount
        )
        assertEquals("Thanks", reader.getShortcuts().single().comment)
        assertFalse(reader.preferences.first().askToSaveNewContacts)

        writer.deleteContact(contact.id)

        assertFalse(settings.hasKey("contacts.document"))
        assertFalse(DefaultContactsRepository(settings).preferences.first().askToSaveNewContacts)
    }
}
