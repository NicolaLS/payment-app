package xyz.lilsus.papp.domain.model

/**
 * Represents user-visible error types emitted by the domain layer.
 *
 * Keep the variants high-level and UI-friendly; data-layer details should be
 * translated before reaching this abstraction.
 */
sealed class AppError {
    /**
     * No wallet connection is configured, so no actions requiring the wallet can succeed.
     */
    data object MissingWalletConnection : AppError()

    /**
     * The wallet rejected the payment request.
     */
    data class PaymentRejected(val code: String? = null, val message: String? = null) : AppError()

    /**
     * Networking is currently unavailable or the wallet could not be reached.
     */
    data object NetworkUnavailable : AppError()

    /**
     * The wallet did not respond within the allowed time window.
     */
    data object Timeout : AppError()

    /**
     * Payment was sent but final status is unknown.
     * This is a "soft" error - the payment may have succeeded, failed, or still be processing.
     * Callers should verify the payment status before treating this as a failure.
     */
    data class PaymentUnconfirmed(val paymentHash: String?, val message: String? = null) :
        AppError()

    /**
     * Authentication failed (e.g., invalid or revoked API key).
     */
    data class AuthenticationFailure(val message: String? = null) : AppError()

    /**
     * Provided wallet URI is invalid or malformed.
     */
    data class InvalidWalletUri(val reason: String? = null) : AppError()

    /**
     * Any other unexpected error. The optional [message] can be used for logging.
     */
    data class Unexpected(val message: String? = null) : AppError()
}

/**
 * Exception type used for propagating [AppError] across coroutine boundaries.
 */
class AppErrorException(val error: AppError, cause: Throwable? = null) :
    Exception(error.toString(), cause)
