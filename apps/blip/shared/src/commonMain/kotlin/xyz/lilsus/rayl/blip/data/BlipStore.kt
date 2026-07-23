package xyz.lilsus.rayl.blip.data

import app.cash.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.utils.msat
import xyz.lilsus.rayl.blip.data.db.BlipDatabase
import xyz.lilsus.rayl.blip.data.db.Connection_profile
import xyz.lilsus.rayl.blip.data.db.Contact as StoredContact
import xyz.lilsus.rayl.blip.data.db.Payment_attempt
import xyz.lilsus.rayl.blip.data.db.Shortcut as StoredShortcut
import xyz.lilsus.rayl.blip.domain.AttemptId
import xyz.lilsus.rayl.blip.domain.BlinkAccountId
import xyz.lilsus.rayl.blip.domain.BlinkWalletId
import xyz.lilsus.rayl.blip.domain.ConnectionId
import xyz.lilsus.rayl.blip.domain.ConnectionProfile
import xyz.lilsus.rayl.blip.domain.ConnectionStatus
import xyz.lilsus.rayl.blip.domain.Contact
import xyz.lilsus.rayl.blip.domain.ContactId
import xyz.lilsus.rayl.blip.domain.ContactSource
import xyz.lilsus.rayl.blip.domain.PaymentAttempt
import xyz.lilsus.rayl.blip.domain.PaymentAttemptState
import xyz.lilsus.rayl.blip.domain.PaymentFailure
import xyz.lilsus.rayl.blip.domain.PaymentHash
import xyz.lilsus.rayl.blip.domain.PaymentOrigin
import xyz.lilsus.rayl.blip.domain.PaymentShortcut
import xyz.lilsus.rayl.blip.domain.ShortcutId
import xyz.lilsus.rayl.blip.domain.canTransitionTo

interface DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

class BlipStore(driverFactory: DatabaseDriverFactory) {
    private val database = BlipDatabase(driverFactory.createDriver())
    private val queries = database.blipQueries

    fun currentConnection(): ConnectionProfile? =
        queries.selectConnection().executeAsOneOrNull()?.toDomain()

    fun connection(id: ConnectionId): ConnectionProfile? =
        queries.selectConnectionById(id.value).executeAsOneOrNull()?.toDomain()

    fun saveConnection(profile: ConnectionProfile) {
        database.transaction {
            currentConnection()?.let { active ->
                queries.disconnectConnection(active.id.value)
            }
            queries.insertConnection(
                id = profile.id.value,
                alias = profile.alias,
                account_id = profile.accountId.value,
                wallet_id = profile.walletId.value,
                status = profile.status.name.uppercase(),
                created_at = profile.createdAtMillis
            )
        }
    }

    fun updateConnectionWallet(
        connectionId: ConnectionId,
        walletId: BlinkWalletId,
        status: ConnectionStatus
    ) {
        queries.updateConnectionWallet(
            wallet_id = walletId.value,
            status = status.name.uppercase(),
            id = connectionId.value
        )
    }

    fun updateConnectionStatus(connectionId: ConnectionId, status: ConnectionStatus) {
        queries.updateConnectionStatus(
            status = status.name.uppercase(),
            id = connectionId.value
        )
    }

    fun disconnect(connectionId: ConnectionId) {
        queries.disconnectConnection(connectionId.value)
    }

    fun createAttempt(attempt: PaymentAttempt) {
        require(attempt.state == PaymentAttemptState.Created)
        database.transaction {
            queries.insertAttempt(
                id = attempt.id.value,
                connection_id = attempt.connectionId.value,
                request = attempt.request,
                fingerprint = attempt.fingerprint,
                payment_hash = attempt.paymentHash.hex,
                amount_msat = attempt.amount.msat,
                origin = attempt.origin.name.uppercase(),
                state = attempt.state.name.uppercase(),
                provider_correlation = attempt.providerCorrelation,
                created_at = attempt.createdAtMillis,
                submitted_at = attempt.submittedAtMillis,
                updated_at = attempt.updatedAtMillis,
                fees_msat = attempt.feesPaid?.msat,
                preimage = attempt.preimage?.toHex(),
                failure_code = attempt.failure?.storageCode()
            )
            queries.insertTransition(
                attempt_id = attempt.id.value,
                state = attempt.state.name.uppercase(),
                occurred_at = attempt.createdAtMillis,
                detail_code = null
            )
        }
    }

