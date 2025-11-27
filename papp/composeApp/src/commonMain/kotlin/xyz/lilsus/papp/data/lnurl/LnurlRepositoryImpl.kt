package xyz.lilsus.papp.data.lnurl

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import xyz.lilsus.papp.data.network.createBaseHttpClient
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.lnurl.LnurlPayMetadata
import xyz.lilsus.papp.domain.lnurl.LnurlPayParams
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.repository.LnurlRepository

class LnurlRepositoryImpl(
    private val client: HttpClient = createBaseHttpClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LnurlRepository {

    override suspend fun fetchPayParams(endpoint: String): Result<LnurlPayParams> =
        withContext(dispatcher) {
            val url = endpoint.trim()
            if (url.isEmpty()) {
                return@withContext Result.Error(AppError.InvalidWalletUri("LNURL is blank"))
            }
            val parsedUrl = runCatching { Url(url) }.getOrNull()
                ?: return@withContext Result.Error(
                    AppError.InvalidWalletUri("LNURL is not a valid URL")
                )
            try {
                val response = client.get(url)
                val body = response.body<String>()
                parsePayParams(body, parsedUrl.host)
            } catch (cause: Throwable) {
                when (cause) {
                    is kotlinx.io.IOException -> Result.Error(
                        AppError.NetworkUnavailable,
                        cause
                    )

                    else -> Result.Error(AppError.Unexpected(cause.message), cause)
                }
            }
        }

    override suspend fun fetchPayParams(address: LightningAddress): Result<LnurlPayParams> {
        val isOnion = address.domain.endsWith(".onion", ignoreCase = true)
        if (isOnion) {
            return Result.Error(
                AppError.InvalidWalletUri("Lightning addresses require HTTPS endpoints")
            )
        }
        val endpoint = buildAddressUrl(address)
        return fetchPayParams(endpoint)
    }

    override suspend fun requestInvoice(
        callback: String,
        amountMsats: Long,
        comment: String?
    ): Result<String> = withContext(dispatcher) {
        if (amountMsats <= 0) {
            return@withContext Result.Error(AppError.InvalidWalletUri("Amount must be positive"))
        }
        try {
            val response = client.get(callback) {
                parameter("amount", amountMsats.toString())
                if (!comment.isNullOrBlank()) {
                    parameter("comment", comment)
                }
            }
            val body = response.body<String>()
            parseInvoice(body)
        } catch (cause: Throwable) {
            when (cause) {
                is kotlinx.io.IOException -> Result.Error(
                    AppError.NetworkUnavailable,
                    cause
                )

                else -> Result.Error(AppError.Unexpected(cause.message), cause)
            }
        }
    }

    private fun parsePayParams(raw: String, domain: String): Result<LnurlPayParams> {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            ?: return Result.Error(AppError.InvalidWalletUri("LNURL pay response is not JSON"))
        if (element is JsonObject &&
            element["status"]?.jsonPrimitive?.contentEquals("ERROR") == true
        ) {
            val reason = element["reason"]?.jsonPrimitive?.contentOrNull
            return Result.Error(AppError.InvalidWalletUri(reason))
        }
        if (element !is JsonObject) {
            return Result.Error(AppError.InvalidWalletUri("LNURL pay response must be an object"))
        }

        val callbackRaw = element["callback"]?.jsonPrimitive?.contentOrNull
            ?: return Result.Error(AppError.InvalidWalletUri("LNURL pay callback missing"))
        val callback = normalizeCallback(callbackRaw)
        val maxSendable = element["maxSendable"]?.jsonPrimitive?.longOrBigInt()
            ?: return Result.Error(AppError.InvalidWalletUri("LNURL maxSendable missing"))
        val minSendable = element["minSendable"]?.jsonPrimitive?.longOrBigInt()
            ?: return Result.Error(AppError.InvalidWalletUri("LNURL minSendable missing"))
        if (maxSendable <= 0 || minSendable <= 0 || maxSendable < minSendable) {
            return Result.Error(AppError.InvalidWalletUri("LNURL sendable amounts invalid"))
        }
        val tag = element["tag"]?.jsonPrimitive?.contentOrNull
        if (tag != null && !tag.equals("payRequest", ignoreCase = true)) {
            return Result.Error(AppError.InvalidWalletUri("LNURL tag is not payRequest"))
        }
        val metadataRaw = element["metadata"]?.jsonPrimitive?.contentOrNull
            ?: return Result.Error(AppError.InvalidWalletUri("LNURL metadata missing"))
        val metadata = parseMetadata(metadataRaw)
            ?: return Result.Error(AppError.InvalidWalletUri("LNURL metadata malformed"))
        val commentAllowed = element["commentAllowed"]?.jsonPrimitive?.intOrNull

        return Result.Success(
            LnurlPayParams(
                callback = callback,
                minSendable = minSendable,
                maxSendable = maxSendable,
                metadataRaw = metadataRaw,
                metadata = metadata,
                commentAllowed = commentAllowed,
                domain = domain
            )
        )
    }

    private fun parseMetadata(raw: String): LnurlPayMetadata? {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            ?: return null
        if (element !is JsonArray) return null
        var plainText: String? = null
        var longText: String? = null
        var imagePng: String? = null
        var imageJpeg: String? = null
        var identifier: String? = null
        var email: String? = null
        var tag: String? = null

        element.forEach { entry ->
            val array = entry as? JsonArray ?: return@forEach
            if (array.isEmpty()) return@forEach
            val type = array[0].jsonPrimitive.contentOrNull ?: return@forEach
            val value = array.getOrNull(1)
            when (type.lowercase()) {
                "text/plain" -> plainText = value?.jsonPrimitive?.contentOrNull
                "text/long-desc" -> longText = value?.jsonPrimitive?.contentOrNull
                "image/png;base64" -> imagePng = value?.jsonPrimitive?.contentOrNull
                "image/jpeg;base64" -> imageJpeg = value?.jsonPrimitive?.contentOrNull
                "text/identifier" -> identifier = value?.jsonPrimitive?.contentOrNull
                "text/email" -> email = value?.jsonPrimitive?.contentOrNull
                "text/tag" -> tag = value?.jsonPrimitive?.contentOrNull
            }
        }

        return LnurlPayMetadata(
            plainText = plainText,
            longText = longText,
            imagePng = imagePng,
            imageJpeg = imageJpeg,
            identifier = identifier,
            email = email,
            tag = tag
        )
    }

    private fun parseInvoice(raw: String): Result<String> {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull()
            ?: return Result.Error(AppError.InvalidWalletUri("LNURL invoice response is not JSON"))
        if (element is JsonObject &&
            element["status"]?.jsonPrimitive?.contentEquals("ERROR") == true
        ) {
            val reason = element["reason"]?.jsonPrimitive?.contentOrNull
            return Result.Error(AppError.InvalidWalletUri(reason))
        }
        if (element !is JsonObject) {
            return Result.Error(
                AppError.InvalidWalletUri("LNURL invoice response must be an object")
            )
        }
        val invoice = element["pr"]?.jsonPrimitive?.contentOrNull
            ?: return Result.Error(AppError.InvalidWalletUri("LNURL invoice is missing"))
        return Result.Success(invoice)
    }

    private fun JsonPrimitive.contentEquals(value: String): Boolean =
        contentOrNull?.equals(value, ignoreCase = true) == true

    private fun JsonPrimitive.longOrBigInt(): Long? {
        longOrNull?.let { return it }
        doubleOrNull?.let { return it.toLong() }
        return contentOrNull?.toLongOrNull()
    }

    private fun normalizeCallback(original: String): String {
        val url = runCatching { Url(original) }.getOrNull() ?: return original
        val protocol = url.protocol
        val defaultPort = protocol.defaultPort
        val port = url.port
        val builder = StringBuilder()
        builder.append(protocol.name)
        builder.append("://")
        builder.append(url.host)
        if (port != defaultPort && port != -1) {
            builder.append(':').append(port)
        }
        builder.append(url.encodedPath)
        if (url.encodedQuery.isNotEmpty()) {
            builder.append('?').append(url.encodedQuery)
        }
        return builder.toString()
    }

    private fun buildAddressUrl(address: LightningAddress): String = buildString {
        append("https://")
        append(address.domain)
        append("/.well-known/lnurlp/")
        append(address.username)
        address.tag?.let { tag ->
            if (tag.isNotEmpty()) {
                append('+')
                append(tag)
            }
        }
    }
}
