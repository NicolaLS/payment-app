package xyz.lilsus.rayl.foundation.ui.presentation.settings.addblink

import xyz.lilsus.rayl.foundation.ui.domain.model.AppError
import xyz.lilsus.rayl.foundation.ui.domain.model.WalletConnection

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
