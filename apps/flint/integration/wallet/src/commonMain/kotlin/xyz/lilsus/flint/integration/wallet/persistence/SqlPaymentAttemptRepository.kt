package xyz.lilsus.flint.integration.wallet.persistence

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CancellationException
import xyz.lilsus.flint.application.payment.CreateAttemptResult
import xyz.lilsus.flint.application.payment.FiatAmountQuote
import xyz.lilsus.flint.application.payment.FiatCurrency
import xyz.lilsus.flint.application.payment.FiatMinorAmount
import xyz.lilsus.flint.application.payment.FiatRateSnapshot
import xyz.lilsus.flint.application.payment.InvoiceFingerprint
import xyz.lilsus.flint.application.payment.PaymentAttempt
import xyz.lilsus.flint.application.payment.PaymentAttemptRepository
import xyz.lilsus.flint.application.payment.PaymentLinkPhase
import xyz.lilsus.flint.application.payment.PaymentMethod
import xyz.lilsus.flint.application.payment.PaymentOrigin
import xyz.lilsus.flint.integration.wallet.persistence.FlintDatabase
import xyz.lilsus.flint.integration.wallet.persistence.Payment_attempt
import xyz.lilsus.flint.integration.wallet.persistence.SelectLinked
import xyz.lilsus.raylsuite.core.model.Satoshi

