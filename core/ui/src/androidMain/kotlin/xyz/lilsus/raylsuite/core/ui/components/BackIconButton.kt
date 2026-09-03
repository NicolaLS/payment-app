package xyz.lilsus.raylsuite.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import xyz.lilsus.raylsuite.core.ui.R

@Composable
fun BackIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, testTag: String? = null) {
    IconButton(
        onClick = onClick,
        modifier = if (testTag == null) modifier else modifier.testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.action_back)
        )
    }
}
