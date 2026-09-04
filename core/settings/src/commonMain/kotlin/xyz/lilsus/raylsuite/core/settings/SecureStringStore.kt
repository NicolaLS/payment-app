package xyz.lilsus.raylsuite.core.settings

/** A deliberately narrow store for credential documents and other secret strings. */
interface SecureStringStore {
    fun putString(key: String, value: String)

    fun getStringOrNull(key: String): String?

    fun remove(key: String)

    fun clear()
}
