package xyz.lilsus.papp.domain.repository

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.Contact
import xyz.lilsus.papp.domain.model.ContactPaymentRecord
import xyz.lilsus.papp.domain.model.ContactPreferences
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.domain.model.PaymentShortcut
import xyz.lilsus.papp.domain.model.ShortcutAmount

interface ContactsRepository {
    val contacts: Flow<List<Contact>>
    val shortcuts: Flow<List<PaymentShortcut>>
    val preferences: Flow<ContactPreferences>

    suspend fun getContacts(): List<Contact>
    suspend fun getShortcuts(): List<PaymentShortcut>
    suspend fun findByAddress(address: LightningAddress): Contact?
    suspend fun saveContact(
        address: LightningAddress,
        alias: String?,
        roles: Set<ContactRole>
    ): Contact

    suspend fun updateContact(id: String, alias: String?, roles: Set<ContactRole>): Contact?
    suspend fun deleteContact(id: String)

    suspend fun saveShortcut(
        id: String?,
        title: String,
        contactId: String,
        amount: ShortcutAmount,
        comment: String?
    ): PaymentShortcut?

    suspend fun deleteShortcut(id: String)
    suspend fun recordShortcutPayment(id: String, paidAtMs: Long)
    suspend fun recordPayment(record: ContactPaymentRecord)
    suspend fun setAskToSaveNewContacts(enabled: Boolean)
}
