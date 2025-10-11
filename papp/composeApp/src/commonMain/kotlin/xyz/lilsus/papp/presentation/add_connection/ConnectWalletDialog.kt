package xyz.lilsus.papp.presentation.add_connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun ConnectWalletDialog(
    modifier: Modifier = Modifier,
    pubKeyHex: String? = null,
    relay: String? = null,
    secretHex: String? = null,
    lud16: String? = null,
) {
    Surface {
            Column() {
                Text("TBD.")
                Text(pubKeyHex ?: "")
                Text(relay ?: "")
                Text(secretHex ?: "")
                Text(lud16 ?: "")
            }

    }
}