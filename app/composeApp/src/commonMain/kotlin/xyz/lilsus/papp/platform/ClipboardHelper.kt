package xyz.lilsus.papp.platform

import androidx.compose.ui.platform.ClipEntry

/**
 * Extension function to read plain text from a ClipEntry.
 * Platform-specific implementations handle the extraction differently.
 */
expect suspend fun ClipEntry.readPlainText(): String?
