package xyz.lilsus.blip.feature.blinkcontacts

import com.russhwolf.settings.Settings
import kotlin.random.Random
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.lilsus.raylsuite.core.model.LightningAddress

/**
 * Installation-scoped contact book. Contacts survive wallet replacement and are
 * stored independently from the user's Payment Hub composition.
 */
class BlinkContactsRepository(
    private val settings: Settings,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val idGenerator: () -> String = ::randomId
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    private val mutationMutex = Mutex()
    private val mutableContacts = MutableStateFlow(load())

    val contacts: StateFlow<List<BlipContact>> = mutableContacts.asStateFlow()

    suspend fun saveContact(address: LightningAddress, alias: String?): BlipContact {
        val normalizedAddress =
            requireNotNull(LightningAddress.parse(address.full)) { "Invalid contact address" }
        val addressKey = normalizedAddress.storageKey()
        lateinit var saved: BlipContact
        mutationMutex.withLock {
            val current = mutableContacts.value
            val existing = current.firstOrNull { it.address.storageKey() == addressKey }
            val now = clock()
            saved =
                if (existing == null) {
                    BlipContact(
                        id = idGenerator(),
                        address = normalizedAddress,
                        alias = alias.cleanOptionalText(),
                        createdAtMs = now,
                        updatedAtMs = now
                    )
                } else {
                    existing.copy(
                        address = normalizedAddress,
                        alias = alias.cleanOptionalText(),
                        updatedAtMs = now
                    )
                }
            val updated = current.filterNot { it.id == saved.id } + saved
            persist(updated)
            mutableContacts.value = updated
        }
        return saved
    }

    private fun load(): List<BlipContact> {
        val encoded = settings.getStringOrNull(CONTACTS_DOCUMENT_KEY) ?: return emptyList()
        return runCatching {
            val document = json.decodeFromString<ContactsDocument>(encoded)
            require(document.schemaVersion == CONTACTS_SCHEMA_VERSION) {
                "Unsupported contacts schema version: ${document.schemaVersion}"
            }
            document.contacts.mapNotNull(ContactRecord::toDomain).distinctBy {
                it.address.storageKey()
            }
        }.getOrElse { emptyList() }
    }

    private fun persist(contacts: List<BlipContact>) {
        val document =
            ContactsDocument(
                schemaVersion = CONTACTS_SCHEMA_VERSION,
                contacts = contacts.map(BlipContact::toRecord)
            )
        settings.putString(CONTACTS_DOCUMENT_KEY, json.encodeToString(document))
    }

    private companion object {
        const val CONTACTS_DOCUMENT_KEY = "contacts.document"
        const val CONTACTS_SCHEMA_VERSION = 1
    }
}

data class BlipContact(
    val id: String,
    val address: LightningAddress,
    val alias: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    val displayName: String
        get() = alias?.takeIf(String::isNotBlank) ?: address.username
}

@Serializable
private data class ContactsDocument(
    val schemaVersion: Int,
    val contacts: List<ContactRecord> = emptyList()
)

/** Fields intentionally match the suite's former standalone contact document. */
@Serializable
private data class ContactRecord(
    val id: String,
    val addressKey: String,
    val username: String,
    val domain: String,
    val tag: String? = null,
    val alias: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    fun toDomain(): BlipContact? {
        val rawAddress = buildString {
            append(username)
            tag?.takeIf(String::isNotBlank)?.let { append('+').append(it) }
            append('@').append(domain)
        }
        val address = LightningAddress.parse(rawAddress) ?: return null
        return BlipContact(
            id = id,
            address = address,
            alias = alias.cleanOptionalText(),
            createdAtMs = createdAtMs,
            updatedAtMs = updatedAtMs
        )
    }
}

private fun BlipContact.toRecord(): ContactRecord = ContactRecord(
    id = id,
    addressKey = address.storageKey(),
    username = address.username,
    domain = address.domain,
    tag = address.tag,
    alias = alias,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs
)

private fun LightningAddress.storageKey(): String = full.trim().lowercase()

private fun String?.cleanOptionalText(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun randomId(): String = Random.nextLong().toULong().toString(radix = 16)
