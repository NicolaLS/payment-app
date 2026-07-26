package xyz.lilsus.blip.integration.blink

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Error as ApolloGraphQlError
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.msat
import kotlin.math.absoluteValue
import xyz.lilsus.blip.integration.blink.graphql.AuthorizationQuery
import xyz.lilsus.blip.integration.blink.graphql.BlinkContactsQuery
import xyz.lilsus.blip.integration.blink.graphql.DefaultWalletIdQuery
import xyz.lilsus.blip.integration.blink.graphql.LnInvoicePaymentSendMutation
import xyz.lilsus.blip.integration.blink.graphql.LnNoAmountInvoicePaymentSendMutation
import xyz.lilsus.blip.integration.blink.graphql.TransactionsByPaymentHashQuery
import xyz.lilsus.blip.integration.blink.graphql.fragment.BlinkTransactionPaymentResult
import xyz.lilsus.blip.integration.blink.graphql.type.LnInvoicePaymentInput
import xyz.lilsus.blip.integration.blink.graphql.type.LnNoAmountInvoicePaymentInput
import xyz.lilsus.blip.integration.blink.graphql.type.PaymentSendResult
import xyz.lilsus.blip.integration.blink.graphql.type.TxDirection
import xyz.lilsus.blip.integration.blink.graphql.type.TxStatus
import xyz.lilsus.blip.integration.blink.graphql.type.WalletCurrency

/**
 * Client for Blink's GraphQL API.
 * Handles Lightning invoice payments using API key authentication.
 *
 * API Reference: https://dev.blink.sv/public-api-reference.html
 */
