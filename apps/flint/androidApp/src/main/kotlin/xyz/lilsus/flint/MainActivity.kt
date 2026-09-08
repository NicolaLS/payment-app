package xyz.lilsus.flint

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import xyz.lilsus.raylsuite.core.ui.orientation.CompactWindowOrientationPolicy

class MainActivity : AppCompatActivity() {
    private val orientationPolicy = CompactWindowOrientationPolicy(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Flint keeps its stricter whole-window policy; explicit receipt copying still works.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false)
        }

        orientationPolicy.apply()

        setContent {
            App(
                host = (application as FlintApplication).appHost
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
            intent.dataString?.let((application as FlintApplication).appHost::offerPaymentLink)
        }
    }
}
