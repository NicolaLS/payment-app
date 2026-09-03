package xyz.lilsus.raylsuite.core.ui.resources

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Resolves a semantic text key through its owning Android resource mapping. */
@Composable
fun LocalizedText.resolve(resourceId: (LocalizedTextKey) -> Int): String =
    resolve(LocalContext.current, resourceId)

fun LocalizedText.resolve(context: Context, resourceId: (LocalizedTextKey) -> Int): String {
    @StringRes val id = resourceId(resource)
    return argument?.let { context.getString(id, it) } ?: context.getString(id)
}
