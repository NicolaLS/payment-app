package xyz.lilsus.papp.data.nwc

import io.github.nicolals.nwc.NwcError
import io.github.nicolals.nwc.NwcErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.testHash

/**
 * Tests for NWC error mapping and repository behavior.
 *
 * Note: Full integration tests requiring NwcClient mocking are not possible
 * with the current design since NwcClient is a concrete class. Consider
 * adding an interface layer if more comprehensive testing is needed.
 */
class NwcWalletRepositoryImplTest {

    @Test
    fun mapsNwcErrorsToAppErrors() {
        val cases = listOf(
            NwcError.WalletError(
                code = NwcErrorCode.PAYMENT_FAILED,
                message = "Insufficient balance"
            ) to AppError.PaymentRejected(
                code = "PAYMENT_FAILED",
                message = "Insufficient balance"
            ),
            NwcError.WalletError(
                code = NwcErrorCode.fromCode(""),
                message = ""
            ) to AppError.PaymentRejected(
                code = "UNKNOWN",
                message = null
            ),
            NwcError.ConnectionError("Failed to connect") to AppError.RelayConnectionFailed(
                "Failed to connect"
            ),
            NwcError.Timeout(message = "Request timed out") to AppError.Timeout,
            NwcError.Cancelled() to AppError.Unexpected("Operation cancelled"),
            NwcError.ProtocolError("Invalid response format") to AppError.Unexpected(
                "Invalid response format"
            ),
            NwcError.CryptoError("Decryption failed") to AppError.Unexpected(
                "Decryption failed"
            ),
            NwcError.PaymentPending(
                message = "Payment is still processing",
                paymentHash = PAYMENT_HASH
            ) to AppError.PaymentUnconfirmed(
                paymentHash = testHash(PAYMENT_HASH),
                message = "Payment is still processing"
            )
        )

        cases.forEach { (error, expected) ->
            assertEquals(expected, error.toAppError())
        }
    }
}

private const val PAYMENT_HASH =
    "1111111111111111111111111111111111111111111111111111111111111111"
