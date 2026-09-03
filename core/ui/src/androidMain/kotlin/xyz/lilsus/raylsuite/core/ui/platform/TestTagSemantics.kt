package xyz.lilsus.raylsuite.core.ui.platform

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

fun Modifier.enableTestTagsAsResourceId(): Modifier = semantics {
    testTagsAsResourceId = true
}
