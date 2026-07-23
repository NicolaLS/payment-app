package xyz.lilsus.rayl.blip.data.blink

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Error as ApolloGraphQlError
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import com.apollographql.apollo.exception.ApolloNetworkException
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.utils.msat
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.absoluteValue
import xyz.lilsus.rayl.blip.data.blink.graphql.AuthorizationQuery
import xyz.lilsus.rayl.blip.data.blink.graphql.BlinkContactsQuery
import xyz.lilsus.rayl.blip.data.blink.graphql.DefaultWalletIdQuery
import xyz.lilsus.rayl.blip.data.blink.graphql.LnInvoicePaymentSendMutation
import xyz.lilsus.rayl.blip.data.blink.graphql.LnNoAmountInvoicePaymentSendMutation
import xyz.lilsus.rayl.blip.data.blink.graphql.TransactionsByPaymentHashQuery
import xyz.lilsus.rayl.blip.data.blink.graphql.fragment.BlinkTransactionPaymentResult
import xyz.lilsus.rayl.blip.data.blink.graphql.type.LnInvoicePaymentInput
import xyz.lilsus.rayl.blip.data.blink.graphql.type.LnNoAmountInvoicePaymentInput
import xyz.lilsus.rayl.blip.data.blink.graphql.type.PaymentSendResult
import xyz.lilsus.rayl.blip.data.blink.graphql.type.TxDirection
import xyz.lilsus.rayl.blip.data.blink.graphql.type.TxStatus
import xyz.lilsus.rayl.blip.data.blink.graphql.type.WalletCurrency
import xyz.lilsus.rayl.blip.domain.BlinkAccountId
import xyz.lilsus.rayl.blip.domain.BlinkApiKey
import xyz.lilsus.rayl.blip.domain.BlinkWalletId

