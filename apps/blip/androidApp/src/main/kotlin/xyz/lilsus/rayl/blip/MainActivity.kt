package xyz.lilsus.rayl.blip

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import xyz.lilsus.rayl.blip.platform.AndroidBlipRuntime
import xyz.lilsus.rayl.blip.presentation.BlipApp

class MainActivity : ComponentActivity() {
    private val runtime by lazy {
        AndroidBlipRuntime(applicationContext)
    }

    private var incomingPaymentUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingPaymentUri = intent.paymentUri()

        setContent {
            BlipApp(
                runtime = runtime,
                incomingPaymentUri = incomingPaymentUri,
                onIncomingPaymentUriConsumed = { incomingPaymentUri = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingPaymentUri = intent.paymentUri()
    }

    private fun Intent.paymentUri(): String? = dataString?.takeIf { action == Intent.ACTION_VIEW }
}
