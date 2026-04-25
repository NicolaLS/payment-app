package xyz.lilsus.papp.presentation.main.components.hero

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.scanner_mode_far
import papp.composeapp.generated.resources.scanner_mode_label
import papp.composeapp.generated.resources.scanner_mode_near
import xyz.lilsus.papp.presentation.main.MainUiState
import xyz.lilsus.papp.presentation.main.scan.QrScannerMode
import xyz.lilsus.papp.presentation.main.tapToDismiss

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
fun Hero(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    scannerMode: QrScannerMode = QrScannerMode.Near,
    showScannerModeSelector: Boolean = false,
    onToggleScannerMode: (() -> Unit)? = null
) {
    val color = when (uiState) {
        MainUiState.Active -> MaterialTheme.colorScheme.onSurfaceVariant

        is MainUiState.Detected, is MainUiState.Confirm, is MainUiState.EnterAmount,
        is MainUiState.PendingRetry,
        is MainUiState.Loading -> MaterialTheme.colorScheme.primary

        is MainUiState.Success -> MaterialTheme.colorScheme.tertiary

        is MainUiState.Error -> MaterialTheme.colorScheme.error
    }

    val animationState = rememberHeroAnimationState(squares, arcs)

    LaunchedEffect(uiState) {
        animationState.animateState(uiState, color)
    }

    LaunchedEffect(scannerMode, uiState) {
        if (uiState == MainUiState.Active) {
            animationState.animateActiveMode(scannerMode)
        }
    }

    val modeLabel = stringResource(
        Res.string.scanner_mode_label,
        stringResource(
            when (scannerMode) {
                QrScannerMode.Near -> Res.string.scanner_mode_near
                QrScannerMode.Far -> Res.string.scanner_mode_far
            }
        )
    )
    val heroModifier = if (onToggleScannerMode == null) {
        modifier
    } else {
        modifier.tapToDismiss(
            enabled = true,
            onDismiss = onToggleScannerMode
        )
    }

    Box(
        modifier = heroModifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .aspectRatio(1f)
            ) {
                val canvasSize = size.minDimension
                val canvasCenter = Offset(size.width / 2f, size.height / 2f)

                scale(animationState.modeScale, pivot = canvasCenter) {
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
                                            cornerRadius =
                                                CornerRadius(childSize.width * 0.1f),
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

            if (showScannerModeSelector) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = modeLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
