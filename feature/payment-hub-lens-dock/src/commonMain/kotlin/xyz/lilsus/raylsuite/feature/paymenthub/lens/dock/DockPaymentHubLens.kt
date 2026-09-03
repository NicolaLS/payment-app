package xyz.lilsus.raylsuite.feature.paymenthub.lens.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.ui.resources.LocalizedText
import xyz.lilsus.raylsuite.feature.paymenthub.DefaultPaymentHubLensId
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubLensId
import xyz.lilsus.raylsuite.feature.paymenthub.lens.HubItemRenderModel
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubActions
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubLensDefinition
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubLensMetadata
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubRenderState
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubScannerSlot
import xyz.lilsus.raylsuite.feature.paymenthub.lens.dock.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymenthub.lens.dock.generated.resources.dock_lens_add
import xyz.lilsus.raylsuite.feature.paymenthub.lens.dock.generated.resources.dock_lens_description
import xyz.lilsus.raylsuite.feature.paymenthub.lens.dock.generated.resources.dock_lens_library
import xyz.lilsus.raylsuite.feature.paymenthub.lens.dock.generated.resources.dock_lens_name
import xyz.lilsus.raylsuite.feature.paymenthub.lens.dock.generated.resources.dock_lens_open_group
import xyz.lilsus.raylsuite.feature.paymenthub.lens.dock.generated.resources.dock_lens_pay
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubItemGlyph
import xyz.lilsus.raylsuite.feature.paymenthub.ui.amountBadge

/**
 * Scanner-dominant home with a restrained row of pinned targets and groups in manual order.
 * Recents never enter the dock; they live in the library.
 */
object DockPaymentHubLens : PaymentHubLensDefinition {
    override val id: PaymentHubLensId = DefaultPaymentHubLensId

    override val metadata: PaymentHubLensMetadata =
        PaymentHubLensMetadata(
            name = LocalizedText(Res.string.dock_lens_name),
            description = LocalizedText(Res.string.dock_lens_description)
        )

    @Composable
    override fun Preview(modifier: Modifier) {
        DockPreview(modifier)
    }

    @Composable
    override fun Content(
        state: PaymentHubRenderState,
        actions: PaymentHubActions,
        scanner: PaymentHubScannerSlot,
        modifier: Modifier
    ) {
        Column(modifier = modifier.fillMaxSize()) {
            scanner.Content(modifier = Modifier.fillMaxWidth().weight(1f))
            DockRow(
                items = state.pinnedItems,
                actions = actions,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DockRow(
    items: List<HubItemRenderModel>,
    actions: PaymentHubActions,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 2.dp
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            items(items, key = { it.id.value }) { item ->
                DockItem(item = item, actions = actions)
            }
            item(key = "library") {
                DockLibraryItem(
                    showAddLabel = items.isEmpty(),
                    onClick = actions::openLibrary
                )
            }
        }
    }
}

@Composable
private fun DockItem(item: HubItemRenderModel, actions: PaymentHubActions) {
    val description =
        stringResource(
            if (item.isGroup) Res.string.dock_lens_open_group else Res.string.dock_lens_pay,
            item.title
        )
    Column(
        modifier =
            Modifier
                .width(DOCK_ITEM_WIDTH)
                .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = {
                if (item.isGroup) actions.openGroup(item.id) else actions.selectItem(item.id)
            },
            enabled = item.enabled,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            HubItemGlyph(item = item, size = DOCK_GLYPH_SIZE)
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
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
}

@Composable
private fun DockLibraryItem(showAddLabel: Boolean, onClick: () -> Unit) {
    val label =
        stringResource(if (showAddLabel) Res.string.dock_lens_add else Res.string.dock_lens_library)
    Column(
        modifier = Modifier.width(DOCK_ITEM_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Box(modifier = Modifier.size(DOCK_GLYPH_SIZE), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (showAddLabel) Icons.Filled.Add else Icons.Filled.Apps,
                    contentDescription = label
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DockPreview(modifier: Modifier) {
    Column(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surface,
            RoundedCornerShape(12.dp)
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
                    )
                    .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            repeat(4) {
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.height(0.dp))
    }
}

private val DOCK_ITEM_WIDTH = 72.dp
private val DOCK_GLYPH_SIZE = 52.dp
