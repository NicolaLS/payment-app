package xyz.lilsus.flint

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import xyz.lilsus.raylsuite.core.ui.orientation.CompactWindowOrientationPolicy

class MainActivity : AppCompatActivity() {
    private val orientationPolicy = CompactWindowOrientationPolicy(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        orientationPolicy.apply()

        setContent {
            App(
                bootstrapConfig = androidBootstrapConfig(),
                runtime = (application as FlintApplication).runtime,
                paymentLinks = (application as FlintApplication).paymentLinks
            )
        }
        deliverPaymentLink(intent)

        orientationPolicy.startListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverPaymentLink(intent)
    }

    override fun onDestroy() {
        orientationPolicy.stopListening()
        super.onDestroy()
    }

    private fun deliverPaymentLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.dataString?.let((application as FlintApplication).paymentLinks::offer)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(
        bootstrapConfig = AppBootstrapConfig(AppEnvironment.DEBUG),
        runtime = previewAppRuntime(),
        paymentLinks = xyz.lilsus.flint.application.payment.createPaymentLinkInbox()
    )
}
