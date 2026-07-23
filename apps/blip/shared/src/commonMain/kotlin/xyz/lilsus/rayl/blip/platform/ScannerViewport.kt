package xyz.lilsus.rayl.blip.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun ScannerViewport(
    active: Boolean,
    onQrCode: (String) -> Unit,
    onPermissionDenied: () -> Unit,
    modifier: Modifier = Modifier
)
