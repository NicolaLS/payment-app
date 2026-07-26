package xyz.lilsus.blip.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import rayl_suite.blip.shared.generated.resources.Res
import rayl_suite.blip.shared.generated.resources.contacts_role_favorite
import rayl_suite.blip.shared.generated.resources.contacts_role_merchants
import rayl_suite.blip.shared.generated.resources.contacts_role_people
import rayl_suite.blip.shared.generated.resources.contacts_search_label
import xyz.lilsus.blip.domain.model.ContactRole

data class ContactListEntry(
    val id: String,
    val displayName: String,
    val address: String,
    val roles: Set<ContactRole> = emptySet()
)

@Composable
fun ContactListContent(
    contacts: List<ContactListEntry>,
    onContactClick: (ContactListEntry) -> Unit,
    modifier: Modifier = Modifier,
    showSearchBar: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    searchLabel: String = stringResource(Res.string.contacts_search_label),
    showTagFilters: Boolean = false,
    selectedTags: Set<ContactRole> = emptySet(),
    onTagSelected: (ContactRole?) -> Unit = {},
    showRowTags: Boolean = false,
    showNavigationIndicator: Boolean = false,
    selectedContactId: String? = null,
    showSelectedIndicator: Boolean = selectedContactId != null,
    emptyMessage: String? = null,
    rowTestTag: (ContactListEntry) -> String? = { null },
    fadeContainerColor: Color = MaterialTheme.colorScheme.background
) {
    AppListScaffold(
        isEmpty = contacts.isEmpty(),
        emptyMessage = emptyMessage,
        modifier = modifier,
        showSearchBar = showSearchBar,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        searchLabel = searchLabel,
        fadeContainerColor = fadeContainerColor,
        fixedContent = {
            if (showTagFilters) {
                ContactRoleChips(
                    selectedRoles = selectedTags,
                    onSelected = onTagSelected
                )
            }
        }
    ) {
        items(contacts, key = { it.id }) { contact ->
            ContactListRow(
                contact = contact,
                selected = selectedContactId == contact.id,
                showSelectedIndicator = showSelectedIndicator,
                showTags = showRowTags,
                testTag = rowTestTag(contact),
                trailingContent = if (showNavigationIndicator) {
                    {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    null
                },
                onClick = { onContactClick(contact) }
            )
        }
    }
}

@Composable
fun ContactListRow(
    contact: ContactListEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    showTags: Boolean = false,
    showSelectedIndicator: Boolean = false,
    testTag: String? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    AppListRow(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        selected = selected,
        showSelectedState = showSelectedIndicator,
        testTag = testTag
    ) {
        leadingContent?.invoke(this)
        ContactSummary(
            contact = contact,
            selected = selected,
            showTags = showTags,
            supportingContent = supportingContent,
            modifier = Modifier.weight(1f)
        )
        if (showSelectedIndicator && selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        trailingContent?.invoke(this)
    }
}

@Composable
fun ContactSummary(
    contact: ContactListEntry,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    showTags: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    addressColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    supportingContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(modifier = modifier) {
        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = contact.address,
            style = MaterialTheme.typography.bodySmall,
            color = addressColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showTags) {
            contact.roles.takeIf { it.isNotEmpty() }?.let { roles ->
                Text(
                    text = contactRolesLabel(roles),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        supportingContent?.invoke(this)
    }
}

@Composable
fun ContactRoleChips(
    selectedRoles: Set<ContactRole>,
    onSelected: (ContactRole?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ContactRole.entries.chunked(ROLE_CHIPS_PER_ROW).forEach { rowRoles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowRoles.forEach { role ->
                    FilterChip(
                        selected = role in selectedRoles,
                        onClick = { onSelected(role) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 36.dp),
                        label = {
                            Text(
                                text = contactRoleLabel(role),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = contactRoleColor(role),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                repeat(ROLE_CHIPS_PER_ROW - rowRoles.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun contactRolesLabel(roles: Set<ContactRole>): String {
    val labels = buildList {
        if (ContactRole.Favorite in roles) add(stringResource(Res.string.contacts_role_favorite))
        if (ContactRole.People in roles) add(stringResource(Res.string.contacts_role_people))
        if (ContactRole.Merchants in roles) add(stringResource(Res.string.contacts_role_merchants))
    }
    return labels.joinToString(" • ")
}

@Composable
private fun contactRoleLabel(role: ContactRole): String = when (role) {
    ContactRole.Favorite -> stringResource(Res.string.contacts_role_favorite)
    ContactRole.People -> stringResource(Res.string.contacts_role_people)
    ContactRole.Merchants -> stringResource(Res.string.contacts_role_merchants)
}

private fun contactRoleColor(role: ContactRole): Color = when (role) {
    ContactRole.Favorite -> Color(0xFFC2185B)
    ContactRole.People -> Color(0xFF1565C0)
    ContactRole.Merchants -> Color(0xFFEF6C00)
}

private const val ROLE_CHIPS_PER_ROW = 3
