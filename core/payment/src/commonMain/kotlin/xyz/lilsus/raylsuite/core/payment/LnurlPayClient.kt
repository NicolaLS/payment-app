package xyz.lilsus.raylsuite.core.payment

import xyz.lilsus.raylsuite.core.model.LightningAddress

interface LnurlPayClient {
    suspend fun fetchPayParams(endpoint: String): LnurlResult<LnurlPayParams>

    suspend fun fetchPayParams(address: LightningAddress): LnurlResult<LnurlPayParams>

    suspend fun requestInvoice(
        callback: String,
        amountMsats: Long,
        comment: String? = null
    ): LnurlResult<String>
}

sealed interface LnurlResult<out T> {
    data class Success<T>(val data: T) : LnurlResult<T>

    data class Error(val error: LnurlError, val cause: Throwable? = null) : LnurlResult<Nothing>
}

sealed interface LnurlError {
    data object NetworkUnavailable : LnurlError

    data class Protocol(val reason: String? = null) : LnurlError

    data class Unexpected(val detail: String? = null) : LnurlError
}

data class LnurlPayMetadata(
    val plainText: String?,
    val longText: String?,
    val imagePng: String?,
    val imageJpeg: String?,
    val identifier: String?,
    val email: String?,
    val tag: String?
)

data class LnurlPayParams(
    val callback: String,
    val minSendable: Long,
    val maxSendable: Long,
    val metadataRaw: String,
    val metadata: LnurlPayMetadata,
    val commentAllowed: Int?,
    val domain: String
)
