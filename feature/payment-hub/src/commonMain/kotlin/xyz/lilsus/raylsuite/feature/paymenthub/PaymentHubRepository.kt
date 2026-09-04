package xyz.lilsus.raylsuite.feature.paymenthub

import kotlinx.coroutines.flow.StateFlow
import xyz.lilsus.raylsuite.core.model.LightningAddress

/**
 * App-scoped, non-sensitive hub storage. Targets, groups, and statistics intentionally survive
 * wallet credential removal or replacement. No lens or payment value enters it.
 */
interface PaymentHubRepository {
    val hub: StateFlow<PaymentHub>

    suspend fun createTarget(draft: DirectTargetDraft): DirectPaymentTarget?

    suspend fun updateTarget(id: HubItemId, draft: DirectTargetDraft): DirectPaymentTarget?

    suspend fun deleteTarget(id: HubItemId)

    suspend fun createGroup(draft: GroupDraft): PaymentTargetGroup?

    suspend fun updateGroup(id: HubItemId, draft: GroupDraft): PaymentTargetGroup?

    suspend fun deleteGroup(id: HubItemId)

    /** Counts one terminal successful wallet payment for a direct target. */
    suspend fun recordSuccessfulPayment(id: HubItemId, paidAtMs: Long)
}

data class DirectTargetDraft(
    val title: String,
    val address: LightningAddress,
    val amountRule: DirectTargetAmountRule,
    val comment: String? = null,
    val appearance: HubItemAppearance = HubItemAppearance.None,
    val groupIds: Set<HubItemId> = emptySet()
)

data class GroupDraft(
    val title: String,
    val memberIds: List<HubItemId> = emptyList(),
    val appearance: HubItemAppearance = HubItemAppearance.None
)
