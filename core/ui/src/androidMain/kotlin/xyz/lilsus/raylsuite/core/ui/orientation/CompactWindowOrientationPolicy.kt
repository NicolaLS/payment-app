package xyz.lilsus.raylsuite.core.ui.orientation

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import androidx.window.layout.WindowMetricsCalculator

/** Keeps compact windows in portrait while allowing larger windows to follow the user. */
class CompactWindowOrientationPolicy(private val activity: Activity) {
    private var configurationListenerView: View? = null

    fun apply() {
        activity.requestedOrientation =
            if (activity.isCompactScreen()) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            }
    }

    fun startListening() {
        if (configurationListenerView != null) return

        val container =
            activity.window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val listenerView =
            object : View(activity) {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    super.onConfigurationChanged(newConfig)
                    apply()
                }
            }.apply {
                layoutParams = ViewGroup.LayoutParams(0, 0)
                isFocusable = false
                isClickable = false
            }

        container.addView(listenerView)
        configurationListenerView = listenerView
    }

    fun stopListening() {
        val container = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        configurationListenerView?.let { listener ->
            container?.removeView(listener)
        }
        configurationListenerView = null
    }

    private fun Activity.isCompactScreen(): Boolean {
        val metrics =
            WindowMetricsCalculator
                .getOrCreate()
                .computeMaximumWindowMetrics(this)
        val density = resources.displayMetrics.density
        val widthDp = metrics.bounds.width() / density
        val heightDp = metrics.bounds.height() / density

        return widthDp < COMPACT_WINDOW_DP || heightDp < COMPACT_WINDOW_DP
    }
}

private const val COMPACT_WINDOW_DP = 600f
