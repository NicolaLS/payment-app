package xyz.lilsus.lasr.feature.payment

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

fun Modifier.tapToDismiss(enabled: Boolean, onDismiss: () -> Unit): Modifier = clickable(
    enabled = enabled,
    indication = null,
    interactionSource = null,
    onClick = onDismiss
)
