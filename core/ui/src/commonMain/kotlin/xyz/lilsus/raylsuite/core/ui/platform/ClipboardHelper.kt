package xyz.lilsus.raylsuite.core.ui.platform

import androidx.compose.ui.platform.ClipEntry

expect suspend fun ClipEntry.readPlainText(): String?
