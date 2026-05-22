package xyz.lilsus.papp.data.nwc

import io.github.nicolals.nwc.NwcError
import io.github.nicolals.nwc.NwcException
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException

internal fun NwcError.toAppErrorException(): AppErrorException =
    AppErrorException(toAppError(), NwcException(this))

internal fun NwcError.toAppError(): AppError = when (this) {
    is NwcError.WalletError -> AppError.PaymentRejected(
        code = code.code.takeIf { it.isNotEmpty() },
        message = message.takeIf { it.isNotEmpty() }
    )

    is NwcError.ConnectionError -> AppError.RelayConnectionFailed(message)

    is NwcError.Timeout -> AppError.Timeout

    is NwcError.Cancelled -> AppError.Unexpected("Operation cancelled")

    is NwcError.ProtocolError -> AppError.Unexpected(message)

    is NwcError.CryptoError -> AppError.Unexpected(message)

    is NwcError.PaymentPending -> AppError.PaymentUnconfirmed(paymentHash, message)
}
