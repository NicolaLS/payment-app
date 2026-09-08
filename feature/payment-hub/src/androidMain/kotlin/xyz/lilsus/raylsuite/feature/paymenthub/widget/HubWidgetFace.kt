package xyz.lilsus.raylsuite.feature.paymenthub.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.paymenthub.HubWidgetKind
import xyz.lilsus.raylsuite.feature.paymenthub.HubWidgetPerson
import xyz.lilsus.raylsuite.feature.paymenthub.HubWidgetTile
import xyz.lilsus.raylsuite.feature.paymenthub.R

/** The same native widget face is used in the gallery preview and on the personal canvas. */
@Composable
internal fun HubWidgetFace(
    tile: HubWidgetTile,
    onPay: (String) -> Unit,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
    interactive: Boolean = true,
    onOpenService: (String?) -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier
    ) {
        when {
            tile.kind == HubWidgetKind.Metric -> MetricFace(tile)

            tile.kind == HubWidgetKind.Service -> HubServiceWidgetFace(
                tile = tile,
                preview = preview,
                interactive = interactive,
                onOpen = onOpenService
            )

            tile.variant.capacity == 1 -> SingleContactFace(tile, onPay, preview, interactive)

            else -> ContactCollectionFace(tile, onPay, preview, interactive)
        }
    }
}

@Composable
private fun SingleContactFace(
    tile: HubWidgetTile,
    onPay: (String) -> Unit,
    preview: Boolean,
    interactive: Boolean
) {
    val person = tile.people.firstOrNull()
    val amount = person?.amount?.let {
        rememberAmountFormatter().format(
            DisplayAmount(it.minor, CurrencyCatalog.infoFor(it.normalizedCurrencyCode).currency)
        )
    }
    val label = person?.title ?: tile.title ?: tile.kind.label()
    val payLabel = stringResource(R.string.hub_canvas_pay, label)
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().then(
            if (interactive && person != null) {
                Modifier.clickable(role = Role.Button) { onPay(person.actionId) }
                    .semantics { contentDescription = payLabel }
            } else {
                Modifier
            }
        ).padding(12.dp)
    ) {
        val avatarSize = (maxHeight * 0.35f).coerceIn(28.dp, 56.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ContactAvatar(
                title = person?.title,
                size = avatarSize
            )
            Text(
                text = tile.title?.takeIf { it.isNotBlank() } ?: label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = amount ?: if (preview && tile.kind == HubWidgetKind.Shortcut) {
                    stringResource(R.string.hub_widget_shortcut_amount)
                } else {
                    stringResource(R.string.hub_amount_ask)
                },
                style = if (amount != null) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.labelMedium
                },
                color = if (amount != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ContactCollectionFace(
    tile: HubWidgetTile,
    onPay: (String) -> Unit,
    preview: Boolean,
    interactive: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().padding(14.dp)
    ) {
        Text(
            text = tile.title?.takeIf { it.isNotBlank() } ?: tile.kind.label(),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (tile.people.isEmpty() && !preview) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = stringResource(
                        when (tile.kind) {
                            HubWidgetKind.Recents -> R.string.hub_widget_recent_empty
                            HubWidgetKind.Favorites -> R.string.hub_widget_favorites_empty
                            else -> R.string.hub_widget_contacts_empty
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val columns = if (tile.variant.rows == 1) tile.variant.capacity else 3
            val rowCount = if (tile.variant.rows == 1) 1 else 2
            val people = tile.people.take(tile.variant.capacity)
            repeat(rowCount) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    repeat(columns) { column ->
                        val person = people.getOrNull(row * columns + column)
                        if (person != null || preview) {
                            ContactCell(
                                person = person,
                                onPay = onPay,
                                interactive = interactive,
                                modifier = Modifier.weight(1f).fillMaxSize()
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactCell(
    person: HubWidgetPerson?,
    onPay: (String) -> Unit,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    val payLabel = person?.let { stringResource(R.string.hub_canvas_pay, it.title) }
    BoxWithConstraints(
        modifier = modifier.clip(MaterialTheme.shapes.medium).then(
            if (person != null && interactive) {
                Modifier.clickable(role = Role.Button) { onPay(person.actionId) }
                    .semantics { contentDescription = payLabel.orEmpty() }
            } else {
                Modifier
            }
        ),
        contentAlignment = Alignment.Center
    ) {
        val textHeight = (if (person?.amount != null) 34.dp else 18.dp) *
            LocalDensity.current.fontScale
        val avatarSize = minOf(maxWidth * 0.7f, maxHeight - textHeight - 10.dp)
            .coerceIn(24.dp, 52.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ContactAvatar(
                title = person?.title,
                size = avatarSize
            )
            if (person != null) {
                Text(
                    text = person.title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                person.amount?.let { amount ->
                    Text(
                        text = rememberAmountFormatter().format(
                            DisplayAmount(
                                amount.minor,
                                CurrencyCatalog.infoFor(amount.normalizedCurrencyCode).currency
                            )
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Box(
                    Modifier.fillMaxWidth(0.7f).height(7.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
internal fun ContactAvatar(title: String?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    )
    val color = colors[(title?.hashCode()?.and(Int.MAX_VALUE) ?: 0) % colors.size]
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size).background(color.first, CircleShape)
    ) {
        if (title.isNullOrBlank()) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = color.second,
                modifier = Modifier.size(size * 0.55f)
            )
        } else {
            val initials = title.trim().split(Regex("\\s+")).take(2).joinToString("") {
                it.substring(0, it.offsetByCodePoints(0, 1)).uppercase()
            }
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = color.second,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MetricFace(tile: HubWidgetTile) {
    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(18.dp)
    ) {
        Text(
            tile.title ?: tile.kind.label(),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        when {
            tile.loading -> Text(
                stringResource(R.string.hub_widget_loading),
                style = MaterialTheme.typography.bodySmall
            )

            tile.unavailable -> Text(
                stringResource(R.string.hub_widget_unavailable),
                style = MaterialTheme.typography.bodySmall
            )

            tile.metric != null -> {
                Text(
                    tile.metric.value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    tile.metric.unit,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    tile.metric.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            else -> Text("—", style = MaterialTheme.typography.headlineMedium, color = Color.Gray)
        }
    }
}
