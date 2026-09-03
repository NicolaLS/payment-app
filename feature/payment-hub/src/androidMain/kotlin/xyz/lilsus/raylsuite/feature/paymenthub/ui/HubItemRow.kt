package xyz.lilsus.raylsuite.feature.paymenthub.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.pluralStringResource
import xyz.lilsus.raylsuite.core.ui.components.AppListRow
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_member_count
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubItemDetail
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubItemRenderModel

/** Standard list row for a hub item: glyph, title, subtitle, and preset badge. */
@Composable
fun HubItemRow(
    item: HubItemRenderModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = item.enabled,
    testTag: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    AppListRow(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        testTag = testTag
    ) {
        HubItemGlyph(item = item)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.subtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        item.amountBadge()?.let { badge ->
            Text(
                text = badge,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        trailingContent?.invoke(this)
    }
}

@Composable
fun HubItemRenderModel.subtitle(): String = when (val detail = detail) {
    is HubItemDetail.Target -> detail.address

    is HubItemDetail.Group ->
        pluralStringResource(
            Res.plurals.hub_group_member_count,
            detail.memberCount,
            detail.memberCount
        )
}
