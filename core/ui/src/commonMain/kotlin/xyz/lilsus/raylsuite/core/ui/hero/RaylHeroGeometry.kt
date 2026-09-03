package xyz.lilsus.raylsuite.core.ui.hero

/** A normalized square in the platform-native hero drawing surface. */
data class RaylHeroSquareSpec(
    val x: Float,
    val y: Float,
    val size: Float,
    val outlined: Boolean = true
)

/** A normalized corner arc in the platform-native hero drawing surface. */
data class RaylHeroArcSpec(
    val x: Float,
    val y: Float,
    val startAngle: Float,
    val sweepAngle: Float = 90f,
    val length: Float = 0.15f
)

/** A normalized point in the hero's success bolt. */
data class RaylHeroPoint(val x: Float, val y: Float)

/**
 * Renderer-neutral geometry for the hero.
 *
 * Android and iOS copy these values into their own native drawing primitives. Animation clocks,
 * interpolation, drawing APIs, and frame state remain platform-owned.
 */
object RaylHeroGeometry {
    const val CANVAS_WIDTH_FRACTION = 0.5f
    const val SQUARE_CORNER_RADIUS_FRACTION = 0.05f
    const val SQUARE_STROKE_WIDTH_FRACTION = 0.1f
    const val FINDER_INNER_SIZE_FRACTION = 0.35f
    const val FINDER_INNER_CORNER_RADIUS_FRACTION = 0.1f
    const val DATA_BIT_GAP_FRACTION = 0.1f
    const val DATA_BIT_CORNER_RADIUS_FRACTION = 0.1f
    const val DATA_BIT_COUNT = 4
    const val ARC_STROKE_WIDTH_FRACTION = 0.02f
    const val BOLT_SIZE_FRACTION = 0.6f

    const val SQUARE_COMPRESSION_FRACTION = 0.1f
    const val ARC_COMPRESSION_FRACTION = 0.25f
    const val ARC_POP_FRACTION = -0.15f
    const val LOADING_ARC_INSET_FRACTION = 0.7f

    val squares: List<RaylHeroSquareSpec> =
        listOf(
            RaylHeroSquareSpec(0.1f, 0.1f, 0.3f),
            RaylHeroSquareSpec(0.6f, 0.1f, 0.3f),
            RaylHeroSquareSpec(0.1f, 0.6f, 0.3f),
            RaylHeroSquareSpec(0.6f, 0.6f, 0.3f, outlined = false)
        )

    val arcs: List<RaylHeroArcSpec> =
        listOf(
            RaylHeroArcSpec(0f, 0f, startAngle = 180f),
            RaylHeroArcSpec(0.85f, 0f, startAngle = 270f),
            RaylHeroArcSpec(0f, 0.85f, startAngle = 90f),
            RaylHeroArcSpec(0.85f, 0.85f, startAngle = 0f)
        )

    val bolt: List<RaylHeroPoint> =
        listOf(
            RaylHeroPoint(0.55f, 0f),
            RaylHeroPoint(0.2f, 0.6f),
            RaylHeroPoint(0.45f, 0.6f),
            RaylHeroPoint(0.35f, 1f),
            RaylHeroPoint(0.8f, 0.35f),
            RaylHeroPoint(0.55f, 0.35f)
        )
}
