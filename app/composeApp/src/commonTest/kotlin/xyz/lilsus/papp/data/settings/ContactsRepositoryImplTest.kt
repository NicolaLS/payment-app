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

class ContactsRepositoryImplTest {
    @Test
    fun saveContactPreservesPayableAddressAndUpdatesDuplicate() = runTest {
        val repository = ContactsRepositoryImpl(MapSettings())

        repository.saveContact(
            address = LightningAddress("Friend", "Blink.SV", "Tips"),
            alias = "First",
            role = ContactRole.Friend
        )
        repository.saveContact(
            address = LightningAddress("Friend", "blink.sv", "Tips"),
            alias = "Updated",
            role = ContactRole.Work
        )

        val contacts = repository.getContacts()
        assertEquals(1, contacts.size)
        assertEquals("Friend+Tips@blink.sv", contacts.first().address.full)
        assertEquals("Updated", contacts.first().alias)
        assertEquals(ContactRole.Work, contacts.first().role)
    }

    @Test
    fun saveShortcutUsesSelectedContactAddress() = runTest {
        val repository = ContactsRepositoryImpl(MapSettings())
        val contact = repository.saveContact(
            address = LightningAddress("waiter", "blink.sv"),
            alias = "Waiter",
            role = ContactRole.Waiter
        )

        val shortcut = repository.saveShortcut(
            id = null,
            title = "",
            contactId = contact.id,
            amountMsats = 1_000_000,
            comment = "thanks"
        )

        assertEquals("Pay Waiter", shortcut?.title)
        assertEquals(contact.id, shortcut?.contactId)
        assertEquals("waiter@blink.sv", shortcut?.address?.full)
        assertEquals(1_000_000, shortcut?.amountMsats)
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
        val address = LightningAddress("waiter", "blink.sv")
        repository.saveContact(address, "Waiter", ContactRole.Waiter)

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
        val address = LightningAddress("waiter", "blink.sv")
        val contact = repository.saveContact(address, "Waiter", ContactRole.Waiter)
        repository.saveShortcut(
            id = null,
            title = "Tip waiter",
            contactId = contact.id,
            amountMsats = 1_000_000,
            comment = null
        )

        repository.deleteContact(contact.id)

        assertEquals(emptyList(), repository.getContacts())
        assertEquals(emptyList(), repository.getShortcuts())
        assertNull(repository.findByAddress(address))
    }
}
