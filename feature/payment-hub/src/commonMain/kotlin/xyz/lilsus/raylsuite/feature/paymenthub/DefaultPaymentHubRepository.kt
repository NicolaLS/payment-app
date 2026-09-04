package xyz.lilsus.raylsuite.feature.paymenthub

import com.russhwolf.settings.Settings
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.StoredAmount

/** Single-writer hub document stored in app-scoped preferences under [HUB_DOCUMENT_KEY]. */
class DefaultPaymentHubRepository(
    private val settings: Settings,
    private val clock: () -> Long = ::platformCurrentTimeMillis,
    private val idGenerator: () -> String = ::randomId
) : PaymentHubRepository {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    private val mutationMutex = Mutex()
    private val mutableHub = MutableStateFlow(load())

    override val hub: StateFlow<PaymentHub> = mutableHub.asStateFlow()

    override suspend fun createTarget(draft: DirectTargetDraft): DirectPaymentTarget? {
        val validated = draft.validated() ?: return null
        var created: DirectPaymentTarget? = null
        mutate { hub ->
            val now = clock()
            val target =
                DirectPaymentTarget(
                    id = HubItemIds.target(idGenerator()),
                    title = validated.title,
                    address = validated.address,
                    amountRule = validated.amountRule,
                    comment = validated.comment,
                    appearance = validated.appearance,
                    stats = HubItemStats(),
                    createdAtMs = now,
                    updatedAtMs = now
                )
            created = target
            hub
                .copy(targets = hub.targets + target)
                .withMembership(target.id, validated.groupIds)
        }
        return created
    }

    override suspend fun updateTarget(
        id: HubItemId,
        draft: DirectTargetDraft
    ): DirectPaymentTarget? {
        val validated = draft.validated() ?: return null
        var updated: DirectPaymentTarget? = null
        mutate { hub ->
            val existing = hub.target(id) ?: return@mutate hub
            val replacement =
                existing.copy(
                    title = validated.title,
                    address = validated.address,
                    amountRule = validated.amountRule,
                    comment = validated.comment,
                    appearance = validated.appearance,
                    updatedAtMs = clock()
                )
            updated = replacement
            hub
                .copy(targets = hub.targets.map { if (it.id == id) replacement else it })
                .withMembership(id, validated.groupIds)
        }
        return updated
    }

    override suspend fun deleteTarget(id: HubItemId) {
        mutate { hub ->
            hub.copy(
                targets = hub.targets.filterNot { it.id == id },
                groups =
                    hub.groups.map { group ->
                        group.copy(memberIds = group.memberIds.filterNot { it == id })
                    }
            )
        }
    }

    override suspend fun createGroup(draft: GroupDraft): PaymentTargetGroup? {
        val title = draft.title.cleanRequiredText() ?: return null
        var created: PaymentTargetGroup? = null
        mutate { hub ->
            val group =
                PaymentTargetGroup(
                    id = HubItemIds.group(idGenerator()),
                    title = title,
                    memberIds = hub.validMemberIds(draft.memberIds),
                    appearance = draft.appearance
                )
            created = group
            hub.copy(groups = hub.groups + group)
        }
        return created
    }

    override suspend fun updateGroup(id: HubItemId, draft: GroupDraft): PaymentTargetGroup? {
        val title = draft.title.cleanRequiredText() ?: return null
        var updated: PaymentTargetGroup? = null
        mutate { hub ->
            val existing = hub.group(id) ?: return@mutate hub
            val replacement =
                existing.copy(
                    title = title,
                    memberIds = hub.validMemberIds(draft.memberIds),
                    appearance = draft.appearance
                )
            updated = replacement
            hub.copy(groups = hub.groups.map { if (it.id == id) replacement else it })
        }
        return updated
    }

    override suspend fun deleteGroup(id: HubItemId) {
        mutate { hub ->
            hub.copy(groups = hub.groups.filterNot { it.id == id })
        }
    }

    override suspend fun recordSuccessfulPayment(id: HubItemId, paidAtMs: Long) {
        mutate { hub ->
            val target = hub.target(id) ?: return@mutate hub
            val replacement =
                target.copy(
                    stats =
                        HubItemStats(
                            successfulPaymentCount = target.stats.successfulPaymentCount + 1,
                            lastSuccessfulPaymentAtMs = paidAtMs
                        )
                )
            hub.copy(targets = hub.targets.map { if (it.id == id) replacement else it })
        }
    }

    private suspend fun mutate(transform: (PaymentHub) -> PaymentHub) {
        mutationMutex.withLock {
            val current = mutableHub.value
            val updated = transform(current).normalized()
            if (updated == current) return

            persist(updated)
            mutableHub.value = updated
        }
    }

    private fun load(): PaymentHub =
        settings.getStringOrNull(HUB_DOCUMENT_KEY)?.let(::decode) ?: PaymentHub()

    private fun decode(encoded: String): PaymentHub? = runCatching {
        val document = json.decodeFromString<HubDocument>(encoded)
        require(document.schemaVersion == HUB_SCHEMA_VERSION) {
            "Unsupported payment hub schema version: ${document.schemaVersion}"
        }
        document.toDomain()
    }.getOrNull()

    private fun persist(hub: PaymentHub) {
        settings.putString(HUB_DOCUMENT_KEY, json.encodeToString(hub.toDocument()))
    }

    private fun DirectTargetDraft.validated(): DirectTargetDraft? {
        val cleanTitle = title.cleanRequiredText() ?: return null
        val normalizedAddress = LightningAddress.parse(address.full) ?: return null
        val rule =
            when (val rule = amountRule) {
                DirectTargetAmountRule.AskEveryTime -> rule

                is DirectTargetAmountRule.Preset -> {
                    val code = rule.amount.normalizedCurrencyCode
                    if (rule.amount.minor <= 0L || code !in CurrencyCatalog.supportedCodes) {
                        return null
                    }
                    DirectTargetAmountRule.Preset(StoredAmount(rule.amount.minor, code))
                }
            }
        return copy(
            title = cleanTitle,
            address = normalizedAddress,
            amountRule = rule,
            comment = comment.cleanOptionalText(),
            groupIds = groupIds.filter(HubItemId::isGroupId).toSet()
        )
    }

    private companion object {
        const val HUB_DOCUMENT_KEY = "paymentHub.document"
        const val HUB_SCHEMA_VERSION = 1
    }
}

