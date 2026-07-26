package xyz.lilsus.blip

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

actual fun Modifier.enableMaestroTestTagsAsResourceId(): Modifier = semantics {
    testTagsAsResourceId = true
}
