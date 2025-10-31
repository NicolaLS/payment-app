package xyz.lilsus.papp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }

    override fun onStart() {
        super.onStart()
        PappApplication.instance.registerActivity(this)
    }

    override fun onStop() {
        PappApplication.instance.unregisterActivity(this)
        super.onStop()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
