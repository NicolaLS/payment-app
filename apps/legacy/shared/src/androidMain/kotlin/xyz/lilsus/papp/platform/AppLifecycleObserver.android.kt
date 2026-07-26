package xyz.lilsus.papp.platform

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidAppLifecycleObserver :
    AppLifecycleObserver,
    DefaultLifecycleObserver {
    private val _isInForeground = MutableStateFlow(true) // Assume foreground on start
    override val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    init {
        // ProcessLifecycleOwner requires main thread for addObserver
        val register = { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            register()
        } else {
            Handler(Looper.getMainLooper()).post(register)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        _isInForeground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        _isInForeground.value = false
    }
}

actual fun createAppLifecycleObserver(): AppLifecycleObserver = AndroidAppLifecycleObserver()
