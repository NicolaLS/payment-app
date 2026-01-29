package xyz.lilsus.papp.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

/**
 * iOS implementation using NSNotificationCenter.
 * Observers are not removed since this is a singleton that lives for the app's lifetime.
 */
class IosAppLifecycleObserver : AppLifecycleObserver {
    private val _isInForeground = MutableStateFlow(true)
    override val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    init {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
            usingBlock = { _isInForeground.value = true }
        )
        center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null,
            usingBlock = { _isInForeground.value = false }
        )
    }
}

actual fun createAppLifecycleObserver(): AppLifecycleObserver = IosAppLifecycleObserver()
