package xyz.lilsus.papp.presentation.main.components.hero

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.lilsus.papp.presentation.main.MainUiState

@Composable
fun rememberHeroAnimationState(
    squares: List<SquareSpec>,
    arcs: List<ArcSpec>
): HeroAnimationState {
    return remember { HeroAnimationState(squares, arcs) }
}

class HeroAnimationState(
    private val squares: List<SquareSpec>,
    private val arcs: List<ArcSpec>
) {
    private val colorAnim = Animatable(Transparent)
    private val clusterScaleAnim = Animatable(1f)
    private val squareScaleAnims = List(squares.size) { Animatable(1f) }
    private val squareOffsetAnims =
        List(squares.size) { Animatable(Offset(0f, 0f), Offset.VectorConverter) }
    private val arcOffsetAnims =
        List(arcs.size) { Animatable(Offset(0f, 0f), Offset.VectorConverter) }

    val color: Color
        get() = colorAnim.value
    val clusterScale: Float
        get() = clusterScaleAnim.value
    val squareScales: List<Float>
        get() = squareScaleAnims.map { it.value }
    val squareOffsets: List<Offset>
        get() = squareOffsetAnims.map { it.value }
    val arcOffsets: List<Offset>
        get() = arcOffsetAnims.map { it.value }

    suspend fun animateState(uiState: MainUiState, targetColor: Color) {
        coroutineScope {
            launch {
                colorAnim.animateTo(
                    targetColor,
                    animationSpec = tween(durationMillis = 500, easing = EaseInOutCubic)
                )
            }

            when (uiState) {
                MainUiState.Active -> animateToActive()
                is MainUiState.Detected, is MainUiState.Confirm, is MainUiState.EnterAmount -> animateToCompressed()
                MainUiState.Loading -> animateToLoading()
                is MainUiState.Success, is MainUiState.Error -> animateToResult()
            }
        }
    }

    private suspend fun animateToActive() {
        coroutineScope {
            launch { reset() }
            launch { animateScalesToInfinite() }
        }
    }

    private suspend fun animateToCompressed() {
        coroutineScope {
            launch { clenchShrink() }
            launch { compressSquareOffsets() }
            launch { compressArcOffsets() }
        }
    }

    private suspend fun animateToLoading() {
        coroutineScope {
            launch { clenchShrink() }
            launch { compressSquareOffsets() }
            launch { compressAndIndicateLoadingArcs() }
        }
    }

    private suspend fun animateToResult() = coroutineScope {
        compressArcOffsets()
        reset(pop = true)
    }


    private suspend fun reset(pop: Boolean = false) = coroutineScope {
        squareScaleAnims.forEach { launch { it.stop() } }

        val popDuration = 250
        val settleDuration = 250
        val totalDuration = popDuration + settleDuration

        // Animate scale - no overshoot for pop
        launch {
            clusterScaleAnim.animateTo(
                1f,
                animationSpec = tween(if (pop) totalDuration else 500, easing = EaseInOutCubic)
            )
        }

        // Animate square scales - no overshoot for pop
        squareScaleAnims.forEach {
            launch {
                it.animateTo(
                    1f,
                    tween(if (pop) totalDuration else 500)
                )
            }
        }

        // Animate square offsets - no overshoot for pop
        squareOffsetAnims.forEach {
            launch {
                it.animateTo(
                    Offset.Zero,
                    animationSpec = tween(if (pop) totalDuration else 500, easing = EaseInOutCubic)
                )
            }
        }

        // Animate arc offsets - keep overshoot for pop
        arcOffsetAnims.forEachIndexed { index, anim ->
            launch {
                if (pop) {
                    val spec = arcs[index]
                    val poppedOffset = Offset(
                        stepTowardCenter(spec.x, spec.cornerLength, -0.1f),
                        stepTowardCenter(spec.y, spec.cornerLength, -0.1f)
                    )
                    anim.animateTo(
                        poppedOffset,
                        animationSpec = tween(popDuration, easing = EaseInOutCubic)
                    )
                }
                anim.animateTo(
                    Offset.Zero,
                    animationSpec = tween(if (pop) settleDuration else 250, easing = EaseInOutCubic)
                )
            }
        }
    }


    private suspend fun clenchShrink() {
        clusterScaleAnim.animateTo(
            0.9f,
            animationSpec = tween(durationMillis = 500, easing = EaseInOutCubic)
        )
    }

    private suspend fun animateScalesToInfinite() = coroutineScope {
        squareScaleAnims.forEachIndexed { index, anim ->
            launch {
                delay(index * 200L)
                anim.animateTo(
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 2000
                            1f at 0 using LinearOutSlowInEasing
                            1.1f at 1000 using EaseInOutCubic
                            1f at 2000 using EaseInOutCubic
                        },
                        repeatMode = RepeatMode.Reverse
                    ),
                )
            }
        }
    }

    private suspend fun compressSquareOffsets() = coroutineScope {
        val step = 0.1f
        squareOffsetAnims.forEachIndexed { index, anim ->
            val spec = squares[index]
            val adjustedX = stepTowardCenter(spec.x, spec.size, step)
            val adjustedY = stepTowardCenter(spec.y, spec.size, step)
            launch {
                anim.animateTo(
                    Offset(adjustedX, adjustedY),
                    animationSpec = tween(durationMillis = 500, easing = EaseInOutCubic)
                )
            }
        }
    }

    private suspend fun compressArcOffsets() = coroutineScope {
        val arcStep = 0.25f
        arcOffsetAnims.forEachIndexed { index, anim ->
            val spec = arcs[index]
            val adjustedX = stepTowardCenter(spec.x, spec.cornerLength, arcStep)
            val adjustedY = stepTowardCenter(spec.y, spec.cornerLength, arcStep)
            launch {
                anim.animateTo(
                    Offset(adjustedX, adjustedY),
                    animationSpec = tween(durationMillis = 500, easing = EaseInOutCubic)
                )
            }
        }
    }

    private suspend fun compressAndIndicateLoadingArcs() = coroutineScope {
        val arcStep = 0.25f
        arcOffsetAnims.forEachIndexed { index, anim ->
            val spec = arcs[index]
            val compressedX = stepTowardCenter(spec.x, spec.cornerLength, arcStep)
            val compressedY = stepTowardCenter(spec.y, spec.cornerLength, arcStep)
            val compressedOffset = Offset(compressedX, compressedY)

            launch {
                anim.animateTo(
                    compressedOffset,
                    animationSpec = tween(durationMillis = 500, easing = EaseInOutCubic)
                )

                delay(index * 100L)

                val current = anim.value
                val targetX = current.x * 0.7f
                val targetY = current.y * 0.7f

                anim.animateTo(
                    Offset(targetX, targetY),
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 600
                            Offset(current.x, current.y) at 0 using EaseInOutCubic
                            Offset(targetX, targetY) at 300 using EaseInOutCubic
                            Offset(current.x, current.y) at 600 using EaseInOutCubic
                        },
                        repeatMode = RepeatMode.Restart
                    )
                )
            }
        }
    }
}

fun stepTowardCenter(value: Float, size: Float, step: Float): Float {
    val center = value + size / 2f
    val delta = 0.5f - center
    return delta * step
}
