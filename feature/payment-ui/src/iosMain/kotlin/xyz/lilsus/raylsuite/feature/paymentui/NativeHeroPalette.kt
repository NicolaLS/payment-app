package xyz.lilsus.raylsuite.feature.paymentui

import xyz.lilsus.raylsuite.core.ui.hero.RaylHeroPhase
import xyz.lilsus.raylsuite.core.ui.hero.raylHeroColorArgb

/** The hero's colour for one phase, in both schemes, so SwiftUI can pick with the environment. */
data class NativeHeroPalette(val lightArgb: Long, val darkArgb: Long)

/**
 * The palette for a hero phase value.
 *
 * The phase itself stays shared: Kotlin decides which phase a payment is in, and the native
 * renderer owns how that phase looks and moves.
 */
fun nativeHeroPalette(phaseValue: String): NativeHeroPalette {
    val phase = phaseValue.toRaylHeroPhase()
    return NativeHeroPalette(
        lightArgb = raylHeroColorArgb(phase, darkTheme = false),
        darkArgb = raylHeroColorArgb(phase, darkTheme = true)
    )
}

fun PaymentScreenState.toNativeHeroPhaseValue(): String = when (toHeroPhase()) {
    RaylHeroPhase.Ready -> NATIVE_HERO_READY
    RaylHeroPhase.Acknowledged -> NATIVE_HERO_ACKNOWLEDGED
    RaylHeroPhase.Processing -> NATIVE_HERO_PROCESSING
    RaylHeroPhase.Succeeded -> NATIVE_HERO_SUCCEEDED
    RaylHeroPhase.Failed -> NATIVE_HERO_FAILED
}

private fun String.toRaylHeroPhase(): RaylHeroPhase = when (this) {
    NATIVE_HERO_ACKNOWLEDGED -> RaylHeroPhase.Acknowledged
    NATIVE_HERO_PROCESSING -> RaylHeroPhase.Processing
    NATIVE_HERO_SUCCEEDED -> RaylHeroPhase.Succeeded
    NATIVE_HERO_FAILED -> RaylHeroPhase.Failed
    else -> RaylHeroPhase.Ready
}

private const val NATIVE_HERO_READY = "ready"
private const val NATIVE_HERO_ACKNOWLEDGED = "acknowledged"
private const val NATIVE_HERO_PROCESSING = "processing"
private const val NATIVE_HERO_SUCCEEDED = "succeeded"
private const val NATIVE_HERO_FAILED = "failed"
