package xyz.lilsus.raylsuite.feature.paymenthub.canvas

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.R
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubTestTags
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubTileMemberRenderModel
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubTileRenderModel
import xyz.lilsus.raylsuite.feature.paymenthub.render.allowedTileSizes
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubMarkView
import xyz.lilsus.raylsuite.feature.paymenthub.ui.amountColor
import xyz.lilsus.raylsuite.feature.paymenthub.ui.amountText

/**
 * The hub canvas: a two-column grid of payment targets. Tapping a leaf starts its payment, tapping
 * a closed container opens it in place, and rearrange mode lets tiles move without changing them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubCanvasScreen(
    state: HubCanvasUiState,
    actions: HubCanvasActions,
    modifier: Modifier = Modifier
) {
    var pendingRemoval by remember { mutableStateOf<HubTileRenderModel?>(null) }

    Scaffold(modifier = modifier.testTag(PaymentHubTestTags.CANVAS)) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
        ) {
            HubCanvasGrid(
                tiles = state.tiles,
                editing = state.editing,
                actions = actions,
                onRequestRemoval = { pendingRemoval = it },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(
                            start = HubGrid.GUTTER.dp,
                            top = if (state.hasItems) EDIT_BUTTON_CLEARANCE else 12.dp,
                            end = HubGrid.GUTTER.dp,
                            bottom = 12.dp
                        )
            )
            state.message?.let { message ->
                CanvasToast(
                    message = message,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                )
            }
            if (state.hasItems) {
                CanvasEditButton(
                    editing = state.editing,
                    onClick = {
                        if (state.editing) actions.stopEditing() else actions.startEditing()
                    },
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = HubGrid.GUTTER.dp)
                )
            }
        }
    }

    pendingRemoval?.let { tile ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.hub_canvas_remove_title, tile.label)) },
            text = { Text(stringResource(R.string.hub_canvas_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.delete(tile.id)
                        pendingRemoval = null
                    }
                ) {
                    Text(stringResource(R.string.hub_canvas_remove_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.hub_canvas_remove_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CanvasEditButton(editing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label =
        stringResource(if (editing) R.string.hub_canvas_done else R.string.hub_canvas_edit)
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.testTag(PaymentHubTestTags.CANVAS_EDIT)
        ) {
            Icon(
                imageVector = if (editing) Icons.Filled.Check else Icons.Filled.Edit,
                contentDescription = label
            )
        }
    }
}

/** What a canvas tile can ask the host to do. Every one of these is a plain UI intent. */
data class HubCanvasActions(
    val pay: (HubItemId) -> Unit,
    val expand: (HubItemId) -> Unit,
    val edit: (HubItemId) -> Unit,
    val addTarget: () -> Unit,
    val startEditing: () -> Unit,
    val stopEditing: () -> Unit,
    val resize: (HubItemId, CanvasTileSize) -> Unit,
    val delete: (HubItemId) -> Unit,
    val move: (HubItemId, HubItemId) -> Unit
)

private sealed interface CanvasEntry {
    val span: HubGridSpan

    data class Item(val tile: HubTileRenderModel) : CanvasEntry {
        override val span: HubGridSpan
            get() = HubGridSpan(tile.columns, tile.rows)
    }

    data object AddTarget : CanvasEntry {
        override val span: HubGridSpan
            get() = HubGridSpan(1, 1)
    }
}

private data class DropResolution(val targetId: HubItemId)

