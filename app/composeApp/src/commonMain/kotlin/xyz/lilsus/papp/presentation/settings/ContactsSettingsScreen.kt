package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lasr.composeapp.generated.resources.Res
import lasr.composeapp.generated.resources.contacts_add
import lasr.composeapp.generated.resources.contacts_address_label
import lasr.composeapp.generated.resources.contacts_alias_label
import lasr.composeapp.generated.resources.contacts_cancel
import lasr.composeapp.generated.resources.contacts_delete
import lasr.composeapp.generated.resources.contacts_edit
import lasr.composeapp.generated.resources.contacts_empty
import lasr.composeapp.generated.resources.contacts_invalid_address
import lasr.composeapp.generated.resources.contacts_role_friend
import lasr.composeapp.generated.resources.contacts_role_label
import lasr.composeapp.generated.resources.contacts_role_merchant
import lasr.composeapp.generated.resources.contacts_role_none
import lasr.composeapp.generated.resources.contacts_role_restaurant
import lasr.composeapp.generated.resources.contacts_role_waiter
import lasr.composeapp.generated.resources.contacts_role_work
import lasr.composeapp.generated.resources.contacts_save
import lasr.composeapp.generated.resources.contacts_search_placeholder
import lasr.composeapp.generated.resources.contacts_title
import lasr.composeapp.generated.resources.settings_contacts
import lasr.composeapp.generated.resources.shortcut_amount_label
import lasr.composeapp.generated.resources.shortcut_comment_label
import lasr.composeapp.generated.resources.shortcut_contact_label
import lasr.composeapp.generated.resources.shortcut_no_contacts
import lasr.composeapp.generated.resources.shortcut_title_label
import lasr.composeapp.generated.resources.shortcuts_add
import lasr.composeapp.generated.resources.shortcuts_empty
import lasr.composeapp.generated.resources.shortcuts_title
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.presentation.common.BackIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsSettingsScreen(
    state: ContactsSettingsUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onAddShortcut: () -> Unit,
    onEditContact: (String) -> Unit,
    onDeleteContact: (String) -> Unit,
    onEditShortcut: (String) -> Unit,
    onDeleteShortcut: (String) -> Unit,
    onContactEditorAddressChange: (String) -> Unit,
    onContactEditorAliasChange: (String) -> Unit,
    onContactEditorRoleSelected: (ContactRole?) -> Unit,
    onContactEditorSave: () -> Unit,
    onShortcutTitleChange: (String) -> Unit,
    onShortcutContactSelected: (String) -> Unit,
    onShortcutAmountChange: (String) -> Unit,
    onShortcutCommentChange: (String) -> Unit,
    onShortcutSave: () -> Unit,
    onEditorDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isEditing = state.shortcutEditor != null || state.contactEditor != null
    Scaffold(
        modifier = modifier.testTag(MaestroTags.Settings.CONTACTS_SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_contacts)) },
                navigationIcon = {
                    BackIconButton(
                        onClick = if (isEditing) onEditorDismiss else onBack
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        when {
            state.shortcutEditor != null -> ShortcutSettingsEditorContent(
                state = state.shortcutEditor,
                onTitleChange = onShortcutTitleChange,
                onContactSelected = onShortcutContactSelected,
                onAmountChange = onShortcutAmountChange,
                onCommentChange = onShortcutCommentChange,
                onAddContact = onAddContact,
                onSave = onShortcutSave,
                onDismiss = onEditorDismiss,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )

            state.contactEditor != null -> ContactSettingsEditorContent(
                state = state.contactEditor,
                onAddressChange = onContactEditorAddressChange,
                onAliasChange = onContactEditorAliasChange,
                onRoleSelected = onContactEditorRoleSelected,
                onSave = onContactEditorSave,
                onDismiss = onEditorDismiss,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )

            else -> SettingsListContent(
                state = state,
                onQueryChange = onQueryChange,
                onAddContact = onAddContact,
                onAddShortcut = onAddShortcut,
                onEditContact = onEditContact,
                onDeleteContact = onDeleteContact,
                onEditShortcut = onEditShortcut,
                onDeleteShortcut = onDeleteShortcut,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )
        }
    }
}

