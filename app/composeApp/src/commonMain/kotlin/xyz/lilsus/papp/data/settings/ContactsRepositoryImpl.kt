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
import xyz.lilsus.papp.domain.model.PaymentShortcut
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
        role: ContactRole?
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
                role = role?.name,
                updatedAtMs = now
            ) ?: StoredContact(
                id = idForAddress(storedAddress),
                addressKey = key,
                username = storedAddress.username,
                tag = storedAddress.tag,
                domain = storedAddress.domain,
                alias = alias.cleanAlias(),
                role = role?.name,
                createdAtMs = now,
                updatedAtMs = now
            )
            saved = contact
            current.copy(contacts = current.contacts.replaceById(contact))
        }
        return saved!!.toDomain()
    }

    override suspend fun updateContact(id: String, alias: String?, role: ContactRole?): Contact? {
        var updated: StoredContact? = null
        updateState { current ->
            val contact = current.contacts.firstOrNull { it.id == id } ?: return@updateState current
            val updatedContact = contact.copy(
                alias = alias.cleanAlias(),
                role = role?.name,
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
        amountMsats: Long,
        comment: String?
    ): PaymentShortcut? {
        if (amountMsats <= 0L) return null
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
                amountMsats = amountMsats,
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
        val updated = transform(current).migrated()
        if (updated == current) return
        persist(updated)
        state.value = updated
    }

    private fun loadState(): StoredContactsState {
        val raw = settings.getStringOrNull(KEY_CONTACTS) ?: return StoredContactsState()
        return runCatching { json.decodeFromString<StoredContactsState>(raw).migrated() }
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

private fun StoredContactsState.migrated(): StoredContactsState {
    val contactsWithoutLegacy = contacts.map { it.copy(shortcuts = emptyList()) }
    val migratedLegacyShortcuts = contacts.flatMap { contact ->
        contact.shortcuts.mapNotNull { shortcut ->
            if (shortcut.amountMsats <= 0) return@mapNotNull null
            StoredShortcut(
                id = shortcut.id,
                title = shortcut.title.cleanTitle(contact),
                contactId = contact.id,
                username = contact.username,
                tag = contact.tag,
                domain = contact.domain,
                amountMsats = shortcut.amountMsats,
                comment = shortcut.comment.cleanComment(),
                createdAtMs = shortcut.createdAtMs,
                updatedAtMs = shortcut.updatedAtMs
            )
        }
    }
    val normalizedShortcuts = (shortcuts + migratedLegacyShortcuts)
        .mapNotNull { it.normalized(contactsWithoutLegacy) }
        .distinctBy { it.id }
    return copy(
        contacts = contactsWithoutLegacy,
        shortcuts = normalizedShortcuts,
        preferences = preferences.copy(suggestShortcuts = false)
    )
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
    role = role?.let { runCatching { ContactRole.valueOf(it) }.getOrNull() },
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
    if (amountMsats <= 0L) return null
    return PaymentShortcut(
        id = id,
        title = title.orEmpty().ifBlank { "Pay $username" },
        contactId = contactId,
        address = LightningAddress(
            username = username,
            domain = domain,
            tag = tag
        ),
        amountMsats = amountMsats,
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
    val legacyRecipient = recipients.firstOrNull()
    val shortcutContactId = contactId ?: legacyRecipient?.contactId
    val contact = shortcutContactId?.let { id -> contacts.firstOrNull { it.id == id } }
    val normalizedUsername = contact?.username ?: username ?: legacyRecipient?.username
    val normalizedDomain = contact?.domain ?: domain ?: legacyRecipient?.domain
    val normalizedAmount = amountMsats.takeIf { it > 0L }
        ?: legacyRecipient?.amountMsats?.takeIf { it > 0L }
        ?: return null
    if (normalizedUsername == null || normalizedDomain == null) return null
    return copy(
        title = title.cleanTitle(contact, normalizedUsername),
        contactId = contact?.id ?: shortcutContactId,
        username = normalizedUsername,
        tag = contact?.tag ?: tag ?: legacyRecipient?.tag,
        domain = normalizedDomain,
        amountMsats = normalizedAmount,
        comment = (comment ?: legacyRecipient?.comment).cleanComment(),
        recipients = emptyList(),
        context = StoredShortcutContext()
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

@Serializable
private data class StoredContactsState(
    val contacts: List<StoredContact> = emptyList(),
    val shortcuts: List<StoredShortcut> = emptyList(),
    val preferences: StoredContactPreferences = StoredContactPreferences()
)

@Serializable
private data class StoredContactPreferences(
    val askToSaveNewContacts: Boolean = true,
    val suggestShortcuts: Boolean = false
)

@Serializable
private data class StoredContact(
    val id: String,
    val addressKey: String,
    val username: String,
    val tag: String? = null,
    val domain: String,
    val alias: String? = null,
    val role: String? = null,
    val shortcuts: List<StoredLegacyShortcut> = emptyList(),
    val paymentCount: Int = 0,
    val lastPaidAtMs: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Serializable
private data class StoredLegacyShortcut(
    val id: String,
    val contactId: String,
    val title: String? = null,
    val amountMsats: Long,
    val comment: String? = null,
    val startMinuteOfDay: Int? = null,
    val endMinuteOfDay: Int? = null,
    val previousTargetKey: String? = null,
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
    val amountMsats: Long = 0L,
    val comment: String? = null,
    val recipients: List<StoredShortcutRecipient> = emptyList(),
    val context: StoredShortcutContext = StoredShortcutContext(),
    val paymentCount: Int = 0,
    val lastPaidAtMs: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Serializable
private data class StoredShortcutRecipient(
    val id: String,
    val contactId: String? = null,
    val username: String,
    val tag: String? = null,
    val domain: String,
    val amountMsats: Long,
    val comment: String? = null
)

@Serializable
private data class StoredShortcutContext(
    val startMinuteOfDay: Int? = null,
    val endMinuteOfDay: Int? = null,
    val dayOfMonth: Int? = null,
    val previousTargetKey: String? = null
)