@Composable
private fun HubCanvasGrid(
    tiles: List<HubTileRenderModel>,
    editing: Boolean,
    actions: HubCanvasActions,
    onRequestRemoval: (HubTileRenderModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val gap = HubGrid.GAP.dp
    val rowHeight = HubGrid.ROW_HEIGHT.dp
    val entries =
        remember(tiles) {
            tiles.map<HubTileRenderModel, CanvasEntry>(CanvasEntry::Item) + CanvasEntry.AddTarget
        }
    val placements = remember(entries) { packHubGrid(entries) { it.span } }
    val rows = placements.gridRowCount()

    var draggingId by remember { mutableStateOf<HubItemId?>(null) }
    var resolution by remember { mutableStateOf<DropResolution?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(modifier = modifier) {
        val columnWidth = (maxWidth - gap * (HubGrid.COLUMNS - 1)) / HubGrid.COLUMNS
        val density = LocalDensity.current
        val columnWidthPx = with(density) { columnWidth.toPx() }
        val rowHeightPx = with(density) { rowHeight.toPx() }
        val gapPx = with(density) { gap.toPx() }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight * rows + gap * (rows - 1).coerceAtLeast(0))
        ) {
            placements.forEach { placement ->
                val entry = placement.value
                val entryKey =
                    when (entry) {
                        CanvasEntry.AddTarget -> "add-target"
                        is CanvasEntry.Item -> entry.tile.id.value
                    }
                key(entryKey) {
                    val tileModifier =
                        Modifier
                            .offset(
                                x = (columnWidth + gap) * placement.column,
                                y = (rowHeight + gap) * placement.row
                            )
                            .width(columnWidth * placement.columns + gap * (placement.columns - 1))
                            .height(rowHeight * placement.rows + gap * (placement.rows - 1))

                    when (entry) {
                        CanvasEntry.AddTarget ->
                            AddTargetTile(onClick = actions.addTarget, modifier = tileModifier)

                        is CanvasEntry.Item -> {
                            val dragging = entry.tile.id == draggingId
                            CanvasTile(
                                tile = entry.tile,
                                editing = editing,
                                dragging = dragging,
                                dragOffset = if (dragging) dragOffset else Offset.Zero,
                                resolution =
                                    resolution?.takeIf { it.targetId == entry.tile.id },
                                actions = actions,
                                onRequestRemoval = { onRequestRemoval(entry.tile) },
                                dragModifier =
                                    Modifier.dragToArrange(
                                        enabled = editing,
                                        onStart = { position ->
                                            draggingId = entry.tile.id
                                            dragOffset = Offset.Zero
                                            dragStartPosition = position
                                        },
                                        onDrag = { offset ->
                                            dragOffset = offset
                                            resolution =
                                                resolvePlacementDrop(
                                                    pointer =
                                                        Offset(
                                                            x =
                                                                (columnWidthPx + gapPx) *
                                                                    placement.column +
                                                                    dragStartPosition.x +
                                                                    offset.x,
                                                            y =
                                                                (rowHeightPx + gapPx) *
                                                                    placement.row +
                                                                    dragStartPosition.y +
                                                                    offset.y
                                                        ),
                                                    placements = placements,
                                                    draggedId = entry.tile.id,
                                                    columnWidthPx = columnWidthPx,
                                                    rowHeightPx = rowHeightPx,
                                                    gapPx = gapPx
                                                )
                                        },
                                        onEnd = {
                                            resolution?.let { drop ->
                                                actions.move(entry.tile.id, drop.targetId)
                                            }
                                            draggingId = null
                                            resolution = null
                                            dragOffset = Offset.Zero
                                        }
                                    ),
                                modifier = tileModifier.zIndex(if (dragging) 1f else 0f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The grid deliberately does not reflow during a drag. The hovered tile is highlighted and takes
 * the dragged tile's place only after release.
 */
private fun resolvePlacementDrop(
    pointer: Offset,
    placements: List<HubGridPlacement<CanvasEntry>>,
    draggedId: HubItemId,
    columnWidthPx: Float,
    rowHeightPx: Float,
    gapPx: Float
): DropResolution? {
    placements.forEach { placement ->
        val tile = (placement.value as? CanvasEntry.Item)?.tile ?: return@forEach
        if (tile.id == draggedId) return@forEach
        val left = (columnWidthPx + gapPx) * placement.column
        val top = (rowHeightPx + gapPx) * placement.row
        val width = columnWidthPx * placement.columns + gapPx * (placement.columns - 1)
        val height = rowHeightPx * placement.rows + gapPx * (placement.rows - 1)
        if (pointer.x !in left..(left + width) || pointer.y !in top..(top + height)) {
            return@forEach
        }
        return DropResolution(targetId = tile.id)
    }
    return null
}

@Composable
private fun Modifier.dragToArrange(
    enabled: Boolean,
    onStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit
): Modifier {
    if (!enabled) return this
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    return pointerInput(Unit) {
        var total = Offset.Zero
        detectDragGestures(
            onDragStart = { offset ->
                total = Offset.Zero
                start(offset)
            },
            onDrag = { change, amount ->
                change.consume()
                total += amount
                drag(total)
            },
            onDragEnd = { end() },
            onDragCancel = { end() }
        )
    }
}

@Composable
private fun Modifier.jiggle(active: Boolean): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "hubJiggle")
    val angle by transition.animateFloat(
        initialValue = -JIGGLE_DEGREES,
        targetValue = JIGGLE_DEGREES,
        animationSpec =
            infiniteRepeatable(tween(durationMillis = 550), repeatMode = RepeatMode.Reverse),
        label = "hubJiggleAngle"
    )
    return rotate(angle)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CanvasTile(
    tile: HubTileRenderModel,
    editing: Boolean,
    dragging: Boolean,
    dragOffset: Offset,
    resolution: DropResolution?,
    actions: HubCanvasActions,
    onRequestRemoval: () -> Unit,
    dragModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.medium
    val haptics = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }
    val description =
        stringResource(
            when {
                editing -> R.string.hub_canvas_move_item
                tile.isContainer -> R.string.hub_canvas_open_group
                else -> R.string.hub_canvas_pay
            },
            tile.label
        )
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = if (dragging) 1.025f else 1f
                    scaleY = if (dragging) 1.025f else 1f
                }
                .alpha(if (dragging) 0.96f else 1f)
    ) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp,
            shadowElevation = if (dragging) 12.dp else 0.dp,
            modifier =
                Modifier
                    .fillMaxSize()
                    .jiggle(editing)
                    .then(
                        when {
                            resolution == null -> Modifier
                            else -> Modifier.border(2.dp, accent, shape)
                        }
                    )
                    .then(dragModifier)
                    .combinedClickable(
                        hapticFeedbackEnabled = false,
                        onLongClick =
                            if (editing) {
                                null
                            } else {
                                {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuExpanded = true
                                }
                            },
                        onClick = {
                            when {
                                editing -> Unit
                                tile.expandable -> actions.expand(tile.id)
                                tile.isContainer -> Unit
                                else -> actions.pay(tile.id)
                            }
                        }
                    )
                    .testTag(PaymentHubTestTags.item(tile.id))
                    .semantics { contentDescription = description }
        ) {
            if (tile.isContainer) {
                ContainerTileContent(tile = tile, editing = editing, onPay = actions.pay)
            } else {
                LeafTileContent(tile = tile, editing = editing)
            }
        }
        TileActionMenu(
            tile = tile,
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onResize = {
                menuExpanded = false
                actions.resize(tile.id, it)
            },
            onEdit = {
                menuExpanded = false
                actions.edit(tile.id)
            },
            onMove = {
                menuExpanded = false
                actions.startEditing()
            },
            onRemove = {
                menuExpanded = false
                onRequestRemoval()
            }
        )
    }
}

@Composable
private fun TileActionMenu(
    tile: HubTileRenderModel,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onResize: (CanvasTileSize) -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit
) {
    val sizes =
        remember(tile.isContainer, tile.memberCount) {
            allowedTileSizes(tile.isContainer, tile.memberCount)
        }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(TILE_MENU_WIDTH)
    ) {
        Text(
            text = stringResource(R.string.hub_configure_size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 6.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            CanvasTileSize.entries.forEach { size ->
                TileSizeButton(
                    size = size,
                    selected = size == tile.storedSize,
                    enabled = size in sizes,
                    onClick = { onResize(size) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        DropdownMenuItem(
            text = { Text(stringResource(R.string.hub_canvas_edit)) },
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
            onClick = onEdit
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.hub_canvas_move)) },
            leadingIcon = { Icon(Icons.Filled.OpenWith, contentDescription = null) },
            onClick = onMove
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.hub_canvas_remove_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = onRemove
        )
    }
}

@Composable
private fun TileSizeButton(
    size: CanvasTileSize,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor =
        when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        onClick = onClick,
        enabled = enabled && !selected,
        shape = MaterialTheme.shapes.small,
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        modifier = modifier.height(TILE_SIZE_BUTTON_HEIGHT)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            TileSizeGlyph(size = size, color = contentColor)
            Text(
                text = "${size.columns} × ${size.rows}",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun TileSizeGlyph(size: CanvasTileSize, color: androidx.compose.ui.graphics.Color) {
    val width = if (size.columns == 1) 17.dp else 29.dp
    val height = if (size.rows == 1) 17.dp else 29.dp
    Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .width(width)
                    .height(height)
                    .border(2.dp, color, MaterialTheme.shapes.extraSmall)
        )
    }
}

@Composable
private fun LeafTileContent(tile: HubTileRenderModel, editing: Boolean) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        HubMarkView(mark = tile.mark, size = 32.dp)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = tile.label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Only a two-row tile has room for the address; at one row the amount line wins the space.
        if (tile.rows >= 2 && tile.subtitle != null && !editing) {
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!editing) {
            tile.amountLine?.let { line ->
                Text(
                    text = line.amountText(),
                    style = MaterialTheme.typography.labelMedium,
                    color = line.amountColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ContainerTileContent(
    tile: HubTileRenderModel,
    editing: Boolean,
    onPay: (HubItemId) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tile.label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (tile.columns >= 2 && !editing) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.hub_group_member_count,
                            tile.memberCount,
                            tile.memberCount
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        when {
            !tile.showsMembers -> {
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tile.members.take(MAX_CLOSED_MARKS).forEach { member ->
                        HubMarkView(mark = member.mark, size = 26.dp)
                    }
                }
            }

            tile.rows == 1 ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp)
                ) {
                    tile.members.forEach { member ->
                        MemberCard(
                            member = member,
                            editing = editing,
                            onClick = { onPay(member.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

            else ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp)
                ) {
                    tile.members.forEach { member ->
                        MemberRow(
                            member = member,
                            editing = editing,
                            onClick = { onPay(member.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
        }
    }
}

@Composable
private fun MemberCard(
    member: HubTileMemberRenderModel,
    editing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MemberSurface(editing = editing, onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            HubMarkView(mark = member.mark, size = 22.dp)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = member.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!editing) {
                Text(
                    text = member.amountLine.amountText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = member.amountLine.amountColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: HubTileMemberRenderModel,
    editing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MemberSurface(editing = editing, onClick = onClick, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .heightIn(min = MEMBER_ROW_MIN_HEIGHT)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            HubMarkView(mark = member.mark, size = 20.dp)
            Text(
                text = member.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!editing) {
                Text(
                    text = member.amountLine.amountText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = member.amountLine.amountColor(),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MemberSurface(
    editing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = !editing,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
        content = content
    )
}

@Composable
private fun AddTargetTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.testTag(PaymentHubTestTags.CANVAS_ADD)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.hub_canvas_add_target),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CanvasToast(message: HubCanvasMessage, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(message.label()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        )
    }
}

private fun HubCanvasMessage.label(): Int = when (this) {
    HubCanvasMessage.Deleted -> R.string.hub_canvas_message_removed
}

private const val MAX_CLOSED_MARKS = 5
private const val JIGGLE_DEGREES = 0.45f
private val EDIT_BUTTON_CLEARANCE = 60.dp
private val MEMBER_ROW_MIN_HEIGHT = 44.dp
private val TILE_MENU_WIDTH = 264.dp
private val TILE_SIZE_BUTTON_HEIGHT = 68.dp
