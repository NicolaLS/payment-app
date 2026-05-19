package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import lasr.composeapp.generated.resources.Res
import lasr.composeapp.generated.resources.contacts_delete
import lasr.composeapp.generated.resources.contacts_invalid_address
import lasr.composeapp.generated.resources.contacts_save
import lasr.composeapp.generated.resources.keyboard_done
import lasr.composeapp.generated.resources.settings_contacts
import lasr.composeapp.generated.resources.settings_payments
import lasr.composeapp.generated.resources.settings_payments_ask_save_contacts
import lasr.composeapp.generated.resources.settings_payments_confirm_label
import lasr.composeapp.generated.resources.settings_payments_confirm_manual_entry
import lasr.composeapp.generated.resources.settings_payments_confirm_shortcuts
import lasr.composeapp.generated.resources.settings_payments_confirm_threshold
import lasr.composeapp.generated.resources.settings_payments_haptics_payment
import lasr.composeapp.generated.resources.settings_payments_haptics_scan
import lasr.composeapp.generated.resources.settings_payments_haptics_title
import lasr.composeapp.generated.resources.settings_payments_option_above
import lasr.composeapp.generated.resources.settings_payments_option_always
import lasr.composeapp.generated.resources.shortcut_amount_label
import lasr.composeapp.generated.resources.shortcut_change
import lasr.composeapp.generated.resources.shortcut_choose_contact
import lasr.composeapp.generated.resources.shortcut_comment_label
import lasr.composeapp.generated.resources.shortcut_contact_label
import lasr.composeapp.generated.resources.shortcut_contact_search_label
import lasr.composeapp.generated.resources.shortcut_currency_label
import lasr.composeapp.generated.resources.shortcut_edit
import lasr.composeapp.generated.resources.shortcut_no_contacts
import lasr.composeapp.generated.resources.shortcut_no_matching_contacts
import lasr.composeapp.generated.resources.shortcut_title_label
import lasr.composeapp.generated.resources.shortcuts_add
import lasr.composeapp.generated.resources.shortcuts_empty
import lasr.composeapp.generated.resources.shortcuts_title
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.domain.format.rememberAmountFormatter
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.domain.model.PaymentConfirmationMode
import xyz.lilsus.papp.domain.model.PaymentPreferences
import xyz.lilsus.papp.presentation.common.AppListDefaults
import xyz.lilsus.papp.presentation.common.BackIconButton
import xyz.lilsus.papp.presentation.common.ContactListContent
import xyz.lilsus.papp.presentation.common.ContactListEntry
import xyz.lilsus.papp.presentation.common.ThresholdSlider
import xyz.lilsus.papp.presentation.common.numericKeyboardPlatformImeOptions
import xyz.lilsus.papp.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsSettingsScreen(
    state: PaymentsSettingsUiState,
    onBack: () -> Unit,
    onModeSelected: (PaymentConfirmationMode) -> Unit,
    onThresholdChanged: (Long) -> Unit,
    onConfirmManualEntryChanged: (Boolean) -> Unit,
    onConfirmShortcutPaymentsChanged: (Boolean) -> Unit,
    onAskToSaveNewContactsChanged: (Boolean) -> Unit,
    onVibrateOnScanChanged: (Boolean) -> Unit,
    onVibrateOnPaymentChanged: (Boolean) -> Unit,
    onAddShortcut: () -> Unit,
    onEditShortcut: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()
    val formatter = rememberAmountFormatter()
    val displayThreshold = DisplayAmount(state.thresholdSats, DisplayCurrency.Satoshi)
    val secondaryText = state.thresholdSecondaryEquivalent?.let {
        " (${formatter.format(it)})"
    } ?: ""
    val thresholdText = when (state.confirmationMode) {
        PaymentConfirmationMode.Above -> stringResource(
            Res.string.settings_payments_confirm_threshold,
            formatter.format(displayThreshold) + secondaryText
        )

        PaymentConfirmationMode.Always -> stringResource(Res.string.settings_payments_option_always)
    }

    Scaffold(
        modifier = modifier.testTag(MaestroTags.Settings.PAYMENTS_SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_payments)) },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Top
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.settings_payments_confirm_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = thresholdText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PaymentModeChips(
                        selected = state.confirmationMode,
                        onSelected = onModeSelected
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(MaestroTags.Settings.PAYMENTS_CONFIRM_MANUAL_ENTRY)
                            .heightIn(48.dp)
                            .toggleable(
                                value = state.confirmManualEntry,
                                role = Role.Switch,
                                onValueChange = onConfirmManualEntryChanged
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                Res.string.settings_payments_confirm_manual_entry
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        )
                        Switch(
                            checked = state.confirmManualEntry,
                            onCheckedChange = null
                        )
                    }
                    if (state.confirmationMode == PaymentConfirmationMode.Above) {
                        ThresholdSlider(
                            thresholdSats = state.thresholdSats,
                            onThresholdChanged = onThresholdChanged
                        )
                    } else {
                        // keep layout height consistent
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ShortcutSettingsSection(
                shortcuts = state.shortcuts,
                confirmShortcutPayments = state.confirmShortcutPayments,
                onConfirmShortcutPaymentsChanged = onConfirmShortcutPaymentsChanged,
                onAddShortcut = onAddShortcut,
                onEditShortcut = onEditShortcut
            )

            Spacer(modifier = Modifier.height(16.dp))

            ContactsPaymentSettingsSection(
                askToSaveNewContacts = state.askToSaveNewContacts,
                onAskToSaveNewContactsChanged = onAskToSaveNewContactsChanged
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.settings_payments_haptics_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(48.dp)
                            .toggleable(
                                value = state.vibrateOnScan,
                                role = Role.Switch,
                                onValueChange = onVibrateOnScanChanged
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_payments_haptics_scan),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        )
                        Switch(
                            checked = state.vibrateOnScan,
                            onCheckedChange = null
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(48.dp)
                            .toggleable(
                                value = state.vibrateOnPayment,
                                role = Role.Switch,
                                onValueChange = onVibrateOnPaymentChanged
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                Res.string.settings_payments_haptics_payment
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        )
                        Switch(
                            checked = state.vibrateOnPayment,
                            onCheckedChange = null
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutSettingsEditorScreen(
    state: ShortcutSettingsEditor?,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContactChange: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: () -> Unit,
    onCommentChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.testTag(MaestroTags.Settings.PAYMENTS_SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state?.shortcutId == null) {
                                Res.string.shortcuts_add
                            } else {
                                Res.string.shortcut_edit
                            }
                        )
                    )
                },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        state?.let { editor ->
            ShortcutSettingsEditorContent(
                state = editor,
                onTitleChange = onTitleChange,
                onContactChange = onContactChange,
                onAmountChange = onAmountChange,
                onCurrencyChange = onCurrencyChange,
                onCommentChange = onCommentChange,
                onSave = if (editor.shortcutId == null) onSave else null,
                onDelete = editor.shortcutId?.let { shortcutId ->
                    { onDelete(shortcutId) }
                },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutContactPickerScreen(
    state: ShortcutContactPickerUiState,
    selectedContactId: String?,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onContactSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.shortcut_choose_contact)) },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        ShortcutContactPickerContent(
            query = state.query,
            options = state.options,
            selectedContactId = selectedContactId,
            onQueryChange = onQueryChange,
            onContactSelected = onContactSelected,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(AppListDefaults.ScreenPadding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutCurrencyPickerScreen(
    selectedCode: String,
    searchQuery: String,
    options: List<CurrencyOption>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.shortcut_currency_label)) },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        CurrencyPickerContent(
            selectedCode = selectedCode,
            searchQuery = searchQuery,
            options = options,
            onQueryChange = onQueryChange,
            onCurrencySelected = onCurrencySelected,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(AppListDefaults.ScreenPadding)
        )
    }
}

