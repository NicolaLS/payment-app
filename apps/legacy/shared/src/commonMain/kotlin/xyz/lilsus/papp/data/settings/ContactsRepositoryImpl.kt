package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.lilsus.papp.data.exchange.currentTimeMillis
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.Contact
import xyz.lilsus.papp.domain.model.ContactPaymentRecord
import xyz.lilsus.papp.domain.model.ContactPreferences
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.domain.model.ContactStats
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.model.PaymentShortcut
import xyz.lilsus.papp.domain.model.ShortcutAmount
import xyz.lilsus.papp.domain.model.ShortcutStats
import xyz.lilsus.papp.domain.repository.ContactsRepository

private const val KEY_CONTACTS = "contacts.state"

class ContactsRepositoryImpl(private val settings: Settings) : ContactsRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val state = MutableStateFlow(loadState())

    override val contacts: Flow<List<Contact>> = state
        .asStateFlow()
        .map { it.contacts.map { contact -> contact.toDomain() } }
        .distinctUntilChanged()

    override val shortcuts: Flow<List<PaymentShortcut>> = state
        .asStateFlow()
        .map { it.shortcuts.mapNotNull { shortcut -> shortcut.toDomain() } }
        .distinctUntilChanged()

    override val preferences: Flow<ContactPreferences> = state
        .asStateFlow()
        .map { it.preferences.toDomain() }
        .distinctUntilChanged()

    override suspend fun getContacts(): List<Contact> = state.value.contacts.map { it.toDomain() }

    override suspend fun getShortcuts(): List<PaymentShortcut> =
        state.value.shortcuts.mapNotNull { it.toDomain() }

    override suspend fun findByAddress(address: LightningAddress): Contact? {
        val key = address.normalizedKey()
        return state.value.contacts.firstOrNull { it.addressKey == key }?.toDomain()
    }

    override suspend fun saveContact(
        address: LightningAddress,
        alias: String?,
        roles: Set<ContactRole>
    ): Contact {
        val storedAddress = address.forStorage()
        val key = storedAddress.normalizedKey()
        var saved: StoredContact? = null
        updateState { current ->
            val now = currentTimeMillis()
            val existing = current.contacts.firstOrNull { it.addressKey == key }
            val contact = existing?.copy(
                username = storedAddress.username,
                tag = storedAddress.tag,
                domain = storedAddress.domain,
                alias = alias.cleanAlias(),
                roles = roles.toStoredRoleNames(),
                updatedAtMs = now
            ) ?: StoredContact(
                id = idForAddress(storedAddress),
                addressKey = key,
                username = storedAddress.username,
                tag = storedAddress.tag,
                domain = storedAddress.domain,
                alias = alias.cleanAlias(),
                roles = roles.toStoredRoleNames(),
                createdAtMs = now,
                updatedAtMs = now
            )
            saved = contact
            current.copy(contacts = current.contacts.replaceById(contact))
        }
        return saved!!.toDomain()
    }

    override suspend fun updateContact(
        id: String,
        alias: String?,
        roles: Set<ContactRole>
    ): Contact? {
        var updated: StoredContact? = null
        updateState { current ->
            val contact = current.contacts.firstOrNull { it.id == id } ?: return@updateState current
            val updatedContact = contact.copy(
                alias = alias.cleanAlias(),
                roles = roles.toStoredRoleNames(),
                updatedAtMs = currentTimeMillis()
            )
            updated = updatedContact
            current.copy(contacts = current.contacts.replaceById(updatedContact))
        }
        return updated?.toDomain()
    }

    override suspend fun deleteContact(id: String) {
        updateState { current ->
            current.copy(
                contacts = current.contacts.filterNot { it.id == id },
                shortcuts = current.shortcuts.filterNot { shortcut ->
                    shortcut.normalized(current.contacts)?.contactId == id
                }
            )
        }
    }

    override suspend fun saveShortcut(
        id: String?,
        title: String,
        contactId: String,
        amount: ShortcutAmount,
        comment: String?
    ): PaymentShortcut? {
        val storedAmount = amount.normalizedForStorage() ?: return null
        var saved: StoredShortcut? = null
        updateState { current ->
            val contact = current.contacts.firstOrNull { it.id == contactId }
                ?: return@updateState current
            val now = currentTimeMillis()
            val existing = id?.let {
                current.shortcuts.firstOrNull { shortcut -> shortcut.id == it }
            }
            val shortcut = StoredShortcut(
                id = existing?.id ?: "shortcut-$now-${contact.id.hashCode().toUInt().toString(16)}",
                title = title.cleanTitle(contact),
                contactId = contact.id,
                username = contact.username,
                tag = contact.tag,
                domain = contact.domain,
                amountMinor = storedAmount.minor,
                amountCurrencyCode = storedAmount.normalizedCurrencyCode,
                comment = comment.cleanComment(),
                paymentCount = existing?.paymentCount ?: 0,
                lastPaidAtMs = existing?.lastPaidAtMs,
                createdAtMs = existing?.createdAtMs ?: now,
                updatedAtMs = now
            )
            saved = shortcut
            current.copy(shortcuts = current.shortcuts.replaceById(shortcut))
        }
        return saved?.toDomain()
    }

    override suspend fun deleteShortcut(id: String) {
        updateState { current ->
            current.copy(shortcuts = current.shortcuts.filterNot { it.id == id })
        }
    }

    override suspend fun recordShortcutPayment(id: String, paidAtMs: Long) {
        updateState { current ->
            val shortcut =
                current.shortcuts.firstOrNull { it.id == id } ?: return@updateState current
            current.copy(
                shortcuts = current.shortcuts.replaceById(
                    shortcut.copy(
                        paymentCount = shortcut.paymentCount + 1,
                        lastPaidAtMs = paidAtMs,
                        updatedAtMs = paidAtMs
                    )
                )
            )
        }
    }

    override suspend fun recordPayment(record: ContactPaymentRecord) {
        val key = record.address.normalizedKey()
        updateState { current ->
            val contact = current.contacts.firstOrNull { it.addressKey == key }
                ?: return@updateState current
            val updatedContact = contact.copy(
                paymentCount = contact.paymentCount + 1,
                lastPaidAtMs = record.paidAtMs,
                updatedAtMs = record.paidAtMs
            )
            current.copy(contacts = current.contacts.replaceById(updatedContact))
        }
    }

    override suspend fun setAskToSaveNewContacts(enabled: Boolean) {
        updateState { current ->
            current.copy(preferences = current.preferences.copy(askToSaveNewContacts = enabled))
        }
    }

    private fun updateState(transform: (StoredContactsState) -> StoredContactsState) {
        val current = state.value
        val updated = transform(current)
        if (updated == current) return
        persist(updated)
        state.value = updated
    }

    private fun loadState(): StoredContactsState {
        val raw = settings.getStringOrNull(KEY_CONTACTS) ?: return StoredContactsState()
        return runCatching { json.decodeFromString<StoredContactsState>(raw) }
            .getOrElse { StoredContactsState() }
    }

    private fun persist(state: StoredContactsState) {
        if (
            state.contacts.isEmpty() &&
            state.shortcuts.isEmpty() &&
            state.preferences == StoredContactPreferences()
        ) {
            settings.remove(KEY_CONTACTS)
            return
        }
        settings.putString(KEY_CONTACTS, json.encodeToString(state))
    }
}

