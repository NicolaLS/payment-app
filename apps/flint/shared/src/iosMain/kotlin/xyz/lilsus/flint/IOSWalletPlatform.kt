package xyz.lilsus.flint

import xyz.lilsus.flint.integration.wallet.platform.createIOSWalletRuntime

fun createIOSAppRuntime(bootstrapConfig: AppBootstrapConfig): AppRuntime =
    createIOSWalletRuntime(bootstrapConfig)
