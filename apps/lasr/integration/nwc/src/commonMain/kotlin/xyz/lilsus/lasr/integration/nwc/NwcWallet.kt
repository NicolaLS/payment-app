package xyz.lilsus.lasr.integration.nwc

import io.github.nicolals.nostr.nip47.model.NwcEncryption
import io.github.nicolals.nwc.Amount
import io.github.nicolals.nwc.LookupInvoiceParams
import io.github.nicolals.nwc.NwcCapability
import io.github.nicolals.nwc.NwcClient
import io.github.nicolals.nwc.NwcConnectionUri
import io.github.nicolals.nwc.NwcError
import io.github.nicolals.nwc.NwcException
import io.github.nicolals.nwc.NwcNotificationType
import io.github.nicolals.nwc.NwcResult
import io.github.nicolals.nwc.Transaction
import io.github.nicolals.nwc.TransactionState
import io.github.nicolals.nwc.WalletDiscovery
import io.github.nicolals.nwc.WalletInfo
import io.github.nicolals.nwc.WalletNotification
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.lilsus.raylsuite.core.network.createWebSocketHttpClient
import xyz.lilsus.raylsuite.core.settings.SecureStringStore

data class NwcWalletConnection(
    val alias: String?,
    val walletPublicKey: String,
    val relayUrls: List<String>,
    val lightningAddress: String?,
    val metadata: NwcWalletMetadata
) {
    val relayUrl: String?
        get() = relayUrls.firstOrNull()
}

data class NwcWalletDiscovery(
    val connectionUri: String,
    val walletPublicKey: String,
    val relayUrls: List<String>,
    val lightningAddress: String?,
    val aliasSuggestion: String?,
    val metadata: NwcWalletMetadata
) {
    val relayUrl: String?
        get() = relayUrls.firstOrNull()

    val supportsPayInvoice: Boolean
        get() = metadata.supports(REQUIRED_PAY_METHOD)

    val supportsLookupInvoice: Boolean
        get() = metadata.supports(REQUIRED_LOOKUP_METHOD)

    val supportsRequiredMethods: Boolean
        get() = supportsPayInvoice && supportsLookupInvoice

    val supportsNip44: Boolean
        get() = metadata.encryptionSchemes.any { it.equals("nip44_v2", ignoreCase = true) }

    val usesLegacyEncryption: Boolean
        get() = metadata.negotiatedEncryption?.equals("nip04", ignoreCase = true) == true
}

data class NwcWalletMetadata(
    val methods: Set<String>,
    val encryptionSchemes: Set<String>,
    val negotiatedEncryption: String?,
    val encryptionDefaultedToNip04: Boolean,
    val notifications: Set<String>,
    val network: String?,
    val color: String?
) {
    fun supports(method: String): Boolean = methods.any { it.equals(method, ignoreCase = true) }
}

sealed interface NwcConnectionError {
    data object AlreadyConnected : NwcConnectionError

    data object InvalidUri : NwcConnectionError

    data object RequiredMethodsMissing : NwcConnectionError

    data class ConnectionFailed(val detail: String? = null) : NwcConnectionError
}

class NwcConnectionException(val error: NwcConnectionError, cause: Throwable? = null) :
    Exception(error.toString(), cause)

sealed interface NwcPayOutcome {
    data class Settled(val preimage: String?, val feesPaidMsats: Long?) : NwcPayOutcome

    data class WalletRejected(val code: String?, val detail: String?) : NwcPayOutcome

    data class DefinitelyNotSent(val detail: String?) : NwcPayOutcome

    data class Uncertain(val detail: String?) : NwcPayOutcome
}

sealed interface NwcLookupOutcome {
    data class Settled(val preimage: String?, val feesPaidMsats: Long?) : NwcLookupOutcome

    data object Pending : NwcLookupOutcome

    data object Failed : NwcLookupOutcome

    data object NotFound : NwcLookupOutcome

    data class RetryableFailure(val detail: String?) : NwcLookupOutcome

    data class PermanentlyUnavailable(val detail: String?) : NwcLookupOutcome
}

data class NwcSentPayment(
    val paymentHash: String,
    val invoice: String?,
    val preimage: String?,
    val feesPaidMsats: Long?
)

