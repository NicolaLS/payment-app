package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.repository.BlinkWalletAccountRepository

class RefreshBlinkDefaultWalletIdUseCase(private val repository: BlinkWalletAccountRepository) {
    suspend operator fun invoke(): String = repository.refreshDefaultWalletId()
}
