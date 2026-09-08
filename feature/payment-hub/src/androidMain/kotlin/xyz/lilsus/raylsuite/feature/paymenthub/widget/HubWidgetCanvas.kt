package xyz.lilsus.raylsuite.feature.paymenthub.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import xyz.lilsus.raylsuite.feature.paymenthub.HubWidgetTile
import xyz.lilsus.raylsuite.feature.paymenthub.R
import xyz.lilsus.raylsuite.feature.paymenthub.WidgetHubState
import xyz.lilsus.raylsuite.feature.paymenthub.WidgetHubViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubGridSpan
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.gridRowCount
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.packHubGrid

@Composable
internal fun HubWidgetCanvas(
    state: WidgetHubState,
    viewModel: WidgetHubViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            if (state.widgets.isNotEmpty()) {
                TextButton(onClick = { viewModel.setArranging(!state.arranging) }) {
                    Text(
                        stringResource(
                            if (state.arranging) {
                                R.string.hub_canvas_done
                            } else {
                                R.string.hub_canvas_edit
                            }
                        )
                    )
                }
            }
            IconButton(onClick = viewModel::openGallery) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.hub_widget_add))
            }
        }
        if (state.hasServiceOrder) {
            OutlinedButton(
                onClick = viewModel::openPendingServiceOrder,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.hub_service_order_banner))
            }
        }
        state.error?.let { error ->
            Text(
                error.label(state.selectedVariant?.capacity ?: 1),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics {
                    liveRegion =
                        LiveRegionMode.Assertive
                }
            )
        }
        if (state.widgets.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(32.dp)
            ) {
                Text(
                    stringResource(R.string.hub_widget_empty_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.hub_widget_empty_body),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = viewModel::openGallery) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.hub_widget_add))
                }
            }
        } else {
            Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) {
                WidgetGrid(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth()
                        .verticalScroll(
                            rememberScrollState()
                        ).padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun WidgetGrid(state: WidgetHubState, viewModel: WidgetHubViewModel, modifier: Modifier) {
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dropId by remember { mutableStateOf<String?>(null) }
    val gap = 12.dp
    BoxWithConstraints(modifier) {
        val columns = if (maxWidth >= 700.dp) 4 else 2
        val unit = (maxWidth - gap * (columns - 1)) / columns
        val placements = remember(state.widgets, columns) {
            packHubGrid(state.widgets, columns) { HubGridSpan(it.variant.columns, it.variant.rows) }
        }
        val rows = placements.gridRowCount()
        val density = LocalDensity.current
        val stridePx = with(density) { (unit + gap).toPx() }
        val gapPx = with(density) { gap.toPx() }
        Box(Modifier.fillMaxWidth().height(unit * rows + gap * (rows - 1).coerceAtLeast(0))) {
            placements.forEachIndexed { index, placement ->
                val tile = placement.value
                key(tile.id) {
                    val dragging = draggingId == tile.id
                    val dragModifier = Modifier.widgetDrag(
                        onStart = { offset ->
                            viewModel.setArranging(true)
                            draggingId = tile.id
                            dragStart = offset
                            dragOffset = Offset.Zero
                            dropId = null
                        },
                        onDrag = { offset ->
                            dragOffset = offset
                            val point =
                                Offset(stridePx * placement.column, stridePx * placement.row) +
                                    dragStart +
                                    offset
                            dropId = placements.firstOrNull { other ->
                                val left = stridePx * other.column
                                val top = stridePx * other.row
                                val right = stridePx * (other.column + other.columns) - gapPx
                                val bottom = stridePx * (other.row + other.rows) - gapPx
                                other.value.id != tile.id &&
                                    point.x in left..right && point.y in top..bottom
                            }?.value?.id
                        },
                        onEnd = {
                            dropId?.let { viewModel.moveWidget(tile.id, it) }
                            draggingId = null
                            dragOffset = Offset.Zero
                            dropId = null
                        },
                        onCancel = {
                            draggingId = null
                            dragOffset = Offset.Zero
                            dropId = null
                        }
                    )
                    WidgetCanvasTile(
                        tile = tile,
                        arranging = state.arranging,
                        canMoveUp = index > 0,
                        canMoveDown = index < placements.lastIndex,
                        dropTarget = dropId == tile.id,
                        viewModel = viewModel,
                        modifier = Modifier.offset(
                            x = (unit + gap) * placement.column,
                            y =
                                (unit + gap) * placement.row
                        )
                            .width(unit * placement.columns + gap * (placement.columns - 1))
                            .height(unit * placement.rows + gap * (placement.rows - 1))
                            .zIndex(if (dragging) 2f else 0f)
                            .graphicsLayer {
                                translationX = if (dragging) dragOffset.x else 0f
                                translationY = if (dragging) dragOffset.y else 0f
                                scaleX = if (dragging) 1.03f else 1f
                                scaleY = if (dragging) 1.03f else 1f
                                alpha = if (dragging) 0.9f else 1f
                            }.then(dragModifier)
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetCanvasTile(
    tile: HubWidgetTile,
    arranging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    dropTarget: Boolean,
    viewModel: WidgetHubViewModel,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val edit = stringResource(R.string.hub_widget_edit)
    val remove = stringResource(R.string.hub_configure_delete)
    val moveUp = stringResource(R.string.hub_action_move_up)
    val moveDown = stringResource(R.string.hub_action_move_down)
    Box(
        modifier = modifier.semantics {
            customActions = buildList {
                add(
                    CustomAccessibilityAction(edit) {
                        viewModel.editWidget(tile.id)
                        true
                    }
                )
                add(
                    CustomAccessibilityAction(remove) {
                        viewModel.removeWidget(tile.id)
                        true
                    }
                )
                if (canMoveUp) {
                    add(
                        CustomAccessibilityAction(moveUp) {
                            viewModel.moveWidgetBy(tile.id, -1)
                            true
                        }
                    )
                }
                if (canMoveDown) {
                    add(
                        CustomAccessibilityAction(moveDown) {
                            viewModel.moveWidgetBy(tile.id, 1)
                            true
                        }
                    )
                }
            }
        }.then(
            if (dropTarget) {
                Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.shapes.extraLarge
                )
            } else {
                Modifier
            }
        )
    ) {
        HubWidgetFace(
            tile,
            onPay = viewModel::pay,
            modifier = Modifier.fillMaxSize(),
            interactive = !arranging,
            onOpenService = { offerId -> viewModel.openService(tile.id, offerId) }
        )
        if (arranging) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            ) {
                Box {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.MoreHoriz,
                            contentDescription = stringResource(R.string.hub_widget_options)
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(edit) }, onClick = {
                            expanded = false
                            viewModel.editWidget(tile.id)
                        })
                        if (tile.unavailable) {
                            DropdownMenuItem(text = {
                                Text(stringResource(R.string.hub_widget_retry))
                            }, onClick = {
                                expanded =
                                    false
                                viewModel.refreshContent()
                            })
                        }
                        DropdownMenuItem(text = { Text(moveUp) }, enabled = canMoveUp, onClick = {
                            expanded =
                                false
                            viewModel.moveWidgetBy(tile.id, -1)
                        })
                        DropdownMenuItem(text = {
                            Text(moveDown)
                        }, enabled = canMoveDown, onClick = {
                            expanded =
                                false
                            viewModel.moveWidgetBy(tile.id, 1)
                        })
                        DropdownMenuItem(text = { Text(remove) }, onClick = {
                            expanded = false
                            viewModel.removeWidget(tile.id)
                        })
                    }
                }
            }
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = stringResource(R.string.hub_canvas_move),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp).size(16.dp)
            )
        }
    }
}

@Composable
private fun Modifier.widgetDrag(
    onStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit
): Modifier {
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    return pointerInput(Unit) {
        var total = Offset.Zero
        detectDragGesturesAfterLongPress(
            onDragStart = {
                total = Offset.Zero
                start(it)
            },
            onDrag = { change, delta ->
                change.consume()
                total += delta
                drag(total)
            },
            onDragEnd = { end() },
            onDragCancel = { cancel() }
        )
    }
}
