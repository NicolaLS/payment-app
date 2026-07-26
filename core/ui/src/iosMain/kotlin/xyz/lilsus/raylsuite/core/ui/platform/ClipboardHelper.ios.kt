package xyz.lilsus.raylsuite.core.ui.platform

import androidx.compose.ui.platform.ClipEntry
import platform.UIKit.UIPasteboard

actual suspend fun ClipEntry.readPlainText(): String? = UIPasteboard.generalPasteboard.string
