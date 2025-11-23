package xyz.lilsus.papp

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.layout.WindowMetricsCalculator
import xyz.lilsus.papp.navigation.DeepLinkEvents

class MainActivity : AppCompatActivity() {
    private var orientationListenerView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        enforceOrientationForCurrentWindow()

        setContent {
            App()
        }
        intent?.data?.let(::handleDeepLink)

        addOrientationListener()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.data?.let(::handleDeepLink)
    }

    override fun onDestroy() {
        removeOrientationListener()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        PappApplication.instance.registerActivity(this)
    }

    override fun onStop() {
        PappApplication.instance.unregisterActivity(this)
        super.onStop()
    }

    private fun addOrientationListener() {
        if (orientationListenerView != null) return

        val container = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val listenerView = object : View(this) {
            override fun onConfigurationChanged(newConfig: Configuration) {
                super.onConfigurationChanged(newConfig)
                enforceOrientationForCurrentWindow()
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(0, 0)
            isFocusable = false
            isClickable = false
        }

        container.addView(listenerView)
        orientationListenerView = listenerView
    }

    private fun removeOrientationListener() {
        val container = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        orientationListenerView?.let { listener ->
            container?.removeView(listener)
        }
        orientationListenerView = null
    }

    private fun enforceOrientationForCurrentWindow() {
        requestedOrientation = if (isCompactScreen()) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    private fun handleDeepLink(uri: Uri) {
        DeepLinkEvents.emit(uri.toString())
    }

    private fun isCompactScreen(): Boolean {
        val metrics = WindowMetricsCalculator
            .getOrCreate()
            .computeMaximumWindowMetrics(this)
        val density = resources.displayMetrics.density
        val widthDp = metrics.bounds.width() / density
        val heightDp = metrics.bounds.height() / density
        val windowSizeClass = WindowSizeClass.compute(widthDp, heightDp)

        return windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT ||
            windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
