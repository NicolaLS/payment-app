package xyz.lilsus.flint

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.window.layout.WindowMetricsCalculator

class MainActivity : AppCompatActivity() {
    private var orientationListenerView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        enforceOrientationForCurrentWindow()

        setContent {
            App(
                bootstrapConfig = androidBootstrapConfig(),
                runtime = (application as FlintApplication).runtime,
                paymentLinks = (application as FlintApplication).paymentLinks
            )
        }
        deliverPaymentLink(intent)

        addOrientationListener()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverPaymentLink(intent)
    }

    override fun onDestroy() {
        removeOrientationListener()
        super.onDestroy()
    }

    private fun deliverPaymentLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.dataString?.let((application as FlintApplication).paymentLinks::offer)
        }
    }

    private fun addOrientationListener() {
        if (orientationListenerView != null) return

        val container = window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
        val listenerView =
            object : View(this) {
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
        requestedOrientation =
            if (isCompactScreen()) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            }
    }

    private fun isCompactScreen(): Boolean {
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

@Preview
@Composable
fun AppAndroidPreview() {
    App(
        bootstrapConfig = AppBootstrapConfig(AppEnvironment.DEBUG),
        runtime = previewAppRuntime(),
        paymentLinks = xyz.lilsus.flint.application.payment.createPaymentLinkInbox()
    )
}
