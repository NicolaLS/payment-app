package xyz.lilsus.rayl.blip.data.blink

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.Bolt11Invoice
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import xyz.lilsus.rayl.blip.data.BlipStore
import xyz.lilsus.rayl.blip.domain.AppClock
import xyz.lilsus.rayl.blip.domain.BlinkApiKey
import xyz.lilsus.rayl.blip.domain.BlinkContactCandidate
import xyz.lilsus.rayl.blip.domain.ConnectBlinkOutcome
import xyz.lilsus.rayl.blip.domain.ConnectionProfile
import xyz.lilsus.rayl.blip.domain.ConnectionStatus
import xyz.lilsus.rayl.blip.domain.Contact
import xyz.lilsus.rayl.blip.domain.ContactSource
import xyz.lilsus.rayl.blip.domain.CredentialVault
import xyz.lilsus.rayl.blip.domain.IdentifierSource
import xyz.lilsus.rayl.blip.domain.LookupPaymentOutcome
import xyz.lilsus.rayl.blip.domain.PaymentBackend
import xyz.lilsus.rayl.blip.domain.PaymentFailure
import xyz.lilsus.rayl.blip.domain.PaymentHash
import xyz.lilsus.rayl.blip.domain.SubmitPaymentOutcome

class BlinkGateway internal constructor(
    private val api: BlinkApi,
    private val store: BlipStore,
    private val vault: CredentialVault,
    private val identifiers: IdentifierSource,
    private val clock: AppClock
) : PaymentBackend {
    suspend fun connect(apiKeyInput: String, aliasInput: String): ConnectBlinkOutcome {
        val apiKey = BlinkApiKey.parse(apiKeyInput)
            ?: return ConnectBlinkOutcome.InvalidInput
        val alias = aliasInput.trim()
        if (alias.length !in 1..64 || alias.any(Char::isISOControl)) {
            return ConnectBlinkOutcome.InvalidInput
        }

        val identity = try {
            withTimeout(PROVIDER_TIMEOUT_MILLIS) {
                api.identify(apiKey)
            }
        } catch (error: TimeoutCancellationException) {
            return ConnectBlinkOutcome.NetworkUnavailable
        } catch (error: BlinkApiFailure) {
            return when (error) {
                BlinkApiFailure.AuthenticationRequired ->
                    ConnectBlinkOutcome.InvalidApiKey

                BlinkApiFailure.PermissionDenied ->
                    ConnectBlinkOutcome.PermissionDenied

                BlinkApiFailure.RateLimited ->
                    ConnectBlinkOutcome.RateLimited

                BlinkApiFailure.Network ->
                    ConnectBlinkOutcome.NetworkUnavailable

                BlinkApiFailure.InvalidResponse,
                is BlinkApiFailure.Provider
                -> ConnectBlinkOutcome.Unexpected
            }
        }

        val profile = ConnectionProfile(
            id = identifiers.newConnectionId(),
            alias = alias,
            accountId = identity.accountId,
            walletId = identity.walletId,
            status = ConnectionStatus.Connected,
            createdAtMillis = clock.nowMillis()
        )

        return try {
            vault.put(profile.id, apiKey)
            store.saveConnection(profile)
            ConnectBlinkOutcome.Connected(profile)
        } catch (error: CancellationException) {
            runCatching { vault.remove(profile.id) }
            throw error
        } catch (error: Throwable) {
            runCatching { vault.remove(profile.id) }
            ConnectBlinkOutcome.Unexpected
        }
    }

    suspend fun disconnect(profile: ConnectionProfile) {
        vault.remove(profile.id)
        store.disconnect(profile.id)
    }

    suspend fun refreshWallet(profile: ConnectionProfile): ConnectBlinkOutcome {
        val apiKey = vault.get(profile.id)
            ?: return ConnectBlinkOutcome.InvalidApiKey
        val identity = try {
            withTimeout(PROVIDER_TIMEOUT_MILLIS) {
                api.identify(apiKey)
            }
        } catch (error: TimeoutCancellationException) {
            return ConnectBlinkOutcome.NetworkUnavailable
        } catch (error: BlinkApiFailure) {
            if (error == BlinkApiFailure.AuthenticationRequired) {
                store.updateConnectionStatus(profile.id, ConnectionStatus.NeedsReauthentication)
            }
            return when (error) {
                BlinkApiFailure.AuthenticationRequired -> ConnectBlinkOutcome.InvalidApiKey

                BlinkApiFailure.PermissionDenied -> ConnectBlinkOutcome.PermissionDenied

                BlinkApiFailure.RateLimited -> ConnectBlinkOutcome.RateLimited

                BlinkApiFailure.Network -> ConnectBlinkOutcome.NetworkUnavailable

                BlinkApiFailure.InvalidResponse,
                is BlinkApiFailure.Provider
                -> ConnectBlinkOutcome.Unexpected
            }
        }

        store.updateConnectionWallet(
            connectionId = profile.id,
            walletId = identity.walletId,
            status = ConnectionStatus.Connected
        )
        return ConnectBlinkOutcome.Connected(
            profile.copy(
                walletId = identity.walletId,
                status = ConnectionStatus.Connected
            )
        )
    }

    suspend fun contactCandidates(profile: ConnectionProfile): List<BlinkContactCandidate> {
        val apiKey = vault.get(profile.id) ?: return emptyList()
        val existingAddresses = store.contacts()
            .map { it.lightningAddress.lowercase() }
            .toSet()

        return withTimeout(PROVIDER_TIMEOUT_MILLIS) {
            api.contacts(apiKey)
        }.map { blinkContact ->
            BlinkContactCandidate(
                name = blinkContact.name,
                lightningAddress = blinkContact.lightningAddress,
                alreadyAdded = blinkContact.lightningAddress.lowercase() in existingAddresses
            )
        }
    }

    suspend fun importContacts(
        profile: ConnectionProfile,
        selectedAddresses: Set<String>? = null
    ): List<Contact> {
        val selected = selectedAddresses?.map(String::lowercase)?.toSet()
        return contactCandidates(profile)
            .filterNot(BlinkContactCandidate::alreadyAdded)
            .filter { candidate ->
                selected == null || candidate.lightningAddress.lowercase() in selected
            }
            .map { blinkContact ->
                Contact(
                    id = identifiers.newContactId(),
                    name = blinkContact.name,
                    lightningAddress = blinkContact.lightningAddress,
                    source = ContactSource.Blink,
                    createdAtMillis = clock.nowMillis()
                ).also(store::saveContact)
            }
    }

    override suspend fun submit(
        connection: ConnectionProfile,
        invoice: Bolt11Invoice,
        amount: MilliSatoshi
    ): SubmitPaymentOutcome {
        val apiKey = vault.get(connection.id)
            ?: return SubmitPaymentOutcome.Rejected(
                PaymentFailure.AuthenticationRequired
            )

        val result = try {
            withTimeout(PROVIDER_TIMEOUT_MILLIS) {
                if (invoice.amount == null) {
                    api.payAmountlessInvoice(
                        apiKey = apiKey,
                        walletId = connection.walletId,
                        invoice = invoice.write(),
                        amountSats = amount.toSatsRoundedUp()
                    )
                } else {
                    api.payFixedInvoice(
                        apiKey = apiKey,
                        walletId = connection.walletId,
                        invoice = invoice.write()
                    )
                }
            }
        } catch (error: TimeoutCancellationException) {
            return SubmitPaymentOutcome.Unknown
        } catch (error: BlinkApiFailure) {
            return when (error) {
                BlinkApiFailure.AuthenticationRequired -> {
                    store.updateConnectionStatus(
                        connection.id,
                        ConnectionStatus.NeedsReauthentication
                    )
                    SubmitPaymentOutcome.Rejected(PaymentFailure.AuthenticationRequired)
                }

                BlinkApiFailure.PermissionDenied ->
                    SubmitPaymentOutcome.Rejected(PaymentFailure.PermissionDenied)

                BlinkApiFailure.RateLimited ->
                    SubmitPaymentOutcome.Rejected(PaymentFailure.RateLimited)

                BlinkApiFailure.Network ->
                    SubmitPaymentOutcome.Unknown

                BlinkApiFailure.InvalidResponse,
                is BlinkApiFailure.Provider
                -> SubmitPaymentOutcome.Unknown
            }
        }

        return when (result) {
            is BlinkSubmitResult.Settled -> SubmitPaymentOutcome.Settled(
                feesPaid = result.feesPaid,
                preimage = result.preimage
            )

            is BlinkSubmitResult.AlreadyPaid ->
                SubmitPaymentOutcome.AlreadyPaid(result.preimage)

            BlinkSubmitResult.Pending -> SubmitPaymentOutcome.Pending

            is BlinkSubmitResult.Rejected ->
                SubmitPaymentOutcome.Rejected(result.rejection.toPaymentFailure())

            BlinkSubmitResult.Unknown -> SubmitPaymentOutcome.Unknown
        }
    }

    override suspend fun lookup(
        connection: ConnectionProfile,
        paymentHash: PaymentHash
    ): LookupPaymentOutcome {
        val apiKey = vault.get(connection.id) ?: return LookupPaymentOutcome.Unknown

        val result = try {
            withTimeout(PROVIDER_TIMEOUT_MILLIS) {
                api.lookup(apiKey, connection.walletId, paymentHash.hex)
            }
        } catch (error: TimeoutCancellationException) {
            return LookupPaymentOutcome.Unknown
        } catch (error: BlinkApiFailure) {
            if (error == BlinkApiFailure.AuthenticationRequired) {
                store.updateConnectionStatus(
                    connection.id,
                    ConnectionStatus.NeedsReauthentication
                )
            }
            return LookupPaymentOutcome.Unknown
        }

        return when (result) {
            is BlinkLookupResult.Settled -> LookupPaymentOutcome.Settled(
                feesPaid = result.feesPaid,
                preimage = result.preimage
            )

            BlinkLookupResult.Pending -> LookupPaymentOutcome.Pending

            BlinkLookupResult.Rejected ->
                LookupPaymentOutcome.Rejected(PaymentFailure.ProviderRejected(null))

            BlinkLookupResult.Unknown -> LookupPaymentOutcome.Unknown
        }
    }

    private fun BlinkRejection.toPaymentFailure(): PaymentFailure = when (this) {
        BlinkRejection.PermissionDenied -> PaymentFailure.PermissionDenied

        BlinkRejection.InsufficientBalance -> PaymentFailure.InsufficientBalance

        BlinkRejection.RouteNotFound -> PaymentFailure.RouteNotFound

        BlinkRejection.InvoiceExpired -> PaymentFailure.ExpiredInvoice

        BlinkRejection.SelfPayment ->
            PaymentFailure.ProviderRejected("SELF_PAYMENT")

        BlinkRejection.InvalidInvoice -> PaymentFailure.InvalidRequest

        BlinkRejection.AmountTooSmall ->
            PaymentFailure.ProviderRejected("AMOUNT_TOO_SMALL")

        BlinkRejection.LimitExceeded ->
            PaymentFailure.ProviderRejected("LIMIT_EXCEEDED")

        BlinkRejection.RateLimited -> PaymentFailure.RateLimited

        is BlinkRejection.Provider -> PaymentFailure.ProviderRejected(code)
    }

    private fun MilliSatoshi.toSatsRoundedUp(): Long {
        require(msat > 0L)
        return ((msat - 1L) / 1_000L) + 1L
    }

    private companion object {
        const val PROVIDER_TIMEOUT_MILLIS = 20_000L
    }
}
