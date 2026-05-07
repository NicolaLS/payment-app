package xyz.lilsus.papp.presentation.main.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lasr.composeapp.generated.resources.Res
import lasr.composeapp.generated.resources.contacts_add
import lasr.composeapp.generated.resources.contacts_add_address
import lasr.composeapp.generated.resources.contacts_address_label
import lasr.composeapp.generated.resources.contacts_alias_label
import lasr.composeapp.generated.resources.contacts_cancel
import lasr.composeapp.generated.resources.contacts_delete
import lasr.composeapp.generated.resources.contacts_edit
import lasr.composeapp.generated.resources.contacts_empty
import lasr.composeapp.generated.resources.contacts_handle
import lasr.composeapp.generated.resources.contacts_invalid_address
import lasr.composeapp.generated.resources.contacts_not_now
import lasr.composeapp.generated.resources.contacts_role_all
import lasr.composeapp.generated.resources.contacts_role_friend
import lasr.composeapp.generated.resources.contacts_role_label
import lasr.composeapp.generated.resources.contacts_role_merchant
import lasr.composeapp.generated.resources.contacts_role_none
import lasr.composeapp.generated.resources.contacts_role_restaurant
import lasr.composeapp.generated.resources.contacts_role_waiter
import lasr.composeapp.generated.resources.contacts_role_work
import lasr.composeapp.generated.resources.contacts_save
import lasr.composeapp.generated.resources.contacts_save_prompt_body
import lasr.composeapp.generated.resources.contacts_save_prompt_title
import lasr.composeapp.generated.resources.contacts_search_placeholder
import lasr.composeapp.generated.resources.contacts_title
import lasr.composeapp.generated.resources.pay_sheet_contacts_tab
import lasr.composeapp.generated.resources.pay_sheet_shortcuts_tab
import lasr.composeapp.generated.resources.shortcuts_empty
import lasr.composeapp.generated.resources.shortcuts_title
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.enableMaestroTestTagsAsResourceId

