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
        intent?.data?.let { BlipDeepLinks.emit(it.toString()) }

        orientationPolicy.startListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { BlipDeepLinks.emit(it.toString()) }
    }

    override fun onDestroy() {
        orientationPolicy.stopListening()
        super.onDestroy()
    }
}
