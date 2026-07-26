package xyz.lilsus.blip.feature.walletconnection

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.lilsus.blip.integration.blink.BlinkApiException
import xyz.lilsus.blip.integration.blink.BlinkConnectionError
import xyz.lilsus.blip.integration.blink.BlinkConnectionException
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.blip.integration.blink.BlinkWalletConnection
import xyz.lilsus.blip.ui.BlinkUiError

class AddBlinkWalletViewModel(
    private val blinkWallet: BlinkWallet,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val mutableUiState = MutableStateFlow(AddBlinkWalletUiState())
    val uiState: StateFlow<AddBlinkWalletUiState> = mutableUiState.asStateFlow()

    private val mutableEvents =
        MutableSharedFlow<AddBlinkWalletEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AddBlinkWalletEvent> = mutableEvents.asSharedFlow()

    fun updateAlias(alias: String) {
        mutableUiState.update { it.copy(alias = alias, error = null) }
    }

    fun updateApiKey(apiKey: String) {
        mutableUiState.update { it.copy(apiKey = apiKey, error = null) }
    }

    fun submit() {
        val state = mutableUiState.value
        val alias = state.alias.trim()
        val apiKey = state.apiKey.trim()

        if (alias.isBlank()) {
            mutableUiState.update {
                it.copy(error = BlinkUiError.Connection(BlinkConnectionError.AliasRequired))
            }
            return
        }

        if (apiKey.isBlank()) {
            mutableUiState.update {
                it.copy(error = BlinkUiError.Connection(BlinkConnectionError.ApiKeyRequired))
            }
            return
        }

        scope.launch {
            mutableUiState.update { it.copy(isSaving = true, error = null) }

            try {
                val connection = blinkWallet.connect(apiKey = apiKey, alias = alias)

                mutableUiState.update { it.copy(isSaving = false) }
                mutableEvents.emit(AddBlinkWalletEvent.Success(connection))
            } catch (error: BlinkApiException) {
                mutableUiState.update {
                    it.copy(isSaving = false, error = BlinkUiError.Api(error.error))
                }
            } catch (error: BlinkConnectionException) {
                mutableUiState.update {
                    it.copy(isSaving = false, error = BlinkUiError.Connection(error.error))
                }
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isSaving = false,
                        error = BlinkUiError.Unexpected(error.message)
                    )
                }
            }
        }
    }

    fun cancel() {
        scope.launch {
            mutableEvents.emit(AddBlinkWalletEvent.Cancelled)
        }
    }

    fun clear() {
        scope.cancel()
    }
}

data class AddBlinkWalletUiState(
    val alias: String = "",
    val apiKey: String = "",
    val isSaving: Boolean = false,
    val error: BlinkUiError? = null
) {
    val canSubmit: Boolean
        get() = alias.isNotBlank() && apiKey.isNotBlank() && !isSaving
}

sealed interface AddBlinkWalletEvent {
    data class Success(val connection: BlinkWalletConnection) : AddBlinkWalletEvent

    data object Cancelled : AddBlinkWalletEvent
}
