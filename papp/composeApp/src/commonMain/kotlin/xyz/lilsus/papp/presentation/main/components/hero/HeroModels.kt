package xyz.lilsus.papp.presentation.main.components.hero

data class SquareSpec(
    val x: Float,
    val y: Float,
    val size: Float,
    val outlined: Boolean = true
)

data class ArcSpec(
    val x: Float,
    val y: Float,
    val startAngle: Float,
    val sweepAngle: Float = 90f,
    val cornerLength: Float = 0.15f
)