    fun attempt(id: AttemptId): PaymentAttempt? =
        queries.selectAttempt(id.value).executeAsOneOrNull()?.toDomain()

    fun attempts(): List<PaymentAttempt> =
        queries.selectAttempts().executeAsList().map(Payment_attempt::toDomain)

    fun nonFinalAttempts(): List<PaymentAttempt> =
        queries.selectNonFinalAttempts().executeAsList().map(Payment_attempt::toDomain)

    fun attemptsForFingerprint(fingerprint: String): List<PaymentAttempt> =
        queries.selectAttemptsByFingerprint(fingerprint)
            .executeAsList()
            .map(Payment_attempt::toDomain)

    fun attemptsForPaymentHash(paymentHash: PaymentHash): List<PaymentAttempt> =
        queries.selectAttemptsByPaymentHash(paymentHash.hex)
            .executeAsList()
            .map(Payment_attempt::toDomain)

    fun transition(
        id: AttemptId,
        next: PaymentAttemptState,
        atMillis: Long,
        providerCorrelation: String? = null,
        feesPaidMsat: Long? = null,
        preimage: ByteVector32? = null,
        failure: PaymentFailure? = null
    ): PaymentAttempt = database.transactionWithResult {
        val current = requireNotNull(attempt(id)) { "Unknown payment attempt" }
        require(current.state.canTransitionTo(next)) {
            "Invalid attempt transition ${current.state} -> $next"
        }

        val submittedAt = if (next == PaymentAttemptState.Submitted) {
            atMillis
        } else {
            current.submittedAtMillis
        }
        queries.updateAttemptState(
            state = next.name.uppercase(),
            provider_correlation = providerCorrelation,
            submitted_at = submittedAt,
            updated_at = atMillis,
            fees_msat = feesPaidMsat,
            preimage = preimage?.toHex(),
            failure_code = failure?.storageCode(),
            id = id.value
        )
        queries.insertTransition(
            attempt_id = id.value,
            state = next.name.uppercase(),
            occurred_at = atMillis,
            detail_code = failure?.storageCode()
        )
        queries.trimTransitions(
            attempt_id = id.value,
            attempt_id_ = id.value
        )
        requireNotNull(attempt(id))
    }

    fun contacts(): List<Contact> =
        queries.selectContacts().executeAsList().map(StoredContact::toDomain)

    fun contactByAddress(lightningAddress: String): Contact? =
        queries.selectContactByAddress(lightningAddress)
            .executeAsOneOrNull()
            ?.toDomain()

    fun saveContact(contact: Contact) {
        queries.insertContact(
            id = contact.id.value,
            name = contact.name,
            lightning_address = contact.lightningAddress,
            source = contact.source.name.uppercase(),
            created_at = contact.createdAtMillis
        )
    }

    fun deleteContact(id: ContactId) {
        queries.deleteContact(id.value)
    }

    fun shortcuts(): List<PaymentShortcut> =
        queries.selectShortcuts().executeAsList().map(StoredShortcut::toDomain)

    fun saveShortcut(shortcut: PaymentShortcut) {
        queries.insertShortcut(
            id = shortcut.id.value,
            contact_id = shortcut.contactId?.value,
            label = shortcut.label,
            lightning_address = shortcut.lightningAddress,
            amount_msat = shortcut.amount,
            currency_code = shortcut.currencyCode,
            created_at = shortcut.createdAtMillis
        )
    }

    fun deleteShortcut(id: ShortcutId) {
        queries.deleteShortcut(id.value)
    }
}

private fun Connection_profile.toDomain(): ConnectionProfile = ConnectionProfile(
    id = ConnectionId.require(id),
    alias = alias,
    accountId = BlinkAccountId.require(account_id),
    walletId = BlinkWalletId.require(wallet_id),
    status = status.toStoredEnum(),
    createdAtMillis = created_at
)

