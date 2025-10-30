package xyz.lilsus.papp.domain.repository

import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.lnurl.LnurlPayParams
import xyz.lilsus.papp.domain.model.Result

interface LnurlRepository {
    suspend fun fetchPayParams(endpoint: String): Result<LnurlPayParams>
    suspend fun fetchPayParams(address: LightningAddress): Result<LnurlPayParams>
    suspend fun requestInvoice(callback: String, amountMsats: Long, comment: String? = null): Result<String>
}