private fun List<StoredContact>.replaceById(contact: StoredContact): List<StoredContact> =
    filterNot { it.id == contact.id } + contact

private fun List<StoredShortcut>.replaceById(shortcut: StoredShortcut): List<StoredShortcut> =
    filterNot { it.id == shortcut.id } + shortcut

private fun StoredContact.toDomain(): Contact = Contact(
    id = id,
    address = LightningAddress(
        username = username,
        domain = domain,
        tag = tag
    ),
    alias = alias,
    roles = roles.mapNotNull { parseContactRole(it) }.toSet(),
    stats = ContactStats(
        paymentCount = paymentCount,
        lastPaidAtMs = lastPaidAtMs
    ),
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs
)

private fun StoredShortcut.toDomain(): PaymentShortcut? {
    val username = username ?: return null
    val domain = domain ?: return null
    val amount = shortcutAmount()?.normalizedForStorage() ?: return null
    return PaymentShortcut(
        id = id,
        title = title.orEmpty().ifBlank { "Pay $username" },
        contactId = contactId,
        address = LightningAddress(
            username = username,
            domain = domain,
            tag = tag
        ),
        amount = amount,
        comment = comment,
        stats = ShortcutStats(
            paymentCount = paymentCount,
            lastPaidAtMs = lastPaidAtMs
        ),
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )
}

