package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.lnurl.LightningAddress
import xyz.lilsus.blip.domain.lnurl.LnurlPayParams
import xyz.lilsus.blip.domain.model.Result
import xyz.lilsus.blip.domain.repository.LnurlRepository

class ResolveLightningAddressUseCase(private val repository: LnurlRepository) {
    suspend operator fun invoke(address: LightningAddress): Result<LnurlPayParams> =
        repository.fetchPayParams(address)
}
