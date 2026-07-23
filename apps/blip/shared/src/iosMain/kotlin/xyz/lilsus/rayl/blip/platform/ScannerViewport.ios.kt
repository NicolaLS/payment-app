package xyz.lilsus.rayl.blip.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun ScannerViewport(
    active: Boolean,
    onQrCode: (String) -> Unit,
    onPermissionDenied: () -> Unit,
    modifier: Modifier
) {
    Box(modifier)
}
