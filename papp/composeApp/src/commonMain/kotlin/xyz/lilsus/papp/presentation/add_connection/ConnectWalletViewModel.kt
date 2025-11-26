package xyz.lilsus.papp.presentation.add_connection

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import xyz.lilsus.papp.domain.model.*
import xyz.lilsus.papp.domain.use_cases.DiscoverWalletUseCase
import xyz.lilsus.papp.domain.use_cases.GetWalletsUseCase
import xyz.lilsus.papp.domain.use_cases.SetWalletConnectionUseCase

class ConnectWalletViewModel internal constructor(
    private val discoverWallet: DiscoverWalletUseCase,
    private val setWalletConnection: SetWalletConnectionUseCase,
    private val getWallets: GetWalletsUseCase,
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
            val defaultSetActive = runCatching { getWallets().isNotEmpty() }.getOrDefault(false)
            runCatching { discoverWallet(trimmed) }
                .onSuccess { discovery ->
                    _uiState.update { current ->
                        val aliasSuggestion = discovery.aliasSuggestion.orEmpty()
                        val alias = if (current.aliasInput.isBlank()) aliasSuggestion else current.aliasInput
                        current.copy(
                            discovery = discovery,
                            aliasInput = alias,
                            isDiscoveryLoading = false,
                            error = null,
                            setActive = if (current.discovery ==
                                null
                            ) {
                                defaultSetActive || current.setActive
                            } else {
                                current.setActive
                            }
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
        _uiState.update { it.copy(aliasInput = alias) }
    }

    fun updateSetActive(setActive: Boolean) {
        _uiState.update { it.copy(setActive = setActive) }
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
                    activate = state.setActive,
                    metadata = state.discovery?.toMetadataSnapshot()
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

data class ConnectWalletUiState(
    val uri: String = "",
    val isDiscoveryLoading: Boolean = false,
    val discovery: WalletDiscovery? = null,
    val aliasInput: String = "",
    val setActive: Boolean = true,
    val isSaving: Boolean = false,
    val error: AppError? = null
)

sealed interface ConnectWalletEvent {
    data class Success(val connection: WalletConnection) : ConnectWalletEvent
    data object Cancelled : ConnectWalletEvent
}
