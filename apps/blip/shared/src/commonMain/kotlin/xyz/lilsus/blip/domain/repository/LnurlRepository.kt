package xyz.lilsus.blip.domain.repository

import xyz.lilsus.blip.domain.lnurl.LightningAddress
import xyz.lilsus.blip.domain.lnurl.LnurlPayParams
import xyz.lilsus.blip.domain.model.Result

interface LnurlRepository {
    suspend fun fetchPayParams(endpoint: String): Result<LnurlPayParams>
    suspend fun fetchPayParams(address: LightningAddress): Result<LnurlPayParams>
    suspend fun requestInvoice(
        callback: String,
        amountMsats: Long,
        comment: String? = null
    ): Result<String>
}
