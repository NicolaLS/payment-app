package xyz.lilsus.blip.integration.blink

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.absoluteValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

data class BlinkContact(val handle: String, val alias: String?, val transactionsCount: Int) {
    val lightningAddress: String
        get() = "$handle@$BLINK_LIGHTNING_ADDRESS_DOMAIN"
}

internal sealed interface BlinkApiError {
    data object InvalidApiKey : BlinkApiError

    data object PermissionDenied : BlinkApiError

    data object InsufficientBalance : BlinkApiError

    data object RouteNotFound : BlinkApiError

    data object InvoiceExpired : BlinkApiError

    data object SelfPayment : BlinkApiError

    data object InvalidInvoice : BlinkApiError

    data object AmountTooSmall : BlinkApiError

    data object LimitExceeded : BlinkApiError

    data object RateLimited : BlinkApiError

    data object NetworkUnavailable : BlinkApiError

    data object Timeout : BlinkApiError

    data class Rejected(val code: String?, val detail: String?) : BlinkApiError

    data class Unexpected(val detail: String? = null) : BlinkApiError
}

internal class BlinkApiException(val error: BlinkApiError, cause: Throwable? = null) :
    Exception(error.toString(), cause)

internal class BlinkApiClient(
    private val httpClient: HttpClient,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
        }
) {
    suspend fun fetchAuthorizationScopes(apiKey: String): Set<String> {
        val data = execute(
            apiKey = apiKey,
            query = AUTHORIZATION_QUERY
        )
        return data
            .objectOrNull("authorization")
            ?.arrayOrNull("scopes")
            .orEmpty()
            .mapNotNull { element ->
                element.primitiveContentOrNull()
                    ?.trim()
                    ?.uppercase()
                    ?.takeIf(String::isNotEmpty)
            }.toSet()
    }

    suspend fun fetchDefaultWalletId(apiKey: String): String {
        val walletId =
            execute(
                apiKey = apiKey,
                query = DEFAULT_WALLET_ID_QUERY
            ).objectOrNull("me")
                ?.objectOrNull("defaultAccount")
                ?.objectOrNull("defaultWallet")
                ?.stringOrNull("id")
                ?.trim()
                .orEmpty()
        if (walletId.isEmpty()) {
            throw BlinkApiException(
                BlinkApiError.Unexpected("Blink did not return a default wallet")
            )
        }
        return walletId
    }

    suspend fun fetchContacts(apiKey: String): List<BlinkContact> = execute(
        apiKey = apiKey,
        query = CONTACTS_QUERY
    ).objectOrNull("me")
        ?.arrayOrNull("contacts")
        .orEmpty()
        .mapNotNull { element ->
            val contact = element as? JsonObject ?: return@mapNotNull null
            val handle =
                contact
                    .stringOrNull("handle")
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
            BlinkContact(
                handle = handle,
                alias =
                    contact
                        .stringOrNull("alias")
                        ?.trim()
                        ?.takeIf(String::isNotEmpty),
                transactionsCount =
                    contact["transactionsCount"]
                        ?.let { it as? JsonPrimitive }
                        ?.intOrNull
                        ?: 0
            )
        }

    suspend fun payInvoice(
        apiKey: String,
        walletId: String,
        invoice: String,
        amountMsats: Long?
    ): BlinkPaymentResult {
        val isAmountlessInvoice = amountMsats != null
        val operation =
            if (isAmountlessInvoice) {
                NO_AMOUNT_PAYMENT_MUTATION
            } else {
                PAYMENT_MUTATION
            }
        val operationName =
            if (isAmountlessInvoice) {
                "lnNoAmountInvoicePaymentSend"
            } else {
                "lnInvoicePaymentSend"
            }
        val input =
            buildJsonObject {
                put("paymentRequest", invoice)
                put("walletId", walletId)
                amountMsats?.let { msats ->
                    put("amount", (msats + MSATS_PER_SAT - 1) / MSATS_PER_SAT)
                }
            }
        val payload =
            execute(
                apiKey = apiKey,
                query = operation,
                variables =
                    buildJsonObject {
                        put("input", input)
                    }
            ).objectOrNull(operationName)
                ?: throw BlinkApiException(
                    BlinkApiError.Unexpected("Blink did not return a payment result")
                )

        payload.arrayOrNull("errors")?.firstOrNull()?.let { element ->
            val paymentError = element as? JsonObject
            throw BlinkApiException(
                classifyError(
                    code = paymentError?.stringOrNull("code"),
                    detail = paymentError?.stringOrNull("message")
                )
            )
        }

        val transaction = payload.objectOrNull("transaction")?.toTransaction()
        return when (payload.stringOrNull("status")?.uppercase()) {
            "SUCCESS" ->
                BlinkPaymentResult.Success(
                    preimageHex = transaction?.preimageHex,
                    feesPaidMsats = transaction?.feesPaidMsats
                )

            "ALREADY_PAID" ->
                BlinkPaymentResult.AlreadyPaid(
                    preimageHex = transaction?.preimageHex
                )

            "PENDING" ->
                BlinkPaymentResult.Pending(
                    preimageHex = transaction?.preimageHex,
                    feesPaidMsats = transaction?.feesPaidMsats
                )

            "FAILURE" -> throw BlinkApiException(BlinkApiError.Rejected(null, null))

            else ->
                throw BlinkApiException(
                    BlinkApiError.Unexpected("Unknown Blink payment status")
                )
        }
    }

    suspend fun lookupPayment(apiKey: String, paymentHash: String): BlinkPaymentStatus {
        val wallets =
            execute(
                apiKey = apiKey,
                query = PAYMENT_LOOKUP_QUERY,
                variables =
                    buildJsonObject {
                        put("paymentHash", paymentHash)
                    }
            ).objectOrNull("me")
                ?.objectOrNull("defaultAccount")
                ?.arrayOrNull("wallets")
                .orEmpty()
        val outgoingTransactions =
            wallets
                .flatMap { walletElement ->
                    (walletElement as? JsonObject)
                        ?.arrayOrNull("transactionsByPaymentHash")
                        .orEmpty()
                }.mapNotNull { it as? JsonObject }
                .filter { transaction ->
                    transaction.stringOrNull("direction")?.uppercase() == "SEND"
                }

        outgoingTransactions
            .firstOrNull { it.stringOrNull("status")?.uppercase() == "SUCCESS" }
            ?.let { transaction ->
                val settled = transaction.toTransaction()
                return BlinkPaymentStatus.Settled(
                    preimageHex = settled.preimageHex,
                    feesPaidMsats = settled.feesPaidMsats
                )
            }
        return when {
            outgoingTransactions.any {
                it.stringOrNull("status")?.uppercase() == "PENDING"
            } -> BlinkPaymentStatus.Pending

            outgoingTransactions.any {
                it.stringOrNull("status")?.uppercase() == "FAILURE"
            } -> BlinkPaymentStatus.Failed

            else -> BlinkPaymentStatus.NotFound
        }
    }

    private suspend fun execute(
        apiKey: String,
        query: String,
        variables: JsonObject = JsonObject(emptyMap())
    ): JsonObject {
        val responseText =
            try {
                val response =
                    httpClient.post(BLINK_API_URL) {
                        contentType(ContentType.Application.Json)
                        header(API_KEY_HEADER, apiKey)
                        setBody(
                            buildJsonObject {
                                put("query", query)
                                put("variables", variables)
                            }.toString()
                        )
                    }
                if (!response.status.isSuccess()) {
                    throw BlinkApiException(response.status.toApiError())
                }
                response.bodyAsText()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (apiError: BlinkApiException) {
                throw apiError
            } catch (error: Throwable) {
                throw BlinkApiException(
                    error =
                        if (error.isTimeout()) {
                            BlinkApiError.Timeout
                        } else {
                            BlinkApiError.NetworkUnavailable
                        },
                    cause = error
                )
            }

        val payload =
            runCatching {
                json.parseToJsonElement(responseText).jsonObject
            }.getOrElse { error ->
                throw BlinkApiException(
                    BlinkApiError.Unexpected("Invalid Blink response"),
                    error
                )
            }
        payload.arrayOrNull("errors")?.firstOrNull()?.let { element ->
            val graphQlError = element as? JsonObject
            val code =
                graphQlError
                    ?.objectOrNull("extensions")
                    ?.stringOrNull("code")
            val detail = graphQlError?.stringOrNull("message")
            throw BlinkApiException(
                classifyError(
                    code = code,
                    detail = detail,
                    isAuthenticationError =
                        code == "UNAUTHENTICATED" || code == "FORBIDDEN"
                )
            )
        }
        return payload.objectOrNull("data")
            ?: throw BlinkApiException(
                BlinkApiError.Unexpected("Blink response did not contain data")
            )
    }
}

internal sealed interface BlinkPaymentResult {
    data class Success(val preimageHex: String?, val feesPaidMsats: Long?) : BlinkPaymentResult

    data class AlreadyPaid(val preimageHex: String?) : BlinkPaymentResult

    data class Pending(val preimageHex: String?, val feesPaidMsats: Long?) : BlinkPaymentResult
}

internal sealed interface BlinkPaymentStatus {
    data class Settled(val preimageHex: String?, val feesPaidMsats: Long?) : BlinkPaymentStatus

    data object Pending : BlinkPaymentStatus

    data object Failed : BlinkPaymentStatus

    data object NotFound : BlinkPaymentStatus
}

private data class BlinkTransaction(val preimageHex: String?, val feesPaidMsats: Long?)

private fun HttpStatusCode.toApiError(): BlinkApiError = when (this) {
    HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> BlinkApiError.InvalidApiKey
    HttpStatusCode.TooManyRequests -> BlinkApiError.RateLimited
    else -> BlinkApiError.NetworkUnavailable
}

internal fun classifyError(
    code: String?,
    detail: String?,
    isAuthenticationError: Boolean = false
): BlinkApiError {
    val combined = listOfNotNull(code, detail).joinToString(" ").lowercase()
    return when {
        combined.contains("authorizationerror") ||
            combined.contains("not authorized to execute mutations") ||
            (combined.contains("not authorized") && combined.contains("mutation")) ->
            BlinkApiError.PermissionDenied

        (combined.contains("insufficient") && combined.contains("balance")) ||
            combined.contains("insufficientbalance") ||
            (combined.contains("not enough") && combined.contains("balance")) ->
            BlinkApiError.InsufficientBalance

        (combined.contains("route") && combined.contains("not found")) ||
            combined.contains("no_route") ||
            combined.contains("routenotfound") ->
            BlinkApiError.RouteNotFound

        combined.contains("invoice") && combined.contains("expired") ->
            BlinkApiError.InvoiceExpired

        combined.contains("selfpayment") ||
            (combined.contains("self") && combined.contains("payment")) ||
            combined.contains("same wallet") ->
            BlinkApiError.SelfPayment

        (combined.contains("invalid") && combined.contains("invoice")) ||
            combined.contains("malformed") ->
            BlinkApiError.InvalidInvoice

        (combined.contains("amount") && combined.contains("too small")) ||
            combined.contains("dust") ->
            BlinkApiError.AmountTooSmall

        (combined.contains("amount") && combined.contains("too large")) ||
            (combined.contains("limit") && combined.contains("exceeded")) ->
            BlinkApiError.LimitExceeded

        (combined.contains("rate") && combined.contains("limit")) ||
            combined.contains("too many requests") ->
            BlinkApiError.RateLimited

        isAuthenticationError -> BlinkApiError.InvalidApiKey

        else -> BlinkApiError.Rejected(code = code, detail = detail)
    }
}

private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key]
    ?.takeUnless { it is JsonNull }
    ?.let { it as? JsonObject }

private fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key]
    ?.takeUnless { it is JsonNull }
    ?.let { it as? JsonArray }

private fun JsonObject.stringOrNull(key: String): String? = this[key]?.primitiveContentOrNull()

private fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonElement.primitiveContentOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun JsonObject.toTransaction(): BlinkTransaction {
    val settlementVia = objectOrNull("settlementVia")
    val fee = longOrNull("settlementFee")
    val feeMsats =
        fee
            ?.takeUnless { it == Long.MIN_VALUE }
            ?.absoluteValue
            ?.takeIf { it <= Long.MAX_VALUE / MSATS_PER_SAT }
            ?.times(MSATS_PER_SAT)
            ?.takeIf {
                stringOrNull("settlementCurrency")?.uppercase() == "BTC"
            }
    return BlinkTransaction(
        preimageHex =
            settlementVia
                ?.stringOrNull("preImage")
                ?.trim()
                ?.takeIf(String::isNotEmpty),
        feesPaidMsats = feeMsats
    )
}

private fun Throwable.isTimeout(): Boolean {
    val errorText = message?.lowercase().orEmpty()
    return errorText.contains("timeout") ||
        errorText.contains("timed out") ||
        cause?.isTimeout() == true
}

private const val BLINK_API_URL = "https://api.blink.sv/graphql"
private const val API_KEY_HEADER = "X-API-KEY"
private const val BLINK_LIGHTNING_ADDRESS_DOMAIN = "blink.sv"
private const val MSATS_PER_SAT = 1_000L
private const val DOLLAR = '$'

