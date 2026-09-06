package xyz.lilsus.flint.application.wallet

import kotlinx.coroutines.flow.StateFlow
import xyz.lilsus.flint.application.payment.PaymentEngine

interface WalletAccess {
    val state: StateFlow<WalletAccessState>
    val payments: PaymentEngine

    fun start()
    suspend fun importWallet(mnemonic: String): ImportWalletResult
    suspend fun retryConnection()
    suspend fun removeWallet(): RemoveWalletResult
}

sealed interface WalletAccessState {
    data object Loading : WalletAccessState
    data object NoWallet : WalletAccessState
    data object Connecting : WalletAccessState
    data object Connected : WalletAccessState
    data object Removing : WalletAccessState
    data class CredentialProblem(val kind: CredentialProblemKind) : WalletAccessState
    data object ReconnectRequired : WalletAccessState
    data object ResetRequired : WalletAccessState
}

enum class CredentialProblemKind {
    UNAVAILABLE,
    INVALIDATED,
    CORRUPT
}

enum class ImportWalletResult {
    IMPORTED,
    ALREADY_CONFIGURED,
    INVALID_MNEMONIC,
    CONNECTION_FAILED,
    CREDENTIAL_STORE_FAILED
}

enum class RemoveWalletResult {
    REMOVED,
    RESET_REQUIRED
}
