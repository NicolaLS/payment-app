package xyz.lilsus.papp.presentation.settings.wallet

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.domain.usecases.GetBlinkDefaultWalletIdUseCase
import xyz.lilsus.papp.domain.usecases.RefreshBlinkDefaultWalletIdUseCase

class WalletDetailsViewModel internal constructor(
    private val walletId: String,
    private val walletSettingsRepository: WalletSettingsRepository,
    private val getBlinkDefaultWalletId: GetBlinkDefaultWalletIdUseCase,
    private val refreshBlinkDefaultWalletId: RefreshBlinkDefaultWalletIdUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(WalletDetailsUiState(walletId = walletId))
    val uiState: StateFlow<WalletDetailsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            val wallet = walletSettingsRepository.getWallets()
                .firstOrNull { it.walletPublicKey == walletId }
            if (wallet == null) {
                _uiState.update {
                    it.copy(
                        error = AppError.MissingWalletConnection,
                        isMissing = true
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    alias = wallet.alias,
                    walletType = wallet.type,
                    blinkDefaultWalletId = if (wallet.type == WalletType.BLINK) {
                        getBlinkDefaultWalletId(walletId)
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun refreshDefaultWalletId() {
        if (_uiState.value.walletType != WalletType.BLINK) return

        scope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val defaultWalletId = refreshBlinkDefaultWalletId(walletId)
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        blinkDefaultWalletId = defaultWalletId
                    )
                }
            } catch (e: AppErrorException) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = e.error
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = AppError.Unexpected(e.message)
                    )
                }
            }
        }
    }

    fun clear() {
        scope.cancel()
    }
}

data class WalletDetailsUiState(
    val walletId: String,
    val alias: String? = null,
    val walletType: WalletType = WalletType.NWC,
    val blinkDefaultWalletId: String? = null,
    val isRefreshing: Boolean = false,
    val isMissing: Boolean = false,
    val error: AppError? = null
) {
    val isBlink: Boolean
        get() = walletType == WalletType.BLINK
}
