package xyz.lilsus.blip.presentation.addconnection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import xyz.lilsus.blip.domain.model.AppError
import xyz.lilsus.blip.domain.model.AppErrorException
import xyz.lilsus.blip.domain.model.WalletConnection
import xyz.lilsus.blip.domain.model.WalletDiscovery
import xyz.lilsus.blip.domain.model.toMetadataSnapshot
import xyz.lilsus.blip.domain.usecases.DiscoverWalletUseCase
import xyz.lilsus.blip.domain.usecases.SetWalletConnectionUseCase

class ConnectWalletViewModel internal constructor(
    private val discoverWallet: DiscoverWalletUseCase,
    private val setWalletConnection: SetWalletConnectionUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(ConnectWalletUiState())
    val uiState: StateFlow<ConnectWalletUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConnectWalletEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<ConnectWalletEvent> = _events.asSharedFlow()

    private var activeDiscoveryJob: Job? = null

    fun load(uri: String) {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = ConnectWalletUiState(uri = "", error = AppError.InvalidWalletUri())
            return
        }
        if (_uiState.value.uri == trimmed && _uiState.value.discovery != null) return

        // Cancel any in-flight discovery to prevent race conditions
        activeDiscoveryJob?.cancel()

        activeDiscoveryJob = scope.launch {
            _uiState.update { it.copy(uri = trimmed, isDiscoveryLoading = true, error = null) }
            runCatching { discoverWallet(trimmed) }
                .onSuccess { discovery ->
                    _uiState.update { current ->
                        val aliasSuggestion = discovery.aliasSuggestion
                            .orEmpty()
                            .toSingleLineInput()
                        val alias = current.aliasInput.ifBlank { aliasSuggestion }
                        current.copy(
                            discovery = discovery,
                            aliasInput = alias,
                            isDiscoveryLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure { throwable ->
                    val error = (throwable as? AppErrorException)?.error
                        ?: AppError.Unexpected(throwable.message)
                    _uiState.update {
                        it.copy(
                            uri = trimmed,
                            discovery = null,
                            isDiscoveryLoading = false,
                            error = error
                        )
                    }
                }
        }
    }

    fun retryDiscovery() {
        val uri = _uiState.value.uri
        if (uri.isNotBlank()) {
            load(uri)
        }
    }

    fun updateAlias(alias: String) {
        _uiState.update { it.copy(aliasInput = alias.toSingleLineInput()) }
    }

    fun confirm() {
        val state = _uiState.value
        if (state.uri.isBlank() || state.discovery == null) {
            return
        }
        scope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                setWalletConnection(
                    uri = state.uri,
                    alias = state.aliasInput,
                    metadata = state.discovery.toMetadataSnapshot()
                )
            }.onSuccess { connection ->
                _events.emit(ConnectWalletEvent.Success(connection))
                _uiState.update { it.copy(isSaving = false) }
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                val error = (throwable as? AppErrorException)?.error
                    ?: AppError.Unexpected(throwable.message)
                _uiState.update { it.copy(isSaving = false, error = error) }
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

private fun String.toSingleLineInput(): String = replace(Regex("[\\r\\n]+"), " ")

data class ConnectWalletUiState(
    val uri: String = "",
    val isDiscoveryLoading: Boolean = false,
    val discovery: WalletDiscovery? = null,
    val aliasInput: String = "",
    val isSaving: Boolean = false,
    val error: AppError? = null
)

sealed interface ConnectWalletEvent {
    data class Success(val connection: WalletConnection) : ConnectWalletEvent
    data object Cancelled : ConnectWalletEvent
}
