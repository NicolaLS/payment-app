package xyz.lilsus.raylsuite.feature.paymentui

import xyz.lilsus.raylsuite.core.ui.hero.RaylHeroGeometry

data class NativeHeroSquare(val x: Float, val y: Float, val size: Float, val outlined: Boolean)

data class NativeHeroArc(
    val x: Float,
    val y: Float,
    val startAngle: Float,
    val sweepAngle: Float,
    val length: Float
)

data class NativeHeroPoint(val x: Float, val y: Float)

data class NativeHeroGeometry(
    val canvasWidthFraction: Float,
    val squareCornerRadiusFraction: Float,
    val squareStrokeWidthFraction: Float,
    val finderInnerSizeFraction: Float,
    val finderInnerCornerRadiusFraction: Float,
    val dataBitGapFraction: Float,
    val dataBitCornerRadiusFraction: Float,
    val dataBitCount: Int,
    val arcStrokeWidthFraction: Float,
    val boltSizeFraction: Float,
    val squareCompressionFraction: Float,
    val arcCompressionFraction: Float,
    val arcPopFraction: Float,
    val loadingArcInsetFraction: Float,
    val squares: List<NativeHeroSquare>,
    val arcs: List<NativeHeroArc>,
    val bolt: List<NativeHeroPoint>
)

/** Copies the shared immutable spec across the umbrella-framework boundary once per Swift process. */
fun nativeHeroGeometry(): NativeHeroGeometry = NativeHeroGeometry(
    canvasWidthFraction = RaylHeroGeometry.CANVAS_WIDTH_FRACTION,
    squareCornerRadiusFraction = RaylHeroGeometry.SQUARE_CORNER_RADIUS_FRACTION,
    squareStrokeWidthFraction = RaylHeroGeometry.SQUARE_STROKE_WIDTH_FRACTION,
    finderInnerSizeFraction = RaylHeroGeometry.FINDER_INNER_SIZE_FRACTION,
    finderInnerCornerRadiusFraction =
        RaylHeroGeometry.FINDER_INNER_CORNER_RADIUS_FRACTION,
    dataBitGapFraction = RaylHeroGeometry.DATA_BIT_GAP_FRACTION,
    dataBitCornerRadiusFraction = RaylHeroGeometry.DATA_BIT_CORNER_RADIUS_FRACTION,
    dataBitCount = RaylHeroGeometry.DATA_BIT_COUNT,
    arcStrokeWidthFraction = RaylHeroGeometry.ARC_STROKE_WIDTH_FRACTION,
    boltSizeFraction = RaylHeroGeometry.BOLT_SIZE_FRACTION,
    squareCompressionFraction = RaylHeroGeometry.SQUARE_COMPRESSION_FRACTION,
    arcCompressionFraction = RaylHeroGeometry.ARC_COMPRESSION_FRACTION,
    arcPopFraction = RaylHeroGeometry.ARC_POP_FRACTION,
    loadingArcInsetFraction = RaylHeroGeometry.LOADING_ARC_INSET_FRACTION,
    squares =
        RaylHeroGeometry.squares.map {
            NativeHeroSquare(
                x = it.x,
                y = it.y,
                size = it.size,
                outlined = it.outlined
            )
        },
    arcs =
        RaylHeroGeometry.arcs.map {
            NativeHeroArc(
                x = it.x,
                y = it.y,
                startAngle = it.startAngle,
                sweepAngle = it.sweepAngle,
                length = it.length
            )
        },
    bolt = RaylHeroGeometry.bolt.map { NativeHeroPoint(x = it.x, y = it.y) }
)
