package xyz.lilsus.raylsuite.core.ui.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Tracks only an explicit credential paste, without retaining a later clipboard value. */
class CredentialClipboard(context: Context) {
    private val clipboard = checkNotNull(context.getSystemService(ClipboardManager::class.java))
    private var pastedTimestamp: Long? = null
    private var pastedText: String? = null
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { discard() }

    init {
        clipboard.addPrimaryClipChangedListener(listener)
    }

    fun read(): String? {
        val clip = clipboard.primaryClip
        pastedText = clip?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text?.toString()
        pastedTimestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            clip?.description?.timestamp
        } else {
            null
        }
        return pastedText
    }

    fun retainFor(value: String) {
        if (pastedText?.trim() != value.trim()) discard()
    }

    fun clearAfterSaving() {
        val expected = pastedText ?: return
        val expectedTimestamp = pastedTimestamp
        discard()
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount != 1 || clip.getItemAt(0).text?.toString() != expected) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            clip.description.timestamp != expectedTimestamp
        ) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    fun discard() {
        pastedText = null
        pastedTimestamp = null
    }

    fun close() {
        discard()
        clipboard.removePrimaryClipChangedListener(listener)
    }
}

@Composable
fun rememberCredentialClipboard(): CredentialClipboard {
    val context = LocalContext.current.applicationContext
    val clipboard = remember(context) { CredentialClipboard(context) }
    DisposableEffect(clipboard) {
        onDispose(clipboard::close)
    }
    return clipboard
}
