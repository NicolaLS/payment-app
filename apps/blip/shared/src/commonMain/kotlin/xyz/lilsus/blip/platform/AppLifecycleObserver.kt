package xyz.lilsus.blip.platform

import kotlinx.coroutines.flow.StateFlow

interface AppLifecycleObserver {
    val isInForeground: StateFlow<Boolean>
}

expect fun createAppLifecycleObserver(): AppLifecycleObserver
