package xyz.lilsus.raylsuite.core.settings

import com.russhwolf.settings.Settings

/** Restartable local erasure. The marker lives outside the connection store being erased. */
class ConnectionStorageReset(
    private val appSettings: Settings,
    private val connectionSettings: Settings,
    private val credentials: SecureStringStore,
    private val marker: String
) {
    val pending: Boolean get() = appSettings.getBoolean(marker, false)

    fun begin() {
        appSettings.putBoolean(marker, true)
    }

    fun finish() {
        check(pending)
        credentials.clear()
        connectionSettings.clear()
        appSettings.remove(marker)
    }

    fun resume(): Boolean {
        if (!pending) return false
        finish()
        return true
    }
}
