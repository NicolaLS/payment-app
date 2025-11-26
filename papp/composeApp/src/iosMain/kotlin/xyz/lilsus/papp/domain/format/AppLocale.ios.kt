package xyz.lilsus.papp.domain.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import platform.Foundation.*

actual fun currentAppLocale(): AppLocale {
    val preferred = NSLocale.preferredLanguages.firstOrNull() as? String
    val identifier = preferred ?: NSLocale.currentLocale().localeIdentifier
    return AppLocale(identifier.replace('_', '-'))
}

@Composable
actual fun rememberAppLocale(): AppLocale {
    val localeState = remember { mutableStateOf(currentAppLocale()) }
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = NSCurrentLocaleDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue(),
            usingBlock = {
                localeState.value = currentAppLocale()
            }
        )
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }
    return localeState.value
}
