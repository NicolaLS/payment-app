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
    data class PaymentRejected(
        val code: String? = null,
        val message: String? = null,
    ) : AppError()

    /**
     * Networking is currently unavailable or the wallet could not be reached.
     */
    data object NetworkUnavailable : AppError()

    /**
     * The wallet did not respond within the allowed time window.
     */
    data object Timeout : AppError()

    /**
     * Any other unexpected error. The optional [message] can be used for logging.
     */
    data class Unexpected(val message: String? = null) : AppError()
}

/**
 * Exception type used for propagating [AppError] across coroutine boundaries.
 */
class AppErrorException(
    val error: AppError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)
