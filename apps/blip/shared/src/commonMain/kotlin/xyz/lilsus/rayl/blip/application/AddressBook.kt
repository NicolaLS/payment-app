package xyz.lilsus.rayl.blip.application

import xyz.lilsus.rayl.blip.data.BlipStore
import xyz.lilsus.rayl.blip.data.blink.BlinkGateway
import xyz.lilsus.rayl.blip.domain.AppClock
import xyz.lilsus.rayl.blip.domain.BlinkContactCandidate
import xyz.lilsus.rayl.blip.domain.Contact
import xyz.lilsus.rayl.blip.domain.ContactId
import xyz.lilsus.rayl.blip.domain.ContactSource
import xyz.lilsus.rayl.blip.domain.CurrencyCode
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

    fun addContact(name: String, lightningAddress: String): Contact? =
        saveContact(existing = null, name = name, lightningAddress = lightningAddress)

    fun updateContact(id: ContactId, name: String, lightningAddress: String): Contact? {
        val existing = store.contacts().firstOrNull { it.id == id } ?: return null
        return saveContact(existing, name, lightningAddress)
    }

    private fun saveContact(existing: Contact?, name: String, lightningAddress: String): Contact? {
        val normalizedName = name.trim()
        val normalizedAddress = lightningAddress.trim().lowercase()
        val duplicate = store.contactByAddress(normalizedAddress)
        if (
            normalizedName.length !in 1..80 ||
            normalizedAddress.length !in 3..320 ||
            !isLightningAddress(normalizedAddress) ||
            duplicate?.id != existing?.id
        ) {
            return null
        }
        val contact = Contact(
            id = existing?.id ?: identifiers.newContactId(),
            name = normalizedName,
            lightningAddress = normalizedAddress,
            source = existing?.source ?: ContactSource.Local,
            createdAtMillis = existing?.createdAtMillis ?: clock.nowMillis()
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
        amountValue: String?,
        currency: CurrencyCode?,
        contactId: ContactId? = null
    ): PaymentShortcut? = saveShortcut(
        existing = null,
        label = label,
        lightningAddress = lightningAddress,
        amountValue = amountValue,
        currency = currency,
        contactId = contactId
    )

    fun updateShortcut(
        id: ShortcutId,
        label: String,
        lightningAddress: String,
        amountValue: String?,
        currency: CurrencyCode?,
        contactId: ContactId? = null
    ): PaymentShortcut? {
        val existing = store.shortcuts().firstOrNull { it.id == id } ?: return null
        return saveShortcut(
            existing = existing,
            label = label,
            lightningAddress = lightningAddress,
            amountValue = amountValue,
            currency = currency,
            contactId = contactId
        )
    }

    private fun saveShortcut(
        existing: PaymentShortcut?,
        label: String,
        lightningAddress: String,
        amountValue: String?,
        currency: CurrencyCode?,
        contactId: ContactId?
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
        val storedAmount = amountValue
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { value ->
                val selectedCurrency = currency ?: return null
                encodeShortcutAmount(value, selectedCurrency) ?: return null
            }
        val shortcut = PaymentShortcut(
            id = existing?.id ?: identifiers.newShortcutId(),
            contactId = contactId,
            label = normalizedLabel,
            lightningAddress = normalizedAddress,
            amount = storedAmount,
            currencyCode = storedAmount?.let { requireNotNull(currency).value },
            createdAtMillis = existing?.createdAtMillis ?: clock.nowMillis()
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

    suspend fun blinkContactCandidates(): List<BlinkContactCandidate> {
        val connection = store.currentConnection() ?: return emptyList()
        return gateway.contactCandidates(connection)
    }

    suspend fun importBlinkContacts(selectedAddresses: Set<String>): List<Contact> {
        val connection = store.currentConnection() ?: return emptyList()
        return gateway.importContacts(connection, selectedAddresses)
    }
}

private fun isLightningAddress(value: String): Boolean = value.count { it == '@' } == 1 &&
    value.substringBefore('@').isNotBlank() &&
    value.substringAfter('@').isNotBlank() &&
    value.none(Char::isWhitespace)

private fun encodeShortcutAmount(value: String, currency: CurrencyCode): Long? {
    val scale = when (currency) {
        CurrencyCode.Sat -> 0
        CurrencyCode.Btc -> 11
        else -> 6
    }
    val parts = value.split('.', limit = 2)
    if (
        value.isBlank() ||
        value.startsWith('-') ||
        value.startsWith('+') ||
        parts.size > 2 ||
        parts[0].any { !it.isDigit() }
    ) {
        return null
    }
    val fraction = parts.getOrElse(1) { "" }
    if (fraction.any { !it.isDigit() } || fraction.length > scale) return null
    val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    var factor = 1L
    repeat(scale) {
        if (factor > Long.MAX_VALUE / 10L) return null
        factor *= 10L
    }
    if (whole > Long.MAX_VALUE / factor) return null
    val fractionValue = fraction.padEnd(scale, '0').ifEmpty { "0" }.toLongOrNull() ?: return null
    val encoded = whole * factor + fractionValue
    return encoded.takeIf { it > 0L }
}
