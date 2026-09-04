package xyz.lilsus.raylsuite.core.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QrPresenceGateTest {
    @Test
    fun sameVisibleQrEmitsOnceAfterScannerRestart() {
        val gate = QrPresenceGate(absentObservationsToRearm = 2)

        assertEquals("invoice", gate.observe("invoice"))
        assertNull(gate.observe("invoice"))

        gate.reset()

        assertEquals("invoice", gate.observe("invoice"))
        assertNull(gate.observe("invoice"))
    }

    @Test
    fun qrRearmsOnlyAfterItDisappears() {
        val gate = QrPresenceGate(absentObservationsToRearm = 2)

        assertEquals("invoice", gate.observe("invoice"))
        assertNull(gate.observe(null))
        assertNull(gate.observe("invoice"))
        assertNull(gate.observe(null))
        assertNull(gate.observe(null))

        assertEquals("invoice", gate.observe("invoice"))
    }

    @Test
    fun differentQrEmitsImmediately() {
        val gate = QrPresenceGate()

        assertEquals("first", gate.observe("first"))
        assertEquals("second", gate.observe("second"))
        assertNull(gate.observe("second"))
    }
}