@Composable
private fun SettingsListContent(
    state: ContactsSettingsUiState,
    onQueryChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onAddShortcut: () -> Unit,
    onEditContact: (String) -> Unit,
    onDeleteContact: (String) -> Unit,
    onEditShortcut: (String) -> Unit,
    onDeleteShortcut: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(Res.string.contacts_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionTitle(stringResource(Res.string.shortcuts_title))
            }
            item {
                Button(onClick = onAddShortcut, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = stringResource(Res.string.shortcuts_add),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (state.shortcuts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.shortcuts_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.shortcuts, key = { it.id }) { item ->
                ShortcutSettingsRow(
                    item = item,
                    onEdit = { onEditShortcut(item.id) },
                    onDelete = { onDeleteShortcut(item.id) }
                )
            }
            item {
                SectionTitle(stringResource(Res.string.contacts_title))
            }
            item {
                OutlinedButton(onClick = onAddContact, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = stringResource(Res.string.contacts_add),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (state.contacts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.contacts_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.contacts, key = { it.id }) { item ->
                ContactSettingsRow(
                    item = item,
                    onEdit = { onEditContact(item.id) },
                    onDelete = { onDeleteContact(item.id) }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ShortcutSettingsRow(
    item: ShortcutSettingsItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    SettingsSurfaceRow(onClick = onEdit) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(
                    "${item.amountSats} sat",
                    item.contactName,
                    item.comment
                ).joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        RowActions(onEdit = onEdit, onDelete = onDelete)
    }
}

@Composable
private fun ContactSettingsRow(
    item: ContactSettingsItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    SettingsSurfaceRow(onClick = onEdit) {
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
        RowActions(onEdit = onEdit, onDelete = onDelete)
    }
}

@Composable
private fun SettingsSurfaceRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun RowActions(onEdit: () -> Unit, onDelete: () -> Unit) {
    IconButton(onClick = onEdit) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = stringResource(Res.string.contacts_edit)
        )
    }
    IconButton(onClick = onDelete) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = stringResource(Res.string.contacts_delete)
        )
    }
}

@Composable
private fun ShortcutSettingsEditorContent(
    state: ShortcutSettingsEditor,
    onTitleChange: (String) -> Unit,
    onContactSelected: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onAddContact: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.shortcut_title_label)) },
            singleLine = true
        )
        SectionTitle(stringResource(Res.string.shortcut_contact_label))
        if (state.contactOptions.isEmpty()) {
            Text(
                text = stringResource(Res.string.shortcut_no_contacts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onAddContact, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(Res.string.contacts_add),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.contactOptions.forEach { option ->
                    ShortcutContactOptionRow(
                        option = option,
                        selected = state.selectedContactId == option.id,
                        onClick = { onContactSelected(option.id) }
                    )
                }
            }
            OutlinedTextField(
                value = state.amountSats,
                onValueChange = onAmountChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.shortcut_amount_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = state.comment,
                onValueChange = onCommentChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.shortcut_comment_label)) },
                singleLine = true
            )
        }
        state.error?.let { ErrorText(it) }
        if (state.contactOptions.isNotEmpty()) {
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.contacts_save))
            }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.contacts_cancel))
        }
    }
}

@Composable
private fun ShortcutContactOptionRow(
    option: ShortcutContactOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = if (selected) 3.dp else 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = option.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ContactSettingsEditorContent(
    state: ContactSettingsEditor,
    onAddressChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onRoleSelected: (ContactRole?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
        RoleChips(selectedRole = state.role, onSelected = onRoleSelected)
        state.error?.let { ErrorText(it) }
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.contacts_save))
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.contacts_cancel))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoleChips(selectedRole: ContactRole?, onSelected: (ContactRole?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedRole == null,
            onClick = { onSelected(null) },
            label = { Text(stringResource(Res.string.contacts_role_none)) }
        )
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
