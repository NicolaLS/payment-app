package xyz.lilsus.papp.platform

import androidx.compose.ui.platform.ClipEntry

actual suspend fun ClipEntry.readPlainText(): String? {
    val clipData = clipData
    if (clipData.itemCount == 0) return null
    return buildString {
        for (i in 0 until clipData.itemCount) {
            val text = clipData.getItemAt(i).text
            if (text != null) {
                if (isNotEmpty()) append("\n")
                append(text)
            }
        }
    }.ifEmpty { null }
}
