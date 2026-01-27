package xyz.lilsus.papp.domain.model

/**
 * Blink-specific error types that can be translated to localized messages.
 */
enum class BlinkErrorType {
    PermissionDenied,
    InsufficientBalance,
    RouteNotFound,
    InvoiceExpired,
    SelfPayment,
    InvalidInvoice,
    AmountTooSmall,
    LimitExceeded,
    RateLimited,
    InvalidApiKey,

    /** API key was invalid/revoked and the wallet was automatically removed. */
    InvalidApiKeyWalletRemoved
}

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
     * Device networking is currently unavailable (no internet connection).
     * Detected by pre-flight network check before attempting wallet operations.
     */
    data object NetworkUnavailable : AppError()

    /**
     * Network is available but connection to the wallet relay failed.
     * This can happen due to relay being down, DNS issues, TLS errors, etc.
     * Unlike [NetworkUnavailable], the device has internet but the relay couldn't be reached.
     */
    data class RelayConnectionFailed(val message: String? = null) : AppError()

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
     * The API key does not have sufficient permissions for the requested operation.
     */
    data class InsufficientPermissions(val message: String? = null) : AppError()

    /**
     * Provided wallet URI is invalid or malformed.
     */
    data class InvalidWalletUri(val reason: String? = null) : AppError()

    /**
     * Any other unexpected error. The optional [message] can be used for logging.
     */
    data class Unexpected(val message: String? = null) : AppError()

    /**
     * Blink wallet specific error with a localizable type.
     */
    data class BlinkError(val type: BlinkErrorType) : AppError()
}

/**
 * Exception type used for propagating [AppError] across coroutine boundaries.
 */
class AppErrorException(val error: AppError, cause: Throwable? = null) :
    Exception(error.toString(), cause)
