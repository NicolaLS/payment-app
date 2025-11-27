package xyz.lilsus.papp.platform

import androidx.compose.ui.platform.ClipEntry
import platform.UIKit.UIPasteboard

actual suspend fun ClipEntry.readPlainText(): String? {
    // On iOS, we read directly from UIPasteboard as ClipEntry doesn't expose text directly
    return UIPasteboard.generalPasteboard.string
}
