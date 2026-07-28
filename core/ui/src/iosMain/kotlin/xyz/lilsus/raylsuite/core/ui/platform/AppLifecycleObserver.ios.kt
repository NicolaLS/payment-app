package xyz.lilsus.raylsuite.core.ui.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.darwin.NSObjectProtocol

private class IosAppLifecycleObserver : AppLifecycleObserver {
    private val foreground = MutableStateFlow(true)
    private val center = NSNotificationCenter.defaultCenter
    private val foregroundObserver: NSObjectProtocol
    private val backgroundObserver: NSObjectProtocol

    override val isInForeground: StateFlow<Boolean> = foreground.asStateFlow()

    init {
        foregroundObserver = center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
            usingBlock = { foreground.value = true }
        )
        backgroundObserver = center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null,
            usingBlock = { foreground.value = false }
        )
    }

    override fun close() {
        center.removeObserver(foregroundObserver)
        center.removeObserver(backgroundObserver)
    }
}

actual fun createAppLifecycleObserver(): AppLifecycleObserver = IosAppLifecycleObserver()
