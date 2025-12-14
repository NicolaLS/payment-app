package xyz.lilsus.papp.presentation.settings.addblink

import kotlin.random.Random
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
import xyz.lilsus.papp.data.blink.BlinkCredentialStore
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

/**
 * ViewModel for adding a Blink wallet via API key.
 */
class AddBlinkWalletViewModel internal constructor(
    private val walletSettingsRepository: WalletSettingsRepository,
    private val credentialStore: BlinkCredentialStore,
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
                // Generate a unique wallet ID for this Blink wallet
                val walletId = generateWalletId()

                // Store the API key securely
                credentialStore.storeApiKey(walletId, apiKey)

                // Create and save the wallet connection
                val connection = WalletConnection(
                    walletPublicKey = walletId,
                    alias = alias,
                    type = WalletType.BLINK
                )

                walletSettingsRepository.saveWalletConnection(connection, activate = true)

                _uiState.update { it.copy(isSaving = false) }
                _events.emit(AddBlinkWalletEvent.Success(connection))
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

    private fun generateWalletId(): String {
        // Generate a unique ID prefixed with "blink-" for easy identification
        val randomPart = buildString {
            repeat(32) {
                append(HEX_CHARS[Random.nextInt(HEX_CHARS.length)])
            }
        }
        return "blink-$randomPart"
    }

    companion object {
        private const val HEX_CHARS = "0123456789abcdef"
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
