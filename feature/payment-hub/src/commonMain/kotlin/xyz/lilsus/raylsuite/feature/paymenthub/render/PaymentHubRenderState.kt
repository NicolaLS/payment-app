package xyz.lilsus.raylsuite.feature.paymenthub.render

import androidx.compose.runtime.Immutable
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.feature.paymenthub.DirectPaymentTarget
import xyz.lilsus.raylsuite.feature.paymenthub.DirectTargetAmountRule
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
    val mark: HubMark,
    val detail: HubItemDetail,
    val lastUsedAtMs: Long? = null,
    /** False for a group without any existing member; selecting it does nothing useful. */
    val enabled: Boolean = true
) {
    val isGroup: Boolean
        get() = detail is HubItemDetail.Group
}

@Immutable
sealed interface HubItemDetail {
    data class Target(val address: String, val amountLine: HubAmountLine) : HubItemDetail

    data class Group(val memberCount: Int) : HubItemDetail
}

@Immutable
data class PaymentHubRenderState(
    /** Direct targets by most recent successful use. */
    val recentItems: List<HubItemRenderModel> = emptyList(),
    /** Every group followed by every target, each in stable alphabetical order. */
    val allItems: List<HubItemRenderModel> = emptyList()
) {
    val isEmpty: Boolean
        get() = allItems.isEmpty()

    val targets: List<HubItemRenderModel>
        get() = allItems.filterNot { it.isGroup }

    fun item(id: HubItemId): HubItemRenderModel? = allItems.firstOrNull { it.id == id }
}

fun PaymentHub.toRenderState(recentLimit: Int = DEFAULT_RECENT_LIMIT): PaymentHubRenderState {
    val targetModels = targets.associate { it.id to it.toRenderModel() }
    val groupModels = groups.associate { it.id to it.toRenderModel(this) }
    val recent =
        targets
            .filter { it.stats.lastSuccessfulPaymentAtMs != null }
            .sortedByDescending { it.stats.lastSuccessfulPaymentAtMs }
            .take(recentLimit)
            .mapNotNull { targetModels[it.id] }
    val all =
        groupModels.values.sortedBy { it.title.lowercase() } +
            targetModels.values.sortedBy { it.title.lowercase() }
    return PaymentHubRenderState(recentItems = recent, allItems = all)
}

internal fun DirectPaymentTarget.amountLine(): HubAmountLine = when (val rule = amountRule) {
    DirectTargetAmountRule.AskEveryTime -> HubAmountLine.AskEachTime

    is DirectTargetAmountRule.Preset ->
        HubAmountLine.Preset(
            DisplayAmount(
                minor = rule.amount.minor,
                currency =
                    CurrencyCatalog.infoFor(rule.amount.normalizedCurrencyCode).currency
            )
        )
}

internal fun DirectPaymentTarget.mark(): HubMark = HubMark(
    initials = hubInitials(title),
    icon = appearance.icon,
    accent = appearance.accent
)

private fun DirectPaymentTarget.toRenderModel(): HubItemRenderModel = HubItemRenderModel(
    id = id,
    title = title,
    mark = mark(),
    detail = HubItemDetail.Target(address = address.full, amountLine = amountLine()),
    lastUsedAtMs = stats.lastSuccessfulPaymentAtMs
)

private fun PaymentTargetGroup.toRenderModel(hub: PaymentHub): HubItemRenderModel {
    val memberCount = hub.members(id).size
    return HubItemRenderModel(
        id = id,
        title = title,
        mark =
            HubMark(
                initials = hubInitials(title),
                icon = appearance.icon ?: HubIcon.Group,
                accent = appearance.accent
            ),
        detail = HubItemDetail.Group(memberCount = memberCount),
        enabled = memberCount > 0
    )
}

private const val DEFAULT_RECENT_LIMIT = 8
