package xyz.lilsus.raylsuite.core.ui.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Resource-backed text selected by its owning presentation layer. */
class LocalizedText(private val resource: StringResource, private val argument: String? = null) {
    @Composable
    fun resolve(): String =
        argument?.let { stringResource(resource, it) } ?: stringResource(resource)

    suspend fun resolveInCoroutine(): String =
        argument?.let { getString(resource, it) } ?: getString(resource)
}

fun localizedTextWithOptionalDetail(
    detail: String?,
    generic: StringResource,
    withDetails: StringResource
): LocalizedText = detail?.takeUnless(String::isBlank)
    ?.let { LocalizedText(withDetails, it) }
    ?: LocalizedText(generic)
