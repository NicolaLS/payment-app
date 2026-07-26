package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.repository.BlinkWalletAccountRepository

class GetBlinkDefaultWalletIdUseCase(private val repository: BlinkWalletAccountRepository) {
    suspend operator fun invoke(): String? = repository.getCachedDefaultWalletId()
}
