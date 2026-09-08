package xyz.lilsus.raylsuite.core.ui.platform

import platform.UIKit.UIPasteboard

/** Tracks an explicit credential paste and preserves any subsequent clipboard change. */
class CredentialClipboard {
    private var pastedText: String? = null
    private var changeCount: Long? = null

    fun read(): String? {
        val clipboard = UIPasteboard.generalPasteboard
        pastedText = clipboard.string.takeIf { clipboard.numberOfItems == 1L }
        changeCount = clipboard.changeCount
        return pastedText
    }

    fun retainFor(value: String) {
        if (pastedText?.trim() != value.trim()) discard()
    }

    fun clearAfterSaving() {
        val expected = pastedText ?: return
        val expectedChangeCount = changeCount
        discard()
        val clipboard = UIPasteboard.generalPasteboard
        if (clipboard.changeCount == expectedChangeCount &&
            clipboard.numberOfItems == 1L && clipboard.string == expected
        ) {
            clipboard.items = emptyList<Map<Any?, *>>()
        }
    }

    fun discard() {
        pastedText = null
        changeCount = null
    }
}
