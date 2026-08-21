package xyz.lilsus.blip.feature.walletsettings

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
import xyz.lilsus.blip.integration.blink.BlinkApiException
import xyz.lilsus.blip.integration.blink.BlinkConnectionException
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.blip.ui.BlinkUiError

class BlinkWalletSettingsViewModel(
    private val blinkWallet: BlinkWallet,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val mutableUiState = MutableStateFlow(BlinkWalletSettingsUiState())
    val uiState: StateFlow<BlinkWalletSettingsUiState> = mutableUiState.asStateFlow()

    fun refreshConnection() {
        scope.launch {
            mutableUiState.update {
                it.copy(isRefreshing = true, refreshSucceeded = false, error = null)
            }
            try {
                blinkWallet.refreshDefaultWalletId()
                mutableUiState.update {
                    it.copy(isRefreshing = false, refreshSucceeded = true)
                }
            } catch (error: BlinkApiException) {
                mutableUiState.update {
                    it.copy(isRefreshing = false, error = BlinkUiError.Api(error.error))
                }
            } catch (error: BlinkConnectionException) {
                mutableUiState.update {
                    it.copy(isRefreshing = false, error = BlinkUiError.Connection(error.error))
                }
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = BlinkUiError.Unexpected(error.message)
                    )
                }
            }
        }
    }

    fun clear() {
        scope.cancel()
    }
}

data class BlinkWalletSettingsUiState(
    val isRefreshing: Boolean = false,
    val refreshSucceeded: Boolean = false,
    val error: BlinkUiError? = null
)
