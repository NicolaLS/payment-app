package xyz.lilsus.raylsuite.feature.paymenthub.lens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import xyz.lilsus.raylsuite.core.ui.resources.LocalizedText
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubLensId

/**
 * A lens is compiled presentation code over the shared render contract. It owns neither hub
 * data, scanner lifecycle, payment execution, navigation policy, nor confirmation policy.
 */
interface PaymentHubLensDefinition {
    val id: PaymentHubLensId
    val metadata: PaymentHubLensMetadata

    /** Static illustration for the settings selector. */
    @Composable
    fun Preview(modifier: Modifier)

    /** Home content while the app payment state is active. */
    @Composable
    fun Content(
        state: PaymentHubRenderState,
        actions: PaymentHubActions,
        scanner: PaymentHubScannerSlot,
        modifier: Modifier
    )
}

data class PaymentHubLensMetadata(val name: LocalizedText, val description: LocalizedText)

/** UI intents a lens may report. The host decides what each intent means and whether it is allowed. */
@Stable
interface PaymentHubActions {
    fun selectItem(id: HubItemId)

    fun openGroup(id: HubItemId)

    fun openLibrary()

    fun submitRawPaymentInput(value: String)

    fun openScanner()
}

/**
 * The host-owned scanner surface. A lens places it exactly once; it never starts or stops a
 * camera itself. [compact] renders the glyph without the app title and hint.
 */
class PaymentHubScannerSlot(private val content: @Composable (Modifier, Boolean) -> Unit) {
    @Composable
    fun Content(modifier: Modifier = Modifier, compact: Boolean = false) {
        content(modifier, compact)
    }
}
