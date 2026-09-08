package xyz.lilsus.raylsuite.feature.paymenthub.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOffer
import xyz.lilsus.raylsuite.feature.paymenthub.HubWidgetTile
import xyz.lilsus.raylsuite.feature.paymenthub.R

@Composable
internal fun HubServiceWidgetFace(
    tile: HubWidgetTile,
    preview: Boolean,
    interactive: Boolean,
    onOpen: (String?) -> Unit
) {
    val offerKind = if (tile.variant.template == "service-topup") "topup" else "package"
    val hasOffers = tile.service?.offers?.any { it.kind == offerKind } == true
    if (!preview && (tile.loading || tile.unavailable || !hasOffers)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize().padding(14.dp)
        ) {
            Text(
                tile.title ?: stringResource(R.string.hub_service_title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            if (tile.loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            Text(
                stringResource(
                    if (tile.loading) {
                        R.string.hub_widget_loading
                    } else {
                        R.string.hub_widget_unavailable
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
        return
    }
    if (tile.variant.template == "service-topup") {
        TopupFace(tile, interactive, onOpen)
    } else {
        PackagesFace(tile, preview, interactive, onOpen)
    }
}

@Composable
private fun TopupFace(tile: HubWidgetTile, interactive: Boolean, onOpen: (String?) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().then(
            if (interactive) Modifier.clickable(role = Role.Button) { onOpen(null) } else Modifier
        ).padding(12.dp)
    ) {
        Icon(
            Icons.Filled.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            tile.title ?: tile.service?.title ?: stringResource(R.string.hub_service_title),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        ServicePhone(tile)
        Text(
            stringResource(R.string.hub_service_topup),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PackagesFace(
    tile: HubWidgetTile,
    preview: Boolean,
    interactive: Boolean,
    onOpen: (String?) -> Unit
) {
    val rows = if (tile.variant.rows > 1) 2 else 1
    val offers = tile.service?.offers.orEmpty().filter { it.kind == "package" }
    val visible = offers.take(rows * 2 - 1)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize().padding(12.dp)
    ) {
        Text(
            tile.title ?: tile.service?.title ?: stringResource(R.string.hub_service_title),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        ServicePhone(tile)
        repeat(rows) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                repeat(2) { column ->
                    val index = row * 2 + column
                    val offer = visible.getOrNull(index)
                    val more = index == rows * 2 - 1
                    if (offer != null || preview || more) {
                        PackageCell(
                            offer = offer,
                            more = more,
                            interactive = interactive,
                            onClick = { onOpen(offer?.id) },
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ServicePhone(tile: HubWidgetTile) {
    Text(
        tile.servicePhone.ifBlank { stringResource(R.string.hub_service_phone) },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PackageCell(
    offer: HubServiceOffer?,
    more: Boolean,
    interactive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val locale = serviceLocale()
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.then(
            if (interactive) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                if (more) {
                    stringResource(R.string.hub_new_more)
                } else {
                    offer?.title ?: stringResource(R.string.hub_service_packages)
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            offer?.amount?.let { amount ->
                Text(
                    amount.display(locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
