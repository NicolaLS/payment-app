package xyz.lilsus.papp.data.blink

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.math.absoluteValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.isDebugBuild

/**
 * Client for Blink's GraphQL API.
 * Handles Lightning invoice payments using API key authentication.
 *
 * API Reference: https://dev.blink.sv/api/llm-api-reference
 */
class BlinkApiClient(
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    /**
     * Fetches the user's default Blink wallet id using the provided API key.
     *
     * Blink requires `walletId` for payment mutations; this allows the app to use the user's
     * default wallet context without asking them for account/wallet identifiers.
     */
    suspend fun fetchDefaultWalletId(apiKey: String): String {
        val query = """
            query DefaultWalletId {
                me {
                    defaultAccount {
                        defaultWallet {
                            id
                        }
                    }
                }
            }
        """.trimIndent()

        val requestBody = buildJsonObject {
            put("query", query)
            put("variables", buildJsonObject { })
        }

        val response = executeGraphQlRequest(
            apiKey = apiKey,
            requestBody = requestBody,
            logLabel = "DefaultWalletId"
        )

        val data = response["data"]?.jsonObject
            ?: throw AppErrorException(AppError.Unexpected("Missing data in response"))

        val walletId = data["me"]?.jsonObject
            ?.get("defaultAccount")?.jsonObject
            ?.get("defaultWallet")?.jsonObject
            ?.get("id")?.jsonPrimitive
            ?.content
            ?.trim()
            .orEmpty()

        if (walletId.isBlank()) {
            throw AppErrorException(AppError.Unexpected("Missing default wallet id in response"))
        }

        return walletId
    }

    /**
     * Pays a BOLT11 invoice with an embedded amount.
     *
     * @param apiKey The Blink API key for authentication.
     * @param walletId The Blink wallet id to pay from.
     * @param invoice The BOLT11 invoice to pay.
     * @return [BlinkPaymentResult] on success.
     * @throws [AppErrorException] on failure.
     */
    suspend fun payInvoice(apiKey: String, walletId: String, invoice: String): BlinkPaymentResult {
        val query = """
            mutation LnInvoicePaymentSend(${'$'}input: LnInvoicePaymentInput!) {
                lnInvoicePaymentSend(input: ${'$'}input) {
                    status
                    errors {
                        message
                        code
                    }
                    transaction {
                        settlementFee
                        settlementCurrency
                    }
                }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put(
                "input",
                buildJsonObject {
                    put("paymentRequest", invoice)
                    put("walletId", walletId)
                }
            )
        }

        return executePaymentMutation(apiKey, query, variables, "lnInvoicePaymentSend")
    }

    /**
     * Pays a zero-amount BOLT11 invoice with a specified amount.
     *
     * @param apiKey The Blink API key for authentication.
     * @param walletId The Blink wallet id to pay from.
     * @param invoice The zero-amount BOLT11 invoice to pay.
     * @param amountSats The amount to pay in satoshis.
     * @return [BlinkPaymentResult] on success.
     * @throws [AppErrorException] on failure.
     */
    suspend fun payNoAmountInvoice(
        apiKey: String,
        walletId: String,
        invoice: String,
        amountSats: Long
    ): BlinkPaymentResult {
        val query = """
            mutation LnNoAmountInvoicePaymentSend(${'$'}input: LnNoAmountInvoicePaymentInput!) {
                lnNoAmountInvoicePaymentSend(input: ${'$'}input) {
                    status
                    errors {
                        message
                        code
                    }
                    transaction {
                        settlementFee
                        settlementCurrency
                    }
                }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put(
                "input",
                buildJsonObject {
                    put("paymentRequest", invoice)
                    put("amount", amountSats)
                    put("walletId", walletId)
                }
            )
        }

        return executePaymentMutation(apiKey, query, variables, "lnNoAmountInvoicePaymentSend")
    }

    private suspend fun executePaymentMutation(
        apiKey: String,
        query: String,
        variables: JsonObject,
        operationName: String
    ): BlinkPaymentResult {
        val requestBody = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }

        val response = executeGraphQlRequest(
            apiKey = apiKey,
            requestBody = requestBody,
            logLabel = operationName
        )

        return parsePaymentResponse(response, operationName)
    }

    private fun parsePaymentResponse(
        jsonResponse: JsonObject,
        operationName: String
    ): BlinkPaymentResult {
        val data = jsonResponse["data"]?.jsonObject
            ?: throw AppErrorException(AppError.Unexpected("Missing data in response"))

        val result = data[operationName]?.jsonObject
            ?: throw AppErrorException(AppError.Unexpected("Missing $operationName in response"))

        // Check for operation-level errors
        result["errors"]?.jsonArray?.let { errors ->
            if (errors.isNotEmpty()) {
                val firstError = errors[0].jsonObject
                val message = firstError["message"]?.jsonPrimitive?.content
                val code = firstError["code"]?.jsonPrimitive?.content
                throw AppErrorException(AppError.PaymentRejected(code = code, message = message))
            }
        }

        val status = result["status"]?.jsonPrimitive?.content
            ?: throw AppErrorException(AppError.Unexpected("Missing status in response"))

        val feePaidMsats = result["transaction"]?.jsonObject?.let { transaction ->
            parseFeesPaidMsats(transaction)
        }

        return when (status) {
            "SUCCESS" -> BlinkPaymentResult.Success(feesPaidMsats = feePaidMsats)

            "PENDING" -> BlinkPaymentResult.Pending(feesPaidMsats = feePaidMsats)

            "ALREADY_PAID" -> BlinkPaymentResult.AlreadyPaid(feesPaidMsats = feePaidMsats)

            "FAILURE" -> throw AppErrorException(
                AppError.PaymentRejected(message = "Payment failed")
            )

            else -> throw AppErrorException(AppError.Unexpected("Unknown status: $status"))
        }
    }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    private fun parseFeesPaidMsats(transaction: JsonObject): Long? {
        val settlementCurrency = transaction["settlementCurrency"]?.jsonPrimitive?.content
            ?.trim()
            .orEmpty()

        if (settlementCurrency != "BTC") return null

        val feeSats = transaction["settlementFee"]?.jsonPrimitive?.content
            ?.toLongOrNull()
            ?.absoluteValue
            ?: return null

        return feeSats
            .takeIf { it <= Long.MAX_VALUE / 1000L }
            ?.times(1000L)
    }

    private suspend fun executeGraphQlRequest(
        apiKey: String,
        requestBody: JsonObject,
        logLabel: String
    ): JsonObject {
        val response: HttpResponse = try {
            httpClient.post(BLINK_API_URL) {
                contentType(ContentType.Application.Json)
                header("X-API-KEY", apiKey)
                setBody(json.encodeToString(JsonObject.serializer(), requestBody))
            }
        } catch (e: Exception) {
            throw AppErrorException(AppError.NetworkUnavailable, e)
        }

        val status = response.status
        val responseBody = runCatching { response.bodyAsText() }
            .getOrElse { error -> "<failed to read response body: ${error.message}>" }

        logHttpResponse(logLabel = logLabel, status = status, body = responseBody)

        if (status == HttpStatusCode.Unauthorized) {
            throw AppErrorException(AppError.AuthenticationFailure("Invalid or revoked API key"))
        }

        val jsonResponse = runCatching {
            json.parseToJsonElement(responseBody).jsonObject
        }.getOrNull()

        if (jsonResponse != null) {
            throwOnGraphQlErrors(jsonResponse)
        }

        if (!status.isSuccess()) {
            throw AppErrorException(
                AppError.NetworkUnavailable,
                Exception("HTTP ${status.value}")
            )
        }

        return jsonResponse
            ?: throw AppErrorException(AppError.Unexpected("Invalid response format"))
    }

    private fun throwOnGraphQlErrors(jsonResponse: JsonObject) {
        jsonResponse["errors"]?.jsonArray?.let { errors ->
            if (errors.isEmpty()) return

            val firstError = errors[0].jsonObject
            val message = firstError["message"]?.jsonPrimitive?.content
            val extensions = firstError["extensions"]?.jsonObject
            val code = extensions?.get("code")?.jsonPrimitive?.content

            if (code == "UNAUTHENTICATED" || code == "FORBIDDEN") {
                throw AppErrorException(AppError.AuthenticationFailure(message))
            }
            throw AppErrorException(AppError.PaymentRejected(code = code, message = message))
        }
    }

    private fun logHttpResponse(logLabel: String, status: HttpStatusCode, body: String) {
        if (!isDebugBuild) return
        println(
            buildString {
                append("Blink HTTP [$logLabel] status=${status.value}\n")
                append(body)
            }
        )
    }

    companion object {
        private const val BLINK_API_URL = "https://api.blink.sv/graphql"
    }
}

/**
 * Result of a Blink payment operation.
 */
sealed class BlinkPaymentResult(open val feesPaidMsats: Long?) {
    /** Payment completed successfully. */
    data class Success(override val feesPaidMsats: Long? = null) : BlinkPaymentResult(feesPaidMsats)

    /** Payment is pending (may complete later). */
    data class Pending(override val feesPaidMsats: Long? = null) : BlinkPaymentResult(feesPaidMsats)

    /** Invoice was already paid. */
    data class AlreadyPaid(override val feesPaidMsats: Long? = null) :
        BlinkPaymentResult(feesPaidMsats)
}
