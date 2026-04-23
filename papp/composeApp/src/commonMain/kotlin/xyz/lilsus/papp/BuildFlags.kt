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
 * Stable identifier for the currently installed app variant.
 *
 * Settings storage derives from this so local debug installs can keep onboarding and wallet data
 * isolated from release installs.
 */
expect val appStorageNamespace: String
