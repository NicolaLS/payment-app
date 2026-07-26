package xyz.lilsus.papp.domain.usecases

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.BlinkContact
import xyz.lilsus.papp.domain.model.Contact
import xyz.lilsus.papp.domain.model.ContactPaymentRecord
import xyz.lilsus.papp.domain.model.ContactPreferences
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.domain.model.PaymentShortcut
import xyz.lilsus.papp.domain.model.ShortcutAmount
import xyz.lilsus.papp.domain.repository.BlinkWalletAccountRepository
import xyz.lilsus.papp.domain.repository.ContactsRepository

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