class NwcWallet internal constructor(
    private val credentialStore: NwcCredentialStore,
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
    private val isNetworkAvailable: () -> Boolean,
    private val ownsHttpClient: Boolean
) {
    private val clientMutex = Mutex()
    private var client: NwcClient? = null
    private var notificationJob: Job? = null
    private val mutableConnection = MutableStateFlow(credentialStore.read()?.toConnection())
    private val mutableForeground = MutableStateFlow(true)
    private val mutableSentPayments = MutableSharedFlow<NwcSentPayment>(extraBufferCapacity = 16)

    val connection: StateFlow<NwcWalletConnection?> = mutableConnection.asStateFlow()
    val isInForeground: StateFlow<Boolean> = mutableForeground.asStateFlow()
    val sentPayments: SharedFlow<NwcSentPayment> = mutableSentPayments.asSharedFlow()

    suspend fun discover(connectionUri: String): NwcWalletDiscovery {
        val normalizedUri = connectionUri.trim()
        if (normalizedUri.isEmpty() || NwcConnectionUri.parse(normalizedUri) == null) {
            throw NwcConnectionException(NwcConnectionError.InvalidUri)
        }
        return when (
            val result = NwcClient.discover(
                uri = normalizedUri,
                httpClient = httpClient,
                timeoutMs = DISCOVERY_TIMEOUT_MILLIS
            )
        ) {
            is NwcResult.Success -> result.value.toWalletDiscovery()
            is NwcResult.Failure -> throw result.error.toConnectionException()
        }
    }

    suspend fun connect(connectionUri: String, alias: String?): NwcWalletConnection =
        connect(discover(connectionUri), alias)

    suspend fun connect(discovery: NwcWalletDiscovery, alias: String?): NwcWalletConnection {
        if (!discovery.supportsRequiredMethods) {
            throw NwcConnectionException(NwcConnectionError.RequiredMethodsMissing)
        }
        if (mutableConnection.value != null) {
            throw NwcConnectionException(NwcConnectionError.AlreadyConnected)
        }
        val normalizedAlias = alias?.trim()?.takeIf(String::isNotEmpty)
        val uri = NwcConnectionUri.parse(discovery.connectionUri)
            ?: throw NwcConnectionException(NwcConnectionError.InvalidUri)
        val credentials = NwcCredentials(uri.raw, normalizedAlias, discovery.metadata)
        val connectedWallet = discovery.toConnection(normalizedAlias)

        clientMutex.withLock {
            if (mutableConnection.value != null) {
                throw NwcConnectionException(NwcConnectionError.AlreadyConnected)
            }
            val newClient = createClient(uri, discovery.metadata)
            try {
                credentialStore.save(credentials)
                client = newClient
                observeNotifications(newClient)
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
        val previousClient = clientMutex.withLock {
            credentialStore.clear()
            mutableConnection.value = null
            notificationJob?.cancel()
            notificationJob = null
            client.also { client = null }
        }
        previousClient?.close()
    }

    suspend fun payInvoice(invoice: String, amountMsats: Long?, timeoutMs: Long): NwcPayOutcome {
        if (!mutableForeground.value) {
            return NwcPayOutcome.DefinitelyNotSent("App is in the background")
        }
        if (!isNetworkAvailable()) {
            return NwcPayOutcome.DefinitelyNotSent("Network unavailable")
        }
        val activeClient = getOrCreateClient()
            ?: return NwcPayOutcome.DefinitelyNotSent("Wallet connection missing")
        return when (
            val result = activeClient.payInvoice(
                invoice = invoice,
                amount = amountMsats?.let(Amount::fromMsats),
                timeoutMs = timeoutMs,
                verifyOnTimeout = false
            )
        ) {
            is NwcResult.Success ->
                NwcPayOutcome.Settled(
                    preimage = result.value.preimage,
                    feesPaidMsats = result.value.feesPaid?.msats
                )

            is NwcResult.Failure -> result.error.toPayOutcome()
        }
    }

    suspend fun lookupInvoice(paymentHash: String, timeoutMs: Long): NwcLookupOutcome {
        if (!mutableForeground.value) {
            return NwcLookupOutcome.RetryableFailure("App is in the background")
        }
        if (!isNetworkAvailable()) {
            return NwcLookupOutcome.RetryableFailure("Network unavailable")
        }
        val activeClient = getOrCreateClient()
            ?: return NwcLookupOutcome.PermanentlyUnavailable("Wallet connection missing")
        return when (
            val result = activeClient.lookupInvoice(
                params = LookupInvoiceParams(paymentHash = paymentHash),
                timeoutMs = timeoutMs
            )
        ) {
            is NwcResult.Success -> result.value.toLookupOutcome()
            is NwcResult.Failure -> result.error.toLookupOutcome()
        }
    }

    suspend fun onAppForegroundChanged(isInForeground: Boolean) {
        mutableForeground.value = isInForeground
        if (isInForeground) {
            if (connection.value != null) {
                getOrCreateClient()
            }
        } else {
            releaseClient()
        }
    }

    suspend fun prewarm() {
        if (mutableForeground.value && connection.value != null) {
            getOrCreateClient()
        }
    }

    suspend fun close() {
        releaseClient()
        if (ownsHttpClient) httpClient.close()
    }

    private suspend fun releaseClient() {
        val previousClient = clientMutex.withLock {
            notificationJob?.cancel()
            notificationJob = null
            client.also { client = null }
        }
        previousClient?.close()
    }

    private suspend fun getOrCreateClient(): NwcClient? = clientMutex.withLock {
        client?.let { return@withLock it }
        val credentials = credentialStore.read() ?: return@withLock null
        val uri = NwcConnectionUri.parse(credentials.connectionUri) ?: return@withLock null
        createClient(uri, credentials.metadata).also {
            client = it
            observeNotifications(it)
        }
    }

    private fun createClient(uri: NwcConnectionUri, metadata: NwcWalletMetadata): NwcClient =
        NwcClient(
            uri = uri,
            scope = scope,
            httpClient = httpClient,
            cachedWalletInfo = metadata.toWalletInfo()
        )

    private fun observeNotifications(observedClient: NwcClient) {
        notificationJob?.cancel()
        notificationJob = scope.launch {
            observedClient.notifications.collect { notification ->
                if (notification is WalletNotification.PaymentSent) {
                    mutableSentPayments.emit(notification.transaction.toSentPayment())
                }
            }
        }
    }
}

fun createNwcWallet(
    secureSettings: SecureStringStore,
    scope: CoroutineScope,
    isNetworkAvailable: () -> Boolean
): NwcWallet {
    val httpClient = createWebSocketHttpClient()
    return NwcWallet(
        credentialStore = NwcCredentialStore(secureSettings),
        scope = scope,
        httpClient = httpClient,
        isNetworkAvailable = isNetworkAvailable,
        ownsHttpClient = true
    )
}

fun createNwcWallet(
    secureSettings: SecureStringStore,
    scope: CoroutineScope,
    httpClient: HttpClient,
    isNetworkAvailable: () -> Boolean
): NwcWallet = NwcWallet(
    credentialStore = NwcCredentialStore(secureSettings),
    scope = scope,
    httpClient = httpClient,
    isNetworkAvailable = isNetworkAvailable,
    ownsHttpClient = false
)

fun isValidNwcConnectionUri(value: String): Boolean = NwcConnectionUri.isValid(value.trim())

private fun NwcCredentials.toConnection(): NwcWalletConnection? {
    val uri = NwcConnectionUri.parse(connectionUri) ?: return null
    return NwcWalletConnection(
        alias = alias,
        walletPublicKey = uri.walletPubkey.hex,
        relayUrls = uri.relays,
        lightningAddress = uri.lud16,
        metadata = metadata
    )
}

private fun WalletDiscovery.toWalletDiscovery(): NwcWalletDiscovery = NwcWalletDiscovery(
    connectionUri = uri.raw,
    walletPublicKey = uri.walletPubkey.hex,
    relayUrls = uri.relays,
    lightningAddress = uri.lud16,
    aliasSuggestion = details?.alias,
    metadata = NwcWalletMetadata(
        methods = walletInfo.capabilityStrings,
        encryptionSchemes = walletInfo.encryptionStrings,
        negotiatedEncryption = walletInfo.preferredEncryption.tag,
        encryptionDefaultedToNip04 = walletInfo.encryptionDefaultedToNip04,
        notifications = walletInfo.notificationStrings,
        network = details?.network,
        color = details?.color
    )
)

private fun NwcWalletDiscovery.toConnection(alias: String?): NwcWalletConnection =
    NwcWalletConnection(
        alias = alias,
        walletPublicKey = walletPublicKey,
        relayUrls = relayUrls,
        lightningAddress = lightningAddress,
        metadata = metadata
    )

private fun NwcWalletMetadata.toWalletInfo(): WalletInfo {
    val capabilities = methods.mapNotNull(NwcCapability::fromValue).toSet()
    val notificationTypes = notifications.mapNotNull(NwcNotificationType::fromValue).toSet()
    val encryptions = encryptionSchemes.mapNotNull(NwcEncryption::fromTag).toSet()
    val preferredEncryption = negotiatedEncryption?.let(NwcEncryption::fromTag)
        ?: when {
            NwcEncryption.NIP44_V2 in encryptions -> NwcEncryption.NIP44_V2
            else -> NwcEncryption.NIP04
        }
    return WalletInfo(
        capabilities = capabilities,
        notifications = notificationTypes,
        encryptions = encryptions.ifEmpty { setOf(NwcEncryption.NIP04) },
        preferredEncryption = preferredEncryption,
        encryptionDefaultedToNip04 = encryptionDefaultedToNip04
    )
}

private fun Transaction.toLookupOutcome(): NwcLookupOutcome = when (state) {
    TransactionState.SETTLED ->
        NwcLookupOutcome.Settled(preimage = preimage, feesPaidMsats = feesPaid?.msats)

    TransactionState.PENDING -> NwcLookupOutcome.Pending

    TransactionState.FAILED, TransactionState.EXPIRED -> NwcLookupOutcome.Failed

    null ->
        if (settledAt != null || preimage != null) {
            NwcLookupOutcome.Settled(preimage = preimage, feesPaidMsats = feesPaid?.msats)
        } else {
            NwcLookupOutcome.Pending
        }
}

private fun Transaction.toSentPayment(): NwcSentPayment = NwcSentPayment(
    paymentHash = paymentHash,
    invoice = invoice,
    preimage = preimage,
    feesPaidMsats = feesPaid?.msats
)

private fun NwcError.toPayOutcome(): NwcPayOutcome = when (this) {
    is NwcError.WalletError ->
        NwcPayOutcome.WalletRejected(
            code = code.code.takeIf(String::isNotEmpty),
            detail = message.takeIf(String::isNotEmpty)
        )

    is NwcError.ConnectionError -> NwcPayOutcome.DefinitelyNotSent(message)

    is NwcError.Timeout -> NwcPayOutcome.Uncertain(message)

    is NwcError.PaymentPending -> NwcPayOutcome.Uncertain(message)

    is NwcError.Cancelled -> NwcPayOutcome.Uncertain(message)

    is NwcError.ProtocolError -> NwcPayOutcome.Uncertain(message)

    is NwcError.CryptoError -> NwcPayOutcome.DefinitelyNotSent(message)
}

private fun NwcError.toLookupOutcome(): NwcLookupOutcome = when (this) {
    is NwcError.WalletError -> when (code.code.uppercase()) {
        "NOT_FOUND" -> NwcLookupOutcome.NotFound

        "RATE_LIMITED", "TEMPORARY_FAILURE", "INTERNAL" ->
            NwcLookupOutcome.RetryableFailure(message)

        else -> NwcLookupOutcome.PermanentlyUnavailable(message)
    }

    is NwcError.ConnectionError,
    is NwcError.Timeout,
    is NwcError.Cancelled -> NwcLookupOutcome.RetryableFailure(message)

    is NwcError.ProtocolError,
    is NwcError.CryptoError -> NwcLookupOutcome.PermanentlyUnavailable(message)

    is NwcError.PaymentPending -> NwcLookupOutcome.Pending
}

private fun NwcError.toConnectionException(): NwcConnectionException = when (this) {
    is NwcError.ProtocolError ->
        NwcConnectionException(
            if (message.contains("invalid", ignoreCase = true)) {
                NwcConnectionError.InvalidUri
            } else {
                NwcConnectionError.ConnectionFailed(message)
            },
            cause
        )

    is NwcError.ConnectionError ->
        NwcConnectionException(NwcConnectionError.ConnectionFailed(message), cause)

    else ->
        NwcConnectionException(
            NwcConnectionError.ConnectionFailed(message),
            NwcException(this)
        )
}

private const val DISCOVERY_TIMEOUT_MILLIS = 10_000L
private const val REQUIRED_PAY_METHOD = "pay_invoice"
private const val REQUIRED_LOOKUP_METHOD = "lookup_invoice"
