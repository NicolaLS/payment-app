package xyz.lilsus.papp.data.nwc

import io.github.nicolals.nwc.NwcError
import io.github.nicolals.nwc.NwcErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.lilsus.papp.domain.model.AppError

/**
 * Tests for NWC error mapping and repository behavior.
 *
 * Note: Full integration tests requiring NwcClient mocking are not possible
 * with the current design since NwcClient is a concrete class. Consider
 * adding an interface layer if more comprehensive testing is needed.
 */
class NwcWalletRepositoryImplTest {

    @Test
    fun walletErrorMapsToPaymentRejected() {
        val error = NwcError.WalletError(
            code = NwcErrorCode.PAYMENT_FAILED,
            message = "Insufficient balance"
        )

        val appError = error.toAppError()

        assertEquals(
            AppError.PaymentRejected(code = "PAYMENT_FAILED", message = "Insufficient balance"),
            appError
        )
    }

    @Test
    fun walletErrorWithUnknownCodeAndEmptyMessageMapsCorrectly() {
        // NwcErrorCode.fromCode("") returns UNKNOWN, not an empty code
        val error = NwcError.WalletError(
            code = NwcErrorCode.fromCode(""),
            message = ""
        )

        val appError = error.toAppError()

        assertEquals(
            AppError.PaymentRejected(code = "UNKNOWN", message = null),
            appError
        )
    }

    @Test
    fun connectionErrorMapsToNetworkUnavailable() {
        val error = NwcError.ConnectionError("Failed to connect")

        val appError = error.toAppError()

        assertEquals(AppError.NetworkUnavailable, appError)
    }

    @Test
    fun timeoutErrorMapsToTimeout() {
        val error = NwcError.Timeout(message = "Request timed out")

        val appError = error.toAppError()

        assertEquals(AppError.Timeout, appError)
    }

    @Test
    fun cancelledErrorMapsToUnexpected() {
        val error = NwcError.Cancelled()

        val appError = error.toAppError()

        assertEquals(AppError.Unexpected("Operation cancelled"), appError)
    }

    @Test
    fun protocolErrorMapsToUnexpected() {
        val error = NwcError.ProtocolError("Invalid response format")

        val appError = error.toAppError()

        assertEquals(AppError.Unexpected("Invalid response format"), appError)
    }

    @Test
    fun cryptoErrorMapsToUnexpected() {
        val error = NwcError.CryptoError("Decryption failed")

        val appError = error.toAppError()

        assertEquals(AppError.Unexpected("Decryption failed"), appError)
    }

    @Test
    fun paymentPendingErrorMapsToPaymentUnconfirmed() {
        val error = NwcError.PaymentPending(
            message = "Payment is still processing",
            paymentHash = "abc123"
        )

        val appError = error.toAppError()

        assertEquals(
            AppError.PaymentUnconfirmed(
                paymentHash = "abc123",
                message = "Payment is still processing"
            ),
            appError
        )
    }
}
