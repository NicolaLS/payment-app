package xyz.lilsus.papp.domain.model

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
