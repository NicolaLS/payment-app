package xyz.lilsus.raylsuite.core.ui.hero

/**
 * What the hero is showing. Shared because payment state decides it; each platform owns how the
 * phase is drawn and animated.
 */
enum class RaylHeroPhase {
    Ready,
    Acknowledged,
    Processing,
    Succeeded,
    Failed
}
