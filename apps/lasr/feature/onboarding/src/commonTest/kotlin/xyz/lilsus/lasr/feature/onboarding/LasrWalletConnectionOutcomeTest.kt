package xyz.lilsus.lasr.feature.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals

class LasrWalletConnectionOutcomeTest {
    @Test
    fun earlyConnectionResumesOnboarding() {
        assertEquals(
            LasrWalletConnectionOutcome.ResumeOnboarding,
            lasrWalletConnectionOutcome(fromSettings = false, hasAgreed = false)
        )
    }

    @Test
    fun connectionAfterAgreementCompletesOnboarding() {
        assertEquals(
            LasrWalletConnectionOutcome.CompleteOnboarding,
            lasrWalletConnectionOutcome(fromSettings = false, hasAgreed = true)
        )
    }

    @Test
    fun settingsConnectionFinishesSettingsFlow() {
        assertEquals(
            LasrWalletConnectionOutcome.FinishSettings,
            lasrWalletConnectionOutcome(fromSettings = true, hasAgreed = false)
        )
    }
}