internal class BlinkApi(
    private val client: ApolloClient = ApolloClient.Builder()
        .serverUrl(BLINK_API_URL)
        .build()
) {
    suspend fun identify(apiKey: BlinkApiKey): BlinkIdentity {
        val scopes = execute(apiKey, client.query(AuthorizationQuery()))
            .authorization
            .scopes
            .map { it.rawValue }

        if (REQUIRED_WRITE_SCOPE !in scopes) {
            throw BlinkApiFailure.PermissionDenied
        }

        val account = execute(apiKey, client.query(DefaultWalletIdQuery()))
            .me
            ?.defaultAccount
            ?: throw BlinkApiFailure.InvalidResponse

        return BlinkIdentity(
            accountId = BlinkAccountId.require(account.id),
            walletId = BlinkWalletId.require(account.defaultWallet.id)
        )
    }

    @Suppress("DEPRECATION")
    suspend fun contacts(apiKey: BlinkApiKey): List<BlinkContactDto> =
        execute(apiKey, client.query(BlinkContactsQuery()))
            .me
            ?.contacts
            .orEmpty()
            .mapNotNull { contact ->
                val handle = contact.handle.trim()
                if (handle.isEmpty()) return@mapNotNull null
                BlinkContactDto(
                    name = contact.alias?.trim().takeUnless { it.isNullOrEmpty() } ?: handle,
                    lightningAddress = if ('@' in
                        handle
                    ) {
                        handle
                    } else {
                        "$handle@$BLINK_ADDRESS_DOMAIN"
                    }
                )
            }

    suspend fun payFixedInvoice(
        apiKey: BlinkApiKey,
        walletId: BlinkWalletId,
        invoice: String
    ): BlinkSubmitResult {
        val payload = execute(
            apiKey,
            client.mutation(
                LnInvoicePaymentSendMutation(
                    LnInvoicePaymentInput(
                        paymentRequest = invoice,
                        walletId = walletId.value
                    )
                )
            )
        ).lnInvoicePaymentSend

        return toSubmitResult(
            status = payload.status,
            errors = payload.errors.map { BlinkPayloadError(it.code) },
            transaction = payload.transaction?.blinkTransactionPaymentResult
        )
    }

    suspend fun payAmountlessInvoice(
        apiKey: BlinkApiKey,
        walletId: BlinkWalletId,
        invoice: String,
        amountSats: Long
    ): BlinkSubmitResult {
        val payload = execute(
            apiKey,
            client.mutation(
                LnNoAmountInvoicePaymentSendMutation(
                    LnNoAmountInvoicePaymentInput(
                        amount = amountSats,
                        paymentRequest = invoice,
                        walletId = walletId.value
                    )
                )
            )
        ).lnNoAmountInvoicePaymentSend

        return toSubmitResult(
            status = payload.status,
            errors = payload.errors.map { BlinkPayloadError(it.code) },
            transaction = payload.transaction?.blinkTransactionPaymentResult
        )
    }

    suspend fun lookup(
        apiKey: BlinkApiKey,
        walletId: BlinkWalletId,
        paymentHash: String
    ): BlinkLookupResult {
        val wallets = execute(
            apiKey,
            client.query(TransactionsByPaymentHashQuery(paymentHash))
        ).me?.defaultAccount?.wallets.orEmpty()
            .filter { it.id == walletId.value }

        val outgoing = wallets.flatMap { wallet ->
            val btc = wallet.onBTCWallet?.transactionsByPaymentHash
                .orEmpty()
                .map { it.blinkTransactionPaymentResult }
            val usd = wallet.onUsdWallet?.transactionsByPaymentHash
                .orEmpty()
                .map { it.blinkTransactionPaymentResult }
            (btc + usd).filter { it.direction == TxDirection.SEND }
        }

        val successful = outgoing.firstOrNull { it.status == TxStatus.SUCCESS }
        return when {
            successful != null -> BlinkLookupResult.Settled(
                feesPaid = successful.feesPaid(),
                preimage = successful.preimage()
            )

            outgoing.any { it.status == TxStatus.PENDING } -> BlinkLookupResult.Pending

            outgoing.any { it.status == TxStatus.FAILURE } -> BlinkLookupResult.Rejected

            else -> BlinkLookupResult.Unknown
        }
    }

    private suspend fun <D : Operation.Data> execute(apiKey: BlinkApiKey, call: ApolloCall<D>): D {
        val response = try {
            apiKey.use { raw ->
                call.addHttpHeader(API_KEY_HEADER, raw).execute()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: ApolloHttpException) {
            throw when (error.statusCode) {
                HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> BlinkApiFailure.AuthenticationRequired
                HTTP_TOO_MANY_REQUESTS -> BlinkApiFailure.RateLimited
                else -> BlinkApiFailure.Network
            }
        } catch (error: ApolloNetworkException) {
            throw BlinkApiFailure.Network
        } catch (error: ApolloException) {
            throw BlinkApiFailure.InvalidResponse
        }

        response.exception?.let { exception ->
            throw when (exception) {
                is ApolloHttpException -> when (exception.statusCode) {
                    HTTP_UNAUTHORIZED, HTTP_FORBIDDEN ->
                        BlinkApiFailure.AuthenticationRequired

                    HTTP_TOO_MANY_REQUESTS -> BlinkApiFailure.RateLimited

                    else -> BlinkApiFailure.Network
                }

                is ApolloNetworkException -> BlinkApiFailure.Network

                else -> BlinkApiFailure.InvalidResponse
            }
        }

        response.errors?.firstOrNull()?.let { error ->
            throw error.toFailure()
        }

        return response.data ?: throw BlinkApiFailure.InvalidResponse
    }

    private fun ApolloGraphQlError.toFailure(): BlinkApiFailure =
        when (extensionCode()?.uppercase()) {
            "UNAUTHENTICATED" -> BlinkApiFailure.AuthenticationRequired
            "FORBIDDEN", "AUTHORIZATION_ERROR" -> BlinkApiFailure.PermissionDenied
            "RATE_LIMITED", "TOO_MANY_REQUESTS" -> BlinkApiFailure.RateLimited
            else -> BlinkApiFailure.Provider(code = extensionCode())
        }

    private fun ApolloGraphQlError.extensionCode(): String? =
        extensions?.get("code")?.toString()?.trim('"')

    private fun toSubmitResult(
        status: PaymentSendResult?,
        errors: List<BlinkPayloadError>,
        transaction: BlinkTransactionPaymentResult?
    ): BlinkSubmitResult {
        errors.firstOrNull()?.let { error ->
            return BlinkSubmitResult.Rejected(error.code.toRejection())
        }

        return when (status) {
            PaymentSendResult.SUCCESS -> BlinkSubmitResult.Settled(
                feesPaid = transaction?.feesPaid(),
                preimage = transaction?.preimage()
            )

            PaymentSendResult.ALREADY_PAID ->
                BlinkSubmitResult.AlreadyPaid(transaction?.preimage())

            PaymentSendResult.PENDING -> BlinkSubmitResult.Pending

            PaymentSendResult.FAILURE ->
                BlinkSubmitResult.Rejected(BlinkRejection.Provider(code = null))

            PaymentSendResult.UNKNOWN__,
            null
            -> BlinkSubmitResult.Unknown
        }
    }

    private fun String?.toRejection(): BlinkRejection = when (this?.uppercase()) {
        "INSUFFICIENT_BALANCE" -> BlinkRejection.InsufficientBalance
        "ROUTE_NOT_FOUND", "NO_ROUTE" -> BlinkRejection.RouteNotFound
        "INVOICE_EXPIRED" -> BlinkRejection.InvoiceExpired
        "SELF_PAYMENT" -> BlinkRejection.SelfPayment
        "INVALID_INVOICE" -> BlinkRejection.InvalidInvoice
        "AMOUNT_TOO_SMALL" -> BlinkRejection.AmountTooSmall
        "LIMIT_EXCEEDED", "WITHDRAWAL_LIMIT_EXCEEDED" -> BlinkRejection.LimitExceeded
        "FORBIDDEN", "AUTHORIZATION_ERROR" -> BlinkRejection.PermissionDenied
        "RATE_LIMITED", "TOO_MANY_REQUESTS" -> BlinkRejection.RateLimited
        else -> BlinkRejection.Provider(this)
    }

    private fun BlinkTransactionPaymentResult.feesPaid(): MilliSatoshi? {
        if (settlementCurrency != WalletCurrency.BTC) return null
        return settlementFee.absoluteValue
            .takeIf { it <= Long.MAX_VALUE / 1_000L }
            ?.times(1_000L)
            ?.msat
    }

    private fun BlinkTransactionPaymentResult.preimage(): ByteVector32? =
        settlementVia.onSettlementViaIntraLedger?.preImage?.toPreimage()
            ?: settlementVia.onSettlementViaLn?.preImage?.toPreimage()

    private fun String.toPreimage(): ByteVector32? =
        runCatching { ByteVector32.fromValidHex(trim()) }.getOrNull()

    private companion object {
        const val BLINK_API_URL = "https://api.blink.sv/graphql"
        const val API_KEY_HEADER = "X-API-KEY"
        const val BLINK_ADDRESS_DOMAIN = "blink.sv"
        const val REQUIRED_WRITE_SCOPE = "WRITE"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

internal data class BlinkIdentity(val accountId: BlinkAccountId, val walletId: BlinkWalletId)

internal data class BlinkContactDto(val name: String, val lightningAddress: String)

internal sealed class BlinkApiFailure : RuntimeException() {
    data object AuthenticationRequired : BlinkApiFailure()
    data object PermissionDenied : BlinkApiFailure()
    data object RateLimited : BlinkApiFailure()
    data object Network : BlinkApiFailure()
    data object InvalidResponse : BlinkApiFailure()
    data class Provider(val code: String?) : BlinkApiFailure()
}

internal sealed interface BlinkSubmitResult {
    data class Settled(val feesPaid: MilliSatoshi?, val preimage: ByteVector32?) :
        BlinkSubmitResult

    data class AlreadyPaid(val preimage: ByteVector32?) : BlinkSubmitResult
    data object Pending : BlinkSubmitResult
    data class Rejected(val rejection: BlinkRejection) : BlinkSubmitResult
    data object Unknown : BlinkSubmitResult
}

internal sealed interface BlinkLookupResult {
    data class Settled(val feesPaid: MilliSatoshi?, val preimage: ByteVector32?) :
        BlinkLookupResult

    data object Pending : BlinkLookupResult
    data object Rejected : BlinkLookupResult
    data object Unknown : BlinkLookupResult
}

internal sealed interface BlinkRejection {
    data object PermissionDenied : BlinkRejection
    data object InsufficientBalance : BlinkRejection
    data object RouteNotFound : BlinkRejection
    data object InvoiceExpired : BlinkRejection
    data object SelfPayment : BlinkRejection
    data object InvalidInvoice : BlinkRejection
    data object AmountTooSmall : BlinkRejection
    data object LimitExceeded : BlinkRejection
    data object RateLimited : BlinkRejection
    data class Provider(val code: String?) : BlinkRejection
}

private data class BlinkPayloadError(val code: String?)
