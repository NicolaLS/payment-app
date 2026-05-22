package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.repository.BlinkWalletAccountRepository

class GetBlinkDefaultWalletIdUseCase(private val repository: BlinkWalletAccountRepository) {
    suspend operator fun invoke(walletId: String): String? =
        repository.getCachedDefaultWalletId(walletId)
}