@Composable
private fun ContactsPaymentSettingsSection(
    askToSaveNewContacts: Boolean,
    onAskToSaveNewContactsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.settings_contacts),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MaestroTags.Settings.PAYMENTS_ASK_SAVE_CONTACTS)
                    .heightIn(48.dp)
                    .toggleable(
                        value = askToSaveNewContacts,
                        role = Role.Switch,
                        onValueChange = onAskToSaveNewContactsChanged
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.settings_payments_ask_save_contacts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                )
                Switch(
                    checked = askToSaveNewContacts,
                    onCheckedChange = null
                )
            }
        }
    }
}

@Composable
private fun ShortcutSettingsSection(
    shortcuts: List<ShortcutSettingsItem>,
    confirmShortcutPayments: Boolean,
    onConfirmShortcutPaymentsChanged: (Boolean) -> Unit,
    onAddShortcut: () -> Unit,
    onEditShortcut: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.shortcuts_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(48.dp)
                    .toggleable(
                        value = confirmShortcutPayments,
                        role = Role.Switch,
                        onValueChange = onConfirmShortcutPaymentsChanged
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.settings_payments_confirm_shortcuts),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                )
                Switch(
                    checked = confirmShortcutPayments,
                    onCheckedChange = null
                )
            }
            Button(onClick = onAddShortcut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(Res.string.shortcuts_add),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (shortcuts.isEmpty()) {
                Text(
                    text = stringResource(Res.string.shortcuts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            shortcuts.forEach { item ->
                ShortcutSettingsRow(
                    item = item,
                    onClick = { onEditShortcut(item.id) }
                )
            }
        }
    }
}

@Composable
private fun ShortcutSettingsRow(item: ShortcutSettingsItem, onClick: () -> Unit) {
    val rowContentColor = MaterialTheme.colorScheme.onSecondaryContainer
    SettingsSurfaceRow(onClick = onClick) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = rowContentColor
            )
            Text(
                text = listOfNotNull(
                    item.amountText,
                    item.contactName,
                    item.comment
                ).joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
                color = rowContentColor.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = rowContentColor.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun ShortcutSettingsEditorContent(
    state: ShortcutSettingsEditor,
    onTitleChange: (String) -> Unit,
    onContactChange: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: () -> Unit,
    onCommentChange: (String) -> Unit,
    onSave: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val selectedContact = state.selectedContact ?: return
    val currencyInfo = CurrencyCatalog.infoFor(state.currencyCode)
    val currencyLabel = stringResource(currencyInfo.nameRes)
    val focusManager = LocalFocusManager.current
    val finishAmountEditing = { focusManager.clearFocus(force = true) }
    val doneLabel = stringResource(Res.string.keyboard_done)

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle(stringResource(Res.string.shortcut_contact_label))
        ShortcutSelectedContactRow(
            option = selectedContact,
            onClick = onContactChange
        )
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.shortcut_title_label)) },
            singleLine = true
        )
        OutlinedTextField(
            value = state.comment,
            onValueChange = onCommentChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.shortcut_comment_label)) },
            singleLine = true
        )
        OutlinedTextField(
            value = state.amount,
            onValueChange = onAmountChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.shortcut_amount_label)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
                platformImeOptions = numericKeyboardPlatformImeOptions(
                    doneLabel = doneLabel,
                    onDone = finishAmountEditing
                )
            ),
            keyboardActions = KeyboardActions(
                onDone = { finishAmountEditing() }
            ),
            singleLine = true
        )
        SectionTitle(stringResource(Res.string.shortcut_currency_label))
        ShortcutSelectedCurrencyRow(
            option = CurrencyOption(code = currencyInfo.code, label = currencyLabel),
            onClick = onCurrencyChange
        )
        state.error?.let { ErrorText(it) }
        onSave?.let { save ->
            Button(onClick = save, modifier = Modifier.fillMaxWidth()) {
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
private fun SettingsSurfaceRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun ShortcutSelectedCurrencyRow(option: CurrencyOption, onClick: () -> Unit) {
    val changeLabel = stringResource(Res.string.shortcut_change)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = changeLabel,
                onClick = onClick
            ),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = option.code,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = changeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ShortcutSelectedContactRow(option: ShortcutContactOption, onClick: () -> Unit) {
    val changeLabel = stringResource(Res.string.shortcut_change)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = changeLabel,
                onClick = onClick
            ),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
            Text(
                text = changeLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ShortcutContactPickerContent(
    query: String,
    options: List<ShortcutContactOption>,
    selectedContactId: String?,
    onQueryChange: (String) -> Unit,
    onContactSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ContactListContent(
        contacts = options.map { it.toContactListEntry() },
        onContactClick = { onContactSelected(it.id) },
        modifier = modifier,
        showSearchBar = true,
        searchQuery = query,
        onSearchQueryChange = onQueryChange,
        searchLabel = stringResource(Res.string.shortcut_contact_search_label),
        selectedContactId = selectedContactId,
        showSelectedIndicator = true,
        emptyMessage = stringResource(
            if (query.isBlank()) {
                Res.string.shortcut_no_contacts
            } else {
                Res.string.shortcut_no_matching_contacts
            }
        )
    )
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
private fun ErrorText(message: String) {
    Text(
        text = message.ifBlank { stringResource(Res.string.contacts_invalid_address) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun PaymentModeChips(
    selected: PaymentConfirmationMode,
    onSelected: (PaymentConfirmationMode) -> Unit
) {
    val options = listOf(
        PaymentConfirmationMode.Always to Res.string.settings_payments_option_always,
        PaymentConfirmationMode.Above to Res.string.settings_payments_option_above
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.forEach { (mode, labelRes) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                modifier = Modifier.testTag(mode.testTag()),
                label = { Text(stringResource(labelRes)) }
            )
        }
    }
}

private fun PaymentConfirmationMode.testTag(): String = when (this) {
    PaymentConfirmationMode.Always -> MaestroTags.Settings.PAYMENTS_CONFIRMATION_MODE_ALWAYS
    PaymentConfirmationMode.Above -> MaestroTags.Settings.PAYMENTS_CONFIRMATION_MODE_ABOVE
}

private fun ShortcutContactOption.toContactListEntry(): ContactListEntry = ContactListEntry(
    id = id,
    displayName = displayName,
    address = address
)

@Preview
@Composable
private fun PaymentsSettingsScreenPreview() {
    AppTheme {
        PaymentsSettingsScreen(
            state = PaymentsSettingsUiState(
                confirmationMode = PaymentConfirmationMode.Above,
                thresholdSats = PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS,
                confirmManualEntry = true,
                vibrateOnScan = true,
                vibrateOnPayment = true
            ),
            onBack = {},
            onModeSelected = {},
            onThresholdChanged = {},
            onConfirmManualEntryChanged = {},
            onConfirmShortcutPaymentsChanged = {},
            onAskToSaveNewContactsChanged = {},
            onVibrateOnScanChanged = {},
            onVibrateOnPaymentChanged = {},
            onAddShortcut = {},
            onEditShortcut = {}
        )
    }
}
