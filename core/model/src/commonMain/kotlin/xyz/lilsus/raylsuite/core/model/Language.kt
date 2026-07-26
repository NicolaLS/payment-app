package xyz.lilsus.raylsuite.core.model

data class LanguageInfo(val code: String, val tag: String)

object LanguageCatalog {
    private val entries =
        listOf(
            LanguageInfo(code = "en", tag = "en"),
            LanguageInfo(code = "de", tag = "de"),
            LanguageInfo(code = "es", tag = "es")
        )

    private val byCode = entries.associateBy { it.code.lowercase() }
    private val byTag = entries.associateBy { it.tag.lowercase() }

    val supported: List<LanguageInfo> = entries

    val fallback: LanguageInfo = entries.first()

    fun infoForCode(code: String): LanguageInfo? = byCode[code.lowercase()]

    fun infoForTag(tag: String): LanguageInfo? = byTag[tag.lowercase()]
}

sealed interface LanguagePreference {
    val resolvedTag: String
    val deviceTag: String

    data class System(override val resolvedTag: String) : LanguagePreference {
        override val deviceTag: String = resolvedTag
    }

    data class Override(
        val overrideTag: String,
        override val resolvedTag: String,
        override val deviceTag: String
    ) : LanguagePreference
}
