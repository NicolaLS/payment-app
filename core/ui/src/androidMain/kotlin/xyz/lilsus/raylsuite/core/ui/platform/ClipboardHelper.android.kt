package xyz.lilsus.raylsuite.core.ui.platform

import androidx.compose.ui.platform.ClipEntry

actual suspend fun ClipEntry.readPlainText(): String? {
    if (clipData.itemCount == 0) return null
    return buildString {
        for (index in 0 until clipData.itemCount) {
            clipData.getItemAt(index).text?.let { text ->
                if (isNotEmpty()) append('\n')
                append(text)
            }
        }
    }.ifEmpty { null }
}
