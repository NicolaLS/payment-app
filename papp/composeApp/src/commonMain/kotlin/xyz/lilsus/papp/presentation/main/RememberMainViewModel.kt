package xyz.lilsus.papp.presentation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.papp.presentation.common.rememberRetainedInstance

@Composable
fun rememberMainViewModel(): MainViewModel {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    return rememberRetainedInstance(
        factory = { koin.get<MainViewModel>() },
        onDispose = { it.clear() },
    )
}
