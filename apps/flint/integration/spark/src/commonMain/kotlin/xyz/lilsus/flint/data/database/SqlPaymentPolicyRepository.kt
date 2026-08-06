package xyz.lilsus.flint.data.database

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CancellationException
import xyz.lilsus.flint.application.payment.LoadPaymentPolicyResult
import xyz.lilsus.flint.application.payment.PaymentConfirmationMode
import xyz.lilsus.flint.application.payment.PaymentConfirmationPolicy
import xyz.lilsus.flint.application.payment.PaymentPolicyRepository
import xyz.lilsus.flint.database.FlintDatabase
import xyz.lilsus.raylsuite.core.model.Satoshi

class SqlPaymentPolicyRepository(driver: SqlDriver) : PaymentPolicyRepository {
    private val database = FlintDatabase(driver)
    private val queries = database.paymentPolicyQueries

    override fun load(): LoadPaymentPolicyResult = try {
        val policy = queries.selectPolicy { mode, amountThreshold, feeThreshold ->
            PaymentConfirmationPolicy(
                mode = PaymentConfirmationMode.valueOf(mode),
                amountThresholdSats = Satoshi.positive(amountThreshold),
                feeThresholdSats = Satoshi.nonNegative(feeThreshold)
            )
        }.executeAsOneOrNull() ?: PaymentConfirmationPolicy.Default
        LoadPaymentPolicyResult.Available(policy)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        LoadPaymentPolicyResult.StorageFailure
    }

    override fun store(policy: PaymentConfirmationPolicy): Boolean = try {
        database.transactionWithResult {
            queries.upsertPolicy(
                mode = policy.mode.name,
                amount_threshold_sats = policy.amountThresholdSats.value,
                fee_threshold_sats = policy.feeThresholdSats.value
            )
            queries.selectPolicy().executeAsOne().let {
                it.mode == policy.mode.name &&
                    it.amount_threshold_sats == policy.amountThresholdSats.value &&
                    it.fee_threshold_sats == policy.feeThresholdSats.value
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }
}
