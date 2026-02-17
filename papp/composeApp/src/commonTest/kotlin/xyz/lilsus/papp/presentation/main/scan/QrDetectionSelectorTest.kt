package xyz.lilsus.papp.presentation.main.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QrDetectionSelectorTest {

    @Test
    fun returnsNullWhenNoCandidates() {
        val result = pickPreferredQrValue(
            candidates = emptyList(),
            frameWidth = 100f,
            frameHeight = 100f
        )

        assertNull(result)
    }

    @Test
    fun returnsOnlyCandidateWhenSingle() {
        val result = pickPreferredQrValue(
            candidates = listOf(
                QrDetectionCandidate(
                    value = "lnbc1single",
                    left = 10f,
                    top = 10f,
                    right = 20f,
                    bottom = 20f
                )
            ),
            frameWidth = 100f,
            frameHeight = 100f
        )

        assertEquals("lnbc1single", result)
    }

    @Test
    fun prefersLargestCandidate() {
        val result = pickPreferredQrValue(
            candidates = listOf(
                QrDetectionCandidate(
                    value = "small_centered",
                    left = 40f,
                    top = 40f,
                    right = 55f,
                    bottom = 55f
                ),
                QrDetectionCandidate(
                    value = "large_edge",
                    left = 0f,
                    top = 0f,
                    right = 60f,
                    bottom = 60f
                )
            ),
            frameWidth = 100f,
            frameHeight = 100f
        )

        assertEquals("large_edge", result)
    }

    @Test
    fun tiesByAreaPreferMostCentered() {
        val result = pickPreferredQrValue(
            candidates = listOf(
                QrDetectionCandidate(
                    value = "left_side",
                    left = 10f,
                    top = 40f,
                    right = 30f,
                    bottom = 60f
                ),
                QrDetectionCandidate(
                    value = "center",
                    left = 40f,
                    top = 40f,
                    right = 60f,
                    bottom = 60f
                )
            ),
            frameWidth = 100f,
            frameHeight = 100f
        )

        assertEquals("center", result)
    }

    @Test
    fun ignoresBlankValues() {
        val result = pickPreferredQrValue(
            candidates = listOf(
                QrDetectionCandidate(
                    value = "",
                    left = 0f,
                    top = 0f,
                    right = 80f,
                    bottom = 80f
                ),
                QrDetectionCandidate(
                    value = "lnbc1valid",
                    left = 10f,
                    top = 10f,
                    right = 20f,
                    bottom = 20f
                )
            ),
            frameWidth = 100f,
            frameHeight = 100f
        )

        assertEquals("lnbc1valid", result)
    }
}
