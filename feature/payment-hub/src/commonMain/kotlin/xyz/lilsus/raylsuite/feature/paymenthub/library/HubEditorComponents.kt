package xyz.lilsus.raylsuite.feature.paymenthub.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.feature.paymenthub.HubAccent
import xyz.lilsus.raylsuite.feature.paymenthub.HubIcon
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_appearance_accent
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_appearance_icon
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_appearance_none
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_pin_description
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_pin_label
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubGlyph
import xyz.lilsus.raylsuite.feature.paymenthub.ui.containerColor
import xyz.lilsus.raylsuite.feature.paymenthub.ui.contentColor

@Composable
internal fun EditorSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 8.dp)
    )
}

@Composable
internal fun EditorErrorText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

/** Bundled icon and suite-accent selection shared by both editors. */
@Composable
internal fun AppearancePickers(
    icon: HubIcon?,
    accent: HubAccent?,
    previewText: String,
    onIconSelected: (HubIcon?) -> Unit,
    onAccentSelected: (HubAccent?) -> Unit
) {
    val noneLabel = stringResource(Res.string.hub_appearance_none)
    EditorSectionTitle(stringResource(Res.string.hub_appearance_icon))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "none") {
            SelectableGlyph(
                selected = icon == null,
                onClick = { onIconSelected(null) },
                label = noneLabel
            ) {
                HubGlyph(icon = null, accent = accent, fallbackText = previewText, size = 44.dp)
            }
        }
        items(HubIcon.entries, key = { it.storedValue }) { option ->
            SelectableGlyph(
                selected = icon == option,
                onClick = { onIconSelected(option) },
                label = option.storedValue
            ) {
                HubGlyph(icon = option, accent = accent, fallbackText = previewText, size = 44.dp)
            }
        }
    }
    EditorSectionTitle(stringResource(Res.string.hub_appearance_accent))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "none") {
            SelectableGlyph(
                selected = accent == null,
                onClick = { onAccentSelected(null) },
                label = noneLabel
            ) {
                AccentSwatch(
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    selected =
                        accent == null
                )
            }
        }
        items(HubAccent.entries, key = { it.storedValue }) { option ->
            SelectableGlyph(
                selected = accent == option,
                onClick = { onAccentSelected(option) },
                label = option.storedValue
            ) {
                AccentSwatch(
                    container = option.containerColor(),
                    selected = accent == option,
                    check = option.contentColor()
                )
            }
        }
    }
}

@Composable
private fun SelectableGlyph(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    content: @Composable () -> Unit
) {
    val outline = MaterialTheme.colorScheme.primary
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label }
                .then(
                    if (selected) {
                        Modifier.border(BorderStroke(2.dp, outline), CircleShape)
                    } else {
                        Modifier
                    }
                ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun AccentSwatch(
    container: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    check: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Box(
        modifier = Modifier.size(40.dp).background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = check,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun PinToggleRow(pinned: Boolean, onPinnedChange: (Boolean) -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = pinned, role = Role.Switch, onValueChange = onPinnedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = stringResource(Res.string.hub_pin_label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.hub_pin_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = pinned, onCheckedChange = null)
    }
}

@Composable
internal fun RemoveIcon() {
    Icon(imageVector = Icons.Filled.Close, contentDescription = null)
}
