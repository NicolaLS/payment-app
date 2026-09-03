package xyz.lilsus.raylsuite.feature.paymenthub.lenses

import com.russhwolf.settings.Settings
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubLensDefinition
import xyz.lilsus.raylsuite.feature.paymenthub.lens.dock.DockPaymentHubLens

/**
 * The built-in home lenses every app registers, in selector order. Removing a lens here is the
 * only registration change it needs; hub data never depends on the registered set.
 */
fun paymentHubLenses(
    @Suppress("UNUSED_PARAMETER") appSettings: Settings
): List<PaymentHubLensDefinition> = listOf(DockPaymentHubLens)
