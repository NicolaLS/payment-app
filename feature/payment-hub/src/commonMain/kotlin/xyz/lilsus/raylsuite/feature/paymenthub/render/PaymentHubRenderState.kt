package xyz.lilsus.raylsuite.feature.paymenthub.render

import androidx.compose.runtime.Immutable
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.feature.paymenthub.DirectPaymentTarget
import xyz.lilsus.raylsuite.feature.paymenthub.DirectTargetAmountRule
import xyz.lilsus.raylsuite.feature.paymenthub.HubAccent
import xyz.lilsus.raylsuite.feature.paymenthub.HubIcon
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHub
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentTargetGroup

/**
 * Deliberately small, lossy render model shared by every lens. It carries only values a lens
 * renders: no repository, provider error, invoice, credential, or execution callback.
 */
@Immutable
data class HubItemRenderModel(
    val id: HubItemId,
    val title: String,
    val detail: HubItemDetail,
    val icon: HubIcon? = null,
    val accent: HubAccent? = null,
    val pinned: Boolean = false,
    val lastUsedAtMs: Long? = null,
    /** False for a group without any existing member; selecting it does nothing useful. */
    val enabled: Boolean = true
) {
    val isGroup: Boolean
        get() = detail is HubItemDetail.Group
}

@Immutable
sealed interface HubItemDetail {
    data class Target(val address: String, val presetAmount: DisplayAmount?) : HubItemDetail

    data class Group(val memberCount: Int) : HubItemDetail
}

@Immutable
data class PaymentHubRenderState(
    /** User-owned manual order. Never reordered automatically. */
    val pinnedItems: List<HubItemRenderModel> = emptyList(),
    /** Direct targets by most recent successful use. */
    val recentItems: List<HubItemRenderModel> = emptyList(),
    /** Every group followed by every target, each in stable alphabetical order. */
    val allItems: List<HubItemRenderModel> = emptyList()
) {
    val isEmpty: Boolean
        get() = allItems.isEmpty()

    fun item(id: HubItemId): HubItemRenderModel? = allItems.firstOrNull { it.id == id }
}

fun PaymentHub.toRenderState(recentLimit: Int = DEFAULT_RECENT_LIMIT): PaymentHubRenderState {
    val targetModels = targets.associate { it.id to it.toRenderModel(isPinned(it.id)) }
    val groupModels = groups.associate { it.id to it.toRenderModel(this) }
    val pinned = pinnedItemIds.mapNotNull { targetModels[it] ?: groupModels[it] }
    val recent =
        targets
            .filter { it.stats.lastSuccessfulPaymentAtMs != null }
            .sortedByDescending { it.stats.lastSuccessfulPaymentAtMs }
            .take(recentLimit)
            .mapNotNull { targetModels[it.id] }
    val all =
        groupModels.values.sortedBy { it.title.lowercase() } +
            targetModels.values.sortedBy { it.title.lowercase() }
    return PaymentHubRenderState(
        pinnedItems = pinned,
        recentItems = recent,
        allItems = all
    )
}

private fun DirectPaymentTarget.toRenderModel(pinned: Boolean): HubItemRenderModel =
    HubItemRenderModel(
        id = id,
        title = title,
        detail =
            HubItemDetail.Target(
                address = address.full,
                presetAmount =
                    (amountRule as? DirectTargetAmountRule.Preset)?.amount?.let { amount ->
                        DisplayAmount(
                            minor = amount.minor,
                            currency = CurrencyCatalog.infoFor(
                                amount.normalizedCurrencyCode
                            ).currency
                        )
                    }
            ),
        icon = appearance.icon,
        accent = appearance.accent,
        pinned = pinned,
        lastUsedAtMs = stats.lastSuccessfulPaymentAtMs
    )

private fun PaymentTargetGroup.toRenderModel(hub: PaymentHub): HubItemRenderModel {
    val memberCount = hub.members(id).size
    return HubItemRenderModel(
        id = id,
        title = title,
        detail = HubItemDetail.Group(memberCount = memberCount),
        icon = appearance.icon ?: HubIcon.Group,
        accent = appearance.accent,
        pinned = hub.isPinned(id),
        enabled = memberCount > 0
    )
}

private const val DEFAULT_RECENT_LIMIT = 8
