package xyz.lilsus.blip

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import xyz.lilsus.raylsuite.core.ui.orientation.CompactWindowOrientationPolicy

open class MainActivity : AppCompatActivity() {
    private val orientationPolicy = CompactWindowOrientationPolicy(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        orientationPolicy.apply()

        setContent {
            App()
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
        if (intent?.action != Intent.ACTION_VIEW) return
        intent.dataString
            ?.takeIf { it.length <= MAX_PAYMENT_LINK_LENGTH }
            ?.let(BlipDeepLinks::emit)
    }

    private companion object {
        const val MAX_PAYMENT_LINK_LENGTH = 8 * 1024
    }
}
