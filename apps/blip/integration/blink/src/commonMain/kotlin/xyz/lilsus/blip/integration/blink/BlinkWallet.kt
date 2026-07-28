package xyz.lilsus.blip.integration.blink

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

data class BlinkWalletConnection(val alias: String)

data class BlinkPaymentRequest(val invoice: String, val amountMsats: Long? = null) {
    init {
        require(invoice.isNotBlank()) { "An encoded Lightning invoice cannot be blank" }
        require(amountMsats == null || amountMsats > 0) {
            "An explicit invoice amount must be greater than zero"
        }
    }
}

sealed interface BlinkPaymentOutcome {
    data class Paid(val preimageHex: String?, val feesPaidMsats: Long?) : BlinkPaymentOutcome

    data object AlreadyPaid : BlinkPaymentOutcome

    data object Pending : BlinkPaymentOutcome

    data class DefinitiveFailure(val error: BlinkApiError) : BlinkPaymentOutcome

    data class StatusUnknown(val error: BlinkApiError) : BlinkPaymentOutcome
}

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
) {
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

    suspend fun submitPayment(request: BlinkPaymentRequest): BlinkPaymentOutcome {
        if (!isNetworkAvailable()) {
            return BlinkPaymentOutcome.DefinitiveFailure(BlinkApiError.NetworkUnavailable)
        }

        val credentials = credentialStore.read()
            ?: return BlinkPaymentOutcome.DefinitiveFailure(
                BlinkApiError.MissingWalletConnection
            )

        val amountMsats = request.amountMsats
        val result =
            withTimeoutOrNull(PAYMENT_RESOURCE_GUARD_MS) {
                try {
                    if (amountMsats != null) {
                        apiClient.payNoAmountInvoice(
                            apiKey = credentials.apiKey,
                            walletId = credentials.defaultWalletId,
                            invoice = request.invoice,
                            amountSats = amountMsats.toSatsRoundedUp()
                        )
                    } else {
                        apiClient.payInvoice(
                            apiKey = credentials.apiKey,
                            walletId = credentials.defaultWalletId,
                            invoice = request.invoice
                        )
                    }
                } catch (error: BlinkApiException) {
                    return@withTimeoutOrNull error.toPaymentOutcome()
                }
            }
                ?: return BlinkPaymentOutcome.StatusUnknown(BlinkApiError.Timeout)

        return when (result) {
            is BlinkPaymentOutcome -> result

            is BlinkPaymentResult.Success ->
                BlinkPaymentOutcome.Paid(
                    preimageHex = result.preimage?.toHex(),
                    feesPaidMsats = result.feesPaid?.msat
                )

            is BlinkPaymentResult.AlreadyPaid -> BlinkPaymentOutcome.AlreadyPaid

            is BlinkPaymentResult.Pending -> BlinkPaymentOutcome.Pending

            else ->
                BlinkPaymentOutcome.StatusUnknown(
                    BlinkApiError.Unexpected("Unexpected payment response")
                )
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

private fun BlinkApiException.toPaymentOutcome(): BlinkPaymentOutcome = when (error) {
    BlinkApiError.NetworkUnavailable,
    BlinkApiError.Timeout,
    is BlinkApiError.Unexpected -> BlinkPaymentOutcome.StatusUnknown(error)

    BlinkApiError.MissingWalletConnection,
    is BlinkApiError.BlinkError,
    is BlinkApiError.PaymentRejected -> BlinkPaymentOutcome.DefinitiveFailure(error)
}

private const val REQUIRED_SCOPE = "WRITE"
private const val PAYMENT_RESOURCE_GUARD_MS = 90_000L
