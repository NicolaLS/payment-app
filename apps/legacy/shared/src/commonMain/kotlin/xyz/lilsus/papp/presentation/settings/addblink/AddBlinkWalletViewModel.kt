package xyz.lilsus.papp.presentation.settings.addblink

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
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.usecases.ConnectBlinkWalletUseCase

/**
 * ViewModel for adding a Blink wallet via API key.
 */
class AddBlinkWalletViewModel internal constructor(
    private val connectBlinkWallet: ConnectBlinkWalletUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(AddBlinkWalletUiState())
    val uiState: StateFlow<AddBlinkWalletUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddBlinkWalletEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AddBlinkWalletEvent> = _events.asSharedFlow()

    fun updateAlias(alias: String) {
        _uiState.update { it.copy(alias = alias, error = null) }
    }

    fun updateApiKey(apiKey: String) {
        _uiState.update { it.copy(apiKey = apiKey, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        val alias = state.alias.trim()
        val apiKey = state.apiKey.trim()

        if (alias.isBlank()) {
            _uiState.update { it.copy(error = AppError.InvalidWalletUri("Alias is required")) }
            return
        }

        if (apiKey.isBlank()) {
            _uiState.update {
                it.copy(error = AppError.AuthenticationFailure("API key is required"))
            }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            try {
                val connection = connectBlinkWallet(apiKey = apiKey, alias = alias)

                _uiState.update { it.copy(isSaving = false) }
                _events.emit(AddBlinkWalletEvent.Success(connection))
            } catch (e: AppErrorException) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.error)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = AppError.Unexpected(e.message)
                    )
                }
            }
        }
    }

    fun cancel() {
        scope.launch {
            _events.emit(AddBlinkWalletEvent.Cancelled)
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
    val error: AppError? = null
) {
    val canSubmit: Boolean
        get() = alias.isNotBlank() && apiKey.isNotBlank() && !isSaving
}

sealed interface AddBlinkWalletEvent {
    data class Success(val connection: WalletConnection) : AddBlinkWalletEvent
    data object Cancelled : AddBlinkWalletEvent
}
