package xyz.lilsus.raylsuite.feature.paymentui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.dismiss_button
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.retry_payment
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.session_transactions_empty
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.session_transactions_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.tap_dismiss_pending

data class NativePaymentRecentItem(
    val id: String,
    val amount: String,
    val statusLabel: String,
    val statusTone: String,
    val createdAtMs: Long,
    val supportingText: String?,
    val detailState: PaymentScreenState,
    val canRetry: Boolean,
    val pendingMessage: String?
)

data class NativePaymentRecentSnapshot(
    val title: String,
    val emptyMessage: String,
    /** Title for the close control a product shows when it presents this outside a tab. */
    val dismissTitle: String,
    val items: List<NativePaymentRecentItem>,
    val selectedDetail: NativePaymentRecentDetail?
)

data class NativePaymentRecentDetail(
    val id: String,
    val heroPhase: String,
    val receiptPreimage: String?,
    val content: NativePaymentScanContent?,
    val pendingMessage: String?,
    val retryTitle: String?
)

/** Native Recent host boundary. Its inputs are already localized, app-owned projections. */
class NativePaymentRecentController(
    private val onIntent: (PaymentIntent) -> Unit,
    private val onSelectTransaction: (String?) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val snapshot = MutableStateFlow<NativePaymentRecentSnapshot?>(null)

    private var items: List<NativePaymentRecentItem> = emptyList()
    private var selectedTransactionId: String? = null
    private var estimatedFeeHint: String? = null
    private var receiptVisible = false
    private var lastReceiptPreimage: String? = null
    private var active = false

    fun observe(onChange: (NativePaymentRecentSnapshot) -> Unit): () -> Unit {
        val job: Job = scope.launch { snapshot.filterNotNull().collect(onChange) }
        return { job.cancel() }
    }

    suspend fun update(
        items: List<NativePaymentRecentItem>,
        selectedTransactionId: String?,
        estimatedFeeHint: String?
    ) {
        val selected = items.firstOrNull { it.id == selectedTransactionId }
        val nextPreimage = selected?.detailState?.receiptPreimage()
        if (selected?.id != this.selectedTransactionId || nextPreimage != lastReceiptPreimage) {
            receiptVisible = false
        }
        this.items = items
        this.selectedTransactionId = selectedTransactionId
        this.estimatedFeeHint = estimatedFeeHint
        lastReceiptPreimage = nextPreimage
        publishSnapshot()
    }

    fun setActive(active: Boolean) {
        if (active && !this.active) onIntent(PaymentIntent.SessionTransactionsOpened)
        this.active = active
    }

    fun selectTransaction(id: String) {
        receiptVisible = false
        onSelectTransaction(id)
    }

    fun closeDetail() {
        receiptVisible = false
        onSelectTransaction(null)
        onIntent(PaymentIntent.DismissResult)
    }

    fun viewReceipt() {
        if (lastReceiptPreimage == null) return
        receiptVisible = true
        scope.launch { publishSnapshot() }
    }

    fun retrySelected() {
        val id = selectedTransactionId ?: return
        receiptVisible = false
        onIntent(PaymentIntent.RetryTransaction(id))
        onSelectTransaction(null)
        onIntent(PaymentIntent.DismissResult)
    }

    fun clear() {
        scope.cancel()
    }

    private suspend fun publishSnapshot() {
        val selected = items.firstOrNull { it.id == selectedTransactionId }
        val detail = selected?.toNativeDetail()
        snapshot.value =
            NativePaymentRecentSnapshot(
                title = getString(Res.string.session_transactions_title),
                emptyMessage = getString(Res.string.session_transactions_empty),
                dismissTitle = getString(Res.string.dismiss_button),
                items = items,
                selectedDetail = detail
            )
    }

    private suspend fun NativePaymentRecentItem.toNativeDetail(): NativePaymentRecentDetail {
        val terminal =
            detailState is PaymentScreenState.Success || detailState is PaymentScreenState.Error
        return NativePaymentRecentDetail(
            id = id,
            heroPhase = detailState.toNativeHeroPhaseValue(),
            receiptPreimage = detailState.receiptPreimage().takeIf { receiptVisible },
            content =
                detailState
                    .toNativeRecentDetailContent(estimatedFeeHint, receiptVisible)
                    .takeIf { terminal },
            pendingMessage =
                if (terminal) {
                    null
                } else {
                    pendingMessage ?: getString(Res.string.tap_dismiss_pending)
                },
            retryTitle = getString(Res.string.retry_payment).takeIf { canRetry }
        )
    }
}

private fun PaymentScreenState.receiptPreimage(): String? = (this as? PaymentScreenState.Success)
    ?.preimage
    ?.trim()
    ?.takeIf(String::isNotEmpty)
