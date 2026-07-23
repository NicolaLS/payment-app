package xyz.lilsus.rayl.blip.application

import fr.acinq.lightning.MilliSatoshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.lilsus.rayl.blip.data.BlipStore
import xyz.lilsus.rayl.blip.domain.AppClock
import xyz.lilsus.rayl.blip.domain.IdentifierSource
import xyz.lilsus.rayl.blip.domain.LookupPaymentOutcome
import xyz.lilsus.rayl.blip.domain.PaymentAttempt
import xyz.lilsus.rayl.blip.domain.PaymentAttemptState
import xyz.lilsus.rayl.blip.domain.PaymentBackend
import xyz.lilsus.rayl.blip.domain.PaymentDraft
import xyz.lilsus.rayl.blip.domain.PaymentFailure
import xyz.lilsus.rayl.blip.domain.SubmitPaymentOutcome

sealed interface StartPaymentOutcome {
    data class Attempt(val value: PaymentAttempt) : StartPaymentOutcome
    data class Blocked(val previous: PaymentAttempt) : StartPaymentOutcome
    data class Rejected(val failure: PaymentFailure) : StartPaymentOutcome
}

class PaymentCoordinator(
    private val store: BlipStore,
    private val backend: PaymentBackend,
    private val identifiers: IdentifierSource,
    private val clock: AppClock
) {
    private val commands = Mutex()

    suspend fun pay(draft: PaymentDraft): StartPaymentOutcome = commands.withLock {
        val connection = store.currentConnection()
            ?: return@withLock StartPaymentOutcome.Rejected(
                PaymentFailure.MissingConnection
            )
        store.attemptsForPaymentHash(draft.paymentHash)
            .firstOrNull { it.state.blocksDuplicate() }
            ?.let { previous ->
                return@withLock StartPaymentOutcome.Blocked(previous)
            }

        val now = clock.nowMillis()
        val attempt = PaymentAttempt(
            id = identifiers.newAttemptId(),
            connectionId = connection.id,
            request = draft.invoice.write(),
            fingerprint = draft.fingerprint,
            paymentHash = draft.paymentHash,
            amount = draft.amount,
            origin = draft.origin,
            state = PaymentAttemptState.Created,
            providerCorrelation = null,
            createdAtMillis = now,
            submittedAtMillis = null,
            updatedAtMillis = now,
            feesPaid = null,
            preimage = null,
            failure = null
        )
        store.createAttempt(attempt)
        val submitted = store.transition(
            id = attempt.id,
            next = PaymentAttemptState.Submitted,
            atMillis = clock.nowMillis(),
            providerCorrelation = attempt.paymentHash.hex
        )

        val outcome = try {
            backend.submit(
                connection = connection,
                invoice = draft.invoice,
                amount = draft.amount
            )
        } catch (cancellation: CancellationException) {
            store.transition(
                id = submitted.id,
                next = PaymentAttemptState.Unknown,
                atMillis = clock.nowMillis()
            )
            throw cancellation
        } catch (_: Throwable) {
            SubmitPaymentOutcome.Unknown
        }

        StartPaymentOutcome.Attempt(applySubmitOutcome(submitted, outcome))
    }

    suspend fun reconcile(): List<PaymentAttempt> = commands.withLock {
        store.nonFinalAttempts().map { attempt ->
            if (attempt.state == PaymentAttemptState.Created) {
                return@map store.transition(
                    id = attempt.id,
                    next = PaymentAttemptState.Rejected,
                    atMillis = clock.nowMillis(),
                    failure = PaymentFailure.Unexpected
                )
            }
            val connection = store.connection(attempt.connectionId)
                ?: return@map store.transition(
                    id = attempt.id,
                    next = PaymentAttemptState.Unknown,
                    atMillis = clock.nowMillis()
                )
            val outcome = try {
                backend.lookup(connection, attempt.paymentHash)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                LookupPaymentOutcome.Unknown
            }
            applyLookupOutcome(attempt, outcome)
        }
    }

    fun attempts(): List<PaymentAttempt> = store.attempts()

    private fun applySubmitOutcome(
        attempt: PaymentAttempt,
        outcome: SubmitPaymentOutcome
    ): PaymentAttempt = when (outcome) {
        is SubmitPaymentOutcome.Settled -> transitionSettled(
            attempt = attempt,
            feesPaid = outcome.feesPaid,
            preimage = outcome.preimage
        )

        is SubmitPaymentOutcome.AlreadyPaid -> store.transition(
            id = attempt.id,
            next = PaymentAttemptState.AlreadyPaid,
            atMillis = clock.nowMillis(),
            preimage = outcome.preimage
        )

        SubmitPaymentOutcome.Pending -> store.transition(
            id = attempt.id,
            next = PaymentAttemptState.Pending,
            atMillis = clock.nowMillis()
        )

        is SubmitPaymentOutcome.Rejected -> store.transition(
            id = attempt.id,
            next = PaymentAttemptState.Rejected,
            atMillis = clock.nowMillis(),
            failure = outcome.failure
        )

        SubmitPaymentOutcome.Unknown -> store.transition(
            id = attempt.id,
            next = PaymentAttemptState.Unknown,
            atMillis = clock.nowMillis()
        )
    }

    private fun applyLookupOutcome(
        attempt: PaymentAttempt,
        outcome: LookupPaymentOutcome
    ): PaymentAttempt = when (outcome) {
        is LookupPaymentOutcome.Settled -> transitionSettled(
            attempt = attempt,
            feesPaid = outcome.feesPaid,
            preimage = outcome.preimage
        )

        LookupPaymentOutcome.Pending -> store.transition(
            id = attempt.id,
            next = PaymentAttemptState.Pending,
            atMillis = clock.nowMillis()
        )

        is LookupPaymentOutcome.Rejected -> store.transition(
            id = attempt.id,
            next = PaymentAttemptState.Rejected,
            atMillis = clock.nowMillis(),
            failure = outcome.failure
        )

        LookupPaymentOutcome.Unknown -> store.transition(
            id = attempt.id,
            next = PaymentAttemptState.Unknown,
            atMillis = clock.nowMillis()
        )
    }

    private fun transitionSettled(
        attempt: PaymentAttempt,
        feesPaid: MilliSatoshi?,
        preimage: fr.acinq.bitcoin.ByteVector32?
    ): PaymentAttempt = store.transition(
        id = attempt.id,
        next = PaymentAttemptState.Settled,
        atMillis = clock.nowMillis(),
        feesPaidMsat = feesPaid?.msat,
        preimage = preimage
    )
}

private fun PaymentAttemptState.blocksDuplicate(): Boolean = this != PaymentAttemptState.Rejected
