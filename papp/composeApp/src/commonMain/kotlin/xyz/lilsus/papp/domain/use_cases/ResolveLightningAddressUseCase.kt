package xyz.lilsus.papp.domain.use_cases

import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.lnurl.LnurlPayParams
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.repository.LnurlRepository

class ResolveLightningAddressUseCase(
    private val repository: LnurlRepository,
) {
    suspend operator fun invoke(address: LightningAddress): Result<LnurlPayParams> {
        return repository.fetchPayParams(address)
    }
}
