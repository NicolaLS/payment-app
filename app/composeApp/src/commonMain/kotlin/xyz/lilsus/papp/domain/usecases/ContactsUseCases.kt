package xyz.lilsus.papp.domain.usecases

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.Contact
import xyz.lilsus.papp.domain.model.ContactPaymentRecord
import xyz.lilsus.papp.domain.model.ContactPreferences
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.domain.model.PaymentShortcut
import xyz.lilsus.papp.domain.repository.ContactsRepository

class ObserveContactsUseCase(private val repository: ContactsRepository) {
    operator fun invoke(): Flow<List<Contact>> = repository.contacts
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
        role: ContactRole?
    ): Contact = repository.saveContact(address, alias, role)
}

class UpdateContactUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(id: String, alias: String?, role: ContactRole?): Contact? =
        repository.updateContact(id, alias, role)
}

class DeleteContactUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(id: String) = repository.deleteContact(id)
}

class SaveShortcutUseCase(private val repository: ContactsRepository) {
    suspend operator fun invoke(
        id: String?,
        title: String,
        contactId: String,
        amountMsats: Long,
        comment: String?
    ): PaymentShortcut? = repository.saveShortcut(id, title, contactId, amountMsats, comment)
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
