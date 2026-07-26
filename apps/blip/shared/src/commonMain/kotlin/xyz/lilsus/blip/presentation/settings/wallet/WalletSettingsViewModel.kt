package xyz.lilsus.blip.presentation.settings.wallet

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.blip.domain.model.WalletConnection
import xyz.lilsus.blip.domain.model.WalletType
import xyz.lilsus.blip.domain.usecases.ObserveWalletConnectionUseCase
import xyz.lilsus.blip.domain.usecases.RemoveWalletConnectionUseCase

class WalletSettingsViewModel internal constructor(
    observeWalletConnection: ObserveWalletConnectionUseCase,
    private val removeWalletConnection: RemoveWalletConnectionUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(WalletSettingsUiState())
    val uiState: StateFlow<WalletSettingsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            observeWalletConnection().collectLatest { connection ->
                _uiState.value = WalletSettingsUiState(connection?.toDisplay())
            }
        }
    }

    fun disconnectWallet() {
        scope.launch {
            removeWalletConnection()
        }
    }

    fun clear() {
        scope.cancel()
    }

    private fun WalletConnection.toDisplay(): WalletDisplay = WalletDisplay(
        connectionId = walletPublicKey,
        relay = relayUrl,
        lud16 = lud16,
        alias = alias,
        type = type
    )
}

data class WalletSettingsUiState(val wallet: WalletDisplay? = null) {
    val hasWallet: Boolean get() = wallet != null
}

data class WalletDisplay(
    val connectionId: String,
    val relay: String?,
    val lud16: String?,
    val alias: String?,
    val type: WalletType = WalletType.NWC
)
