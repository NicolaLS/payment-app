package xyz.lilsus.papp.domain.use_cases

import xyz.lilsus.papp.domain.lnurl.LnurlPayParams
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.repository.LnurlRepository

class FetchLnurlPayParamsUseCase(
    private val repository: LnurlRepository,
) {
    suspend operator fun invoke(endpoint: String): Result<LnurlPayParams> {
        return repository.fetchPayParams(endpoint)
    }
}
