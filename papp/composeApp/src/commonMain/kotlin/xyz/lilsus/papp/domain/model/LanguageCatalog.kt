package xyz.lilsus.papp.domain.model

data class LanguageInfo(
    val code: String,
    val tag: String,
    val displayName: String,
)

object LanguageCatalog {
    private val entries = listOf(
        LanguageInfo(
            code = "en",
            tag = "en",
            displayName = "English",
        ),
        LanguageInfo(
            code = "de",
            tag = "de",
            displayName = "Deutsch",
        ),
        LanguageInfo(
            code = "es",
            tag = "es",
            displayName = "Español",
        ),
    )

    private val byCode = entries.associateBy { it.code.lowercase() }
    private val byTag = entries.associateBy { it.tag.lowercase() }

    val supported: List<LanguageInfo> = entries

    fun infoForCode(code: String): LanguageInfo? = byCode[code.lowercase()]

    fun infoForTag(tag: String): LanguageInfo? = byTag[tag.lowercase()]
    fun displayName(code: String): String = infoForCode(code)?.displayName ?: fallback.displayName

    val fallback: LanguageInfo = entries.first()
}
