package xyz.lilsus.raylsuite.feature.paymenthub.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import xyz.lilsus.raylsuite.feature.paymenthub.HubAccent
import xyz.lilsus.raylsuite.feature.paymenthub.HubIcon
import xyz.lilsus.raylsuite.feature.paymenthub.R
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubMark
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubMarkView
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
    val noneLabel = stringResource(R.string.hub_appearance_none)
    EditorSectionTitle(stringResource(R.string.hub_appearance_icon))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "none") {
            SelectableGlyph(
                selected = icon == null,
                onClick = { onIconSelected(null) },
                label = noneLabel
            ) {
                HubMarkView(mark = HubMark(previewText, null, accent), size = 44.dp)
            }
        }
        items(HubIcon.entries, key = { it.storedValue }) { option ->
            SelectableGlyph(
                selected = icon == option,
                onClick = { onIconSelected(option) },
                label = option.storedValue
            ) {
                HubMarkView(mark = HubMark(previewText, option, accent), size = 44.dp)
            }
        }
    }
    EditorSectionTitle(stringResource(R.string.hub_appearance_accent))
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
internal fun RemoveIcon() {
    Icon(imageVector = Icons.Filled.Close, contentDescription = null)
}
