package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.lnurl.LnurlPayParams
import xyz.lilsus.blip.domain.model.Result
import xyz.lilsus.blip.domain.repository.LnurlRepository

class FetchLnurlPayParamsUseCase(private val repository: LnurlRepository) {
    suspend operator fun invoke(endpoint: String): Result<LnurlPayParams> =
        repository.fetchPayParams(endpoint)
}
