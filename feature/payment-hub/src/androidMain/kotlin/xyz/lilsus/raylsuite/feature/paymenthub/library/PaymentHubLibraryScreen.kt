package xyz.lilsus.raylsuite.feature.paymenthub.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.ui.components.AppListDefaults
import xyz.lilsus.raylsuite.core.ui.components.AppListScaffold
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_move_down
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_move_up
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_pin
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_unpin
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_add
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_add_group
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_add_target
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_arrange_pins
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_done
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_empty_body
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_empty_title
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_no_matches
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_search
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_section_groups
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_section_pinned
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_section_recent
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_section_targets
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_title
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubTestTags
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubItemRenderModel
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubItemRow

/**
 * The canonical management surface every lens and settings can open. It never starts a
 * payment; selecting an item opens its editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHubLibraryScreen(
    state: PaymentHubLibraryUiState,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onAddTarget: () -> Unit,
    onAddGroup: () -> Unit,
    onOpenItem: (HubItemId) -> Unit,
    onSetPinned: (HubItemId, Boolean) -> Unit,
    onMovePinned: (HubItemId, Int) -> Unit,
    onToggleArrangePins: () -> Unit,
    modifier: Modifier = Modifier,
    additionalActions: @Composable ColumnScope.() -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var addMenuOpen by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.testTag(PaymentHubTestTags.LIBRARY),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.hub_library_title)) },
                navigationIcon = { BackIconButton(onClick = onBack) },
                actions = {
                    if (state.pinned.isNotEmpty() || state.arrangingPins) {
                        IconButton(onClick = onToggleArrangePins) {
                            Icon(
                                imageVector = Icons.Filled.SwapVert,
                                contentDescription =
                                    stringResource(
                                        if (state.arrangingPins) {
                                            Res.string.hub_library_done
                                        } else {
                                            Res.string.hub_library_arrange_pins
                                        }
                                    ),
                                tint =
                                    if (state.arrangingPins) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { addMenuOpen = true },
                            modifier = Modifier.testTag(PaymentHubTestTags.LIBRARY_ADD)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(Res.string.hub_library_add)
                            )
                        }
                        DropdownMenu(
                            expanded = addMenuOpen,
                            onDismissRequest = { addMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.hub_library_add_target)) },
                                onClick = {
                                    addMenuOpen = false
                                    onAddTarget()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.hub_library_add_group)) },
                                onClick = {
                                    addMenuOpen = false
                                    onAddGroup()
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .navigationBarsPadding()
                    .padding(AppListDefaults.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            additionalActions()
            if (state.isEmpty) {
                EmptyLibrary(onAddTarget = onAddTarget, modifier = Modifier.weight(1f))
            } else {
                AppListScaffold(
                    isEmpty = !state.hasMatches,
                    emptyMessage = stringResource(Res.string.hub_library_no_matches),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    showSearchBar = true,
                    searchQuery = state.query,
                    onSearchQueryChange = onSearchChange,
                    searchLabel = stringResource(Res.string.hub_library_search)
                ) {
                    section(
                        key = "pinned",
                        title = Res.string.hub_library_section_pinned,
                        items = state.pinned
                    ) { item, index ->
                        HubItemRow(
                            item = item,
                            onClick = { onOpenItem(item.id) },
                            enabled = true,
                            testTag = PaymentHubTestTags.item(item.id)
                        ) {
                            if (state.arrangingPins) {
                                IconButton(
                                    onClick = { onMovePinned(item.id, -1) },
                                    enabled = index > 0
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowUpward,
                                        contentDescription =
                                            stringResource(Res.string.hub_action_move_up)
                                    )
                                }
                                IconButton(
                                    onClick = { onMovePinned(item.id, 1) },
                                    enabled = index < state.pinned.lastIndex
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDownward,
                                        contentDescription =
                                            stringResource(Res.string.hub_action_move_down)
                                    )
                                }
                            }
                            PinToggle(pinned = true, onPinnedChange = { onSetPinned(item.id, it) })
                        }
                    }
                    section(
                        key = "groups",
                        title = Res.string.hub_library_section_groups,
                        items = state.groups
                    ) { item, _ ->
                        HubItemRow(
                            item = item,
                            onClick = { onOpenItem(item.id) },
                            enabled = true,
                            testTag = PaymentHubTestTags.item(item.id)
                        ) {
                            PinToggle(
                                pinned = item.pinned,
                                onPinnedChange = { onSetPinned(item.id, it) }
                            )
                        }
                    }
                    section(
                        key = "recent",
                        title = Res.string.hub_library_section_recent,
                        items = state.recent
                    ) { item, _ ->
                        HubItemRow(item = item, onClick = { onOpenItem(item.id) })
                    }
                    section(
                        key = "targets",
                        title = Res.string.hub_library_section_targets,
                        items = state.targets
                    ) { item, _ ->
                        HubItemRow(
                            item = item,
                            onClick = { onOpenItem(item.id) },
                            testTag = PaymentHubTestTags.item(item.id)
                        ) {
                            PinToggle(
                                pinned = item.pinned,
                                onPinnedChange = { onSetPinned(item.id, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.section(
    key: String,
    title: StringResource,
    items: List<HubItemRenderModel>,
    row: @Composable (HubItemRenderModel, Int) -> Unit
) {
    if (items.isEmpty()) return
    item(key = "section:$key") {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    items(items.withIndex().toList(), key = { "$key:${it.value.id.value}" }) { (index, item) ->
        row(item, index)
    }
}

@Composable
private fun PinToggle(pinned: Boolean, onPinnedChange: (Boolean) -> Unit) {
    IconToggleButton(checked = pinned, onCheckedChange = onPinnedChange) {
        Icon(
            imageVector = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
            contentDescription =
                stringResource(
                    if (pinned) Res.string.hub_action_unpin else Res.string.hub_action_pin
                ),
            tint =
                if (pinned) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
        )
    }
}

@Composable
private fun EmptyLibrary(onAddTarget: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(Res.string.hub_library_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(Res.string.hub_library_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onAddTarget) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(Res.string.hub_library_add_target),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
