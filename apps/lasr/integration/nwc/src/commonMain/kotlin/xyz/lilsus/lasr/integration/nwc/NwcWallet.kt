package xyz.lilsus.lasr.integration.nwc

import com.russhwolf.settings.Settings
import io.github.nicolals.nwc.Amount
import io.github.nicolals.nwc.LookupInvoiceParams
import io.github.nicolals.nwc.NwcCapability
import io.github.nicolals.nwc.NwcClient
import io.github.nicolals.nwc.NwcConnectionUri
import io.github.nicolals.nwc.NwcError
import io.github.nicolals.nwc.NwcException
import io.github.nicolals.nwc.NwcResult
import io.github.nicolals.nwc.TransactionState
import io.github.nicolals.nwc.WalletDiscovery
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.lilsus.raylsuite.core.network.createWebSocketHttpClient
import xyz.lilsus.raylsuite.core.payment.PaidInvoice
import xyz.lilsus.raylsuite.core.payment.PayInvoiceRequest
import xyz.lilsus.raylsuite.core.payment.PaymentError
import xyz.lilsus.raylsuite.core.payment.PaymentException
import xyz.lilsus.raylsuite.core.payment.PaymentHash
import xyz.lilsus.raylsuite.core.payment.PaymentLookupResult
import xyz.lilsus.raylsuite.core.payment.PaymentProvider

data class NwcWalletConnection(
    val alias: String,
    val walletPublicKey: String,
    val relayUrl: String?,
    val lightningAddress: String?,
    val network: String? = null,
    val color: String? = null
)

sealed interface NwcConnectionError {
    data object AlreadyConnected : NwcConnectionError

    data object InvalidUri : NwcConnectionError

    data object PaymentPermissionRequired : NwcConnectionError

    data class ConnectionFailed(val detail: String? = null) : NwcConnectionError
}

class NwcConnectionException(val error: NwcConnectionError, cause: Throwable? = null) :
    Exception(error.toString(), cause)

class NwcWallet internal constructor(
    private val credentialStore: NwcCredentialStore,
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
    private val ownsHttpClient: Boolean
) : PaymentProvider {
    private val clientMutex = Mutex()
    private var client: NwcClient? = null
    private val mutableConnection =
        MutableStateFlow(credentialStore.read()?.toConnection())

    val connection: StateFlow<NwcWalletConnection?> = mutableConnection.asStateFlow()

    suspend fun connect(connectionUri: String, alias: String): NwcWalletConnection {
        if (mutableConnection.value != null) {
            throw NwcConnectionException(NwcConnectionError.AlreadyConnected)
        }
        val normalizedAlias = alias.trim()
        if (normalizedAlias.isEmpty()) {
            throw NwcConnectionException(
                NwcConnectionError.ConnectionFailed("Wallet alias is required")
            )
        }

        val discovery =
            when (
                val result =
                    NwcClient.discover(
                        uri = connectionUri.trim(),
                        httpClient = httpClient,
                        timeoutMs = DISCOVERY_TIMEOUT_MILLIS
                    )
            ) {
                is NwcResult.Success -> result.value
                is NwcResult.Failure -> throw result.error.toConnectionException()
            }
        if (NwcCapability.PAY_INVOICE !in discovery.capabilities) {
            throw NwcConnectionException(
                NwcConnectionError.PaymentPermissionRequired
            )
        }

        val credentials =
            NwcCredentials(
                connectionUri = discovery.uri.raw,
                alias = normalizedAlias
            )
        val connectedWallet = discovery.toConnection(normalizedAlias)

        clientMutex.withLock {
            if (mutableConnection.value != null) {
                throw NwcConnectionException(NwcConnectionError.AlreadyConnected)
            }
            val newClient =
                NwcClient(
                    uri = discovery.uri,
                    scope = scope,
                    httpClient = httpClient,
                    cachedWalletInfo = discovery.walletInfo
                )
            try {
                credentialStore.save(credentials)
                client = newClient
                mutableConnection.value = connectedWallet
            } catch (error: Throwable) {
                newClient.close()
                credentialStore.clear()
                throw error
            }
        }

        return connectedWallet
    }

    suspend fun disconnect() {
        val previousClient =
            clientMutex.withLock {
                credentialStore.clear()
                mutableConnection.value = null
                client.also { client = null }
            }
        previousClient?.close()
    }

    override suspend fun payInvoice(request: PayInvoiceRequest): PaidInvoice {
        val result =
            getOrCreateClient().payInvoice(
                invoice = request.invoice.value,
                amount = request.amountMsats?.let(Amount::fromMsats),
                timeoutMs = PAY_TIMEOUT_MILLIS,
                verifyOnTimeout = true
            )

        return when (result) {
            is NwcResult.Success ->
                PaidInvoice(
                    preimageHex = result.value.preimage,
                    feesPaidMsats = result.value.feesPaid?.msats
                )

            is NwcResult.Failure ->
                throw PaymentException(
                    error = result.error.toPaymentError(),
                    cause = NwcException(result.error)
                )
        }
    }

    override suspend fun lookupPayment(paymentHash: PaymentHash): PaymentLookupResult = try {
        when (
            val result =
                getOrCreateClient().lookupInvoice(
                    params = LookupInvoiceParams(paymentHash = paymentHash.hex),
                    timeoutMs = LOOKUP_TIMEOUT_MILLIS
                )
        ) {
            is NwcResult.Success -> result.value.toLookupResult()
            is NwcResult.Failure -> result.error.toLookupResult()
        }
    } catch (error: PaymentException) {
        PaymentLookupResult.LookupError(error.error)
    }

    suspend fun close() {
        val previousClient =
            clientMutex.withLock {
                client.also { client = null }
            }
        previousClient?.close()
        if (ownsHttpClient) {
            httpClient.close()
        }
    }

    private suspend fun getOrCreateClient(): NwcClient = clientMutex.withLock {
        client?.let { return@withLock it }

        val credentials =
            credentialStore.read()
                ?: throw PaymentException(PaymentError.MissingWalletConnection)
        val uri =
            NwcConnectionUri.parse(credentials.connectionUri)
                ?: throw PaymentException(
                    PaymentError.WalletConnectionFailed("Invalid NWC connection")
                )
        NwcClient(
            uri = uri,
            scope = scope,
            httpClient = httpClient
        ).also { client = it }
    }
}

