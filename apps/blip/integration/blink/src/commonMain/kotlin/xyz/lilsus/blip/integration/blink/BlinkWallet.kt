package xyz.lilsus.blip.integration.blink

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.raylsuite.core.payment.PaidInvoice
import xyz.lilsus.raylsuite.core.payment.PayInvoiceRequest
import xyz.lilsus.raylsuite.core.payment.PaymentError
import xyz.lilsus.raylsuite.core.payment.PaymentException
import xyz.lilsus.raylsuite.core.payment.PaymentHash
import xyz.lilsus.raylsuite.core.payment.PaymentLookupResult
import xyz.lilsus.raylsuite.core.payment.PaymentProvider

data class BlinkWalletConnection(val alias: String)

sealed interface BlinkConnectionError {
    data object AlreadyConnected : BlinkConnectionError

    data object MissingConnection : BlinkConnectionError

    data object AliasRequired : BlinkConnectionError

    data object ApiKeyRequired : BlinkConnectionError

    data object PaymentPermissionRequired : BlinkConnectionError
}

class BlinkConnectionException(val error: BlinkConnectionError) : Exception(error.toString())

class BlinkWallet internal constructor(
    private val apiClient: BlinkApiClient,
    private val credentialStore: BlinkCredentialStore,
    private val isNetworkAvailable: () -> Boolean
) : PaymentProvider {
    private val mutableConnection =
        MutableStateFlow(credentialStore.read()?.toConnection())

    val connection: StateFlow<BlinkWalletConnection?> = mutableConnection.asStateFlow()

    suspend fun connect(apiKey: String, alias: String): BlinkWalletConnection {
        ensureNotConnected()

        val normalizedApiKey = apiKey.trim()
        val normalizedAlias = alias.trim()
        if (normalizedAlias.isEmpty()) {
            throw BlinkConnectionException(BlinkConnectionError.AliasRequired)
        }
        if (normalizedApiKey.isEmpty()) {
            throw BlinkConnectionException(BlinkConnectionError.ApiKeyRequired)
        }

        val scopes = apiClient.fetchAuthorizationScopes(normalizedApiKey)
        if (REQUIRED_SCOPE !in scopes) {
            throw BlinkConnectionException(BlinkConnectionError.PaymentPermissionRequired)
        }

        val defaultWalletId = apiClient.fetchDefaultWalletId(normalizedApiKey)
        ensureNotConnected()

        val credentials = BlinkCredentials(
            apiKey = normalizedApiKey,
            defaultWalletId = defaultWalletId,
            alias = normalizedAlias
        )
        credentialStore.save(credentials)

        return credentials.toConnection().also {
            mutableConnection.value = it
        }
    }

    fun disconnect() {
        credentialStore.clear()
        mutableConnection.value = null
    }

    suspend fun getCachedDefaultWalletId(): String = requireCredentials().defaultWalletId

    suspend fun refreshDefaultWalletId(): String {
        val credentials = requireCredentials()
        val defaultWalletId = apiClient.fetchDefaultWalletId(credentials.apiKey)
        credentialStore.save(credentials.copy(defaultWalletId = defaultWalletId))
        return defaultWalletId
    }

    suspend fun fetchContacts(): List<BlinkContact> {
        val credentials = requireCredentials()
        return apiClient.fetchContacts(credentials.apiKey)
    }

    override suspend fun payInvoice(request: PayInvoiceRequest): PaidInvoice {
        if (!isNetworkAvailable()) {
            throw PaymentException(PaymentError.NetworkUnavailable)
        }

        val credentials = credentialStore.read()
            ?: throw PaymentException(PaymentError.MissingWalletConnection)

        val amountMsats = request.amountMsats
        val result = try {
            if (amountMsats != null) {
                apiClient.payNoAmountInvoice(
                    apiKey = credentials.apiKey,
                    walletId = credentials.defaultWalletId,
                    invoice = request.invoice.value,
                    amountSats = amountMsats.toSatsRoundedUp()
                )
            } else {
                apiClient.payInvoice(
                    apiKey = credentials.apiKey,
                    walletId = credentials.defaultWalletId,
                    invoice = request.invoice.value
                )
            }
        } catch (error: BlinkApiException) {
            val paymentError = error.error.toPaymentError()
            if (
                paymentError == PaymentError.NetworkUnavailable ||
                paymentError == PaymentError.Timeout
            ) {
                throw PaymentException(
                    PaymentError.PaymentUnconfirmed(
                        paymentHash = null,
                        detail = "Payment status unknown"
                    ),
                    error
                )
            }
            throw PaymentException(paymentError, error)
        }

        if (result is BlinkPaymentResult.Pending) {
            throw PaymentException(
                PaymentError.PaymentUnconfirmed(
                    paymentHash = null,
                    detail = "Payment is being processed"
                )
            )
        }

        val wasAlreadyPaid = result is BlinkPaymentResult.AlreadyPaid
        return PaidInvoice(
            preimageHex = result.preimage?.toHex(),
            feesPaidMsats = if (wasAlreadyPaid) null else result.feesPaid?.msat,
            wasAlreadyPaid = wasAlreadyPaid
        )
    }

    override suspend fun lookupPayment(paymentHash: PaymentHash): PaymentLookupResult {
        if (!isNetworkAvailable()) {
            return PaymentLookupResult.LookupError(PaymentError.NetworkUnavailable)
        }

        val credentials = credentialStore.read()
            ?: return PaymentLookupResult.LookupError(PaymentError.MissingWalletConnection)

        return try {
            when (
                val status = apiClient.lookupPaymentStatus(
                    apiKey = credentials.apiKey,
                    paymentHash = paymentHash.hex
                )
            ) {
                is BlinkPaymentStatusResult.Paid ->
                    PaymentLookupResult.Settled(
                        PaidInvoice(
                            preimageHex = status.preimage?.toHex(),
                            feesPaidMsats = status.feesPaid?.msat
                        )
                    )

                BlinkPaymentStatusResult.Pending -> PaymentLookupResult.Pending

                BlinkPaymentStatusResult.Failed -> PaymentLookupResult.Failed

                BlinkPaymentStatusResult.NotFound -> PaymentLookupResult.NotFound
            }
        } catch (error: BlinkApiException) {
            PaymentLookupResult.LookupError(error.error.toPaymentError())
        }
    }

    private fun ensureNotConnected() {
        if (mutableConnection.value != null) {
            throw BlinkConnectionException(BlinkConnectionError.AlreadyConnected)
        }
    }

    private fun requireCredentials(): BlinkCredentials = credentialStore.read()
        ?: throw BlinkConnectionException(BlinkConnectionError.MissingConnection)
}