private fun Payment_attempt.toDomain(): PaymentAttempt = PaymentAttempt(
    id = AttemptId.require(id),
    connectionId = ConnectionId.require(connection_id),
    request = request,
    fingerprint = fingerprint,
    paymentHash = requireNotNull(PaymentHash.parse(payment_hash)),
    amount = amount_msat.msat,
    origin = origin.toStoredEnum(),
    state = state.toStoredEnum(),
    providerCorrelation = provider_correlation,
    createdAtMillis = created_at,
    submittedAtMillis = submitted_at,
    updatedAtMillis = updated_at,
    feesPaid = fees_msat?.msat,
    preimage = preimage?.let(ByteVector32::fromValidHex),
    failure = failure_code?.toPaymentFailure()
)

private fun StoredContact.toDomain(): Contact = Contact(
    id = ContactId.require(id),
    name = name,
    lightningAddress = lightning_address,
    source = source.toStoredEnum(),
    createdAtMillis = created_at
)

private fun StoredShortcut.toDomain(): PaymentShortcut = PaymentShortcut(
    id = ShortcutId.require(id),
    contactId = contact_id?.let(ContactId::require),
    label = label,
    lightningAddress = lightning_address,
    amount = amount_msat,
    currencyCode = currency_code,
    createdAtMillis = created_at
)

private fun PaymentFailure.storageCode(): String = when (this) {
    PaymentFailure.InvalidRequest -> "INVALID_REQUEST"
    PaymentFailure.ExpiredInvoice -> "EXPIRED_INVOICE"
    PaymentFailure.WrongNetwork -> "WRONG_NETWORK"
    PaymentFailure.MissingConnection -> "MISSING_CONNECTION"
    PaymentFailure.AuthenticationRequired -> "AUTHENTICATION_REQUIRED"
    PaymentFailure.PermissionDenied -> "PERMISSION_DENIED"
    PaymentFailure.InsufficientBalance -> "INSUFFICIENT_BALANCE"
    PaymentFailure.RouteNotFound -> "ROUTE_NOT_FOUND"
    PaymentFailure.RateLimited -> "RATE_LIMITED"
    PaymentFailure.NetworkUnavailable -> "NETWORK_UNAVAILABLE"
    PaymentFailure.TimedOut -> "TIMED_OUT"
    PaymentFailure.DuplicateInvoice -> "DUPLICATE_INVOICE"
    is PaymentFailure.ProviderRejected -> "PROVIDER:${code.orEmpty()}"
    is PaymentFailure.Unsupported -> "UNSUPPORTED:$kind"
    PaymentFailure.Unexpected -> "UNEXPECTED"
}

private fun String.toPaymentFailure(): PaymentFailure = when {
    this == "INVALID_REQUEST" -> PaymentFailure.InvalidRequest
    this == "EXPIRED_INVOICE" -> PaymentFailure.ExpiredInvoice
    this == "WRONG_NETWORK" -> PaymentFailure.WrongNetwork
    this == "MISSING_CONNECTION" -> PaymentFailure.MissingConnection
    this == "AUTHENTICATION_REQUIRED" -> PaymentFailure.AuthenticationRequired
    this == "PERMISSION_DENIED" -> PaymentFailure.PermissionDenied
    this == "INSUFFICIENT_BALANCE" -> PaymentFailure.InsufficientBalance
    this == "ROUTE_NOT_FOUND" -> PaymentFailure.RouteNotFound
    this == "RATE_LIMITED" -> PaymentFailure.RateLimited
    this == "NETWORK_UNAVAILABLE" -> PaymentFailure.NetworkUnavailable
    this == "TIMED_OUT" -> PaymentFailure.TimedOut
    this == "DUPLICATE_INVOICE" -> PaymentFailure.DuplicateInvoice
    startsWith("PROVIDER:") -> PaymentFailure.ProviderRejected(substringAfter(':'))
    startsWith("UNSUPPORTED:") -> PaymentFailure.Unsupported(substringAfter(':'))
    else -> PaymentFailure.Unexpected
}

private inline fun <reified T : Enum<T>> String.toStoredEnum(): T =
    enumValues<T>().single { it.name.equals(this, ignoreCase = true) }
