package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

private const val KEY_URI = "wallet.uri"
private const val KEY_PUBKEY = "wallet.pubkey"
private const val KEY_RELAY = "wallet.relay"
private const val KEY_LUD16 = "wallet.lud16"

class WalletSettingsRepositoryImpl(
    private val settings: Settings,
) : WalletSettingsRepository {

    private val state = MutableStateFlow(readWallet())

    override val walletConnection: Flow<WalletConnection?> = state.asStateFlow()

    override suspend fun getWalletConnection(): WalletConnection? = state.value

    override suspend fun saveWalletConnection(connection: WalletConnection) {
        settings.putString(KEY_URI, connection.uri)
        settings.putString(KEY_PUBKEY, connection.walletPublicKey)
        connection.relayUrl?.let { settings.putString(KEY_RELAY, it) } ?: settings.remove(KEY_RELAY)
        connection.lud16?.let { settings.putString(KEY_LUD16, it) } ?: settings.remove(KEY_LUD16)
        state.value = connection
    }

    override suspend fun clearWalletConnection() {
        settings.remove(KEY_URI)
        settings.remove(KEY_PUBKEY)
        settings.remove(KEY_RELAY)
        settings.remove(KEY_LUD16)
        state.value = null
    }

    private fun readWallet(): WalletConnection? {
        val uri = settings.getStringOrNull(KEY_URI) ?: return null
        val pubKey = settings.getStringOrNull(KEY_PUBKEY) ?: return null
        val relay = settings.getStringOrNull(KEY_RELAY)
        val lud16 = settings.getStringOrNull(KEY_LUD16)
        return WalletConnection(
            uri = uri,
            walletPublicKey = pubKey,
            relayUrl = relay,
            lud16 = lud16,
        )
    }
}
