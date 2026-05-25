package xyz.lilsus.papp.presentation.main.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lasr.shared.generated.resources.Res
import lasr.shared.generated.resources.contacts_add
import lasr.shared.generated.resources.contacts_alias_label
import lasr.shared.generated.resources.contacts_empty
import lasr.shared.generated.resources.contacts_handle
import lasr.shared.generated.resources.contacts_invalid_address
import lasr.shared.generated.resources.contacts_no_matching_contacts
import lasr.shared.generated.resources.contacts_not_now
import lasr.shared.generated.resources.contacts_save
import lasr.shared.generated.resources.contacts_save_prompt_body
import lasr.shared.generated.resources.contacts_save_prompt_title
import lasr.shared.generated.resources.pay_sheet_contacts_tab
import lasr.shared.generated.resources.pay_sheet_shortcuts_tab
import lasr.shared.generated.resources.shortcuts_create_first
import lasr.shared.generated.resources.shortcuts_empty
import lasr.shared.generated.resources.shortcuts_title
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.enableMaestroTestTagsAsResourceId
import xyz.lilsus.papp.presentation.common.AppFadingLazyColumn
import xyz.lilsus.papp.presentation.common.ContactEditorError
import xyz.lilsus.papp.presentation.common.ContactListContent
import xyz.lilsus.papp.presentation.common.ContactListEntry
import xyz.lilsus.papp.presentation.common.ContactRoleChips

@Composable
fun ContactsIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.contacts_handle)
    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .testTag(MaestroTags.Payment.CONTACTS_HANDLE)
        ) {
            Icon(
                imageVector = LightningBoltIcon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsBottomSheet(
    state: ContactsUiState,
    onDismiss: () -> Unit,
    onTabSelected: (PaySheetTab) -> Unit,
    onRoleSelected: (ContactRole?) -> Unit,
    onContactSelected: (String) -> Unit,
    onShortcutSelected: (String) -> Unit,
    onCreateShortcut: () -> Unit,
    onCreateContact: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.enableMaestroTestTagsAsResourceId()
    ) {
        PaySheetContent(
            state = state,
            onTabSelected = onTabSelected,
            onRoleSelected = onRoleSelected,
            onContactSelected = onContactSelected,
            onShortcutSelected = onShortcutSelected,
            onCreateShortcut = onCreateShortcut,
            onCreateContact = onCreateContact
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveContactBottomSheet(
    state: ContactSavePromptUiState,
    onAliasChange: (String) -> Unit,
    onRoleSelected: (ContactRole?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.enableMaestroTestTagsAsResourceId()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(Res.string.contacts_save_prompt_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(Res.string.contacts_save_prompt_body, state.address),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.alias,
                onValueChange = onAliasChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.contacts_alias_label)) },
                singleLine = true
            )
            ContactRoleChips(
                selectedRoles = state.selectedRoles,
                onSelected = onRoleSelected
            )
            state.error?.let { ErrorText(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.contacts_not_now))
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.contacts_save))
                }
            }
        }
    }
}

@Composable
private fun PaySheetContent(
    state: ContactsUiState,
    onTabSelected: (PaySheetTab) -> Unit,
    onRoleSelected: (ContactRole?) -> Unit,
    onContactSelected: (String) -> Unit,
    onShortcutSelected: (String) -> Unit,
    onCreateShortcut: () -> Unit,
    onCreateContact: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PAY_SHEET_CONTENT_HEIGHT)
            .testTag(MaestroTags.Payment.CONTACTS_SHEET)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PrimaryTabRow(selectedTabIndex = if (state.selectedTab == PaySheetTab.Shortcuts) 0 else 1) {
            Tab(
                selected = state.selectedTab == PaySheetTab.Shortcuts,
                onClick = { onTabSelected(PaySheetTab.Shortcuts) },
                text = {
                    Text(
                        text = tabLabel(
                            label = stringResource(Res.string.pay_sheet_shortcuts_tab),
                            count = state.shortcuts.size
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            Tab(
                selected = state.selectedTab == PaySheetTab.Contacts,
                onClick = { onTabSelected(PaySheetTab.Contacts) },
                text = {
                    Text(
                        text = tabLabel(
                            label = stringResource(Res.string.pay_sheet_contacts_tab),
                            count = state.contactCount
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
        if (state.selectedTab == PaySheetTab.Contacts) {
            if (state.hasContacts) {
                ContactsTab(
                    state = state,
                    onRoleSelected = onRoleSelected,
                    onContactSelected = onContactSelected,
                    modifier = Modifier.weight(1f)
                )
            } else {
                EmptyContactsState(
                    onCreateContact = onCreateContact,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        } else {
            if (state.shortcuts.isEmpty()) {
                EmptyShortcutsState(
                    onCreateShortcut = onCreateShortcut,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                ShortcutsTab(
                    state = state,
                    onShortcutSelected = onShortcutSelected,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ShortcutsTab(
    state: ContactsUiState,
    onShortcutSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AppFadingLazyColumn(
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.shortcuts, key = { it.id }) { shortcut ->
            ShortcutRow(
                item = shortcut,
                onPay = { onShortcutSelected(shortcut.id) }
            )
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun EmptyShortcutsState(onCreateShortcut: () -> Unit, modifier: Modifier = Modifier) {
    EmptyPaySheetState(
        message = stringResource(Res.string.shortcuts_empty),
        actionLabel = stringResource(Res.string.shortcuts_create_first),
        onAction = onCreateShortcut,
        modifier = modifier
    )
}

@Composable
private fun EmptyContactsState(onCreateContact: () -> Unit, modifier: Modifier = Modifier) {
    EmptyPaySheetState(
        message = stringResource(Res.string.contacts_empty),
        actionLabel = stringResource(Res.string.contacts_add),
        onAction = onCreateContact,
        modifier = modifier
    )
}

@Composable
private fun EmptyPaySheetState(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onAction) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = actionLabel,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ContactsTab(
    state: ContactsUiState,
    onRoleSelected: (ContactRole?) -> Unit,
    onContactSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ContactListContent(
        contacts = state.contacts.map { it.toContactListEntry() },
        onContactClick = { onContactSelected(it.id) },
        modifier = modifier,
        showTagFilters = true,
        selectedTags = state.selectedRoles,
        onTagSelected = onRoleSelected,
        showRowTags = true,
        emptyMessage = stringResource(Res.string.contacts_no_matching_contacts),
        rowTestTag = { MaestroTags.Payment.contactRow(it.address) },
        fadeContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
}

@Composable
private fun ShortcutRow(item: ShortcutListItem, onPay: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickable(
                    role = Role.Button,
                    onClick = onPay
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.recipientSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.commentSummary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = item.amountLabel,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun tabLabel(label: String, count: Int): String =
    if (count > 0) "$label ($count)" else label

private val PAY_SHEET_CONTENT_HEIGHT = 430.dp

private fun ContactListItem.toContactListEntry(): ContactListEntry = ContactListEntry(
    id = id,
    displayName = displayName,
    address = address,
    roles = roles
)

private val LightningBoltIcon: ImageVector = ImageVector.Builder(
    name = "LightningBolt",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(13f, 2f)
        lineTo(4f, 14f)
        horizontalLineTo(11f)
        lineTo(10f, 22f)
        lineTo(20f, 9f)
        horizontalLineTo(13f)
        lineTo(13f, 2f)
        close()
    }
}.build()

@Composable
private fun ErrorText(error: ContactEditorError) {
    Text(
        text = stringResource(error.stringRes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

private val ContactEditorError.stringRes
    get() = when (this) {
        ContactEditorError.InvalidAddress -> Res.string.contacts_invalid_address
    }
