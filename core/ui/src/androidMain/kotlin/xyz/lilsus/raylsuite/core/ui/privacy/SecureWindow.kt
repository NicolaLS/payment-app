package xyz.lilsus.raylsuite.core.ui.privacy

import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import java.util.WeakHashMap

/** Protects the host window while a credential screen is composed. */
@Composable
fun SecureWindow() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        SecureWindows.acquire(window)
        onDispose { SecureWindows.release(window) }
    }
}

// Composition effects run on the main thread. Count overlapping navigation surfaces so
// disposing the outgoing screen cannot remove the incoming screen's protection.
private object SecureWindows {
    private data class Protection(val originallySecure: Boolean, var owners: Int = 1)

    private val windows = WeakHashMap<Window, Protection>()

    fun acquire(window: Window) {
        val protection = windows[window]
        if (protection != null) {
            protection.owners += 1
            return
        }
        windows[window] = Protection(
            originallySecure =
                window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun release(window: Window) {
        val protection = windows[window] ?: return
        protection.owners -= 1
        if (protection.owners != 0) return
        windows.remove(window)
        if (!protection.originallySecure) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
