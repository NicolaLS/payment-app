package xyz.lilsus.rayl

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RaylSelectionTest {
    @Test
    fun anotherConnectionCannotReplaceTheSelectedExperience() {
        val settings = MapSettings()
        val selection = RaylSelection(settings)
        selection.choose(RaylWallet.Blink)
        assertFailsWith<IllegalStateException> { selection.choose(RaylWallet.Nwc) }
        assertEquals(RaylWallet.Blink, RaylSelection(settings).wallet.value)
        selection.clear()
        assertNull(RaylSelection(settings).wallet.value)
        selection.choose(RaylWallet.Nwc)
        assertEquals(RaylWallet.Nwc, RaylSelection(settings).wallet.value)
        assertTrue(selection.welcomeCompleted.value)
    }
}
