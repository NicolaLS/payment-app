package xyz.lilsus.raylsuite.feature.paymenthub

import kotlin.jvm.JvmInline
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.StoredAmount

/**
 * Stable presentation identity of a hub item. It refers to a direct target or a group today and
 * may refer to a service-projected item later without teaching lenses how that item executes.
 */
@JvmInline
value class HubItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "Hub item ID must not be blank" }
    }
}

/** A stable, named configuration that starts one Lightning Address payment flow. */
data class DirectPaymentTarget(
    val id: HubItemId,
    val title: String,
    val address: LightningAddress,
    val amountRule: DirectTargetAmountRule,
    val comment: String? = null,
    val appearance: HubItemAppearance = HubItemAppearance.None,
    val stats: HubItemStats = HubItemStats(),
    val createdAtMs: Long,
    val updatedAtMs: Long
)

sealed interface DirectTargetAmountRule {
    data object AskEveryTime : DirectTargetAmountRule

    data class Preset(val amount: StoredAmount) : DirectTargetAmountRule
}

/** A named, one-level collection of direct targets. A group organizes; it never pays. */
data class PaymentTargetGroup(
    val id: HubItemId,
    val title: String,
    val memberIds: List<HubItemId> = emptyList(),
    val appearance: HubItemAppearance = HubItemAppearance.None
)

data class HubItemAppearance(val icon: HubIcon? = null, val accent: HubAccent? = null) {
    companion object {
        val None = HubItemAppearance()
    }
}

/** Bundled icons. The stored value never changes once shipped. */
enum class HubIcon(val storedValue: String) {
    Person("person"),
    Group("group"),
    Store("store"),
    Restaurant("restaurant"),
    Coffee("coffee"),
    Gift("gift"),
    Heart("heart"),
    Star("star"),
    Bolt("bolt"),
    Home("home"),
    Wallet("wallet"),
    Work("work");

    companion object {
        fun fromStoredValue(value: String?): HubIcon? =
            entries.firstOrNull { it.storedValue == value }
    }
}

/** Accent tokens from the suite palette. The stored value never changes once shipped. */
enum class HubAccent(val storedValue: String) {
    Orange("orange"),
    Blue("blue"),
    Green("green"),
    Purple("purple"),
    Pink("pink"),
    Teal("teal"),
    Amber("amber"),
    Slate("slate");

    companion object {
        fun fromStoredValue(value: String?): HubAccent? =
            entries.firstOrNull { it.storedValue == value }
    }
}

/** Only a terminal successful wallet payment updates these values. */
data class HubItemStats(
    val successfulPaymentCount: Int = 0,
    val lastSuccessfulPaymentAtMs: Long? = null
)

/**
 * The canonical, non-sensitive hub document: direct targets, groups with membership, and the
 * common pinned order. Pins may reference targets and groups.
 */
data class PaymentHub(
    val targets: List<DirectPaymentTarget> = emptyList(),
    val groups: List<PaymentTargetGroup> = emptyList(),
    val pinnedItemIds: List<HubItemId> = emptyList()
) {
    val isEmpty: Boolean
        get() = targets.isEmpty() && groups.isEmpty()

    fun target(id: HubItemId): DirectPaymentTarget? = targets.firstOrNull { it.id == id }

    fun group(id: HubItemId): PaymentTargetGroup? = groups.firstOrNull { it.id == id }

    fun contains(id: HubItemId): Boolean = target(id) != null || group(id) != null

    fun isPinned(id: HubItemId): Boolean = id in pinnedItemIds

    /** Members of [groupId] that still exist, in explicit group order. */
    fun members(groupId: HubItemId): List<DirectPaymentTarget> =
        group(groupId)?.memberIds?.mapNotNull(::target).orEmpty()

    /** Groups that contain [targetId]. */
    fun groupsContaining(targetId: HubItemId): List<PaymentTargetGroup> =
        groups.filter { targetId in it.memberIds }

    /**
     * Drops dangling pins and members, duplicate IDs, and group members that are not direct
     * targets. Lenses never see inconsistent data.
     */
    fun normalized(): PaymentHub {
        val uniqueTargets = targets.distinctBy { it.id }
        val targetIds = uniqueTargets.mapTo(mutableSetOf()) { it.id }
        val uniqueGroups =
            groups
                .filter { it.id !in targetIds }
                .distinctBy { it.id }
                .map { group ->
                    group.copy(memberIds = group.memberIds.filter { it in targetIds }.distinct())
                }
        val groupIds = uniqueGroups.mapTo(mutableSetOf()) { it.id }
        return PaymentHub(
            targets = uniqueTargets,
            groups = uniqueGroups,
            pinnedItemIds =
                pinnedItemIds.filter { it in targetIds || it in groupIds }.distinct()
        )
    }
}

fun HubItemId.isDirectTargetId(): Boolean = value.startsWith(HubItemIds.TARGET_PREFIX)

fun HubItemId.isGroupId(): Boolean = value.startsWith(HubItemIds.GROUP_PREFIX)

object HubItemIds {
    const val TARGET_PREFIX = "target:"
    const val GROUP_PREFIX = "group:"

    fun target(suffix: String): HubItemId = HubItemId(TARGET_PREFIX + suffix)

    fun group(suffix: String): HubItemId = HubItemId(GROUP_PREFIX + suffix)
}
