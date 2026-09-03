package xyz.lilsus.raylsuite.feature.paymenthub.canvas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.lilsus.raylsuite.core.ui.platform.enableTestTagsAsResourceId
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.R
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubTestTags
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubItemRenderModel
import xyz.lilsus.raylsuite.feature.paymenthub.render.PaymentHubRenderState
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubItemGlyph
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubItemRow
import xyz.lilsus.raylsuite.feature.paymenthub.ui.amountBadge

/**
 * The Hub tab: a user-arranged grid of targets and groups. Arrangement is presentation-only and
 * normal mode never moves a tile on its own. Managing the items themselves happens in the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHubCanvasScreen(
    state: PaymentHubRenderState,
    layout: CanvasLayout,
    onSelectItem: (HubItemId) -> Unit,
    onOpenGroup: (HubItemId) -> Unit,
    onOpenLibrary: () -> Unit,
    onUpdateLayout: ((CanvasLayout) -> CanvasLayout) -> Unit,
    onResetLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var arranging by rememberSaveable { mutableStateOf(false) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val tiles =
        remember(layout, state) {
            layout.tiles.mapIndexedNotNull { index, tile ->
                state.item(tile.id)?.let { PlacedTile(index, it, tile.size) }
            }
        }
    val placeable = remember(layout, state) {
        state.allItems.filterNot {
            it.id in
                layout.placedItemIds
        }
    }

    Scaffold(
        modifier = modifier.testTag(PaymentHubTestTags.CANVAS),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.hub_library_title)) },
                actions = {
                    if (arranging) {
                        IconButton(onClick = {
                            showAddSheet = true
                        }, enabled = placeable.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.hub_canvas_add)
                            )
                        }
                        IconButton(onClick = onResetLayout, enabled = layout.tiles.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Filled.RestartAlt,
                                contentDescription = stringResource(R.string.hub_canvas_reset)
                            )
                        }
                        TextButton(onClick = { arranging = false }) {
                            Text(stringResource(R.string.hub_canvas_done))
                        }
                    } else {
                        IconButton(
                            onClick = { arranging = true },
                            enabled = !state.isEmpty,
                            modifier = Modifier.testTag(PaymentHubTestTags.CANVAS_ARRANGE)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = stringResource(R.string.hub_canvas_arrange)
                            )
                        }
                        IconButton(
                            onClick = onOpenLibrary,
                            modifier = Modifier.testTag(PaymentHubTestTags.CANVAS_LIBRARY)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.hub_library_title)
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
        ) {
            when {
                state.isEmpty ->
                    EmptyHub(onAddTarget = onOpenLibrary, modifier = Modifier.fillMaxSize())

                tiles.isEmpty() ->
                    EmptyCanvas(
                        onArrange = {
                            arranging = true
                            showAddSheet = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                else ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = MIN_TILE_WIDTH),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
                        verticalArrangement = Arrangement.spacedBy(TILE_SPACING)
                    ) {
                        items(
                            items = tiles,
                            key = { it.item.id.value },
                            span = { tile ->
                                GridItemSpan(
                                    if (tile.size ==
                                        CanvasTileSize.Wide
                                    ) {
                                        minOf(2, maxLineSpan)
                                    } else {
                                        1
                                    }
                                )
                            }
                        ) { tile ->
                            CanvasItemTile(
                                tile = tile,
                                arranging = arranging,
                                canMoveEarlier = tile.index > 0,
                                canMoveLater = tile.index < layout.tiles.lastIndex,
                                onSelect = {
                                    if (tile.item.isGroup) {
                                        onOpenGroup(tile.item.id)
                                    } else {
                                        onSelectItem(tile.item.id)
                                    }
                                },
                                onMove = { offset ->
                                    onUpdateLayout { it.move(tile.index, offset) }
                                },
                                onResize = {
                                    val next =
                                        if (tile.size == CanvasTileSize.Wide) {
                                            CanvasTileSize.Compact
                                        } else {
                                            CanvasTileSize.Wide
                                        }
                                    onUpdateLayout { it.resize(tile.item.id, next) }
                                },
                                onRemove = { onUpdateLayout { it.remove(tile.item.id) } }
                            )
                        }
                    }
            }
        }
    }

    if (showAddSheet) {
        AddToCanvasSheet(
            candidates = placeable,
            onAdd = { id -> onUpdateLayout { it.place(id) } },
            onDismiss = { showAddSheet = false }
        )
    }
}

private data class PlacedTile(
    val index: Int,
    val item: HubItemRenderModel,
    val size: CanvasTileSize
)

@Composable
private fun CanvasItemTile(
    tile: PlacedTile,
    arranging: Boolean,
    canMoveEarlier: Boolean,
    canMoveLater: Boolean,
    onSelect: () -> Unit,
    onMove: (Int) -> Unit,
    onResize: () -> Unit,
    onRemove: () -> Unit
) {
    val item = tile.item
    val description =
        stringResource(
            if (item.isGroup) R.string.hub_canvas_open_group else R.string.hub_canvas_pay,
            item.title
        )
    Surface(
        onClick = onSelect,
        enabled = item.enabled && !arranging,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier =
            Modifier
                .heightIn(min = TILE_HEIGHT)
                .testTag(PaymentHubTestTags.item(item.id))
                .semantics { contentDescription = description }
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HubItemGlyph(item = item, size = 40.dp)
                if (tile.size == CanvasTileSize.Wide) {
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = item.title,
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        item.amountBadge()?.let { badge ->
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            Box(modifier = Modifier.weight(1f))
            if (tile.size == CanvasTileSize.Compact) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.amountBadge()?.let { badge ->
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (arranging) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onResize, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector =
                                if (tile.size == CanvasTileSize.Wide) {
                                    Icons.Filled.CloseFullscreen
                                } else {
                                    Icons.Filled.OpenInFull
                                },
                            contentDescription =
                                stringResource(
                                    if (tile.size == CanvasTileSize.Wide) {
                                        R.string.hub_canvas_compact
                                    } else {
                                        R.string.hub_canvas_wide
                                    }
                                )
                        )
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.hub_canvas_remove)
                        )
                    }
                    IconButton(
                        onClick = { onMove(-1) },
                        enabled = canMoveEarlier,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.hub_canvas_move_earlier)
                        )
                    }
                    IconButton(
                        onClick = { onMove(1) },
                        enabled = canMoveLater,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.hub_canvas_move_later)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToCanvasSheet(
    candidates: List<HubItemRenderModel>,
    onAdd: (HubItemId) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.enableTestTagsAsResourceId()
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.hub_canvas_add_title),
                style = MaterialTheme.typography.titleLarge
            )
            if (candidates.isEmpty()) {
                Text(
                    text = stringResource(R.string.hub_canvas_add_all_placed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidates, key = { it.id.value }) { item ->
                        HubItemRow(item = item, onClick = { onAdd(item.id) }, enabled = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHub(onAddTarget: () -> Unit, modifier: Modifier = Modifier) {
    EmptyMessage(
        title = stringResource(R.string.hub_library_empty_title),
        body = stringResource(R.string.hub_library_empty_body),
        actionLabel = stringResource(R.string.hub_library_add_target),
        onAction = onAddTarget,
        modifier = modifier
    )
}

@Composable
private fun EmptyCanvas(onArrange: () -> Unit, modifier: Modifier = Modifier) {
    EmptyMessage(
        title = null,
        body = stringResource(R.string.hub_canvas_empty_body),
        actionLabel = stringResource(R.string.hub_canvas_add),
        onAction = onArrange,
        modifier = modifier
    )
}

@Composable
private fun EmptyMessage(
    title: String?,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onAction) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(text = actionLabel, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private val MIN_TILE_WIDTH = 150.dp
private val TILE_SPACING = 12.dp
private val TILE_HEIGHT = 120.dp
