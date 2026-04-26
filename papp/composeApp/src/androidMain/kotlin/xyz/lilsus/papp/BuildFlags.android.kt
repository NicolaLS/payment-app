package xyz.lilsus.papp

actual val isDebugBuild: Boolean = BuildConfig.DEBUG

actual val appStorageNamespace: String = BuildConfig.APPLICATION_ID

actual val allowE2eHooks: Boolean = BuildConfig.ALLOW_E2E_HOOKS
