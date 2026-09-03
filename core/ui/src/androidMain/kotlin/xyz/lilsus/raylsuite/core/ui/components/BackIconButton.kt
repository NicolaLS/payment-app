package xyz.lilsus.raylsuite.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.ui.generated.resources.Res
import xyz.lilsus.raylsuite.core.ui.generated.resources.action_back

@Composable
fun BackIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, testTag: String? = null) {
    IconButton(
        onClick = onClick,
        modifier = if (testTag == null) modifier else modifier.testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(Res.string.action_back)
        )
    }
}
