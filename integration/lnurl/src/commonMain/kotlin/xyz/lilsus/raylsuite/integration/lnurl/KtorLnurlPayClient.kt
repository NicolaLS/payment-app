package xyz.lilsus.raylsuite.integration.lnurl

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
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.network.NetworkConnectivity
import xyz.lilsus.raylsuite.core.network.createHttpClient
import xyz.lilsus.raylsuite.core.payment.LnurlError
import xyz.lilsus.raylsuite.core.payment.LnurlPayClient
import xyz.lilsus.raylsuite.core.payment.LnurlPayMetadata
import xyz.lilsus.raylsuite.core.payment.LnurlPayParams
import xyz.lilsus.raylsuite.core.payment.LnurlResult

class KtorLnurlPayClient(
    private val networkConnectivity: NetworkConnectivity,
    private val client: HttpClient = createHttpClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : LnurlPayClient {
    override suspend fun fetchPayParams(endpoint: String): LnurlResult<LnurlPayParams> =
        withContext(dispatcher) {
            val url = endpoint.trim()
            if (url.isEmpty()) {
                return@withContext protocolError("LNURL is blank")
            }
            if (!networkConnectivity.isNetworkAvailable()) {
                return@withContext networkUnavailable()
            }
            val parsedUrl =
                runCatching { Url(url) }.getOrNull()
                    ?: return@withContext protocolError("LNURL is not a valid URL")
            try {
                val response = client.get(url)
                parsePayParams(response.body(), parsedUrl.host)
            } catch (cause: Throwable) {
                requestFailure(cause, "Failed to reach LNURL endpoint")
            }
        }

    override suspend fun fetchPayParams(address: LightningAddress): LnurlResult<LnurlPayParams> {
        if (address.domain.endsWith(".onion", ignoreCase = true)) {
            return protocolError("Lightning addresses require HTTPS endpoints")
        }
        return fetchPayParams(buildAddressUrl(address))
    }

    override suspend fun requestInvoice(
        callback: String,
        amountMsats: Long,
        comment: String?
    ): LnurlResult<String> = withContext(dispatcher) {
        if (!networkConnectivity.isNetworkAvailable()) {
            return@withContext networkUnavailable()
        }
        if (amountMsats <= 0) {
            return@withContext protocolError("Amount must be positive")
        }
        try {
            val response =
                client.get(callback) {
                    parameter("amount", amountMsats.toString())
                    if (!comment.isNullOrBlank()) {
                        parameter("comment", comment)
                    }
                }
            parseInvoice(response.body())
        } catch (cause: Throwable) {
            requestFailure(cause, "Failed to reach LNURL callback endpoint")
        }
    }

    private fun parsePayParams(raw: String, domain: String): LnurlResult<LnurlPayParams> {
        val element =
            runCatching { json.parseToJsonElement(raw) }.getOrNull()
                ?: return protocolError("LNURL pay response is not JSON")
        if (
            element is JsonObject &&
            element["status"]?.jsonPrimitive?.contentEquals("ERROR") == true
        ) {
            return protocolError(element["reason"]?.jsonPrimitive?.contentOrNull)
        }
        if (element !is JsonObject) {
            return protocolError("LNURL pay response must be an object")
        }

        val callbackRaw =
            element["callback"]?.jsonPrimitive?.contentOrNull
                ?: return protocolError("LNURL pay callback missing")
        val maxSendable =
            element["maxSendable"]?.jsonPrimitive?.longOrBigInt()
                ?: return protocolError("LNURL maxSendable missing")
        val minSendable =
            element["minSendable"]?.jsonPrimitive?.longOrBigInt()
                ?: return protocolError("LNURL minSendable missing")
        if (maxSendable <= 0 || minSendable <= 0 || maxSendable < minSendable) {
            return protocolError("LNURL sendable amounts invalid")
        }
        val tag = element["tag"]?.jsonPrimitive?.contentOrNull
        if (tag != null && !tag.equals("payRequest", ignoreCase = true)) {
            return protocolError("LNURL tag is not payRequest")
        }
        val metadataRaw =
            element["metadata"]?.jsonPrimitive?.contentOrNull
                ?: return protocolError("LNURL metadata missing")
        val metadata =
            parseMetadata(metadataRaw)
                ?: return protocolError("LNURL metadata malformed")

        return LnurlResult.Success(
            LnurlPayParams(
                callback = normalizeCallback(callbackRaw),
                minSendable = minSendable,
                maxSendable = maxSendable,
                metadataRaw = metadataRaw,
                metadata = metadata,
                commentAllowed = element["commentAllowed"]?.jsonPrimitive?.intOrNull,
                domain = domain
            )
        )
    }

    private fun parseMetadata(raw: String): LnurlPayMetadata? {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
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
            val type = array.firstOrNull()?.jsonPrimitive?.contentOrNull ?: return@forEach
            val value = array.getOrNull(1)?.jsonPrimitive?.contentOrNull
            when (type.lowercase()) {
                "text/plain" -> plainText = value
                "text/long-desc" -> longText = value
                "image/png;base64" -> imagePng = value
                "image/jpeg;base64" -> imageJpeg = value
                "text/identifier" -> identifier = value
                "text/email" -> email = value
                "text/tag" -> tag = value
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

    private fun parseInvoice(raw: String): LnurlResult<String> {
        val element =
            runCatching { json.parseToJsonElement(raw) }.getOrNull()
                ?: return protocolError("LNURL invoice response is not JSON")
        if (
            element is JsonObject &&
            element["status"]?.jsonPrimitive?.contentEquals("ERROR") == true
        ) {
            return protocolError(element["reason"]?.jsonPrimitive?.contentOrNull)
        }
        if (element !is JsonObject) {
            return protocolError("LNURL invoice response must be an object")
        }
        val invoice =
            element["pr"]?.jsonPrimitive?.contentOrNull
                ?: return protocolError("LNURL invoice is missing")
        return LnurlResult.Success(invoice)
    }

    private fun requestFailure(cause: Throwable, message: String): LnurlResult.Error = when {
        !networkConnectivity.isNetworkAvailable() -> networkUnavailable(cause)
        cause is kotlinx.io.IOException ->
            LnurlResult.Error(LnurlError.Protocol(message), cause)

        else -> LnurlResult.Error(LnurlError.Unexpected(cause.message), cause)
    }

    private fun normalizeCallback(original: String): String {
        val url = runCatching { Url(original) }.getOrNull() ?: return original
        return buildString {
            append(url.protocol.name)
            append("://")
            append(url.host)
            if (url.port != url.protocol.defaultPort && url.port != -1) {
                append(':').append(url.port)
            }
            append(url.encodedPath)
            if (url.encodedQuery.isNotEmpty()) {
                append('?').append(url.encodedQuery)
            }
        }
    }

    private fun buildAddressUrl(address: LightningAddress): String = buildString {
        append("https://")
        append(address.domain.lowercase())
        append("/.well-known/lnurlp/")
        append(address.username)
        address.tag?.takeIf(String::isNotEmpty)?.let {
            append('+').append(it)
        }
    }

    private fun JsonPrimitive.contentEquals(value: String): Boolean =
        contentOrNull?.equals(value, ignoreCase = true) == true

    private fun JsonPrimitive.longOrBigInt(): Long? {
        longOrNull?.let { return it }
        doubleOrNull?.let { return it.toLong() }
        return contentOrNull?.toLongOrNull()
    }

    private fun protocolError(reason: String?): LnurlResult.Error =
        LnurlResult.Error(LnurlError.Protocol(reason))

    private fun networkUnavailable(cause: Throwable? = null): LnurlResult.Error =
        LnurlResult.Error(LnurlError.NetworkUnavailable, cause)
}
