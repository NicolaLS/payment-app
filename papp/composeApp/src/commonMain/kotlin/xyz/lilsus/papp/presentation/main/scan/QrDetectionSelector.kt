package xyz.lilsus.papp.presentation.main.scan

import kotlin.math.abs
import kotlin.math.min

internal data class QrDetectionCandidate(
    val value: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

/**
 * Picks one QR payload from multiple detections.
 *
 * Selection rules:
 * 1. Prefer the largest candidate by area.
 * 2. If areas are effectively equal, prefer the one closest to frame center.
 */
internal fun pickPreferredQrValue(
    candidates: List<QrDetectionCandidate>,
    frameWidth: Float,
    frameHeight: Float
): String? {
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) {
        return candidates[0].value.takeIf { it.isNotBlank() }
    }

    val safeWidth = frameWidth.takeIf { it > 0f } ?: 1f
    val safeHeight = frameHeight.takeIf { it > 0f } ?: 1f
    val invWidth = 1f / safeWidth
    val invHeight = 1f / safeHeight

    var bestValue: String? = null
    var bestArea = Float.NEGATIVE_INFINITY
    var bestCenterDistanceSquared = Float.POSITIVE_INFINITY

    for (candidate in candidates) {
        val value = candidate.value
        if (value.isBlank()) continue

        val left = min(candidate.left, candidate.right)
        val right = if (candidate.right >= candidate.left) candidate.right else candidate.left
        val top = min(candidate.top, candidate.bottom)
        val bottom = if (candidate.bottom >= candidate.top) candidate.bottom else candidate.top

        val width = (right - left).coerceAtLeast(0f)
        val height = (bottom - top).coerceAtLeast(0f)
        val normalizedArea = (width * invWidth) * (height * invHeight)

        val normalizedCenterX = ((left + right) * 0.5f) * invWidth
        val normalizedCenterY = ((top + bottom) * 0.5f) * invHeight
        val dx = normalizedCenterX - 0.5f
        val dy = normalizedCenterY - 0.5f
        val centerDistanceSquared = (dx * dx) + (dy * dy)

        val largerByArea = normalizedArea > bestArea + AREA_EPSILON
        val tiedArea = abs(normalizedArea - bestArea) <= AREA_EPSILON
        val closerToCenter = centerDistanceSquared < bestCenterDistanceSquared

        if (largerByArea || (tiedArea && closerToCenter)) {
            bestValue = value
            bestArea = normalizedArea
            bestCenterDistanceSquared = centerDistanceSquared
        }
    }

    return bestValue
}

private const val AREA_EPSILON = 1e-6f
