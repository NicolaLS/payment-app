package xyz.lilsus.papp.presentation.main.components.hero

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import xyz.lilsus.papp.presentation.main.MainUiState

private val squares = listOf(
    SquareSpec(0.1f, 0.1f, 0.3f),
    SquareSpec(1f - (0.3f + 0.1f), 0.1f, 0.3f),
    SquareSpec(0.1f, 1f - (0.3f + 0.1f), 0.3f),
    SquareSpec(1f - (0.3f + 0.1f), 1f - (0.3f + 0.1f), 0.3f, false)
)

private val arcs = listOf(
    ArcSpec(0f, 0f, startAngle = 180f),
    ArcSpec(1f - 0.15f, 0f, startAngle = 270f),
    ArcSpec(0f, 1f - 0.15f, startAngle = 90f),
    ArcSpec(1f - 0.15f, 1f - 0.15f, startAngle = 0f)
)

@Composable
fun Hero(modifier: Modifier = Modifier, uiState: MainUiState) {
    val color = when (uiState) {
        MainUiState.Active -> MaterialTheme.colorScheme.onSurfaceVariant

        is MainUiState.Detected, is MainUiState.Confirm, is MainUiState.EnterAmount,
        MainUiState.Loading -> MaterialTheme.colorScheme.primary

        is MainUiState.Success -> MaterialTheme.colorScheme.tertiary

        is MainUiState.Error -> MaterialTheme.colorScheme.error
    }

    val animationState = rememberHeroAnimationState(squares, arcs)

    LaunchedEffect(uiState) {
        animationState.animateState(uiState, color)
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .aspectRatio(1f)
        ) {
            val canvasSize = size.minDimension
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)

            scale(animationState.clusterScale, pivot = canvasCenter) {
                squares.forEachIndexed { index, spec ->
                    val px = (spec.x + animationState.squareOffsets[index].x) * canvasSize
                    val py = (spec.y + animationState.squareOffsets[index].y) * canvasSize

                    val s = spec.size * canvasSize
                    val size = Size(s, s)
                    val squareCenter = Offset(px + s / 2, py + s / 2)

                    val cornerRadius = CornerRadius(s * 0.1f)
                    val stroke = Stroke(width = s * 0.1f)

                    scale(animationState.squareScales[index], pivot = squareCenter) {
                        if (spec.outlined) {
                            drawRoundRect(
                                color = animationState.color,
                                size = size,
                                cornerRadius = cornerRadius,
                                topLeft = Offset(px, py),
                                style = stroke
                            )
                            val childSize = Size(size.width * 0.5f, size.height * 0.5f)
                            val offsetX = px + (size.width - childSize.width) / 2f
                            val offsetY = py + (size.height - childSize.height) / 2f
                            drawRoundRect(
                                color = animationState.color,
                                size = childSize,
                                cornerRadius = cornerRadius,
                                topLeft = Offset(offsetX, offsetY)
                            )
                        } else {
                            drawRoundRect(
                                color = animationState.color,
                                size = size,
                                cornerRadius = cornerRadius,
                                topLeft = Offset(px, py)
                            )
                        }
                    }
                }
            }

            arcs.forEachIndexed { index, spec ->
                val px = (spec.x + animationState.arcOffsets[index].x) * canvasSize
                val py = (spec.y + animationState.arcOffsets[index].y) * canvasSize
                val cornerLength = canvasSize * spec.cornerLength
                val cornerStroke = Stroke(width = canvasSize * 0.02f, cap = StrokeCap.Round)
                drawArc(
                    color = animationState.color,
                    startAngle = spec.startAngle,
                    sweepAngle = spec.sweepAngle,
                    useCenter = false,
                    style = cornerStroke,
                    size = Size(cornerLength, cornerLength),
                    topLeft = Offset(px, py)
                )
            }
        }
    }
}
