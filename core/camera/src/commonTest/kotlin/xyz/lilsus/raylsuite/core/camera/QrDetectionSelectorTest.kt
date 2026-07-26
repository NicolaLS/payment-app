package xyz.lilsus.raylsuite.core.camera

import kotlin.test.Test
import kotlin.test.assertEquals

class QrDetectionSelectorTest {
    @Test
    fun prefersLargestCandidate() {
        val result =
            pickPreferredQrValue(
                candidates =
                listOf(
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
}
