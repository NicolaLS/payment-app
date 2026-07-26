@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalForeignApi::class)

package xyz.lilsus.blip.presentation.common

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.input.PlatformImeOptions
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemStyle
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.UIToolbar

actual fun doneKeyboardPlatformImeOptions(
    doneLabel: String,
    onDone: () -> Unit
): PlatformImeOptions? = PlatformImeOptions {
    inputAccessoryView(DoneToolbar(doneLabel = doneLabel, onDone = onDone))
}

private class DoneToolbar(doneLabel: String, private val onDone: () -> Unit) :
    UIToolbar(frame = CGRectZero.readValue()) {
    init {
        sizeToFit()

        val spacer = UIBarButtonItem(
            barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemFlexibleSpace,
            target = null,
            action = null
        )
        val done = UIBarButtonItem(
            title = doneLabel,
            style = UIBarButtonItemStyle.UIBarButtonItemStyleDone,
            target = this,
            action = NSSelectorFromString("doneTapped")
        )

        setItems(listOf(spacer, done), animated = false)
    }

    @OptIn(BetaInteropApi::class)
    @ObjCAction
    fun doneTapped() {
        onDone()
    }
}
