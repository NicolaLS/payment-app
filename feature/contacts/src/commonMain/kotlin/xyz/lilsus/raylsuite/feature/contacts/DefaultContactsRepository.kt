package xyz.lilsus.raylsuite.feature.contacts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.lilsus.raylsuite.core.model.Contact
import xyz.lilsus.raylsuite.core.model.ContactPaymentRecord
import xyz.lilsus.raylsuite.core.model.ContactPreferences
import xyz.lilsus.raylsuite.core.model.ContactRole
import xyz.lilsus.raylsuite.core.model.ContactStats
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.PaymentShortcut
import xyz.lilsus.raylsuite.core.model.ShortcutAmount
import xyz.lilsus.raylsuite.core.model.ShortcutStats
import xyz.lilsus.raylsuite.core.settings.rememberAppSettings

class DefaultContactsRepository(
    private val settings: Settings,
    private val clock: () -> Long = ::platformCurrentTimeMillis,
    private val idGenerator: () -> String = ::randomId
) : ContactsRepository {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    private val state = MutableStateFlow(loadState())

    override val contacts: Flow<List<Contact>> =
        state
            .asStateFlow()
            .map { stored -> stored.contacts.map(ContactRecord::toDomain) }
            .distinctUntilChanged()

    override val shortcuts: Flow<List<PaymentShortcut>> =
        state
            .asStateFlow()
            .map(ContactsState::toShortcuts)
            .distinctUntilChanged()

    override val preferences: Flow<ContactPreferences> =
        state
            .asStateFlow()
            .map { stored ->
                ContactPreferences(
                    askToSaveNewContacts = stored.askToSaveNewContacts
                )
            }.distinctUntilChanged()

    override suspend fun getContacts(): List<Contact> =
        state.value.contacts.map(ContactRecord::toDomain)

    override suspend fun getShortcuts(): List<PaymentShortcut> = state.value.toShortcuts()

    override suspend fun findByAddress(address: LightningAddress): Contact? {
        val addressKey = address.normalizedKey()
        return state.value.contacts
            .firstOrNull { it.addressKey == addressKey }
            ?.toDomain()
    }

    override suspend fun saveContact(
        address: LightningAddress,
        alias: String?,
        roles: Set<ContactRole>
    ): Contact {
        val normalizedAddress = address.normalizedForStorage()
        val addressKey = normalizedAddress.normalizedKey()
        lateinit var saved: ContactRecord
        updateState { current ->
            val now = clock()
            val existing = current.contacts.firstOrNull { it.addressKey == addressKey }
            saved =
                if (existing == null) {
                    ContactRecord(
                        id = idGenerator(),
                        addressKey = addressKey,
                        username = normalizedAddress.username,
                        domain = normalizedAddress.domain,
                        tag = normalizedAddress.tag,
                        alias = alias.cleanOptionalText(),
                        roles = roles.toStoredRoles(),
                        createdAtMs = now,
                        updatedAtMs = now
                    )
                } else {
                    existing.copy(
                        username = normalizedAddress.username,
                        domain = normalizedAddress.domain,
                        tag = normalizedAddress.tag,
                        alias = alias.cleanOptionalText(),
                        roles = roles.toStoredRoles(),
                        updatedAtMs = now
                    )
                }
            current.copy(contacts = current.contacts.replace(saved))
        }
        return saved.toDomain()
    }

    override suspend fun updateContact(
        id: String,
        alias: String?,
        roles: Set<ContactRole>
    ): Contact? {
        var updated: ContactRecord? = null
        updateState { current ->
            val existing =
                current.contacts.firstOrNull { it.id == id }
                    ?: return@updateState current
            val replacement =
                existing.copy(
                    alias = alias.cleanOptionalText(),
                    roles = roles.toStoredRoles(),
                    updatedAtMs = clock()
                )
            updated = replacement
            current.copy(contacts = current.contacts.replace(replacement))
        }
        return updated?.toDomain()
    }

    override suspend fun deleteContact(id: String) {
        updateState { current ->
            current.copy(
                contacts = current.contacts.filterNot { it.id == id },
                shortcuts = current.shortcuts.filterNot { it.contactId == id }
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
        val normalizedTitle = title.cleanRequiredText() ?: return null
        val normalizedAmount = amount.normalizedForStorage() ?: return null
        var saved: ShortcutRecord? = null
        updateState { current ->
            val contact =
                current.contacts.firstOrNull { it.id == contactId }
                    ?: return@updateState current
            val now = clock()
            val existing = id?.let { shortcutId ->
                current.shortcuts.firstOrNull { it.id == shortcutId }
            }
            val replacement =
                ShortcutRecord(
                    id = existing?.id ?: idGenerator(),
                    title = normalizedTitle,
                    contactId = contact.id,
                    amountMinor = normalizedAmount.minor,
                    amountCurrencyCode = normalizedAmount.normalizedCurrencyCode,
                    comment = comment.cleanOptionalText(),
                    paymentCount = existing?.paymentCount ?: 0,
                    lastPaidAtMs = existing?.lastPaidAtMs,
                    createdAtMs = existing?.createdAtMs ?: now,
                    updatedAtMs = now
                )
            saved = replacement
            current.copy(shortcuts = current.shortcuts.replace(replacement))
        }
        return saved?.toDomain(state.value.contacts)
    }

    override suspend fun deleteShortcut(id: String) {
        updateState { current ->
            current.copy(shortcuts = current.shortcuts.filterNot { it.id == id })
        }
    }

    override suspend fun recordShortcutPayment(id: String, paidAtMs: Long) {
        updateState { current ->
            val shortcut =
                current.shortcuts.firstOrNull { it.id == id }
                    ?: return@updateState current
            current.copy(
                shortcuts =
                current.shortcuts.replace(
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
        val addressKey = record.address.normalizedKey()
        updateState { current ->
            val contact =
                current.contacts.firstOrNull { it.addressKey == addressKey }
                    ?: return@updateState current
            current.copy(
                contacts =
                current.contacts.replace(
                    contact.copy(
                        paymentCount = contact.paymentCount + 1,
                        lastPaidAtMs = record.paidAtMs,
                        updatedAtMs = record.paidAtMs
                    )
                )
            )
        }
    }

    override suspend fun setAskToSaveNewContacts(enabled: Boolean) {
        updateState { current ->
            current.copy(askToSaveNewContacts = enabled)
        }
    }

    private fun updateState(transform: (ContactsState) -> ContactsState) {
        val current = state.value
        val updated = transform(current)
        if (updated == current) return

        persist(updated)
        state.value = updated
    }

    private fun loadState(): ContactsState {
        val encoded = settings.getStringOrNull(CONTACTS_KEY) ?: return ContactsState()
        return runCatching {
            json.decodeFromString<ContactsState>(encoded)
        }.getOrElse {
            ContactsState()
        }
    }

    private fun persist(state: ContactsState) {
        if (state == ContactsState()) {
            settings.remove(CONTACTS_KEY)
        } else {
            settings.putString(CONTACTS_KEY, json.encodeToString(state))
        }
    }

    private companion object {
        const val CONTACTS_KEY = "contacts"
    }
}

@Composable
fun rememberContactsRepository(storageName: String): ContactsRepository {
    val settings = rememberAppSettings(storageName)
    return remember(settings) {
        DefaultContactsRepository(settings)
    }
}

private fun ContactsState.toShortcuts(): List<PaymentShortcut> =
    shortcuts.mapNotNull { shortcut -> shortcut.toDomain(contacts) }

private fun ContactRecord.toDomain(): Contact = Contact(
    id = id,
    address =
    LightningAddress(
        username = username,
        domain = domain,
        tag = tag
    ),
    alias = alias,
    roles = roles.mapNotNull(::parseContactRole).toSet(),
    stats =
    ContactStats(
        paymentCount = paymentCount,
        lastPaidAtMs = lastPaidAtMs
    ),
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs
)

private fun ShortcutRecord.toDomain(contacts: List<ContactRecord>): PaymentShortcut? {
    val contact = contacts.firstOrNull { it.id == contactId } ?: return null
    return PaymentShortcut(
        id = id,
        title = title,
        contactId = contactId,
        address =
        LightningAddress(
            username = contact.username,
            domain = contact.domain,
            tag = contact.tag
        ),
        amount =
        ShortcutAmount(
            minor = amountMinor,
            currencyCode = amountCurrencyCode
        ),
        comment = comment,
        stats =
        ShortcutStats(
            paymentCount = paymentCount,
            lastPaidAtMs = lastPaidAtMs
        ),
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )
}

private fun List<ContactRecord>.replace(contact: ContactRecord): List<ContactRecord> =
    filterNot { it.id == contact.id } + contact

private fun List<ShortcutRecord>.replace(shortcut: ShortcutRecord): List<ShortcutRecord> =
    filterNot { it.id == shortcut.id } + shortcut

private fun LightningAddress.normalizedForStorage(): LightningAddress = LightningAddress(
    username = username.trim(),
    domain = domain.trim().lowercase(),
    tag = tag.cleanOptionalText()
)

private fun LightningAddress.normalizedKey(): String = buildString {
    append(username.trim().lowercase())
    tag.cleanOptionalText()?.let { append('+').append(it.lowercase()) }
    append('@')
    append(domain.trim().lowercase())
}

private fun String?.cleanOptionalText(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun String.cleanRequiredText(): String? = trim().takeIf(String::isNotEmpty)

private fun Set<ContactRole>.toStoredRoles(): List<String> =
    ContactRole.entries.filter { it in this }.map(ContactRole::name)

private fun parseContactRole(raw: String): ContactRole? =
    runCatching { ContactRole.valueOf(raw) }.getOrNull()

private fun ShortcutAmount.normalizedForStorage(): ShortcutAmount? {
    val code = normalizedCurrencyCode
    val supported = CurrencyCatalog.supportedCodes.any { it.equals(code, ignoreCase = true) }
    if (!supported || minor <= 0L) return null
    return copy(currencyCode = code)
}

private fun randomId(): String = Random.nextLong().toULong().toString(radix = 16)

internal expect fun platformCurrentTimeMillis(): Long

@Serializable
private data class ContactsState(
    val contacts: List<ContactRecord> = emptyList(),
    val shortcuts: List<ShortcutRecord> = emptyList(),
    val askToSaveNewContacts: Boolean = true
)

@Serializable
private data class ContactRecord(
    val id: String,
    val addressKey: String,
    val username: String,
    val domain: String,
    val tag: String? = null,
    val alias: String? = null,
    val roles: List<String> = emptyList(),
    val paymentCount: Int = 0,
    val lastPaidAtMs: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Serializable
private data class ShortcutRecord(
    val id: String,
    val title: String,
    val contactId: String,
    val amountMinor: Long,
    val amountCurrencyCode: String,
    val comment: String? = null,
    val paymentCount: Int = 0,
    val lastPaidAtMs: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long
)
