package xyz.lilsus.flint.feature.payment.contacts

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.flint.feature.payment.PaymentTestTags
import xyz.lilsus.flint.feature.payment.generated.resources.Res
import xyz.lilsus.flint.feature.payment.generated.resources.contacts_add
import xyz.lilsus.flint.feature.payment.generated.resources.contacts_alias_label
import xyz.lilsus.flint.feature.payment.generated.resources.contacts_empty
import xyz.lilsus.flint.feature.payment.generated.resources.contacts_invalid_address
import xyz.lilsus.flint.feature.payment.generated.resources.contacts_no_matching_contacts
import xyz.lilsus.flint.feature.payment.generated.resources.contacts_not_now
import xyz.lilsus.flint.feature.payment.generated.resources.contacts_save
import xyz.lilsus.flint.feature.payment.generated.resources.contacts_save_prompt_body
import xyz.lilsus.flint.feature.payment.generated.resources.contacts_save_prompt_title
import xyz.lilsus.flint.feature.payment.generated.resources.pay_sheet_contacts_tab
import xyz.lilsus.flint.feature.payment.generated.resources.pay_sheet_shortcuts_tab
import xyz.lilsus.flint.feature.payment.generated.resources.shortcuts_create_first
import xyz.lilsus.flint.feature.payment.generated.resources.shortcuts_empty
import xyz.lilsus.raylsuite.core.model.ContactRole
import xyz.lilsus.raylsuite.core.ui.components.AppFadingLazyColumn
import xyz.lilsus.raylsuite.core.ui.platform.enableTestTagsAsResourceId
import xyz.lilsus.raylsuite.feature.contacts.ContactEditorError
import xyz.lilsus.raylsuite.feature.contacts.ContactListContent
import xyz.lilsus.raylsuite.feature.contacts.ContactListEntry
import xyz.lilsus.raylsuite.feature.contacts.ContactRoleChips

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentContactsBottomSheet(
    state: PaymentContactsUiState,
    onDismiss: () -> Unit,
    onTabSelected: (PaymentSheetTab) -> Unit,
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
        modifier = Modifier.enableTestTagsAsResourceId()
    ) {
        PaymentSheetContent(
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
        modifier = Modifier.enableTestTagsAsResourceId()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentSheetContent(
    state: PaymentContactsUiState,
    onTabSelected: (PaymentSheetTab) -> Unit,
    onRoleSelected: (ContactRole?) -> Unit,
    onContactSelected: (String) -> Unit,
    onShortcutSelected: (String) -> Unit,
    onCreateShortcut: () -> Unit,
    onCreateContact: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(PAYMENT_SHEET_CONTENT_HEIGHT)
            .testTag(PaymentTestTags.CONTACTS_SHEET)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PrimaryTabRow(
            selectedTabIndex =
                if (state.selectedTab == PaymentSheetTab.Shortcuts) {
                    0
                } else {
                    1
                }
        ) {
            Tab(
                selected = state.selectedTab == PaymentSheetTab.Shortcuts,
                onClick = { onTabSelected(PaymentSheetTab.Shortcuts) },
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
                selected = state.selectedTab == PaymentSheetTab.Contacts,
                onClick = { onTabSelected(PaymentSheetTab.Contacts) },
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
        if (state.selectedTab == PaymentSheetTab.Contacts) {
            if (state.hasContacts) {
                ContactsTab(
                    state = state,
                    onRoleSelected = onRoleSelected,
                    onContactSelected = onContactSelected,
                    modifier = Modifier.weight(1f)
                )
            } else {
                EmptyPaymentSheetState(
                    message = stringResource(Res.string.contacts_empty),
                    actionLabel = stringResource(Res.string.contacts_add),
                    onAction = onCreateContact,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        } else if (state.shortcuts.isEmpty()) {
            EmptyPaymentSheetState(
                message = stringResource(Res.string.shortcuts_empty),
                actionLabel = stringResource(Res.string.shortcuts_create_first),
                onAction = onCreateShortcut,
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

@Composable
private fun ShortcutsTab(
    state: PaymentContactsUiState,
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
private fun EmptyPaymentSheetState(
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
    state: PaymentContactsUiState,
    onRoleSelected: (ContactRole?) -> Unit,
    onContactSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ContactListContent(
        contacts = state.contacts.map(PaymentContactListItem::toContactListEntry),
        onContactClick = { onContactSelected(it.id) },
        modifier = modifier,
        showRoleFilters = true,
        selectedRoles = state.selectedRoles,
        onRoleSelected = onRoleSelected,
        showRowRoles = true,
        emptyMessage = stringResource(Res.string.contacts_no_matching_contacts),
        rowTestTag = { PaymentTestTags.contactRow(it.address) },
        fadeContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    )
}

@Composable
private fun ShortcutRow(item: PaymentShortcutListItem, onPay: () -> Unit) {
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
                item.commentSummary?.let { comment ->
                    Text(
                        text = comment,
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

private val PAYMENT_SHEET_CONTENT_HEIGHT = 430.dp

private fun PaymentContactListItem.toContactListEntry(): ContactListEntry = ContactListEntry(
    id = id,
    displayName = displayName,
    address = address,
    roles = roles
)

@Composable
private fun ErrorText(error: ContactEditorError) {
    val message = when (error) {
        ContactEditorError.InvalidAddress -> Res.string.contacts_invalid_address
    }
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
