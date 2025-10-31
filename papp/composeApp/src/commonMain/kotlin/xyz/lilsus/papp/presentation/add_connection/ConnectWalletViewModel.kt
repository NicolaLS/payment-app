package xyz.lilsus.papp.presentation.add_connection

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import io.github.nostr.nwc.parseNwcUri
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.SetWalletConnectionUseCase

class ConnectWalletViewModel internal constructor(
    private val setWalletConnection: SetWalletConnectionUseCase,
    private val observeWalletConnection: ObserveWalletConnectionUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(ConnectWalletUiState())
    val uiState: StateFlow<ConnectWalletUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConnectWalletEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ConnectWalletEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            observeWalletConnection().collect { connection ->
                if (connection != null && _uiState.value.isSubmitting) {
                    _uiState.value = ConnectWalletUiState()
                }
            }
        }
    }

    fun updateUri(uri: String) {
        _uiState.update { current ->
            current.copy(uri = uri, error = null)
        }
    }

    fun prefillUriIfValid(candidate: String?) {
        if (candidate.isNullOrBlank()) return
        if (_uiState.value.uri.isNotBlank()) return

        val trimmed = candidate.trim()
        val isValid = runCatching { parseNwcUri(trimmed) }.isSuccess
        if (!isValid) return

        _uiState.update { it.copy(uri = trimmed, error = null) }
    }

    fun submit() {
        val uri = _uiState.value.uri
        if (uri.isBlank()) {
            _uiState.update { it.copy(error = AppError.InvalidWalletUri()) }
            return
        }
        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            runCatching {
                setWalletConnection(uri)
            }.onSuccess {
                _events.emit(ConnectWalletEvent.Success)
                _uiState.value = ConnectWalletUiState()
            }.onFailure { throwable ->
                val error = when (throwable) {
                    is AppErrorException -> throwable.error
                    else -> AppError.Unexpected(throwable.message)
                }
                _uiState.update { it.copy(isSubmitting = false, error = error) }
            }
        }
    }

    fun cancel() {
        scope.launch {
            _events.emit(ConnectWalletEvent.Cancelled)
        }
    }

    fun clear() {
        scope.cancel()
    }
}

data class ConnectWalletUiState(
    val uri: String = "",
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
)

sealed interface ConnectWalletEvent {
    data object Success : ConnectWalletEvent
    data object Cancelled : ConnectWalletEvent
}
