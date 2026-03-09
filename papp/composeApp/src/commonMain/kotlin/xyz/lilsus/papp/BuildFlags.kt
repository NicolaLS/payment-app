package xyz.lilsus.papp

/**
 * Platform-specific debug mode flag.
 * - Android: Uses BuildConfig.DEBUG (automatically true for debug builds, false for release)
 * - iOS: Uses Platform.isDebugBinary
 *
 * In release builds, R8/ProGuard will strip out logging calls automatically.
 */
expect val isDebugBuild: Boolean

/**
 * Local dev override for onboarding testing.
 *
 * Set this to `true` temporarily when you need to force onboarding in debug builds,
 * even if a wallet already exists. This is mainly useful on iOS, where Keychain-backed
 * wallet data survives reinstall.
 */
internal const val FORCE_SHOW_ONBOARDING_IN_DEBUG_BUILDS = false

internal val shouldForceShowOnboarding: Boolean
    get() = isDebugBuild && FORCE_SHOW_ONBOARDING_IN_DEBUG_BUILDS