class BlinkApiClient(
    private val apolloClient: ApolloClient = createBlinkApolloClient(),
    private val logResponses: Boolean = false
) {
    /**
     * Translates Blink API errors into user-friendly messages.
     * Returns a translated message or null if no specific translation applies.
     */
    private fun translateBlinkError(code: String?, message: String?): BlinkErrorTranslation? {
        val combinedText = listOfNotNull(code, message)
            .joinToString(" ")
            .lowercase()

        return when {
            // Permission/Authorization errors - API key missing write permissions
            combinedText.contains("authorizationerror") ||
                combinedText.contains("not authorized to execute mutations") ||
                (combinedText.contains("not authorized") && combinedText.contains("mutation")) ->
                BlinkErrorTranslation.PermissionDenied

            // Insufficient balance
            (combinedText.contains("insufficient") && combinedText.contains("balance")) ||
                combinedText.contains("insufficientbalance") ||
                (combinedText.contains("not enough") && combinedText.contains("balance")) ->
                BlinkErrorTranslation.InsufficientBalance

            // Route not found (Lightning Network routing failure)
            (combinedText.contains("route") && combinedText.contains("not found")) ||
                combinedText.contains("no_route") ||
                combinedText.contains("routenotfound") ||
                (combinedText.contains("unable to find") && combinedText.contains("path")) ->
                BlinkErrorTranslation.RouteNotFound

            // Invoice expired
            (combinedText.contains("invoice") && combinedText.contains("expired")) ||
                (combinedText.contains("payment request") && combinedText.contains("expired")) ||
                (combinedText.contains("expir") && combinedText.contains("invoice")) ->
                BlinkErrorTranslation.InvoiceExpired

            // Self-payment (trying to pay your own invoice)
            (combinedText.contains("self") && combinedText.contains("payment")) ||
                combinedText.contains("selfpayment") ||
                combinedText.contains("same wallet") ||
                combinedText.contains("pay yourself") ->
                BlinkErrorTranslation.SelfPayment

            // Invalid invoice format
            (combinedText.contains("invalid") && combinedText.contains("invoice")) ||
                (combinedText.contains("invalid") && combinedText.contains("payment request")) ||
                (combinedText.contains("decode") && combinedText.contains("fail")) ||
                combinedText.contains("malformed") ->
                BlinkErrorTranslation.InvalidInvoice

            // Amount too small
            (combinedText.contains("amount") && combinedText.contains("too small")) ||
                (combinedText.contains("below") && combinedText.contains("minimum")) ||
                combinedText.contains("dust") ->
                BlinkErrorTranslation.AmountTooSmall

            // Amount too large / limit exceeded
            (combinedText.contains("amount") && combinedText.contains("too large")) ||
                (combinedText.contains("exceeds") && combinedText.contains("limit")) ||
                (combinedText.contains("limit") && combinedText.contains("exceeded")) ||
                combinedText.contains("withdrawal limit") ->
                BlinkErrorTranslation.LimitExceeded

            // Rate limiting
            (combinedText.contains("rate") && combinedText.contains("limit")) ||
                combinedText.contains("too many requests") ||
                combinedText.contains("throttl") ->
                BlinkErrorTranslation.RateLimited

            else -> null
        }
    }

    /**
     * Returns a user-friendly error for known Blink error patterns.
     */
    private fun createUserFriendlyError(
        code: String?,
        message: String?,
        isAuthError: Boolean = false
    ): BlinkApiError {
        val translation = translateBlinkError(code, message)

        return when {
            translation != null -> when (translation) {
                BlinkErrorTranslation.PermissionDenied ->
                    BlinkApiError.BlinkError(BlinkErrorType.PermissionDenied)

                BlinkErrorTranslation.InsufficientBalance ->
                    BlinkApiError.BlinkError(BlinkErrorType.InsufficientBalance)

                BlinkErrorTranslation.RouteNotFound ->
                    BlinkApiError.BlinkError(BlinkErrorType.RouteNotFound)

                BlinkErrorTranslation.InvoiceExpired ->
                    BlinkApiError.BlinkError(BlinkErrorType.InvoiceExpired)

                BlinkErrorTranslation.SelfPayment ->
                    BlinkApiError.BlinkError(BlinkErrorType.SelfPayment)

                BlinkErrorTranslation.InvalidInvoice ->
                    BlinkApiError.BlinkError(BlinkErrorType.InvalidInvoice)

                BlinkErrorTranslation.AmountTooSmall ->
                    BlinkApiError.BlinkError(BlinkErrorType.AmountTooSmall)

                BlinkErrorTranslation.LimitExceeded ->
                    BlinkApiError.BlinkError(BlinkErrorType.LimitExceeded)

                BlinkErrorTranslation.RateLimited ->
                    BlinkApiError.BlinkError(BlinkErrorType.RateLimited)
            }

            isAuthError ->
                BlinkApiError.BlinkError(BlinkErrorType.InvalidApiKey)

            else ->
                BlinkApiError.PaymentRejected(code = code, message = message)
        }
    }

    private enum class BlinkErrorTranslation {
        PermissionDenied,
        InsufficientBalance,
        RouteNotFound,
        InvoiceExpired,
        SelfPayment,
        InvalidInvoice,
        AmountTooSmall,
        LimitExceeded,
        RateLimited
    }

    /**
     * Looks up the status of a Lightning payment by its payment hash.
     *
     * Uses wallet transactions for outgoing payments to avoid invoice-not-found errors.
     *
     * @param apiKey The Blink API key for authentication.
     * @param paymentHash The hex-encoded payment hash to look up.
     * @return [BlinkPaymentStatusResult] indicating the payment status.
     * @throws [BlinkApiException] on failure.
     */
    suspend fun lookupPaymentStatus(apiKey: String, paymentHash: String): BlinkPaymentStatusResult {
        val data = executeGraphQlRequest(
            apiKey = apiKey,
            logLabel = "TransactionsByPaymentHash",
            call = apolloClient.query(TransactionsByPaymentHashQuery(paymentHash))
        )

        val wallets = data.me?.defaultAccount?.wallets
            ?: return BlinkPaymentStatusResult.NotFound

        val sendTransactions = wallets.flatMap { wallet ->
            val btcTransactions = wallet.onBTCWallet?.transactionsByPaymentHash
                ?.map { it.blinkTransactionPaymentResult }
                ?.filter { it.direction == TxDirection.SEND }
                ?: emptyList()
            val usdTransactions = wallet.onUsdWallet?.transactionsByPaymentHash
                ?.map { it.blinkTransactionPaymentResult }
                ?.filter { it.direction == TxDirection.SEND }
                ?: emptyList()

            btcTransactions + usdTransactions
        }
        val successfulTransaction = sendTransactions.firstOrNull { it.status == TxStatus.SUCCESS }

        return when {
            successfulTransaction != null -> BlinkPaymentStatusResult.Paid(
                preimage = successfulTransaction.preimage(),
                feesPaid = successfulTransaction.feesPaid()
            )

            sendTransactions.any {
                it.status == TxStatus.PENDING
            } -> BlinkPaymentStatusResult.Pending

            sendTransactions.any {
                it.status == TxStatus.FAILURE
            } -> BlinkPaymentStatusResult.Failed

            sendTransactions.isEmpty() -> BlinkPaymentStatusResult.NotFound

            else -> BlinkPaymentStatusResult.NotFound
        }
    }

    /**
     * Fetches the authorization scopes for the provided API key.
     *
     * @param apiKey The Blink API key to check.
     * @return List of scope strings (e.g., ["READ", "WRITE", "RECEIVE"]).
     * @throws [BlinkApiException] on failure.
     */
    suspend fun fetchAuthorizationScopes(apiKey: String): List<String> {
        val data = executeGraphQlRequest(
            apiKey = apiKey,
            logLabel = "Authorization",
            call = apolloClient.query(AuthorizationQuery())
        )

        return data.authorization.scopes
            .mapNotNull { scope -> scope.rawValue.trim().takeIf { it.isNotEmpty() } }
    }

    /**
     * Fetches the user's default Blink wallet id using the provided API key.
     *
     * Blink requires `walletId` for payment mutations; this allows the app to use the user's
     * default wallet context without asking them for account/wallet identifiers.
     */
    suspend fun fetchDefaultWalletId(apiKey: String): String {
        val data = executeGraphQlRequest(
            apiKey = apiKey,
            logLabel = "DefaultWalletId",
            call = apolloClient.query(DefaultWalletIdQuery())
        )

        val walletId = data.me?.defaultAccount?.defaultWallet?.id
            ?.trim()
            .orEmpty()

        if (walletId.isBlank()) {
            throw BlinkApiException(
                BlinkApiError.Unexpected("Missing default wallet id in response")
            )
        }

        return walletId
    }

    /**
     * Fetches contacts saved in Blink for the API key's user.
     */
    @Suppress("DEPRECATION")
    suspend fun fetchContacts(apiKey: String): List<BlinkContact> {
        val data = executeGraphQlRequest(
            apiKey = apiKey,
            logLabel = "BlinkContacts",
            call = apolloClient.query(BlinkContactsQuery())
        )

        return data.me?.contacts.orEmpty()
            .mapNotNull { contact ->
                val handle = contact.handle.trim()
                if (handle.isBlank()) return@mapNotNull null
                BlinkContact(
                    handle = handle,
                    alias = contact.alias?.trim()?.takeIf { it.isNotEmpty() },
                    transactionsCount = contact.transactionsCount,
                    // Blink mobile displays bare contact handles with @blink.sv even when
                    // deprecated globals return legacy domains like pay.bbw.sv.
                    lightningAddressDomain = DEFAULT_LIGHTNING_ADDRESS_DOMAIN
                )
            }
    }

    /**
     * Pays a BOLT11 invoice with an embedded amount.
     *
     * @param apiKey The Blink API key for authentication.
     * @param walletId The Blink wallet id to pay from.
     * @param invoice The BOLT11 invoice to pay.
     * @return [BlinkPaymentResult] on success.
     * @throws [BlinkApiException] on failure.
     */
    suspend fun payInvoice(apiKey: String, walletId: String, invoice: String): BlinkPaymentResult {
        val data = executeGraphQlRequest(
            apiKey = apiKey,
            logLabel = "lnInvoicePaymentSend",
            call = apolloClient.mutation(
                LnInvoicePaymentSendMutation(
                    LnInvoicePaymentInput(
                        paymentRequest = invoice,
                        walletId = walletId
                    )
                )
            )
        )

        return parsePaymentResponse(
            PaymentPayload(
                status = data.lnInvoicePaymentSend.status,
                errors = data.lnInvoicePaymentSend.errors.map {
                    PaymentPayloadError(it.code, it.message)
                },
                feesPaid = data.lnInvoicePaymentSend.transaction
                    ?.blinkTransactionPaymentResult
                    ?.feesPaid(),
                preimage = data.lnInvoicePaymentSend.transaction
                    ?.blinkTransactionPaymentResult
                    ?.preimage()
            )
        )
    }

    /**
     * Pays a zero-amount BOLT11 invoice with a specified amount.
     *
     * @param apiKey The Blink API key for authentication.
     * @param walletId The Blink wallet id to pay from.
     * @param invoice The zero-amount BOLT11 invoice to pay.
     * @param amountSats The amount to pay in satoshis.
     * @return [BlinkPaymentResult] on success.
     * @throws [BlinkApiException] on failure.
     */
    suspend fun payNoAmountInvoice(
        apiKey: String,
        walletId: String,
        invoice: String,
        amountSats: Long
    ): BlinkPaymentResult {
        val data = executeGraphQlRequest(
            apiKey = apiKey,
            logLabel = "lnNoAmountInvoicePaymentSend",
            call = apolloClient.mutation(
                LnNoAmountInvoicePaymentSendMutation(
                    LnNoAmountInvoicePaymentInput(
                        amount = amountSats,
                        paymentRequest = invoice,
                        walletId = walletId
                    )
                )
            )
        )

        return parsePaymentResponse(
            PaymentPayload(
                status = data.lnNoAmountInvoicePaymentSend.status,
                errors = data.lnNoAmountInvoicePaymentSend.errors.map {
                    PaymentPayloadError(it.code, it.message)
                },
                feesPaid = data.lnNoAmountInvoicePaymentSend.transaction
                    ?.blinkTransactionPaymentResult
                    ?.feesPaid(),
                preimage = data.lnNoAmountInvoicePaymentSend.transaction
                    ?.blinkTransactionPaymentResult
                    ?.preimage()
            )
        )
    }

    private suspend fun <D : Operation.Data> executeGraphQlRequest(
        apiKey: String,
        logLabel: String,
        call: ApolloCall<D>
    ): D {
        val response = try {
            call
                .addHttpHeader(API_KEY_HEADER, apiKey)
                .execute()
        } catch (e: ApolloException) {
            throw mapApolloException(e)
        }

        logGraphQlResponse(logLabel, response)

        response.exception?.let { exception ->
            throw mapApolloException(exception)
        }

        response.errors?.firstOrNull()?.let { error ->
            throw BlinkApiException(mapGraphQlError(error))
        }

        return response.data
            ?: throw BlinkApiException(BlinkApiError.Unexpected("Missing data in response"))
    }

    private fun mapApolloException(exception: ApolloException): BlinkApiException {
        if (exception is ApolloHttpException) {
            val error = when (exception.statusCode) {
                HTTP_UNAUTHORIZED -> BlinkApiError.BlinkError(BlinkErrorType.InvalidApiKey)
                HTTP_TOO_MANY_REQUESTS -> BlinkApiError.BlinkError(BlinkErrorType.RateLimited)
                else -> BlinkApiError.NetworkUnavailable
            }
            return BlinkApiException(error, exception)
        }

        return BlinkApiException(
            if (exception.isTimeout()) BlinkApiError.Timeout else BlinkApiError.NetworkUnavailable,
            exception
        )
    }

    private fun mapGraphQlError(error: ApolloGraphQlError): BlinkApiError {
        val code = error.extensionCode()
        val isAuthError = code == "UNAUTHENTICATED" || code == "FORBIDDEN"
        return createUserFriendlyError(code, error.message, isAuthError)
    }

    private fun parsePaymentResponse(payload: PaymentPayload): BlinkPaymentResult {
        payload.errors.firstOrNull()?.let { error ->
            throw BlinkApiException(createUserFriendlyError(error.code, error.message))
        }

        return when (payload.status) {
            PaymentSendResult.SUCCESS -> BlinkPaymentResult.Success(
                feesPaid = payload.feesPaid,
                preimage = payload.preimage
            )

            PaymentSendResult.PENDING -> BlinkPaymentResult.Pending(
                feesPaid = payload.feesPaid,
                preimage = payload.preimage
            )

            PaymentSendResult.ALREADY_PAID -> BlinkPaymentResult.AlreadyPaid(
                feesPaid = payload.feesPaid,
                preimage = payload.preimage
            )

            PaymentSendResult.FAILURE -> throw BlinkApiException(BlinkApiError.PaymentRejected())

            PaymentSendResult.UNKNOWN__ -> throw BlinkApiException(
                BlinkApiError.Unexpected("Unknown payment status: ${payload.status.rawValue}")
            )

            null -> throw BlinkApiException(BlinkApiError.Unexpected("Missing status in response"))
        }
    }

    private fun ApolloGraphQlError.extensionCode(): String? =
        extensions?.get("code")?.let { value ->
            when (value) {
                is String -> value
                else -> value.toString()
            }
        }

    private fun Throwable.isTimeout(): Boolean {
        val text = message?.lowercase().orEmpty()
        return text.contains("timeout") ||
            text.contains("timed out") ||
            cause?.isTimeout() == true
    }

    private fun BlinkTransactionPaymentResult.feesPaid(): MilliSatoshi? =
        settlementFee.toLightningFee(settlementCurrency)

    private fun BlinkTransactionPaymentResult.preimage(): ByteVector32? =
        settlementVia.onSettlementViaIntraLedger?.preImage?.toPreimageOrNull()
            ?: settlementVia.onSettlementViaLn?.preImage?.toPreimageOrNull()

    private fun String.toPreimageOrNull(): ByteVector32? =
        runCatching { ByteVector32.fromValidHex(trim()) }.getOrNull()

    private fun Long.toLightningFee(settlementCurrency: WalletCurrency): MilliSatoshi? {
        if (settlementCurrency != WalletCurrency.BTC) return null

        return absoluteValue
            .takeIf { it <= Long.MAX_VALUE / 1000L }
            ?.times(1000L)
            ?.msat
    }

    private fun <D : Operation.Data> logGraphQlResponse(
        logLabel: String,
        response: ApolloResponse<D>
    ) {
        if (!logResponses) return
        println(
            "Blink GraphQL [$logLabel] " +
                "data=${response.data != null} " +
                "errors=${response.errors?.size ?: 0} " +
                "exception=${response.exception?.message.orEmpty()}"
        )
    }

    private data class PaymentPayload(
        val status: PaymentSendResult?,
        val errors: List<PaymentPayloadError>,
        val feesPaid: MilliSatoshi?,
        val preimage: ByteVector32?
    )

    private data class PaymentPayloadError(val code: String?, val message: String?)

    companion object {
        private const val BLINK_API_URL = "https://api.blink.sv/graphql"
        private const val API_KEY_HEADER = "X-API-KEY"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val DEFAULT_LIGHTNING_ADDRESS_DOMAIN = "blink.sv"

        fun createBlinkApolloClient(): ApolloClient = ApolloClient.Builder()
            .serverUrl(BLINK_API_URL)
            .build()
    }
}

