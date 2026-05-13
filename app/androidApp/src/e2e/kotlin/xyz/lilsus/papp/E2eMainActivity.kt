package xyz.lilsus.papp

import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.lilsus.papp.e2e.applyE2eLaunchArguments
import xyz.lilsus.papp.e2e.e2ePaymentInput
import xyz.lilsus.papp.navigation.DeepLinkEvents
import xyz.lilsus.papp.navigation.PaymentDeepLinkEvents
import xyz.lilsus.papp.platform.AndroidAppContext

class E2eMainActivity : AppCompatActivity() {
    private var orientationListenerView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        enforceOrientationForCurrentWindow()
        applyE2eLaunchArguments(intent)

        setContent {
            App()
        }
        dispatchE2ePaymentInput(intent)
        intent?.data?.let(::handleDeepLink)

        addOrientationListener()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyE2eLaunchArguments(intent)
        dispatchE2ePaymentInput(intent)
        intent.data?.let(::handleDeepLink)
    }

    override fun onDestroy() {
        removeOrientationListener()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        AndroidAppContext.registerActivity(this)
    }

    override fun onStop() {
        AndroidAppContext.unregisterActivity(this)
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

    private fun dispatchE2ePaymentInput(intent: Intent?) {
        val paymentInput = intent.e2ePaymentInput() ?: return
        lifecycleScope.launch {
            delay(500)
            PaymentDeepLinkEvents.emit(paymentInput)
        }
    }

    private fun isCompactScreen(): Boolean {
        val metrics = WindowMetricsCalculator
            .getOrCreate()
            .computeMaximumWindowMetrics(this)
        val density = resources.displayMetrics.density
        val widthDp = metrics.bounds.width() / density
        val heightDp = metrics.bounds.height() / density

        return widthDp < 600f || heightDp < 600f
    }
}

@Preview
@Composable
fun AppAndroidE2ePreview() {
    App()
}
