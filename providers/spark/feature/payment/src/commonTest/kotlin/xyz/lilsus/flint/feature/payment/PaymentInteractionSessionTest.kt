package xyz.lilsus.flint.feature.payment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import xyz.lilsus.flint.application.payment.PaymentActivity
import xyz.lilsus.flint.application.payment.PaymentMethod
import xyz.lilsus.flint.application.payment.PaymentOrigin
import xyz.lilsus.flint.application.payment.PaymentOutcome
import xyz.lilsus.raylsuite.core.model.Satoshi

class PaymentInteractionSessionTest {
    @Test
    fun activeAttemptKeepsItsIdentityWhenVisibleResultIsCleared() {
        val interaction = PaymentInteractionSession()
        interaction.beginAttempt("attempt-1")
        interaction.showActivity(VisibleActivity(activity("attempt-1"), wasAlreadyPaid = false))

        assertEquals("attempt-1", interaction.activeAttemptId)
        assertEquals("attempt-1", interaction.visibleActivity?.activity?.attemptId)

        interaction.clearVisibleActivity()

        assertEquals("attempt-1", interaction.activeAttemptId)
        assertNull(interaction.visibleActivity)
        assertIs<PaymentInteractionPhase.Attempt>(interaction.phase)
    }

    @Test
    fun resetInvalidatesAttemptAndStandaloneResultTogether() {
        val interaction = PaymentInteractionSession()
        interaction.beginAttempt("attempt-1")
        interaction.showActivity(VisibleActivity(activity("attempt-1"), wasAlreadyPaid = false))

        interaction.reset()

        assertIs<PaymentInteractionPhase.Idle>(interaction.phase)
        assertNull(interaction.activeAttemptId)
        assertNull(interaction.visibleActivity)

        interaction.showActivity(VisibleActivity(activity("existing"), wasAlreadyPaid = true))
        assertIs<PaymentInteractionPhase.Result>(interaction.phase)
        assertNull(interaction.activeAttemptId)
        assertEquals(true, interaction.visibleActivity?.wasAlreadyPaid)
    }

    private fun activity(id: String) = PaymentActivity(
        attemptId = id,
        method = PaymentMethod.BOLT11,
        amountSats = Satoshi.positive(21),
        feeSats = Satoshi.nonNegative(1),
        origin = PaymentOrigin.DEEP_LINK,
        createdAtEpochSeconds = 100L,
        outcome = PaymentOutcome.PENDING
    )
}
