package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Color
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
import lasr.composeapp.generated.resources.contacts_empty
import lasr.composeapp.generated.resources.contacts_import_blink
import lasr.composeapp.generated.resources.contacts_import_blink_choose_wallet
import lasr.composeapp.generated.resources.contacts_invalid_address
import lasr.composeapp.generated.resources.contacts_no_matching_contacts
import lasr.composeapp.generated.resources.contacts_role_bills
import lasr.composeapp.generated.resources.contacts_role_favorite
import lasr.composeapp.generated.resources.contacts_role_label
import lasr.composeapp.generated.resources.contacts_role_merchants
import lasr.composeapp.generated.resources.contacts_role_people
import lasr.composeapp.generated.resources.contacts_role_personal
import lasr.composeapp.generated.resources.contacts_role_work
import lasr.composeapp.generated.resources.contacts_save
import lasr.composeapp.generated.resources.contacts_search_label
import lasr.composeapp.generated.resources.settings_contacts
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.presentation.common.BackIconButton

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
    onContactEditorAddressChange: (String) -> Unit,
    onContactEditorAliasChange: (String) -> Unit,
    onContactEditorRoleSelected: (ContactRole?) -> Unit,
    onContactEditorSave: () -> Unit,
    onContactEditorDelete: () -> Unit,
    onEditorDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isEditing = state.contactEditor != null
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
            state.contactEditor != null -> ContactSettingsEditorContent(
                state = state.contactEditor,
                onAddressChange = onContactEditorAddressChange,
                onAliasChange = onContactEditorAliasChange,
                onRoleSelected = onContactEditorRoleSelected,
                onSave = if (state.contactEditor.contactId == null) {
                    onContactEditorSave
                } else {
                    null
                },
                onDelete = if (state.contactEditor.contactId != null) {
                    onContactEditorDelete
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )

            else -> SettingsListContent(
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
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )
        }
    }
    state.blinkWalletChooser?.let { chooser ->
        BlinkWalletChooserDialog(
            chooser = chooser,
            onWalletSelected = onBlinkWalletSelected,
            onDismiss = onBlinkWalletChooserDismiss
        )
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedButton(onClick = onAddContact, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = stringResource(Res.string.contacts_add),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (state.hasBlinkWallets) {
                item {
                    OutlinedButton(
                        onClick = onImportBlinkContacts,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.contacts_import_blink))
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.contacts_search_label)) },
                    singleLine = true
                )
            }
            if (state.contacts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (state.query.isBlank()) {
                                Res.string.contacts_empty
                            } else {
                                Res.string.contacts_no_matching_contacts
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state.contacts, key = { it.id }) { item ->
                ContactSettingsRow(
                    item = item,
                    onClick = { onEditContact(item.id) }
                )
            }
        }
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
private fun ContactSettingsRow(item: ContactSettingsItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
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
            item.roles.takeIf { it.isNotEmpty() }?.let {
                Text(
                    text = rolesLabel(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
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
    onSave: (() -> Unit)?,
    onDelete: (() -> Unit)?,
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
        RoleChips(
            selectedRoles = state.roles,
            onSelected = onRoleSelected
        )
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
private fun RoleChips(selectedRoles: Set<ContactRole>, onSelected: (ContactRole?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                text = roleLabel(role),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = roleColor(role),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                repeat(ROLE_CHIPS_PER_ROW - rowRoles.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun roleLabel(role: ContactRole): String = when (role) {
    ContactRole.Favorite -> stringResource(Res.string.contacts_role_favorite)
    ContactRole.Personal -> stringResource(Res.string.contacts_role_personal)
    ContactRole.Work -> stringResource(Res.string.contacts_role_work)
    ContactRole.People -> stringResource(Res.string.contacts_role_people)
    ContactRole.Merchants -> stringResource(Res.string.contacts_role_merchants)
    ContactRole.Bills -> stringResource(Res.string.contacts_role_bills)
}

@Composable
private fun rolesLabel(roles: Set<ContactRole>): String {
    val labels = buildList {
        if (ContactRole.Favorite in roles) add(stringResource(Res.string.contacts_role_favorite))
        if (ContactRole.Personal in roles) add(stringResource(Res.string.contacts_role_personal))
        if (ContactRole.Work in roles) add(stringResource(Res.string.contacts_role_work))
        if (ContactRole.People in roles) add(stringResource(Res.string.contacts_role_people))
        if (ContactRole.Merchants in roles) add(stringResource(Res.string.contacts_role_merchants))
        if (ContactRole.Bills in roles) add(stringResource(Res.string.contacts_role_bills))
    }
    return labels.joinToString(" • ")
}

private fun roleColor(role: ContactRole): Color = when (role) {
    ContactRole.Favorite -> Color(0xFFC2185B)
    ContactRole.Personal -> Color(0xFF2E7D32)
    ContactRole.Work -> Color(0xFF5D4037)
    ContactRole.People -> Color(0xFF1565C0)
    ContactRole.Merchants -> Color(0xFFEF6C00)
    ContactRole.Bills -> Color(0xFF455A64)
}

private const val ROLE_CHIPS_PER_ROW = 3

private fun abbreviateWalletId(value: String): String = if (value.length <= 16) {
    value
} else {
    value.take(8) + "…" + value.takeLast(4)
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message.ifBlank { stringResource(Res.string.contacts_invalid_address) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