@Composable
fun ContactsHandle(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.contacts_handle)
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(MaestroTags.Payment.CONTACTS_HANDLE)
            .clickable(
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsBottomSheet(
    state: ContactsUiState,
    onDismiss: () -> Unit,
    onTabSelected: (PaySheetTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onRoleSelected: (ContactRole?) -> Unit,
    onAddCandidate: () -> Unit,
    onContactSelected: (String) -> Unit,
    onShortcutSelected: (String) -> Unit,
    onEditContact: (String) -> Unit,
    onEditorAliasChange: (String) -> Unit,
    onEditorAddressChange: (String) -> Unit,
    onEditorRoleSelected: (ContactRole?) -> Unit,
    onEditorSave: () -> Unit,
    onEditorDelete: () -> Unit,
    onEditorDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.enableMaestroTestTagsAsResourceId()
    ) {
        state.editor?.let { editor ->
            ContactEditorContent(
                state = editor,
                onAliasChange = onEditorAliasChange,
                onAddressChange = onEditorAddressChange,
                onRoleSelected = onEditorRoleSelected,
                onSave = onEditorSave,
                onDelete = onEditorDelete,
                onDismiss = onEditorDismiss
            )
        } ?: PaySheetContent(
            state = state,
            onTabSelected = onTabSelected,
            onQueryChange = onQueryChange,
            onRoleSelected = onRoleSelected,
            onAddCandidate = onAddCandidate,
            onContactSelected = onContactSelected,
            onShortcutSelected = onShortcutSelected,
            onEditContact = onEditContact
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
            RoleChips(
                selectedRole = state.selectedRole,
                includeAll = false,
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
    onQueryChange: (String) -> Unit,
    onRoleSelected: (ContactRole?) -> Unit,
    onAddCandidate: () -> Unit,
    onContactSelected: (String) -> Unit,
    onShortcutSelected: (String) -> Unit,
    onEditContact: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .testTag(MaestroTags.Payment.CONTACTS_SHEET)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (state.selectedTab == PaySheetTab.Shortcuts) {
                stringResource(Res.string.shortcuts_title)
            } else {
                stringResource(Res.string.contacts_title)
            },
            style = MaterialTheme.typography.headlineSmall
        )
        PrimaryTabRow(selectedTabIndex = if (state.selectedTab == PaySheetTab.Shortcuts) 0 else 1) {
            Tab(
                selected = state.selectedTab == PaySheetTab.Shortcuts,
                onClick = { onTabSelected(PaySheetTab.Shortcuts) },
                text = { Text(stringResource(Res.string.pay_sheet_shortcuts_tab)) }
            )
            Tab(
                selected = state.selectedTab == PaySheetTab.Contacts,
                onClick = { onTabSelected(PaySheetTab.Contacts) },
                text = { Text(stringResource(Res.string.pay_sheet_contacts_tab)) }
            )
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(MaestroTags.Payment.CONTACTS_SEARCH),
            placeholder = { Text(stringResource(Res.string.contacts_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )
        if (state.selectedTab == PaySheetTab.Contacts) {
            ContactsTab(
                state = state,
                onRoleSelected = onRoleSelected,
                onAddCandidate = onAddCandidate,
                onContactSelected = onContactSelected,
                onEditContact = onEditContact,
                modifier = Modifier.weight(1f, fill = false)
            )
        } else {
            ShortcutsTab(
                state = state,
                onShortcutSelected = onShortcutSelected,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun ShortcutsTab(
    state: ContactsUiState,
    onShortcutSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.shortcuts.isEmpty()) {
            item {
                Text(
                    text = stringResource(Res.string.shortcuts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else {
            items(state.shortcuts, key = { it.id }) { shortcut ->
                ShortcutRow(
                    item = shortcut,
                    onPay = { onShortcutSelected(shortcut.id) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun ContactsTab(
    state: ContactsUiState,
    onRoleSelected: (ContactRole?) -> Unit,
    onAddCandidate: () -> Unit,
    onContactSelected: (String) -> Unit,
    onEditContact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RoleChips(
            selectedRole = state.selectedRole,
            includeAll = true,
            onSelected = onRoleSelected
        )
        state.addCandidate?.let { candidate ->
            Button(
                onClick = onAddCandidate,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MaestroTags.Payment.CONTACTS_ADD_BUTTON)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(Res.string.contacts_add_address, candidate),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.contacts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.contacts_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(state.contacts, key = { it.id }) { contact ->
                    ContactRow(
                        item = contact,
                        onClick = { onContactSelected(contact.id) },
                        onEdit = { onEditContact(contact.id) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
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
                text = "${item.amountSats} sat",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun ContactRow(item: ContactListItem, onClick: () -> Unit, onEdit: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MaestroTags.Payment.contactRow(item.address)),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(
                    role = Role.Button,
                    onClick = onClick
                )
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.role?.let {
                    Text(
                        text = roleLabel(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(Res.string.contacts_edit)
                )
            }
        }
    }
}

@Composable
private fun ContactEditorContent(
    state: ContactEditorUiState,
    onAliasChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onRoleSelected: (ContactRole?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.contactId == null) {
                    stringResource(Res.string.contacts_add)
                } else {
                    stringResource(Res.string.contacts_edit)
                },
                style = MaterialTheme.typography.headlineSmall
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.contacts_cancel)
                )
            }
        }
        OutlinedTextField(
            value = state.address,
            onValueChange = onAddressChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.addressEditable,
            label = { Text(stringResource(Res.string.contacts_address_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )
        OutlinedTextField(
            value = state.alias,
            onValueChange = onAliasChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.contacts_alias_label)) },
            singleLine = true
        )
        Text(
            text = stringResource(Res.string.contacts_role_label),
            style = MaterialTheme.typography.titleSmall
        )
        RoleChips(
            selectedRole = state.selectedRole,
            includeAll = false,
            onSelected = onRoleSelected
        )
        state.error?.let { ErrorText(it) }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.contacts_save))
        }
        if (state.contactId != null) {
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.contacts_delete))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleChips(
    selectedRole: ContactRole?,
    includeAll: Boolean,
    onSelected: (ContactRole?) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (includeAll) {
            FilterChip(
                selected = selectedRole == null,
                onClick = { onSelected(null) },
                label = { Text(stringResource(Res.string.contacts_role_all)) }
            )
        } else {
            FilterChip(
                selected = selectedRole == null,
                onClick = { onSelected(null) },
                label = { Text(stringResource(Res.string.contacts_role_none)) }
            )
        }
        ContactRole.entries.forEach { role ->
            FilterChip(
                selected = selectedRole == role,
                onClick = { onSelected(role) },
                label = { Text(roleLabel(role)) }
            )
        }
    }
}

@Composable
private fun roleLabel(role: ContactRole): String = when (role) {
    ContactRole.Friend -> stringResource(Res.string.contacts_role_friend)
    ContactRole.Waiter -> stringResource(Res.string.contacts_role_waiter)
    ContactRole.Restaurant -> stringResource(Res.string.contacts_role_restaurant)
    ContactRole.Merchant -> stringResource(Res.string.contacts_role_merchant)
    ContactRole.Work -> stringResource(Res.string.contacts_role_work)
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message.ifBlank { stringResource(Res.string.contacts_invalid_address) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
