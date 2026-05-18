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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import lasr.composeapp.generated.resources.Res
import lasr.composeapp.generated.resources.contacts_add
import lasr.composeapp.generated.resources.contacts_alias_label
import lasr.composeapp.generated.resources.contacts_empty
import lasr.composeapp.generated.resources.contacts_handle
import lasr.composeapp.generated.resources.contacts_invalid_address
import lasr.composeapp.generated.resources.contacts_no_matching_contacts
import lasr.composeapp.generated.resources.contacts_not_now
import lasr.composeapp.generated.resources.contacts_role_bills
import lasr.composeapp.generated.resources.contacts_role_favorite
import lasr.composeapp.generated.resources.contacts_role_merchants
import lasr.composeapp.generated.resources.contacts_role_people
import lasr.composeapp.generated.resources.contacts_role_personal
import lasr.composeapp.generated.resources.contacts_role_work
import lasr.composeapp.generated.resources.contacts_save
import lasr.composeapp.generated.resources.contacts_save_prompt_body
import lasr.composeapp.generated.resources.contacts_save_prompt_title
import lasr.composeapp.generated.resources.pay_sheet_contacts_tab
import lasr.composeapp.generated.resources.pay_sheet_shortcuts_tab
import lasr.composeapp.generated.resources.shortcuts_create_first
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
                imageVector = LightningBoltIcon,
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
            RoleChips(
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
                        text = stringResource(Res.string.pay_sheet_shortcuts_tab),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            )
            Tab(
                selected = state.selectedTab == PaySheetTab.Contacts,
                onClick = { onTabSelected(PaySheetTab.Contacts) },
                text = {
                    Text(
                        text = stringResource(Res.string.pay_sheet_contacts_tab),
                        style = MaterialTheme.typography.titleMedium
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
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RoleChips(
            selectedRoles = state.selectedRoles,
            onSelected = onRoleSelected
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.contacts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.contacts_no_matching_contacts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(state.contacts, key = { it.id }) { contact ->
                    ContactRow(
                        item = contact,
                        onClick = { onContactSelected(contact.id) }
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
                text = item.amountLabel,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun ContactRow(item: ContactListItem, onClick: () -> Unit) {
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
                    val selected = role in selectedRoles
                    FilterChip(
                        selected = selected,
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

private val PAY_SHEET_CONTENT_HEIGHT = 430.dp
private const val ROLE_CHIPS_PER_ROW = 3

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
private fun ErrorText(message: String) {
    Text(
        text = message.ifBlank { stringResource(Res.string.contacts_invalid_address) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}
