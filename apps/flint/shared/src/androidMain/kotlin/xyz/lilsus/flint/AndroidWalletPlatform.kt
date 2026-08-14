package xyz.lilsus.flint

import android.content.Context
import xyz.lilsus.flint.integration.wallet.platform.createAndroidWalletRuntime

fun createAndroidAppRuntime(context: Context, bootstrapConfig: AppBootstrapConfig): AppRuntime =
    createAndroidWalletRuntime(context, bootstrapConfig)
