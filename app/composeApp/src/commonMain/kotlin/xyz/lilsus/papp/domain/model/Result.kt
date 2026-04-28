package xyz.lilsus.papp.domain.model

/**
 * Represents the lifecycle of an async operation.
 */
sealed class Result<out T> {
    /**
     * Emitted while an operation is in progress.
     */
    data object Loading : Result<Nothing>()

    /**
     * Emitted when an operation completes successfully with [data].
     */
    data class Success<T>(val data: T) : Result<T>()

    /**
     * Emitted when an operation fails with the given [error].
     */
    data class Error(val error: AppError, val cause: Throwable? = null) : Result<Nothing>()

    companion object {
        fun <T> success(data: T): Result<T> = Success(data)
        fun error(error: AppError, cause: Throwable? = null): Result<Nothing> = Error(error, cause)
    }
}