class SqlPaymentAttemptRepository(
    driver: SqlDriver,
    private val maxRecords: Long = DEFAULT_MAX_RECORDS,
    private val maxLinkedRecords: Long = DEFAULT_MAX_LINKED_RECORDS,
    private val linkedRetentionSeconds: Long = DEFAULT_LINKED_RETENTION_SECONDS
) : PaymentAttemptRepository {
    private val database = FlintDatabase(driver)
    private val queries = database.paymentAttemptQueries

    override fun createConfirmed(
        attemptId: String,
        fingerprint: InvoiceFingerprint,
        method: PaymentMethod,
        amountSats: Satoshi,
        feeSats: Satoshi,
        origin: PaymentOrigin,
        nowEpochSeconds: Long,
        fiatQuote: FiatAmountQuote?
    ): CreateAttemptResult = try {
        database.transactionWithResult {
            queries.deleteLinkedBefore(nowEpochSeconds - linkedRetentionSeconds)
            queries.deleteOldestLinkedOverflow(maxLinkedRecords)
            queries.selectByFingerprint(fingerprint.value).executeAsOneOrNull()?.let {
                return@transactionWithResult CreateAttemptResult.Existing(it.toDomain())
            }
            if (queries.countAll().executeAsOne() >= maxRecords) {
                return@transactionWithResult CreateAttemptResult.CapacityReached
            }
            queries.insertAttempt(
                attempt_id = attemptId,
                invoice_fingerprint = fingerprint.value,
                payment_method = method.name,
                amount_sats = amountSats.value,
                fee_sats = feeSats.value,
                origin = origin.name,
                created_at_epoch_seconds = nowEpochSeconds,
                updated_at_epoch_seconds = nowEpochSeconds,
                fiat_currency_code = fiatQuote?.currency?.code,
                fiat_currency_name = fiatQuote?.currency?.name,
                fiat_fraction_digits = fiatQuote?.currency?.fractionDigits?.toLong(),
                fiat_amount_minor = fiatQuote?.input?.minorUnits,
                fiat_price_per_bitcoin = fiatQuote?.rate?.pricePerBitcoin,
                fiat_rate_observed_at_epoch_seconds = fiatQuote?.rate?.observedAtEpochSeconds
            )
            CreateAttemptResult.Created(checkNotNull(findById(attemptId)))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        val existing = try {
            findByFingerprint(fingerprint)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
        existing?.let(CreateAttemptResult::Existing) ?: CreateAttemptResult.Failed
    }

    override fun findById(attemptId: String): PaymentAttempt? =
        queries.selectById(attemptId).executeAsOneOrNull()?.toDomain()

    override fun findByFingerprint(fingerprint: InvoiceFingerprint): PaymentAttempt? =
        queries.selectByFingerprint(fingerprint.value).executeAsOneOrNull()?.toDomain()

    override fun unresolved(): List<PaymentAttempt> =
        queries.selectUnresolved().executeAsList().map {
            it.toDomain()
        }

    override fun linked(): List<PaymentAttempt> = queries.selectLinked().executeAsList().map {
        it.toDomain()
    }

    override fun all(): List<PaymentAttempt> = queries.selectAll().executeAsList().map {
        it.toDomain()
    }

    override fun markSubmissionStarted(attemptId: String, nowEpochSeconds: Long): Boolean =
        queries.markSubmissionStarted(nowEpochSeconds, attemptId).value == 1L

    override fun linkPayment(
        attemptId: String,
        breezPaymentId: String,
        nowEpochSeconds: Long
    ): Boolean =
        queries.linkPayment(breezPaymentId, nowEpochSeconds, attemptId, breezPaymentId).value == 1L

    override fun clear() = queries.clearAll().let { Unit }

    private fun Payment_attempt.toDomain(): PaymentAttempt = PaymentAttempt(
        attemptId = attempt_id,
        fingerprint = InvoiceFingerprint.persisted(invoice_fingerprint),
        method = PaymentMethod.valueOf(payment_method),
        amountSats = Satoshi.positive(amount_sats),
        feeSats = Satoshi.nonNegative(fee_sats),
        origin = PaymentOrigin.valueOf(origin),
        createdAtEpochSeconds = created_at_epoch_seconds,
        updatedAtEpochSeconds = updated_at_epoch_seconds,
        linkPhase = PaymentLinkPhase.valueOf(link_phase),
        breezPaymentId = breez_payment_id,
        fiatQuote = fiatQuote(
            amount_sats,
            fiat_currency_code,
            fiat_currency_name,
            fiat_fraction_digits,
            fiat_amount_minor,
            fiat_price_per_bitcoin,
            fiat_rate_observed_at_epoch_seconds
        )
    )

    private fun SelectLinked.toDomain(): PaymentAttempt = PaymentAttempt(
        attemptId = attempt_id,
        fingerprint = InvoiceFingerprint.persisted(invoice_fingerprint),
        method = PaymentMethod.valueOf(payment_method),
        amountSats = Satoshi.positive(amount_sats),
        feeSats = Satoshi.nonNegative(fee_sats),
        origin = PaymentOrigin.valueOf(origin),
        createdAtEpochSeconds = created_at_epoch_seconds,
        updatedAtEpochSeconds = updated_at_epoch_seconds,
        linkPhase = PaymentLinkPhase.valueOf(link_phase),
        breezPaymentId = breez_payment_id,
        fiatQuote = fiatQuote(
            amount_sats,
            fiat_currency_code,
            fiat_currency_name,
            fiat_fraction_digits,
            fiat_amount_minor,
            fiat_price_per_bitcoin,
            fiat_rate_observed_at_epoch_seconds
        )
    )

    private fun fiatQuote(
        amountSats: Long,
        code: String?,
        name: String?,
        fractionDigits: Long?,
        minor: Long?,
        rate: Double?,
        observedAt: Long?
    ): FiatAmountQuote? {
        code ?: return null
        name ?: return null
        fractionDigits ?: return null
        minor ?: return null
        rate ?: return null
        observedAt ?: return null
        return runCatching {
            FiatAmountQuote(
                input = FiatMinorAmount(code, minor),
                currency = FiatCurrency(code, name, fractionDigits.toInt()),
                sats = Satoshi.positive(amountSats),
                rate = FiatRateSnapshot(code, rate, observedAt)
            )
        }.getOrNull()
    }

    companion object {
        const val DEFAULT_MAX_RECORDS = 256L
        const val DEFAULT_MAX_LINKED_RECORDS = 192L
        const val DEFAULT_LINKED_RETENTION_SECONDS = 90L * 24L * 60L * 60L
    }
}
