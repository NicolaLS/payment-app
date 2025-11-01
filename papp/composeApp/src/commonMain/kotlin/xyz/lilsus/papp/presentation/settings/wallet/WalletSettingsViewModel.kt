package xyz.lilsus.papp.presentation.settings.wallet

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.use_cases.ClearWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletsUseCase
import xyz.lilsus.papp.domain.use_cases.SetActiveWalletUseCase

class WalletSettingsViewModel internal constructor(
    private val observeWallets: ObserveWalletsUseCase,
    private val observeActiveWallet: ObserveWalletConnectionUseCase,
    private val setActiveWallet: SetActiveWalletUseCase,
    private val clearWalletConnection: ClearWalletConnectionUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(WalletSettingsUiState())
    val uiState: StateFlow<WalletSettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<WalletSettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WalletSettingsEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            combine(
                observeWallets(),
                observeActiveWallet(),
            ) { wallets, active ->
                WalletSettingsUiState(
                    wallets = wallets.map { it.toDisplay(isActive = it.walletPublicKey == active?.walletPublicKey) }
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun selectWallet(pubKey: String) {
        scope.launch {
            setActiveWallet(pubKey)
            _events.emit(WalletSettingsEvent.WalletActivated(pubKey))
        }
    }

    fun removeWallet(pubKey: String) {
        scope.launch {
            clearWalletConnection(pubKey)
            _events.emit(WalletSettingsEvent.WalletRemoved(pubKey))
        }
    }

    fun clear() {
        scope.cancel()
    }

    private fun WalletConnection.toDisplay(isActive: Boolean): WalletRow {
        return WalletRow(
            wallet = WalletDisplay(
                pubKey = walletPublicKey,
                relay = relayUrl,
                lud16 = lud16,
                alias = alias,
            ),
            isActive = isActive,
        )
    }
}

data class WalletSettingsUiState(
    val wallets: List<WalletRow> = emptyList(),
) {
    val hasWallets: Boolean get() = wallets.isNotEmpty()
}

data class WalletRow(
    val wallet: WalletDisplay,
    val isActive: Boolean,
)

data class WalletDisplay(
    val pubKey: String,
    val relay: String?,
    val lud16: String?,
    val alias: String?,
)

sealed interface WalletSettingsEvent {
    data class WalletRemoved(val pubKey: String) : WalletSettingsEvent
    data class WalletActivated(val pubKey: String) : WalletSettingsEvent
}
