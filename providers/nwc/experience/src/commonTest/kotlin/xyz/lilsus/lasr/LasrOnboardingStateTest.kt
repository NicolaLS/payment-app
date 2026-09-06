package xyz.lilsus.lasr

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LasrOnboardingStateTest {
    @Test
    fun freshInstallStartsIncompleteAndRoutesDeepLinksThroughOnboarding() {
        val state = LasrOnboardingState(MapSettings())

        assertFalse(state.completed.value)
        assertEquals(LasrNwcDeepLinkTarget.Onboarding, state.nwcDeepLinkTarget())
        assertFalse(state.canHandlePaymentDeepLink(walletConnected = false))
    }

    @Test
    fun interruptedOnboardingRemainsIncompleteAcrossRestart() {
        val settings = MapSettings()
        LasrOnboardingState(settings)

        val restarted = LasrOnboardingState(settings)

        assertFalse(restarted.completed.value)
        assertEquals(LasrNwcDeepLinkTarget.Onboarding, restarted.nwcDeepLinkTarget())
        assertFalse(restarted.canHandlePaymentDeepLink(walletConnected = true))
    }

    @Test
    fun completionSurvivesRestartAndWalletRemoval() {
        val settings = MapSettings()
        val state = LasrOnboardingState(settings)
        state.complete()

        val restartedWithoutWallet = LasrOnboardingState(settings)

        assertTrue(restartedWithoutWallet.completed.value)
        assertEquals(LasrNwcDeepLinkTarget.Settings, restartedWithoutWallet.nwcDeepLinkTarget())
        assertFalse(restartedWithoutWallet.canHandlePaymentDeepLink(walletConnected = false))
    }
}
