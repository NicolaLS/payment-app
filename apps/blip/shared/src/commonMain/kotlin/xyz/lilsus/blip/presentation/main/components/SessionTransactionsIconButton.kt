package xyz.lilsus.blip.presentation.main.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import rayl_suite.blip.shared.generated.resources.Res
import rayl_suite.blip.shared.generated.resources.view_session_transactions
import xyz.lilsus.blip.MaestroTags

@Composable
fun SessionTransactionsIconButton(
    badgeCount: Int,
    attentionKey: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hasObservedAttentionKey by remember { mutableStateOf(false) }
    var pulsing by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pulsing) 1.14f else 1f,
        animationSpec = tween(durationMillis = TRANSACTION_BUTTON_PULSE_MS)
    )

    LaunchedEffect(attentionKey) {
        if (!hasObservedAttentionKey) {
            hasObservedAttentionKey = true
            return@LaunchedEffect
        }
        pulsing = true
        delay(TRANSACTION_BUTTON_PULSE_MS.toLong())
        pulsing = false
    }

    Box(modifier = modifier.size(52.dp)) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .scale(scale),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(MaestroTags.Payment.SESSION_TRANSACTIONS_BUTTON)
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = stringResource(Res.string.view_session_transactions)
                )
            }
        }
        if (badgeCount > 0) {
            Badge(
                modifier = Modifier.align(Alignment.TopEnd),
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) {
                Text(text = badgeCount.badgeLabel())
            }
        }
    }
}

private fun Int.badgeLabel(): String = if (this > MAX_BADGE_COUNT) {
    "$MAX_BADGE_COUNT+"
} else {
    toString()
}

private const val MAX_BADGE_COUNT = 99
private const val TRANSACTION_BUTTON_PULSE_MS = 220
