package xyz.lilsus.papp.presentation.settings.wallet

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import xyz.lilsus.papp.domain.use_cases.ClearWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase

class WalletSettingsViewModel internal constructor(
    private val observeWalletConnection: ObserveWalletConnectionUseCase,
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
            observeWalletConnection().collect { connection ->
                _uiState.value = WalletSettingsUiState(
                    wallet = connection?.let {
                        WalletDisplay(
                            pubKey = it.walletPublicKey,
                            relay = it.relayUrl,
                            lud16 = it.lud16,
                        )
                    }
                )
            }
        }
    }

    fun removeWallet() {
        scope.launch {
            clearWalletConnection()
            _events.emit(WalletSettingsEvent.WalletRemoved)
        }
    }

    fun clear() {
        scope.cancel()
    }
}

data class WalletSettingsUiState(
    val wallet: WalletDisplay? = null,
)

data class WalletDisplay(
    val pubKey: String,
    val relay: String?,
    val lud16: String?,
)

sealed interface WalletSettingsEvent {
    data object WalletRemoved : WalletSettingsEvent
}
