package xyz.lilsus.raylsuite.core.ui.hero

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.qrose.options.QrBrush
import io.github.alexzhirkevich.qrose.options.solid
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.ui.generated.resources.Res

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
fun RaylHero(
    phase: RaylHeroPhase,
    modifier: Modifier = Modifier,
    qrContent: RaylHeroQrContent? = null
) {
    val color = when (phase) {
        RaylHeroPhase.Ready -> MaterialTheme.colorScheme.onSurfaceVariant

        RaylHeroPhase.Acknowledged, RaylHeroPhase.Processing ->
            MaterialTheme.colorScheme.primary

        RaylHeroPhase.Succeeded -> MaterialTheme.colorScheme.tertiary

        RaylHeroPhase.Failed -> MaterialTheme.colorScheme.error
    }

    val animationState = rememberHeroAnimationState(squares, arcs)

    LaunchedEffect(phase) {
        animationState.animatePhase(phase, color)
    }

    BoxWithConstraints(modifier = modifier) {
        val canvasSide = minOf(maxWidth * HERO_CANVAS_WIDTH_FRACTION, maxHeight)
        if (qrContent != null) {
            HeroQrCode(
                content = qrContent,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(canvasSide)
            )
            return@BoxWithConstraints
        }

        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(canvasSide)
        ) {
            val canvasSize = size.minDimension
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)

            scale(animationState.clusterScale, pivot = canvasCenter) {
                // Apply Shake (if any)
                translate(left = animationState.clusterShakeX) {
                    // Squares
                    squares.forEachIndexed { index, spec ->
                        val squareOffset = animationState.squareOffset(index)
                        val px = (spec.x + squareOffset.x) * canvasSize
                        val py = (spec.y + squareOffset.y) * canvasSize

                        val s = spec.size * canvasSize
                        val size = Size(s, s)
                        val squareCenter = Offset(px + s / 2, py + s / 2)

                        // Sharper corners for a more digital/tech look (5% of size instead of 10%)
                        val cornerRadius = CornerRadius(s * 0.05f)
                        val stroke = Stroke(width = s * 0.1f)

                        scale(animationState.squareScale(index), pivot = squareCenter) {
                            if (spec.outlined) {
                                // Finder Pattern (Ring)
                                drawRoundRect(
                                    color = animationState.color,
                                    size = size,
                                    cornerRadius = cornerRadius,
                                    topLeft = Offset(px, py),
                                    style = stroke
                                )
                                // Inner solid square (35% size)
                                val childSize =
                                    Size(size.width * 0.35f, size.height * 0.35f)
                                val offsetX = px + (size.width - childSize.width) / 2f
                                val offsetY = py + (size.height - childSize.height) / 2f
                                drawRoundRect(
                                    color = animationState.color,
                                    size = childSize,
                                    cornerRadius = CornerRadius(childSize.width * 0.1f),
                                    topLeft = Offset(offsetX, offsetY)
                                )
                            } else {
                                // 4th Corner: "Data" Cluster with Flickering Bits
                                val gap = s * 0.1f
                                val miniSize = (s - gap) / 2f
                                val miniRadius = CornerRadius(miniSize * 0.1f)

                                fun drawBit(x: Float, y: Float, opacity: Float) {
                                    drawRoundRect(
                                        color = animationState.color.copy(
                                            alpha = animationState.color.alpha * opacity
                                        ),
                                        size = Size(miniSize, miniSize),
                                        cornerRadius = miniRadius,
                                        topLeft = Offset(x, y)
                                    )
                                }

                                drawBit(px, py, animationState.bitOpacity(0))
                                drawBit(
                                    px + miniSize + gap,
                                    py,
                                    animationState.bitOpacity(1)
                                )
                                drawBit(
                                    px,
                                    py + miniSize + gap,
                                    animationState.bitOpacity(2)
                                )
                                drawBit(
                                    px + miniSize + gap,
                                    py + miniSize + gap,
                                    animationState.bitOpacity(3)
                                )
                            }
                        }
                    }

                    if (animationState.boltScale > 0f) {
                        val boltSize = canvasSize * 0.6f
                        val boltPath = Path().apply {
                            moveTo(boltSize * 0.55f, 0f)
                            lineTo(boltSize * 0.2f, boltSize * 0.6f)
                            lineTo(boltSize * 0.45f, boltSize * 0.6f)
                            lineTo(boltSize * 0.35f, boltSize * 1f)
                            lineTo(boltSize * 0.8f, boltSize * 0.35f)
                            lineTo(boltSize * 0.55f, boltSize * 0.35f)
                            close()
                        }

                        val boltCenter = Offset(canvasSize / 2f, canvasSize / 2f)
                        val pathBounds = boltPath.getBounds()
                        val pathCenter = pathBounds.center

                        translate(
                            left = boltCenter.x - pathCenter.x,
                            top = boltCenter.y - pathCenter.y
                        ) {
                            scale(animationState.boltScale, pivot = pathCenter) {
                                drawPath(boltPath, animationState.color)
                            }
                        }
                    }
                }
            }

            rotate(animationState.rotation, pivot = canvasCenter) {
                arcs.forEachIndexed { index, spec ->
                    val arcOffset = animationState.arcOffset(index)
                    val px = (spec.x + arcOffset.x) * canvasSize
                    val py = (spec.y + arcOffset.y) * canvasSize
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
}

@Composable
private fun HeroQrCode(content: RaylHeroQrContent, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = Color.White,
        contentColor = Color.Black
    ) {
        Image(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            painter = rememberQrCodePainter(
                data = content.data,
                darkBrush = QrBrush.solid(Color.Black),
                lightBrush = QrBrush.solid(Color.White),
                ballBrush = QrBrush.solid(Color.Black),
                frameBrush = QrBrush.solid(Color.Black)
            ),
            contentDescription = content.contentDescription
        )
    }
}

private const val HERO_CANVAS_WIDTH_FRACTION = 0.5f
enum class RaylHeroPhase {
    Ready,
    Acknowledged,
    Processing,
    Succeeded,
    Failed
}

data class RaylHeroQrContent(val data: String, val contentDescription: String)
