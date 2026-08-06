package xyz.lilsus.flint.application.payment

import xyz.lilsus.raylsuite.core.model.Satoshi

enum class PaymentConfirmationMode {
    ALWAYS,
    THRESHOLD
}

data class PaymentConfirmationPolicy(
    val mode: PaymentConfirmationMode,
    val amountThresholdSats: Satoshi,
    val feeThresholdSats: Satoshi
) {
    fun requiresConfirmation(
        amountSats: Satoshi,
        feeSats: Satoshi,
        origin: PaymentOrigin,
        amountEnteredByUser: Boolean
    ): Boolean = mode == PaymentConfirmationMode.ALWAYS ||
        origin == PaymentOrigin.DEEP_LINK ||
        origin == PaymentOrigin.MANUAL_RECOVERY ||
        amountEnteredByUser ||
        amountSats.value > amountThresholdSats.value ||
        feeSats.value > feeThresholdSats.value

    companion object {
        val Default = PaymentConfirmationPolicy(
            mode = PaymentConfirmationMode.ALWAYS,
            amountThresholdSats = Satoshi.positive(10_000),
            feeThresholdSats = Satoshi.nonNegative(0)
        )
    }
}

enum class UpdatePaymentPolicyResult {
    UPDATED,
    STORAGE_FAILURE
}

sealed interface LoadPaymentPolicyResult {
    data class Available(val policy: PaymentConfirmationPolicy) : LoadPaymentPolicyResult
    data object StorageFailure : LoadPaymentPolicyResult
}

interface PaymentPolicyRepository {
    fun load(): LoadPaymentPolicyResult
    fun store(policy: PaymentConfirmationPolicy): Boolean
}

object AlwaysConfirmPaymentPolicyRepository : PaymentPolicyRepository {
    override fun load() = LoadPaymentPolicyResult.Available(PaymentConfirmationPolicy.Default)
    override fun store(policy: PaymentConfirmationPolicy) = false
}
