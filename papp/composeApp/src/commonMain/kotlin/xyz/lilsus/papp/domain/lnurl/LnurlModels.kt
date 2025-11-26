package xyz.lilsus.papp.domain.lnurl

/**
 * Represents a user-facing lightning address (LUD-16) in the form `name@domain`.
 */
data class LightningAddress(val username: String, val domain: String, val tag: String? = null) {
    val full: String
        get() = buildString {
            append(username)
            tag?.let { append('+').append(it) }
            append('@')
            append(domain)
        }
}

/**
 * Structured representation of the LNURL-pay metadata array.
 */
data class LnurlPayMetadata(
    val plainText: String?,
    val longText: String?,
    val imagePng: String?,
    val imageJpeg: String?,
    val identifier: String?,
    val email: String?,
    val tag: String?
)

/**
 * Parameters returned by an LNURL-pay endpoint (LUD-06).
 */
data class LnurlPayParams(
    val callback: String,
    val minSendable: Long,
    val maxSendable: Long,
    val metadataRaw: String,
    val metadata: LnurlPayMetadata,
    val commentAllowed: Int?,
    val domain: String
)