private fun PaymentHub.withMembership(targetId: HubItemId, groupIds: Set<HubItemId>): PaymentHub =
    copy(
        groups =
            groups.map { group ->
                val isMember = targetId in group.memberIds
                when {
                    group.id in groupIds && !isMember ->
                        group.copy(memberIds = group.memberIds + targetId)

                    group.id !in groupIds && isMember ->
                        group.copy(memberIds = group.memberIds.filterNot { it == targetId })

                    else -> group
                }
            }
    )

private fun PaymentHub.validMemberIds(memberIds: List<HubItemId>): List<HubItemId> =
    memberIds.filter { it.isDirectTargetId() && target(it) != null }.distinct()

private fun String?.cleanOptionalText(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun String.cleanRequiredText(): String? = trim().takeIf(String::isNotEmpty)

private fun randomId(): String = Random.nextLong().toULong().toString(radix = 16)

internal expect fun platformCurrentTimeMillis(): Long

@Serializable
private data class HubDocument(
    val schemaVersion: Int,
    val targets: List<TargetRecord> = emptyList(),
    val groups: List<GroupRecord> = emptyList()
)

@Serializable
private data class TargetRecord(
    val id: String,
    val title: String,
    val username: String,
    val domain: String,
    val tag: String? = null,
    val amountRule: AmountRuleRecord,
    val comment: String? = null,
    val icon: String? = null,
    val accent: String? = null,
    val successfulPaymentCount: Int = 0,
    val lastSuccessfulPaymentAtMs: Long? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Serializable
private data class AmountRuleRecord(
    val kind: String,
    val minor: Long? = null,
    val currencyCode: String? = null
)

@Serializable
private data class GroupRecord(
    val id: String,
    val title: String,
    val memberIds: List<String> = emptyList(),
    val icon: String? = null,
    val accent: String? = null
)

private fun PaymentHub.toDocument(): HubDocument = HubDocument(
    schemaVersion = 1,
    targets =
        targets.map { target ->
            TargetRecord(
                id = target.id.value,
                title = target.title,
                username = target.address.username,
                domain = target.address.domain,
                tag = target.address.tag,
                amountRule =
                    when (val rule = target.amountRule) {
                        DirectTargetAmountRule.AskEveryTime ->
                            AmountRuleRecord(kind = AMOUNT_RULE_ASK)

                        is DirectTargetAmountRule.Preset ->
                            AmountRuleRecord(
                                kind = AMOUNT_RULE_PRESET,
                                minor = rule.amount.minor,
                                currencyCode = rule.amount.normalizedCurrencyCode
                            )
                    },
                comment = target.comment,
                icon = target.appearance.icon?.storedValue,
                accent = target.appearance.accent?.storedValue,
                successfulPaymentCount = target.stats.successfulPaymentCount,
                lastSuccessfulPaymentAtMs = target.stats.lastSuccessfulPaymentAtMs,
                createdAtMs = target.createdAtMs,
                updatedAtMs = target.updatedAtMs
            )
        },
    groups =
        groups.map { group ->
            GroupRecord(
                id = group.id.value,
                title = group.title,
                memberIds = group.memberIds.map(HubItemId::value),
                icon = group.appearance.icon?.storedValue,
                accent = group.appearance.accent?.storedValue
            )
        }
)

private fun HubDocument.toDomain(): PaymentHub = PaymentHub(
    targets =
        targets.map { record ->
            DirectPaymentTarget(
                id = HubItemId(record.id),
                title = record.title,
                address =
                    LightningAddress(
                        username = record.username,
                        domain = record.domain,
                        tag = record.tag
                    ),
                amountRule =
                    when (record.amountRule.kind) {
                        AMOUNT_RULE_PRESET ->
                            DirectTargetAmountRule.Preset(
                                StoredAmount(
                                    minor = requireNotNull(record.amountRule.minor),
                                    currencyCode = requireNotNull(record.amountRule.currencyCode)
                                )
                            )

                        AMOUNT_RULE_ASK -> DirectTargetAmountRule.AskEveryTime

                        else -> error("Unknown amount rule: ${record.amountRule.kind}")
                    },
                comment = record.comment,
                appearance =
                    HubItemAppearance(
                        icon = HubIcon.fromStoredValue(record.icon),
                        accent = HubAccent.fromStoredValue(record.accent)
                    ),
                stats =
                    HubItemStats(
                        successfulPaymentCount = record.successfulPaymentCount,
                        lastSuccessfulPaymentAtMs = record.lastSuccessfulPaymentAtMs
                    ),
                createdAtMs = record.createdAtMs,
                updatedAtMs = record.updatedAtMs
            )
        },
    groups =
        groups.map { record ->
            PaymentTargetGroup(
                id = HubItemId(record.id),
                title = record.title,
                memberIds = record.memberIds.map(::HubItemId),
                appearance =
                    HubItemAppearance(
                        icon = HubIcon.fromStoredValue(record.icon),
                        accent = HubAccent.fromStoredValue(record.accent)
                    )
            )
        }
).normalized()

private const val AMOUNT_RULE_ASK = "ask"
private const val AMOUNT_RULE_PRESET = "preset"
