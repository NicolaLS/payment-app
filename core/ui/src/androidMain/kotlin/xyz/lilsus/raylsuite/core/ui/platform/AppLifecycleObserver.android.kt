package xyz.lilsus.raylsuite.core.ui.platform

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private class AndroidAppLifecycleObserver :
    AppLifecycleObserver,
    DefaultLifecycleObserver {
    private val foreground = MutableStateFlow(true)

    override val isInForeground: StateFlow<Boolean> = foreground.asStateFlow()

    init {
        val register = { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            register()
        } else {
            Handler(Looper.getMainLooper()).post(register)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground.value = false
    }

    override fun close() {
        val unregister = { ProcessLifecycleOwner.get().lifecycle.removeObserver(this) }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            unregister()
        } else {
            Handler(Looper.getMainLooper()).post(unregister)
        }
    }
}

actual fun createAppLifecycleObserver(): AppLifecycleObserver = AndroidAppLifecycleObserver()