private const val AUTHORIZATION_QUERY =
    """
    query Authorization {
      authorization {
        scopes
      }
    }
    """

private const val DEFAULT_WALLET_ID_QUERY =
    """
    query DefaultWalletId {
      me {
        defaultAccount {
          defaultWallet {
            id
          }
        }
      }
    }
    """

private const val CONTACTS_QUERY =
    """
    query BlinkContacts {
      me {
        contacts {
          alias
          handle
          transactionsCount
        }
      }
    }
    """

private const val PAYMENT_TRANSACTION_FIELDS =
    """
    status
    direction
    settlementFee
    settlementCurrency
    settlementVia {
      ... on SettlementViaIntraLedger {
        preImage
      }
      ... on SettlementViaLn {
        preImage
      }
    }
    """

private const val PAYMENT_MUTATION =
    """
    mutation LnInvoicePaymentSend(${DOLLAR}input: LnInvoicePaymentInput!) {
      lnInvoicePaymentSend(input: ${DOLLAR}input) {
        status
        errors {
          message
          code
        }
        transaction {
          $PAYMENT_TRANSACTION_FIELDS
        }
      }
    }
    """

private const val NO_AMOUNT_PAYMENT_MUTATION =
    """
    mutation LnNoAmountInvoicePaymentSend(${DOLLAR}input: LnNoAmountInvoicePaymentInput!) {
      lnNoAmountInvoicePaymentSend(input: ${DOLLAR}input) {
        status
        errors {
          message
          code
        }
        transaction {
          $PAYMENT_TRANSACTION_FIELDS
        }
      }
    }
    """

private const val PAYMENT_LOOKUP_QUERY =
    """
    query TransactionsByPaymentHash(${DOLLAR}paymentHash: PaymentHash!) {
      me {
        defaultAccount {
          wallets {
            ... on BTCWallet {
              transactionsByPaymentHash(paymentHash: ${DOLLAR}paymentHash) {
                $PAYMENT_TRANSACTION_FIELDS
              }
            }
            ... on UsdWallet {
              transactionsByPaymentHash(paymentHash: ${DOLLAR}paymentHash) {
                $PAYMENT_TRANSACTION_FIELDS
              }
            }
          }
        }
      }
    }
    """
