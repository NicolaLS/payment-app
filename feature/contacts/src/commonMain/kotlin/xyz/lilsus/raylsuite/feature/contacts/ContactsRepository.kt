package xyz.lilsus.raylsuite.feature.contacts

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.raylsuite.core.model.Contact
import xyz.lilsus.raylsuite.core.model.ContactPaymentRecord
import xyz.lilsus.raylsuite.core.model.ContactPreferences
import xyz.lilsus.raylsuite.core.model.ContactRole
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.PaymentShortcut
import xyz.lilsus.raylsuite.core.model.ShortcutAmount

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
