package xyz.lilsus.papp.presentation.settings.addwallet

import io.github.nicolals.nwc.NwcConnectionUri
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

class AddWalletViewModel internal constructor(
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(AddWalletUiState())
    val uiState: StateFlow<AddWalletUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddWalletEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AddWalletEvent> = _events.asSharedFlow()

    fun updateUri(uri: String) {
        val trimmed = uri.trim()
        val isValid = isValid(trimmed)
        _uiState.update { it.copy(uri = uri, error = null, isUriValid = isValid) }
        if (isValid) {
            submit()
        }
    }

    fun prefillUriIfValid(candidate: String?) {
        if (candidate.isNullOrBlank()) return
        val trimmed = candidate.trim()
        if (!isValid(trimmed)) return
        _uiState.update { it.copy(uri = trimmed, error = null, isUriValid = true) }
        submit()
    }

    fun submit() {
        val trimmed = _uiState.value.uri.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(error = AppError.InvalidWalletUri(), isUriValid = false) }
            return
        }
        if (!isValid(trimmed)) {
            _uiState.update { it.copy(error = AppError.InvalidWalletUri(), isUriValid = false) }
            return
        }
        scope.launch {
            _events.emit(AddWalletEvent.NavigateToConfirm(trimmed))
        }
    }

    fun handleScannedValue(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        val isValid = isValid(trimmed)
        _uiState.update { it.copy(uri = trimmed, error = null, isUriValid = isValid) }
        if (isValid) {
            submit()
        }
    }

    private fun isValid(uri: String): Boolean = uri.isNotEmpty() && NwcConnectionUri.isValid(uri)

    fun clear() {
        scope.cancel()
    }
}

data class AddWalletUiState(
    val uri: String = "",
    val error: AppError? = null,
    val isUriValid: Boolean = false
) {
    val canContinue: Boolean
        get() = isUriValid
}

sealed interface AddWalletEvent {
    data class NavigateToConfirm(val uri: String) : AddWalletEvent
}
