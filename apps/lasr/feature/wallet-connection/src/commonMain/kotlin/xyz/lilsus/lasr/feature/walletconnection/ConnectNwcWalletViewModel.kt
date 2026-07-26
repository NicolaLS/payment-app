package xyz.lilsus.lasr.feature.walletconnection

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
import xyz.lilsus.lasr.integration.nwc.NwcConnectionError
import xyz.lilsus.lasr.integration.nwc.NwcConnectionException
import xyz.lilsus.lasr.integration.nwc.NwcWallet
import xyz.lilsus.lasr.integration.nwc.NwcWalletConnection
import xyz.lilsus.lasr.integration.nwc.NwcWalletDiscovery

class ConnectNwcWalletViewModel(
    private val nwcWallet: NwcWallet,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableUiState = MutableStateFlow(ConnectNwcWalletUiState())
    private val mutableEvents =
        MutableSharedFlow<ConnectNwcWalletEvent>(extraBufferCapacity = 4)
    private var discoveryJob: Job? = null

    val uiState: StateFlow<ConnectNwcWalletUiState> = mutableUiState.asStateFlow()
    val events: SharedFlow<ConnectNwcWalletEvent> = mutableEvents.asSharedFlow()

    fun load(uri: String) {
        val normalized = uri.trim()
        if (normalized.isEmpty()) {
            mutableUiState.value =
                ConnectNwcWalletUiState(
                    uri = normalized,
                    error = NwcConnectionError.InvalidUri
                )
            return
        }
        if (
            mutableUiState.value.uri == normalized &&
            mutableUiState.value.discovery != null
        ) {
            return
        }

        discoveryJob?.cancel()
        discoveryJob =
            scope.launch {
                mutableUiState.update {
                    it.copy(
                        uri = normalized,
                        isDiscoveryLoading = true,
                        discovery = null,
                        error = null
                    )
                }
                try {
                    val discovery = nwcWallet.discover(normalized)
                    mutableUiState.update { current ->
                        current.copy(
                            discovery = discovery,
                            alias =
                                current.alias.ifBlank {
                                    discovery.aliasSuggestion.orEmpty().toSingleLine()
                                },
                            isDiscoveryLoading = false
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: NwcConnectionException) {
                    mutableUiState.update {
                        it.copy(
                            isDiscoveryLoading = false,
                            error = error.error
                        )
                    }
                } catch (error: Exception) {
                    mutableUiState.update {
                        it.copy(
                            isDiscoveryLoading = false,
                            error = NwcConnectionError.ConnectionFailed(error.message)
                        )
                    }
                }
            }
    }

    fun retryDiscovery() {
        mutableUiState.value.uri.takeIf(String::isNotBlank)?.let(::load)
    }

    fun updateAlias(alias: String) {
        mutableUiState.update {
            it.copy(
                alias = alias.toSingleLine(),
                error = null
            )
        }
    }

    fun confirm() {
        val state = mutableUiState.value
        val discovery = state.discovery ?: return
        if (state.isSaving) return

        scope.launch {
            mutableUiState.update { it.copy(isSaving = true, error = null) }
            try {
                val connection =
                    nwcWallet.connect(
                        discovery = discovery,
                        alias = state.alias
                    )
                mutableUiState.update { it.copy(isSaving = false) }
                mutableEvents.emit(ConnectNwcWalletEvent.Success(connection))
            } catch (error: CancellationException) {
                throw error
            } catch (error: NwcConnectionException) {
                mutableUiState.update {
                    it.copy(isSaving = false, error = error.error)
                }
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isSaving = false,
                        error = NwcConnectionError.ConnectionFailed(error.message)
                    )
                }
            }
        }
    }

    fun cancel() {
        scope.launch {
            mutableEvents.emit(ConnectNwcWalletEvent.Cancelled)
        }
    }

    fun clear() {
        scope.cancel()
    }
}

data class ConnectNwcWalletUiState(
    val uri: String = "",
    val isDiscoveryLoading: Boolean = false,
    val discovery: NwcWalletDiscovery? = null,
    val alias: String = "",
    val isSaving: Boolean = false,
    val error: NwcConnectionError? = null
)

sealed interface ConnectNwcWalletEvent {
    data class Success(val connection: NwcWalletConnection) : ConnectNwcWalletEvent

    data object Cancelled : ConnectNwcWalletEvent
}

private fun String.toSingleLine(): String = replace(Regex("[\\r\\n]+"), " ")
