package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.model.Result
import xyz.lilsus.blip.domain.repository.LnurlRepository

class RequestLnurlInvoiceUseCase(private val repository: LnurlRepository) {
    suspend operator fun invoke(
        callback: String,
        amountMsats: Long,
        comment: String? = null
    ): Result<String> = repository.requestInvoice(callback, amountMsats, comment)
}
