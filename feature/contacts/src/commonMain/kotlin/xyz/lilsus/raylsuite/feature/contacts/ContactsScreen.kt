package xyz.lilsus.raylsuite.feature.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.model.ContactRole
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.ui.components.AppListDefaults
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.Res
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contact_shortcuts_create
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contact_shortcuts_empty
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contact_shortcuts_title
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contacts_add
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contacts_address_label
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contacts_alias_label
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contacts_delete
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contacts_empty
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contacts_invalid_address
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contacts_no_matching_contacts
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contacts_role_label
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contacts_save
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contacts_search_label
import xyz.lilsus.raylsuite.feature.contacts.generated.resources.contacts_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    state: ContactsUiState,
    onBack: () -> Unit,
    onAddContact: () -> Unit,
    onSearchChange: (String) -> Unit,
    onEditContact: (String) -> Unit,
    modifier: Modifier = Modifier,
    additionalActions: @Composable ColumnScope.() -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.testTag(ContactsTestTags.SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.contacts_title)) },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(AppListDefaults.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onAddContact,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(Res.string.contacts_add),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            additionalActions()
            ContactListContent(
                contacts = state.contacts,
                onContactClick = { contact -> onEditContact(contact.id) },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                showSearchBar = true,
                searchQuery = state.query,
                onSearchQueryChange = onSearchChange,
                searchLabel = stringResource(Res.string.contacts_search_label),
                showRowRoles = true,
                showNavigationIndicator = true,
                emptyMessage =
                stringResource(
                    if (state.query.isBlank()) {
                        Res.string.contacts_empty
                    } else {
                        Res.string.contacts_no_matching_contacts
                    }
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditorScreen(
    state: ContactEditorState?,
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
        modifier = modifier.testTag(ContactsTestTags.EDITOR),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.contacts_title)) },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        state?.let { editor ->
            ContactEditorContent(
                state = editor,
                onAddressChange = onAddressChange,
                onAliasChange = onAliasChange,
                onRoleSelected = onRoleSelected,
                onSave = onSave.takeIf { editor.contactId == null },
                onDelete = onDelete.takeIf { editor.contactId != null },
                onCreateShortcut = onCreateShortcut.takeIf { editor.contactId != null },
                modifier =
                Modifier
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
private fun ContactEditorContent(
    state: ContactEditorState,
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
        state.error?.let { error ->
            ContactEditorErrorText(error)
        }
        onSave?.let { save ->
            Button(
                onClick = save,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.contacts_save))
            }
        }
        onDelete?.let { delete ->
            OutlinedButton(
                onClick = delete,
                modifier = Modifier.fillMaxWidth()
            ) {
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
    Button(
        onClick = onCreateShortcut,
        modifier = Modifier.fillMaxWidth()
    ) {
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
            ContactShortcutRow(shortcut)
        }
    }
}

@Composable
private fun ContactShortcutRow(shortcut: ContactShortcutItem) {
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val amountFormatter = rememberAmountFormatter()
    val currency = CurrencyCatalog.infoFor(shortcut.amount.normalizedCurrencyCode).currency
    val amount = amountFormatter.format(DisplayAmount(shortcut.amount.minor, currency))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = contentColor,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier =
            Modifier
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
                    text = listOfNotNull(amount, shortcut.comment).joinToString(" - "),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ContactEditorErrorText(error: ContactEditorError) {
    Text(
        text = stringResource(error.stringResource),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

private val ContactEditorError.stringResource: StringResource
    get() =
        when (this) {
            ContactEditorError.InvalidAddress -> Res.string.contacts_invalid_address
        }

object ContactsTestTags {
    const val SCREEN = "contacts_screen"
    const val EDITOR = "contact_editor"
}
