package xyz.lilsus.blip.feature.blinkcontacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.Res as BlinkContactsRes
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.import_contacts_no_matches
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.import_contacts_search
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_already_added
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_empty
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_import
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_loading
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_select_all
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_selected
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_skip
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_success
import xyz.lilsus.blip.feature.blinkcontacts.generated.resources.settings_wallet_details_import_contacts_transactions
import xyz.lilsus.blip.ui.blinkErrorMessageFor
import xyz.lilsus.blip.ui.generated.resources.Res as BlipUiRes
import xyz.lilsus.blip.ui.generated.resources.blink_contacts_import
import xyz.lilsus.blip.ui.generated.resources.blink_contacts_import_hint
import xyz.lilsus.blip.ui.generated.resources.blink_contacts_title
import xyz.lilsus.raylsuite.core.ui.components.AppListDefaults
import xyz.lilsus.raylsuite.core.ui.components.AppListScaffold
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton

/** Android renderer for the Blip-owned contact import flow. */
@Composable
fun BlinkContactsImportButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(stringResource(BlipUiRes.string.blink_contacts_import))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkContactsImportScreen(
    state: BlinkContactsImportUiState,
    onBack: () -> Unit,
    onToggleContact: (String) -> Unit,
    onToggleAll: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val errorText = state.error?.let { blinkErrorMessageFor(it) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            BlipUiRes.string.blink_contacts_title
                        )
                    )
                },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            BlinkContactsImportBottomBar(
                state = state,
                onImport = onImport,
                onSkip = onSkip
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .padding(AppListDefaults.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(
                    BlipUiRes.string.blink_contacts_import_hint
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(
                            BlinkContactsRes.string.settings_wallet_details_import_contacts_loading
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (
                state.hasLoaded &&
                !state.isLoading &&
                state.items.isEmpty() &&
                errorText == null
            ) {
                Text(
                    text = stringResource(
                        BlinkContactsRes.string.settings_wallet_details_import_contacts_empty
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.items.isNotEmpty()) {
                AppListScaffold(
                    isEmpty = state.filteredItems.isEmpty(),
                    emptyMessage =
                        stringResource(
                            BlinkContactsRes.string.import_contacts_no_matches
                        ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    showSearchBar = true,
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    searchLabel =
                        stringResource(
                            BlinkContactsRes.string.import_contacts_search
                        )
                ) {
                    item {
                        SelectAllContactsRow(
                            checked = state.allSelected,
                            selectedCount = state.selectedCount,
                            enabled = state.hasSelectableItems && !state.isImporting,
                            onToggle = onToggleAll
                        )
                    }
                    items(state.filteredItems, key = { it.id }) { item ->
                        BlinkContactImportRow(
                            item = item,
                            selected = item.id in state.selectedIds,
                            enabled = !item.alreadyAdded && !state.isImporting,
                            onToggle = { onToggleContact(item.id) }
                        )
                    }
                }
            }

            errorText?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun BlinkContactsImportBottomBar(
    state: BlinkContactsImportUiState,
    onImport: () -> Unit,
    onSkip: (() -> Unit)?
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.importedCount?.let { count ->
                Text(
                    text = stringResource(
                        BlinkContactsRes.string.settings_wallet_details_import_contacts_success,
                        count
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = stringResource(
                    BlinkContactsRes.string.settings_wallet_details_import_contacts_selected,
                    state.selectedCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onImport,
                enabled = state.selectedCount > 0 && !state.isLoading && !state.isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(
                    text = stringResource(
                        BlinkContactsRes.string.settings_wallet_details_import_contacts_import
                    )
                )
            }
            onSkip?.let { skip ->
                TextButton(
                    onClick = skip,
                    enabled = !state.isImporting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            BlinkContactsRes.string.settings_wallet_details_import_contacts_skip
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectAllContactsRow(
    checked: Boolean,
    selectedCount: Int,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val labelColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(
            text = stringResource(
                BlinkContactsRes.string.settings_wallet_details_import_contacts_select_all
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(
                BlinkContactsRes.string.settings_wallet_details_import_contacts_selected,
                selectedCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BlinkContactImportRow(
    item: BlinkContactImportItem,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = if (item.alreadyAdded) {
        stringResource(
            BlinkContactsRes.string.settings_wallet_details_import_contacts_already_added
        )
    } else {
        stringResource(
            BlinkContactsRes.string.settings_wallet_details_import_contacts_transactions,
            item.transactionsCount
        )
    }
    val statusColor = if (item.alreadyAdded) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = if (selected) 3.dp else 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            ImportedContactSummary(
                item = item,
                modifier = Modifier.weight(1f),
                titleColor = titleColor
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun ImportedContactSummary(
    item: BlinkContactImportItem,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingContent: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = item.displayName,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.address,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        supportingContent()
    }
}
