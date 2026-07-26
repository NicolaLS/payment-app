package xyz.lilsus.blip.domain.usecases

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.blip.domain.lnurl.LightningAddress
import xyz.lilsus.blip.domain.model.BlinkContact
import xyz.lilsus.blip.domain.model.Contact
import xyz.lilsus.blip.domain.model.ContactPaymentRecord
import xyz.lilsus.blip.domain.model.ContactPreferences
import xyz.lilsus.blip.domain.model.ContactRole
import xyz.lilsus.blip.domain.model.PaymentShortcut
import xyz.lilsus.blip.domain.model.ShortcutAmount
import xyz.lilsus.blip.domain.repository.BlinkWalletAccountRepository
import xyz.lilsus.blip.domain.repository.ContactsRepository

class ObserveContactsUseCase(private val repository: ContactsRepository) {
    operator fun invoke(): Flow<List<Contact>> = repository.contacts
}

class GetContactsUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(): List<Contact> = repository.getContacts()
}

class ObserveShortcutsUseCase(private val repository: ContactsRepository) {
    operator fun invoke(): Flow<List<PaymentShortcut>> = repository.shortcuts
}

class ObserveContactPreferencesUseCase(private val repository: ContactsRepository) {
    operator fun invoke(): Flow<ContactPreferences> = repository.preferences
}

class SaveContactUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(
        address: LightningAddress,
        alias: String?,
        roles: Set<ContactRole>
    ): Contact = repository.saveContact(address, alias, roles)
}

class UpdateContactUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(id: String, alias: String?, roles: Set<ContactRole>): Contact? =
        repository.updateContact(id, alias, roles)
}

class DeleteContactUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(id: String) = repository.deleteContact(id)
}

class SaveShortcutUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(
        id: String?,
        title: String,
        contactId: String,
        amount: ShortcutAmount,
        comment: String?
    ): PaymentShortcut? = repository.saveShortcut(id, title, contactId, amount, comment)
}

class DeleteShortcutUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(id: String) = repository.deleteShortcut(id)
}

class RecordShortcutPaymentUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(id: String, paidAtMs: Long) =
        repository.recordShortcutPayment(id, paidAtMs)
}

class RecordContactPaymentUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(record: ContactPaymentRecord) = repository.recordPayment(record)
}

class SetAskToSaveContactsUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(enabled: Boolean) = repository.setAskToSaveNewContacts(enabled)
}

class FetchBlinkContactsUseCase(private val repository: BlinkWalletAccountRepository) {
    suspend operator fun invoke(): List<BlinkContact> = repository.fetchContacts()
}
