package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.ContactPaymentRecord
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.domain.model.ShortcutAmount

class ContactsRepositoryImplTest {
    @Test
    fun saveContactPreservesPayableAddressAndUpdatesDuplicate() = runTest {
        val repository = ContactsRepositoryImpl(MapSettings())

        repository.saveContact(
            address = LightningAddress("Person", "Blink.SV", "Tips"),
            alias = "First",
            roles = setOf(ContactRole.People)
        )
        repository.saveContact(
            address = LightningAddress("Person", "blink.sv", "Tips"),
            alias = "Updated",
            roles = setOf(ContactRole.Work, ContactRole.Favorite)
        )

        val contacts = repository.getContacts()
        assertEquals(1, contacts.size)
        assertEquals("Person+Tips@blink.sv", contacts.first().address.full)
        assertEquals("Updated", contacts.first().alias)
        assertEquals(setOf(ContactRole.Favorite, ContactRole.Work), contacts.first().roles)
    }

    @Test
    fun saveShortcutUsesSelectedContactAddress() = runTest {
        val repository = ContactsRepositoryImpl(MapSettings())
        val contact = repository.saveContact(
            address = LightningAddress("cafe", "blink.sv"),
            alias = "Cafe",
            roles = setOf(ContactRole.Merchants)
        )

        val shortcut = repository.saveShortcut(
            id = null,
            title = "",
            contactId = contact.id,
            amount = ShortcutAmount(1_000, "SAT"),
            comment = "thanks"
        )

        assertEquals("Pay Cafe", shortcut?.title)
        assertEquals(contact.id, shortcut?.contactId)
        assertEquals("cafe@blink.sv", shortcut?.address?.full)
        assertEquals(ShortcutAmount(1_000, "SAT"), shortcut?.amount)
        assertEquals("thanks", shortcut?.comment)
        assertEquals(listOf(shortcut), repository.getShortcuts())
    }

    @Test
    fun preferencesPersist() = runTest {
        val settings = MapSettings()
        val writer = ContactsRepositoryImpl(settings)
        writer.setAskToSaveNewContacts(false)

        val reader = ContactsRepositoryImpl(settings)

        assertEquals(false, reader.preferences.first().askToSaveNewContacts)
    }

    @Test
    fun recordPaymentUpdatesKnownContactStats() = runTest {
        val repository = ContactsRepositoryImpl(MapSettings())
        val address = LightningAddress("cafe", "blink.sv")
        repository.saveContact(address, "Cafe", setOf(ContactRole.Merchants))

        repository.recordPayment(
            ContactPaymentRecord(
                address = address,
                amountMsats = 1_000_000,
                comment = "thanks",
                paidAtMs = 3_000L
            )
        )

        val contact = repository.getContacts().first()
        assertEquals(1, contact.stats.paymentCount)
        assertEquals(3_000L, contact.stats.lastPaidAtMs)
    }

    @Test
    fun deleteContactRemovesContactAndItsShortcuts() = runTest {
        val repository = ContactsRepositoryImpl(MapSettings())
        val address = LightningAddress("cafe", "blink.sv")
        val contact = repository.saveContact(address, "Cafe", setOf(ContactRole.Merchants))
        repository.saveShortcut(
            id = null,
            title = "Tip cafe",
            contactId = contact.id,
            amount = ShortcutAmount(1_000, "SAT"),
            comment = null
        )

        repository.deleteContact(contact.id)

        assertEquals(emptyList(), repository.getContacts())
        assertEquals(emptyList(), repository.getShortcuts())
        assertNull(repository.findByAddress(address))
    }
}
