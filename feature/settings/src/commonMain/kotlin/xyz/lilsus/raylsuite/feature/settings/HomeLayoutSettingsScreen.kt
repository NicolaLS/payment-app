package xyz.lilsus.raylsuite.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.ui.components.AppFadingLazyColumn
import xyz.lilsus.raylsuite.core.ui.components.AppListDefaults
import xyz.lilsus.raylsuite.core.ui.components.AppListRow
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubLensId
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubLensDefinition
import xyz.lilsus.raylsuite.feature.settings.generated.resources.Res
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_home_layout
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_home_layout_body

/**
 * Lens selector. It renders whatever definitions are registered and knows only their metadata
 * and preview; changing the selection never touches hub data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLayoutSettingsScreen(
    definitions: List<PaymentHubLensDefinition>,
    selectedId: PaymentHubLensId?,
    onSelect: (PaymentHubLensId) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.testTag(SettingsTestTags.HOME_LAYOUT_SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_home_layout)) },
                navigationIcon = { BackIconButton(onClick = onBack) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        AppFadingLazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
            contentPadding = AppListDefaults.ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(AppListDefaults.ItemSpacing)
        ) {
            item(key = "body") {
                Text(
                    text = stringResource(Res.string.settings_home_layout_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(definitions, key = { it.id.value }) { definition ->
                val selected = definition.id == selectedId
                AppListRow(
                    onClick = { onSelect(definition.id) },
                    selected = selected,
                    showSelectedState = true,
                    testTag = SettingsTestTags.homeLayoutOption(definition.id),
                    minHeight = 120.dp
                ) {
                    definition.Preview(
                        modifier =
                            Modifier
                                .width(72.dp)
                                .height(104.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = definition.metadata.name.resolve(),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight =
                                        if (selected) FontWeight.SemiBold else FontWeight.Normal
                                ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = definition.metadata.description.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
