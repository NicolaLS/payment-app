package xyz.lilsus.raylsuite.core.ui.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

private class IosAppLifecycleObserver : AppLifecycleObserver {
    private val foreground = MutableStateFlow(true)

    override val isInForeground: StateFlow<Boolean> = foreground.asStateFlow()

    init {
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
            usingBlock = { foreground.value = true }
        )
        center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null,
            usingBlock = { foreground.value = false }
        )
    }
}

actual fun createAppLifecycleObserver(): AppLifecycleObserver = IosAppLifecycleObserver()
