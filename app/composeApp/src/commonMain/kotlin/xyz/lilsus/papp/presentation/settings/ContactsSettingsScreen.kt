package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lasr.composeapp.generated.resources.Res
import lasr.composeapp.generated.resources.contact_shortcuts_create
import lasr.composeapp.generated.resources.contact_shortcuts_empty
import lasr.composeapp.generated.resources.contact_shortcuts_title
import lasr.composeapp.generated.resources.contacts_add
import lasr.composeapp.generated.resources.contacts_address_label
import lasr.composeapp.generated.resources.contacts_alias_label
import lasr.composeapp.generated.resources.contacts_cancel
import lasr.composeapp.generated.resources.contacts_delete
import lasr.composeapp.generated.resources.contacts_empty
import lasr.composeapp.generated.resources.contacts_import_blink
import lasr.composeapp.generated.resources.contacts_import_blink_choose_wallet
import lasr.composeapp.generated.resources.contacts_invalid_address
import lasr.composeapp.generated.resources.contacts_no_matching_contacts
import lasr.composeapp.generated.resources.contacts_role_label
import lasr.composeapp.generated.resources.contacts_save
import lasr.composeapp.generated.resources.contacts_search_label
import lasr.composeapp.generated.resources.settings_contacts
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.presentation.common.AppListDefaults
import xyz.lilsus.papp.presentation.common.BackIconButton
import xyz.lilsus.papp.presentation.common.ContactListContent
import xyz.lilsus.papp.presentation.common.ContactListEntry
import xyz.lilsus.papp.presentation.common.ContactRoleChips

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsSettingsScreen(
    state: ContactsSettingsUiState,
    onBack: () -> Unit,
    onAddContact: () -> Unit,
    onImportBlinkContacts: () -> Unit,
    onBlinkWalletSelected: (String) -> Unit,
    onBlinkWalletChooserDismiss: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onEditContact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.testTag(MaestroTags.Settings.CONTACTS_SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_contacts)) },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        SettingsListContent(
            state = state,
            onAddContact = onAddContact,
            onImportBlinkContacts = onImportBlinkContacts,
            onSearchQueryChange = onSearchQueryChange,
            onEditContact = onEditContact,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(AppListDefaults.ScreenPadding)
        )
    }
    state.blinkWalletChooser?.let { chooser ->
        BlinkWalletChooserDialog(
            chooser = chooser,
            onWalletSelected = onBlinkWalletSelected,
            onDismiss = onBlinkWalletChooserDismiss
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSettingsEditorScreen(
    state: ContactSettingsEditor?,
    onBack: () -> Unit,
    onAddressChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onRoleSelected: (ContactRole?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onCreateShortcut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.testTag(MaestroTags.Settings.CONTACTS_SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_contacts)) },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        state?.let { editor ->
            ContactSettingsEditorContent(
                state = editor,
                onAddressChange = onAddressChange,
                onAliasChange = onAliasChange,
                onRoleSelected = onRoleSelected,
                onSave = if (editor.contactId == null) onSave else null,
                onDelete = if (editor.contactId != null) onDelete else null,
                onCreateShortcut = if (editor.contactId != null) onCreateShortcut else null,
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
    onAddContact: () -> Unit,
    onImportBlinkContacts: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onEditContact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onAddContact, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = stringResource(Res.string.contacts_add),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (state.hasBlinkWallets) {
            OutlinedButton(
                onClick = onImportBlinkContacts,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.contacts_import_blink))
            }
        }
        ContactListContent(
            contacts = state.contacts.map { it.toContactListEntry() },
            onContactClick = { onEditContact(it.id) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            showSearchBar = true,
            searchQuery = state.query,
            onSearchQueryChange = onSearchQueryChange,
            searchLabel = stringResource(Res.string.contacts_search_label),
            showRowTags = true,
            showNavigationIndicator = true,
            emptyMessage = stringResource(
                if (state.query.isBlank()) {
                    Res.string.contacts_empty
                } else {
                    Res.string.contacts_no_matching_contacts
                }
            )
        )
    }
}

@Composable
private fun BlinkWalletChooserDialog(
    chooser: BlinkWalletChooser,
    onWalletSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.contacts_import_blink_choose_wallet)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chooser.wallets.forEach { wallet ->
                    BlinkWalletChoiceRow(
                        wallet = wallet,
                        onClick = { onWalletSelected(wallet.walletId) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.contacts_cancel))
            }
        }
    )
}

@Composable
private fun BlinkWalletChoiceRow(wallet: BlinkWalletImportOption, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = wallet.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = abbreviateWalletId(wallet.subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContactSettingsEditorContent(
    state: ContactSettingsEditor,
    onAddressChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onRoleSelected: (ContactRole?) -> Unit,
    onSave: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onCreateShortcut: (() -> Unit)?,
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
        ContactRoleChips(
            selectedRoles = state.roles,
            onSelected = onRoleSelected
        )
        onCreateShortcut?.let { createShortcut ->
            ContactShortcutsSection(
                shortcuts = state.shortcuts,
                onCreateShortcut = createShortcut
            )
        }
        state.error?.let { ErrorText(it) }
        onSave?.let { save ->
            Button(onClick = save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.contacts_save))
            }
        }
        onDelete?.let { delete ->
            OutlinedButton(onClick = delete, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text(
                    text = stringResource(Res.string.contacts_delete),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ContactShortcutsSection(
    shortcuts: List<ContactShortcutItem>,
    onCreateShortcut: () -> Unit
) {
    Text(
        text = stringResource(Res.string.contact_shortcuts_title),
        style = MaterialTheme.typography.titleSmall
    )
    Button(onClick = onCreateShortcut, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(
            text = stringResource(Res.string.contact_shortcuts_create),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
    if (shortcuts.isEmpty()) {
        Text(
            text = stringResource(Res.string.contact_shortcuts_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        shortcuts.forEach { shortcut ->
            ContactShortcutRow(shortcut = shortcut)
        }
    }
}

@Composable
private fun ContactShortcutRow(shortcut: ContactShortcutItem) {
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = contentColor,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(shortcut.amountText, shortcut.comment)
                        .joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun abbreviateWalletId(value: String): String = if (value.length <= 16) {
    value
} else {
    value.take(8) + "…" + value.takeLast(4)
}

private fun ContactSettingsItem.toContactListEntry(): ContactListEntry = ContactListEntry(
    id = id,
    displayName = displayName,
    address = address,
    roles = roles
)

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message.ifBlank { stringResource(Res.string.contacts_invalid_address) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
