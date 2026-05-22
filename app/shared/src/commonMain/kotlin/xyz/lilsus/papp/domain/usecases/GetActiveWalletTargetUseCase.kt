package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.model.WalletPaymentTarget
import xyz.lilsus.papp.domain.model.toPaymentTarget
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

/**
 * Resolves the currently active wallet into the concrete target used for payment operations.
 */
class GetActiveWalletTargetUseCase(private val repository: WalletSettingsRepository) {
    suspend operator fun invoke(): WalletPaymentTarget? =
        repository.getWalletConnection()?.toPaymentTarget()
}
