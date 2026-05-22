package xyz.lilsus.papp

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.papp.di.nwcModule

@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController {
    ensureKoin()
    App()
}

private fun ensureKoin() {
    val alreadyStarted = runCatching { KoinPlatformTools.defaultContext().get() }.isSuccess
    if (!alreadyStarted) {
        startKoin {
            modules(nwcModule)
        }
    }
}
