package xyz.lilsus.raylsuite.feature.paymenthub.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubLensPreferences
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubLensDefinition
import xyz.lilsus.raylsuite.feature.paymenthub.resolvePaymentHubLensId

/**
 * Resolves the lens to show from the stored ID and the registered definitions, replacing a
 * stale stored ID with the fallback. Returns `null` only when no lens is registered.
 */
@Composable
fun rememberSelectedPaymentHubLens(
    preferences: PaymentHubLensPreferences,
    definitions: List<PaymentHubLensDefinition>
): PaymentHubLensDefinition? {
    val storedId by preferences.selectedLensId.collectAsState()
    val resolved =
        remember(storedId, definitions) {
            val resolvedId = resolvePaymentHubLensId(storedId, definitions.map { it.id })
            definitions.firstOrNull { it.id == resolvedId }
        }
    LaunchedEffect(storedId, resolved) {
        if (resolved != null && storedId != resolved.id) preferences.select(resolved.id)
    }
    return resolved
}
