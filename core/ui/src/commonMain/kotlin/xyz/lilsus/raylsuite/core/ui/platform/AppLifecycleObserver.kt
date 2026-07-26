package xyz.lilsus.raylsuite.core.ui.platform

import kotlinx.coroutines.flow.StateFlow

interface AppLifecycleObserver {
    val isInForeground: StateFlow<Boolean>
}

expect fun createAppLifecycleObserver(): AppLifecycleObserver
