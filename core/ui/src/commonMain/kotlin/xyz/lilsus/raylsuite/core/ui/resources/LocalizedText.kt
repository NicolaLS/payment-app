package xyz.lilsus.raylsuite.core.ui.resources

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Resource-backed text selected by its owning presentation layer. */
class LocalizedText(internal val resource: StringResource, internal val argument: String? = null) {
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
