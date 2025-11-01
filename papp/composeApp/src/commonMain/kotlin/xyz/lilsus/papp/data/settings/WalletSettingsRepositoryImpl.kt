package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

private const val KEY_WALLETS = "wallet.list"
private const val KEY_ACTIVE_PUBKEY = "wallet.active"

class WalletSettingsRepositoryImpl(
    private val settings: Settings,
) : WalletSettingsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val state = MutableStateFlow(loadState())

    override val wallets: Flow<List<WalletConnection>> = state
        .asStateFlow()
        .map { it.wallets }
        .distinctUntilChanged()

    override val walletConnection: Flow<WalletConnection?> = state
        .asStateFlow()
        .map { it.activeWallet() }
        .distinctUntilChanged()

    override suspend fun getWalletConnection(): WalletConnection? = state.value.activeWallet()

    override suspend fun getWallets(): List<WalletConnection> = state.value.wallets

    override suspend fun saveWalletConnection(connection: WalletConnection, activate: Boolean) {
        updateState { current ->
            val existing = current.wallets.filterNot { it.walletPublicKey == connection.walletPublicKey }
            val updatedWallets = existing + connection
            current.copy(
                wallets = updatedWallets,
                activePubKey = when {
                    activate -> connection.walletPublicKey
                    current.activePubKey == connection.walletPublicKey -> connection.walletPublicKey
                    else -> current.activePubKey
                },
            )
        }
    }

    override suspend fun setActiveWallet(walletPublicKey: String) {
        updateState { current ->
            if (current.wallets.none { it.walletPublicKey == walletPublicKey }) {
                current
            } else {
                current.copy(activePubKey = walletPublicKey)
            }
        }
    }

    override suspend fun removeWallet(walletPublicKey: String) {
        updateState { current ->
            val remaining = current.wallets.filterNot { it.walletPublicKey == walletPublicKey }
            val nextActive = when {
                remaining.isEmpty() -> null
                current.activePubKey == walletPublicKey -> remaining.first().walletPublicKey
                else -> current.activePubKey
            }
            current.copy(
                wallets = remaining,
                activePubKey = nextActive,
            )
        }
    }

    override suspend fun clearWalletConnection() {
        updateState { WalletState() }
    }

    private fun updateState(transform: (WalletState) -> WalletState) {
        val current = state.value
        val newState = transform(current).normalise()
        if (newState == current) return
        persist(newState)
        state.value = newState
    }

    private fun loadState(): WalletState {
        val persisted = settings.getStringOrNull(KEY_WALLETS) ?: return WalletState()
        val wallets = runCatching {
            json.decodeFromString<List<StoredWallet>>(persisted)
        }.getOrElse { emptyList() }
            .map { it.toDomain() }
        val activeKey = settings.getStringOrNull(KEY_ACTIVE_PUBKEY)
        val resolvedActive = activeKey?.takeIf { key -> wallets.any { it.walletPublicKey == key } }
            ?: wallets.firstOrNull()?.walletPublicKey
        return WalletState(wallets = wallets, activePubKey = resolvedActive).normalise()
    }

    private fun persist(state: WalletState) {
        if (state.wallets.isEmpty()) {
            settings.remove(KEY_WALLETS)
            settings.remove(KEY_ACTIVE_PUBKEY)
            return
        }
        val stored = state.wallets.map { it.toStored() }
        settings.putString(KEY_WALLETS, json.encodeToString(stored))
        val activeKey = state.activePubKey ?: state.wallets.first().walletPublicKey
        settings.putString(KEY_ACTIVE_PUBKEY, activeKey)
    }

    private fun WalletConnection.toStored(): StoredWallet = StoredWallet(
        uri = uri,
        walletPublicKey = walletPublicKey,
        relayUrl = relayUrl,
        lud16 = lud16,
        alias = alias,
    )

    private fun StoredWallet.toDomain(): WalletConnection = WalletConnection(
        uri = uri,
        walletPublicKey = walletPublicKey,
        relayUrl = relayUrl,
        lud16 = lud16,
        alias = alias,
    )

    private data class WalletState(
        val wallets: List<WalletConnection> = emptyList(),
        val activePubKey: String? = null,
    ) {
        fun activeWallet(): WalletConnection? =
            activePubKey?.let { key -> wallets.firstOrNull { it.walletPublicKey == key } }
                ?: wallets.firstOrNull()

        fun normalise(): WalletState {
            if (wallets.isEmpty()) return WalletState()
            val activeKey = activePubKey?.takeIf { key -> wallets.any { it.walletPublicKey == key } }
                ?: wallets.first().walletPublicKey
            return copy(activePubKey = activeKey)
        }
    }

    @Serializable
    private data class StoredWallet(
        val uri: String,
        val walletPublicKey: String,
        val relayUrl: String? = null,
        val lud16: String? = null,
        val alias: String? = null,
    )
}