private fun StoredShortcut.normalized(contacts: List<StoredContact>): StoredShortcut? {
    val contact = contactId?.let { id -> contacts.firstOrNull { it.id == id } }
    val normalizedUsername = contact?.username ?: username
    val normalizedDomain = contact?.domain ?: domain
    val amount = shortcutAmount()?.normalizedForStorage() ?: return null
    if (normalizedUsername == null || normalizedDomain == null) return null
    return copy(
        title = title.cleanTitle(contact, normalizedUsername),
        contactId = contact?.id ?: contactId,
        username = normalizedUsername,
        tag = contact?.tag ?: tag,
        domain = normalizedDomain,
        amountMinor = amount.minor,
        amountCurrencyCode = amount.normalizedCurrencyCode,
        comment = comment.cleanComment()
    )
}

private fun StoredContactPreferences.toDomain(): ContactPreferences = ContactPreferences(
    askToSaveNewContacts = askToSaveNewContacts
)

private fun String?.cleanTitle(contact: StoredContact): String {
    val explicit = cleanAlias()
    if (explicit != null) return explicit
    val name = contact.alias?.takeIf { it.isNotBlank() } ?: contact.username
    return "Pay $name"
}

private fun String?.cleanTitle(contact: StoredContact?, username: String): String {
    val explicit = cleanAlias()
    if (explicit != null) return explicit
    val name = contact?.alias?.takeIf { it.isNotBlank() } ?: contact?.username ?: username
    return "Pay $name"
}

private fun LightningAddress.forStorage(): LightningAddress = LightningAddress(
    username = username.trim(),
    domain = domain.trim().lowercase(),
    tag = tag?.trim()?.takeIf { it.isNotEmpty() }
)

private fun LightningAddress.normalizedKey(): String = buildString {
    append(username.trim().lowercase())
    tag?.trim()?.takeIf { it.isNotEmpty() }?.let { append('+').append(it.lowercase()) }
    append('@')
    append(domain.trim().lowercase())
}

private fun idForAddress(address: LightningAddress): String =
    "contact-${address.normalizedKey().hashCode().toUInt().toString(16)}"

private fun String?.cleanAlias(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun String?.cleanComment(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun Set<ContactRole>.toStoredRoleNames(): List<String> =
    ContactRole.entries.filter { it in this }.map { it.name }

private fun parseContactRole(raw: String): ContactRole? =
    runCatching { ContactRole.valueOf(raw) }.getOrNull()

private fun ShortcutAmount.normalizedForStorage(): ShortcutAmount? {
    val code = normalizedCurrencyCode
    val supported = CurrencyCatalog.supportedCodes.any { it.equals(code, ignoreCase = true) }
    if (!supported || minor <= 0L) return null
    return copy(currencyCode = code)
}

private fun StoredShortcut.shortcutAmount(): ShortcutAmount? {
    if (amountMinor != null && amountCurrencyCode != null) {
        return ShortcutAmount(amountMinor, amountCurrencyCode)
    }
    val legacyAmountMsats = amountMsats ?: return null
    if (legacyAmountMsats <= 0L) return null
    return ShortcutAmount(
        minor = legacyAmountMsats / MSATS_PER_SAT,
        currencyCode = CurrencyCatalog.DEFAULT_CODE
    )
}

@Serializable
private data class StoredContactsState(
    val contacts: List<StoredContact> = emptyList(),
    val shortcuts: List<StoredShortcut> = emptyList(),
    val preferences: StoredContactPreferences = StoredContactPreferences()
)

@Serializable
private data class StoredContactPreferences(val askToSaveNewContacts: Boolean = true)

@Serializable
private data class StoredContact(
    val id: String,
    val addressKey: String,
    val username: String,
    val tag: String? = null,
    val domain: String,
    val alias: String? = null,
    val roles: List<String> = emptyList(),
    val paymentCount: Int = 0,
    val lastPaidAtMs: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Serializable
private data class StoredShortcut(
    val id: String,
    val title: String? = null,
    val contactId: String? = null,
    val username: String? = null,
    val tag: String? = null,
    val domain: String? = null,
    val amountMinor: Long? = null,
    val amountCurrencyCode: String? = null,
    val amountMsats: Long? = null,
    val comment: String? = null,
    val paymentCount: Int = 0,
    val lastPaidAtMs: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

private const val MSATS_PER_SAT = 1_000L