fun createBlinkWallet(secureSettings: Settings, isNetworkAvailable: () -> Boolean): BlinkWallet =
    BlinkWallet(
        apiClient = BlinkApiClient(),
        credentialStore = BlinkCredentialStore(secureSettings),
        isNetworkAvailable = isNetworkAvailable
    )

private fun BlinkCredentials.toConnection(): BlinkWalletConnection =
    BlinkWalletConnection(alias = alias)

private fun Long.toSatsRoundedUp(): Long = (this + 999L) / 1_000L

private fun BlinkApiError.toPaymentError(): PaymentError = when (this) {
    is BlinkApiError.BlinkError ->
        when (type) {
            BlinkErrorType.InvalidApiKey ->
                PaymentError.AuthenticationFailure()

            BlinkErrorType.PermissionDenied ->
                PaymentError.InsufficientPermissions()

            else ->
                PaymentError.PaymentRejected(code = type.name)
        }

    BlinkApiError.NetworkUnavailable -> PaymentError.NetworkUnavailable

    is BlinkApiError.PaymentRejected ->
        PaymentError.PaymentRejected(code = code, detail = message)

    BlinkApiError.Timeout -> PaymentError.Timeout

    is BlinkApiError.Unexpected -> PaymentError.Unexpected(message)
}

private const val REQUIRED_SCOPE = "WRITE"
