package xyz.lilsus.blip.feature.walletdetails

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
import xyz.lilsus.blip.integration.blink.BlinkConnectionError
import xyz.lilsus.blip.integration.blink.BlinkConnectionException
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.blip.ui.BlinkUiError

class BlinkWalletDetailsViewModel(
    private val blinkWallet: BlinkWallet,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val mutableUiState = MutableStateFlow(BlinkWalletDetailsUiState())
    val uiState: StateFlow<BlinkWalletDetailsUiState> = mutableUiState.asStateFlow()

    init {
        scope.launch {
            val connection = blinkWallet.connection.value
            if (connection == null) {
                mutableUiState.update {
                    it.copy(
                        error = BlinkUiError.Connection(
                            BlinkConnectionError.MissingConnection
                        ),
                        isMissing = true
                    )
                }
                return@launch
            }

            mutableUiState.update {
                it.copy(
                    alias = connection.alias,
                    defaultWalletId = blinkWallet.getCachedDefaultWalletId()
                )
            }
        }
    }

    fun refreshDefaultWalletId() {
        scope.launch {
            mutableUiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val defaultWalletId = blinkWallet.refreshDefaultWalletId()
                mutableUiState.update {
                    it.copy(
                        isRefreshing = false,
                        defaultWalletId = defaultWalletId
                    )
                }
            } catch (error: BlinkApiException) {
                mutableUiState.update {
                    it.copy(isRefreshing = false, error = BlinkUiError.Api(error.error))
                }
            } catch (error: BlinkConnectionException) {
                mutableUiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = BlinkUiError.Connection(error.error)
                    )
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

data class BlinkWalletDetailsUiState(
    val alias: String = "",
    val defaultWalletId: String? = null,
    val isRefreshing: Boolean = false,
    val isMissing: Boolean = false,
    val error: BlinkUiError? = null
)
