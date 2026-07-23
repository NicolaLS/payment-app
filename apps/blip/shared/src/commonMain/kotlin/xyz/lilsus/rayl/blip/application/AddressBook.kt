package xyz.lilsus.rayl.blip.application

import xyz.lilsus.rayl.blip.data.BlipStore
import xyz.lilsus.rayl.blip.data.blink.BlinkGateway
import xyz.lilsus.rayl.blip.domain.AppClock
import xyz.lilsus.rayl.blip.domain.Contact
import xyz.lilsus.rayl.blip.domain.ContactId
import xyz.lilsus.rayl.blip.domain.ContactSource
import xyz.lilsus.rayl.blip.domain.IdentifierSource
import xyz.lilsus.rayl.blip.domain.PaymentShortcut
import xyz.lilsus.rayl.blip.domain.ShortcutId

class AddressBook(
    private val store: BlipStore,
    private val gateway: BlinkGateway,
    private val identifiers: IdentifierSource,
    private val clock: AppClock
) {
    fun contacts(): List<Contact> = store.contacts()

    fun shortcuts(): List<PaymentShortcut> = store.shortcuts()

    fun addContact(name: String, lightningAddress: String): Contact? {
        val normalizedName = name.trim()
        val normalizedAddress = lightningAddress.trim().lowercase()
        if (
            normalizedName.length !in 1..80 ||
            normalizedAddress.length !in 3..320 ||
            !isLightningAddress(normalizedAddress) ||
            store.contactByAddress(normalizedAddress) != null
        ) {
            return null
        }
        val contact = Contact(
            id = identifiers.newContactId(),
            name = normalizedName,
            lightningAddress = normalizedAddress,
            source = ContactSource.Local,
            createdAtMillis = clock.nowMillis()
        )
        store.saveContact(contact)
        return contact
    }

    fun deleteContact(id: ContactId) {
        store.deleteContact(id)
    }

    fun addShortcut(
        label: String,
        lightningAddress: String,
        amountMsat: Long?,
        contactId: ContactId? = null
    ): PaymentShortcut? {
        val normalizedLabel = label.trim()
        val normalizedAddress = lightningAddress.trim().lowercase()
        if (
            normalizedLabel.length !in 1..80 ||
            normalizedAddress.length !in 3..320 ||
            !isLightningAddress(normalizedAddress)
        ) {
            return null
        }
        if (amountMsat != null && amountMsat <= 0L) return null
        val shortcut = PaymentShortcut(
            id = identifiers.newShortcutId(),
            contactId = contactId,
            label = normalizedLabel,
            lightningAddress = normalizedAddress,
            amount = amountMsat,
            currencyCode = amountMsat?.let { "MSAT" },
            createdAtMillis = clock.nowMillis()
        )
        store.saveShortcut(shortcut)
        return shortcut
    }

    fun deleteShortcut(id: ShortcutId) {
        store.deleteShortcut(id)
    }

    suspend fun importBlinkContacts(): List<Contact> {
        val connection = store.currentConnection() ?: return emptyList()
        return gateway.importContacts(connection)
    }
}

private fun isLightningAddress(value: String): Boolean = value.count { it == '@' } == 1 &&
    value.substringBefore('@').isNotBlank() &&
    value.substringAfter('@').isNotBlank() &&
    value.none(Char::isWhitespace)
