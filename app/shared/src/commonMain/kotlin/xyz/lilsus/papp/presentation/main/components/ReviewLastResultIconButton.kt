package xyz.lilsus.papp.presentation.main.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import lasr.shared.generated.resources.Res
import lasr.shared.generated.resources.review_last_transaction
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags

@Composable
fun ReviewLastResultIconButton(onReviewLastResult: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp
    ) {
        IconButton(
            onClick = onReviewLastResult,
            modifier = Modifier
                .fillMaxSize()
                .testTag(MaestroTags.Payment.REVIEW_LAST_RESULT_BUTTON)
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = stringResource(Res.string.review_last_transaction)
            )
        }
    }
}
