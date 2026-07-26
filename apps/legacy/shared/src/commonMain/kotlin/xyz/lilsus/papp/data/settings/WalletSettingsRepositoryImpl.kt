package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletMetadataSnapshot
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

private const val KEY_WALLET = "wallet.connection"
private const val LEGACY_KEY_WALLETS = "wallet.list"
private const val LEGACY_KEY_ACTIVE_PUBKEY = "wallet.active"

class WalletSettingsRepositoryImpl(
    private val settings: Settings,
    private val onWalletRemoved: suspend (WalletConnection) -> Unit = {},
    private val onLegacyWalletsMigrated: (
        retained: WalletConnection?,
        discarded: List<WalletConnection>
    ) -> Unit = { _, _ -> }
) : WalletSettingsRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val mutex = Mutex()
    private val loadedWallet = loadWallet()
    private val state = MutableStateFlow(loadedWallet.wallet)

    init {
        loadedWallet.legacyWallets?.let { legacyWallets ->
            onLegacyWalletsMigrated(
                loadedWallet.wallet,
                legacyWallets.filterNot { it == loadedWallet.wallet }
            )
            persist(loadedWallet.wallet)
            settings.remove(LEGACY_KEY_WALLETS)
            settings.remove(LEGACY_KEY_ACTIVE_PUBKEY)
        }
    }

    override val walletConnection: Flow<WalletConnection?> = state.asStateFlow()

    override suspend fun getWalletConnection(): WalletConnection? = state.value

    override suspend fun saveWalletConnection(connection: WalletConnection) {
        mutex.withLock {
            val current = state.value
            if (current != null && current != connection) {
                throw AppErrorException(AppError.WalletAlreadyConnected)
            }
            if (current == connection) return
            persist(connection)
            state.value = connection
        }
    }

    override suspend fun clearWalletConnection() {
        val removed = mutex.withLock {
            val current = state.value ?: return
            persist(null)
            state.value = null
            current
        }
        onWalletRemoved(removed)
    }

    private fun loadWallet(): LoadedWallet {
        settings.getStringOrNull(KEY_WALLET)?.let { persisted ->
            val wallet = runCatching {
                json.decodeFromString<StoredWallet>(persisted).toDomain()
            }.getOrNull()
            return LoadedWallet(wallet = wallet)
        }

        val legacyPersisted = settings.getStringOrNull(LEGACY_KEY_WALLETS)
            ?: return LoadedWallet(wallet = null)
        val legacyWallets = runCatching {
            json.decodeFromString<List<StoredWallet>>(legacyPersisted)
        }.getOrElse { emptyList() }
            .map { it.toDomain() }
        val activeKey = settings.getStringOrNull(LEGACY_KEY_ACTIVE_PUBKEY)
        val retained = legacyWallets.firstOrNull { it.walletPublicKey == activeKey }
            ?: legacyWallets.firstOrNull()
        return LoadedWallet(wallet = retained, legacyWallets = legacyWallets)
    }

    private fun persist(wallet: WalletConnection?) {
        if (wallet == null) {
            settings.remove(KEY_WALLET)
        } else {
            settings.putString(KEY_WALLET, json.encodeToString(wallet.toStored()))
        }
    }

    private fun WalletConnection.toStored(): StoredWallet = StoredWallet(
        uri = uri,
        walletPublicKey = walletPublicKey,
        relayUrl = relayUrl,
        lud16 = lud16,
        alias = alias,
        metadata = metadata?.toStored(),
        type = type.name
    )

    private fun StoredWallet.toDomain(): WalletConnection = WalletConnection(
        uri = uri,
        walletPublicKey = walletPublicKey,
        relayUrl = relayUrl,
        lud16 = lud16,
        alias = alias,
        metadata = metadata?.toDomain(),
        type = type?.let { runCatching { WalletType.valueOf(it) }.getOrNull() } ?: WalletType.NWC
    )

    private data class LoadedWallet(
        val wallet: WalletConnection?,
        val legacyWallets: List<WalletConnection>? = null
    )

    @Serializable
    private data class StoredWallet(
        val uri: String = "",
        val walletPublicKey: String,
        val relayUrl: String? = null,
        val lud16: String? = null,
        val alias: String? = null,
        val metadata: StoredWalletMetadata? = null,
        // null defaults to NWC for backward compatibility
        val type: String? = null
    )

    @Serializable
    private data class StoredWalletMetadata(
        val methods: Set<String> = emptySet(),
        val encryptionSchemes: Set<String> = emptySet(),
        val negotiatedEncryption: String? = null,
        val encryptionDefaultedToNip04: Boolean = false,
        val notifications: Set<String> = emptySet(),
        val network: String? = null,
        val color: String? = null
    )

    private fun WalletMetadataSnapshot.toStored(): StoredWalletMetadata = StoredWalletMetadata(
        methods = methods,
        encryptionSchemes = encryptionSchemes,
        negotiatedEncryption = negotiatedEncryption,
        encryptionDefaultedToNip04 = encryptionDefaultedToNip04,
        notifications = notifications,
        network = network,
        color = color
    )

    private fun StoredWalletMetadata.toDomain(): WalletMetadataSnapshot = WalletMetadataSnapshot(
        methods = methods,
        encryptionSchemes = encryptionSchemes,
        negotiatedEncryption = negotiatedEncryption,
        encryptionDefaultedToNip04 = encryptionDefaultedToNip04,
        notifications = notifications,
        network = network,
        color = color
    )
}