fun createNwcWallet(secureSettings: Settings, scope: CoroutineScope): NwcWallet {
    val httpClient = createWebSocketHttpClient()
    return NwcWallet(
        credentialStore = NwcCredentialStore(secureSettings),
        scope = scope,
        httpClient = httpClient,
        ownsHttpClient = true
    )
}

fun createNwcWallet(
    secureSettings: Settings,
    scope: CoroutineScope,
    httpClient: HttpClient
): NwcWallet = NwcWallet(
    credentialStore = NwcCredentialStore(secureSettings),
    scope = scope,
    httpClient = httpClient,
    ownsHttpClient = false
)

private fun NwcCredentials.toConnection(): NwcWalletConnection? {
    val uri = NwcConnectionUri.parse(connectionUri) ?: return null
    return NwcWalletConnection(
        alias = alias,
        walletPublicKey = uri.walletPubkey.hex,
        relayUrl = uri.relays.firstOrNull(),
        lightningAddress = uri.lud16
    )
}

private fun WalletDiscovery.toConnection(alias: String): NwcWalletConnection = NwcWalletConnection(
    alias = alias,
    walletPublicKey = uri.walletPubkey.hex,
    relayUrl = uri.relays.firstOrNull(),
    lightningAddress = uri.lud16,
    network = details?.network,
    color = details?.color
)

private fun io.github.nicolals.nwc.Transaction.toLookupResult(): PaymentLookupResult =
    when (state) {
        TransactionState.SETTLED ->
            PaymentLookupResult.Settled(
                PaidInvoice(
                    preimageHex = preimage,
                    feesPaidMsats = feesPaid?.msats
                )
            )

        TransactionState.PENDING -> PaymentLookupResult.Pending

        TransactionState.FAILED, TransactionState.EXPIRED -> PaymentLookupResult.Failed

        null ->
            if (settledAt != null || preimage != null) {
                PaymentLookupResult.Settled(
                    PaidInvoice(
                        preimageHex = preimage,
                        feesPaidMsats = feesPaid?.msats
                    )
                )
            } else {
                PaymentLookupResult.Pending
            }
    }

private fun NwcError.toLookupResult(): PaymentLookupResult =
    if (this is NwcError.WalletError && code.code == "NOT_FOUND") {
        PaymentLookupResult.NotFound
    } else {
        PaymentLookupResult.LookupError(toPaymentError())
    }

private fun NwcError.toPaymentError(): PaymentError = when (this) {
    is NwcError.WalletError ->
        PaymentError.PaymentRejected(
            code = code.code.takeIf(String::isNotEmpty),
            detail = message.takeIf(String::isNotEmpty)
        )

    is NwcError.ConnectionError -> PaymentError.WalletConnectionFailed(message)

    is NwcError.Timeout -> PaymentError.Timeout

    is NwcError.Cancelled -> PaymentError.Unexpected(message)

    is NwcError.ProtocolError -> PaymentError.Unexpected(message)

    is NwcError.CryptoError -> PaymentError.Unexpected(message)

    is NwcError.PaymentPending ->
        PaymentError.PaymentUnconfirmed(
            paymentHash =
                paymentHash
                    ?.takeIf(String::isNotBlank)
                    ?.let(::PaymentHash),
            detail = message
        )
}

private fun NwcError.toConnectionException(): NwcConnectionException = when (this) {
    is NwcError.ProtocolError ->
        NwcConnectionException(
            error =
                if (message.contains("invalid", ignoreCase = true)) {
                    NwcConnectionError.InvalidUri
                } else {
                    NwcConnectionError.ConnectionFailed(message)
                },
            cause = cause
        )

    is NwcError.ConnectionError ->
        NwcConnectionException(
            NwcConnectionError.ConnectionFailed(message),
            cause
        )

    else ->
        NwcConnectionException(
            NwcConnectionError.ConnectionFailed(message),
            NwcException(this)
        )
}

private const val DISCOVERY_TIMEOUT_MILLIS = 10_000L
private const val PAY_TIMEOUT_MILLIS = 20_000L
private const val LOOKUP_TIMEOUT_MILLIS = 10_000L
