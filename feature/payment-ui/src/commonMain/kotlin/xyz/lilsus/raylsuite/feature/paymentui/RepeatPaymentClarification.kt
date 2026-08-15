package xyz.lilsus.raylsuite.feature.paymentui

data class RepeatPaymentClarification(val situation: PreviousPaymentSituation) {
    val canRetryPreviousInvoice: Boolean
        get() = situation == PreviousPaymentSituation.OutcomeUnknown
}

enum class PreviousPaymentSituation {
    InProgress,
    OutcomeUnknown,
    Completed
}

enum class RepeatPaymentDecision {
    RetryPreviousInvoice,
    CreateAdditionalPayment,
    ViewPreviousPayment,
    Dismiss
}
