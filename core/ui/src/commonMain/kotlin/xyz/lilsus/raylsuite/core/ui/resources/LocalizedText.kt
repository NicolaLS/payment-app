package xyz.lilsus.raylsuite.core.ui.resources

/** A semantic localization key implemented by the owning platform catalogs. */
interface LocalizedTextKey {
    val table: String
    val key: String
}

/** Platform-neutral text selected by an app-owned presentation projection. */
class LocalizedText(val resource: LocalizedTextKey, val argument: String? = null)

fun localizedTextWithOptionalDetail(
    detail: String?,
    generic: LocalizedTextKey,
    withDetails: LocalizedTextKey
): LocalizedText = detail?.takeUnless(String::isBlank)
    ?.let { LocalizedText(withDetails, it) }
    ?: LocalizedText(generic)
