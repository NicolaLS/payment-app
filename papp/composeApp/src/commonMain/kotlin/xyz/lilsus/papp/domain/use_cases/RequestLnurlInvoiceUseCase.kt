package xyz.lilsus.papp.domain.use_cases

import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.repository.LnurlRepository

class RequestLnurlInvoiceUseCase(private val repository: LnurlRepository) {
    suspend operator fun invoke(
        callback: String,
        amountMsats: Long,
        comment: String? = null
    ): Result<String> = repository.requestInvoice(callback, amountMsats, comment)
}
