package xyz.lilsus.blip.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import rayl_suite.blip.shared.generated.resources.Res
import rayl_suite.blip.shared.generated.resources.action_back
import xyz.lilsus.blip.MaestroTags

@Composable
fun BackIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.testTag(MaestroTags.Settings.BACK_BUTTON)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(Res.string.action_back)
        )
    }
}
