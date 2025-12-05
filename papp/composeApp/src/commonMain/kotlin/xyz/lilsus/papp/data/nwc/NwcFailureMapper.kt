package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.NwcEncryptionException
import io.github.nostr.nwc.NwcException
import io.github.nostr.nwc.NwcProtocolException
import io.github.nostr.nwc.NwcRequestException
import io.github.nostr.nwc.NwcTimeoutException
import io.github.nostr.nwc.model.NwcFailure
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException

internal fun NwcFailure.toAppErrorException(): AppErrorException =
    AppErrorException(toAppError(), toCause())

internal fun NwcFailure.toAppError(): AppError = when (this) {
    NwcFailure.None -> AppError.Unexpected()

    is NwcFailure.Network -> AppError.NetworkUnavailable

    is NwcFailure.Timeout -> AppError.Timeout

    is NwcFailure.Wallet -> AppError.PaymentRejected(
        code = error.code.takeIf { it.isNotEmpty() },
        message = error.message.takeIf { it.isNotEmpty() }
    )

    is NwcFailure.Protocol -> AppError.Unexpected(message)

    is NwcFailure.EncryptionUnsupported -> AppError.Unexpected(message)

    is NwcFailure.Unknown -> AppError.Unexpected(message)
}

private fun NwcFailure.toCause(): Throwable? = when (this) {
    NwcFailure.None -> null
    is NwcFailure.Network -> throwable ?: NwcException(message ?: "Network failure")
    is NwcFailure.Timeout -> NwcTimeoutException(message ?: "Request timed out")
    is NwcFailure.Wallet -> NwcRequestException(error)
    is NwcFailure.Protocol -> NwcProtocolException(message)
    is NwcFailure.EncryptionUnsupported -> NwcEncryptionException(message)
    is NwcFailure.Unknown -> cause ?: message?.let { NwcException(it) }
}
