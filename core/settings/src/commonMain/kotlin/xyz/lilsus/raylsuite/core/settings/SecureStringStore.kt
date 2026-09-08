package xyz.lilsus.raylsuite.core.settings

/**
 * A deliberately narrow store for credential documents and other secret strings.
 *
 * Mutations return only after persistence succeeds and throw if storage fails. Reads return null
 * only for an absent key; storage and decryption failures propagate to the caller.
 */
interface SecureStringStore {
    fun putString(key: String, value: String)

    fun getStringOrNull(key: String): String?

    fun remove(key: String)

    fun clear()
}