/**
 * Result of a Blink payment operation.
 */
sealed class BlinkPaymentResult(
    open val feesPaid: MilliSatoshi?,
    open val preimage: ByteVector32?
) {
    /** Payment completed successfully. */
    data class Success(
        override val feesPaid: MilliSatoshi? = null,
        override val preimage: ByteVector32? = null
    ) : BlinkPaymentResult(feesPaid, preimage)

    /** Payment is pending (may complete later). */
    data class Pending(
        override val feesPaid: MilliSatoshi? = null,
        override val preimage: ByteVector32? = null
    ) : BlinkPaymentResult(feesPaid, preimage)

    /** Invoice was already paid. */
    data class AlreadyPaid(
        override val feesPaid: MilliSatoshi? = null,
        override val preimage: ByteVector32? = null
    ) : BlinkPaymentResult(feesPaid, preimage)
}

/**
 * Result of a Blink payment status lookup.
 */
sealed class BlinkPaymentStatusResult {
    /** Payment was confirmed as paid. */
    data class Paid(val preimage: ByteVector32?, val feesPaid: MilliSatoshi?) :
        BlinkPaymentStatusResult()

    /** Payment is still pending. */
    data object Pending : BlinkPaymentStatusResult()

    /** Payment failed or invoice expired. */
    data object Failed : BlinkPaymentStatusResult()

    /** Payment not found (may not have been initiated yet). */
    data object NotFound : BlinkPaymentStatusResult()
}
