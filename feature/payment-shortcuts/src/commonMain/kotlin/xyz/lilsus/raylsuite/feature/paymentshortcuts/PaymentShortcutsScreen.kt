package xyz.lilsus.raylsuite.feature.paymentshortcuts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.ui.components.AppListDefaults
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.core.ui.keyboard.doneKeyboardPlatformImeOptions
import xyz.lilsus.raylsuite.feature.contacts.ContactListContent
import xyz.lilsus.raylsuite.feature.contacts.ContactListEntry
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPicker
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.keyboard_done
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_amount_label
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_change
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_choose_contact
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_comment_label
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_contact_label
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_contact_search_label
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_currency_label
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_default_title
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_delete
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_edit
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_error_enter_amount
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_error_enter_title
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_error_select_contact
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_error_whole_amount
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_fiat_amount_support
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_no_contacts
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_no_matching_contacts
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_save
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_selected_currency_content_description
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcut_title_label
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcuts_add
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcuts_empty
import xyz.lilsus.raylsuite.feature.paymentshortcuts.generated.resources.shortcuts_title

@Composable
fun PaymentShortcutsSection(
    shortcuts: List<PaymentShortcutItem>,
    onAddShortcut: () -> Unit,
    onEditShortcut: (String) -> Unit,
    modifier: Modifier = Modifier,
    additionalSettings: @Composable ColumnScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier =
                Modifier
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
            additionalSettings()
            Button(
                onClick = onAddShortcut,
                modifier = Modifier.fillMaxWidth()
            ) {
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
            } else {
                shortcuts.forEach { shortcut ->
                    PaymentShortcutRow(
                        shortcut = shortcut,
                        onClick = { onEditShortcut(shortcut.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentShortcutRow(shortcut: PaymentShortcutItem, onClick: () -> Unit) {
    val amountFormatter = rememberAmountFormatter()
    val currency = CurrencyCatalog.infoFor(shortcut.amount.normalizedCurrencyCode).currency
    val amount = amountFormatter.format(DisplayAmount(shortcut.amount.minor, currency))
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ShortcutSurfaceRow(onClick = onClick) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = shortcut.title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text =
                    listOfNotNull(
                        amount,
                        shortcut.contactName,
                        shortcut.comment
                    ).joinToString(" - "),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.72f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentShortcutEditorScreen(
    state: PaymentShortcutEditorState?,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContactChange: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: () -> Unit,
    onCommentChange: (String) -> Unit,
    onSave: (defaultTitle: String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.testTag(PaymentShortcutsTestTags.EDITOR),
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
            PaymentShortcutEditorContent(
                state = editor,
                onTitleChange = onTitleChange,
                onContactChange = onContactChange,
                onAmountChange = onAmountChange,
                onCurrencyChange = onCurrencyChange,
                onCommentChange = onCommentChange,
                onSave = onSave.takeIf { editor.shortcutId == null },
                onDelete = onDelete.takeIf { editor.shortcutId != null },
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
private fun PaymentShortcutEditorContent(
    state: PaymentShortcutEditorState,
    onTitleChange: (String) -> Unit,
    onContactChange: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: () -> Unit,
    onCommentChange: (String) -> Unit,
    onSave: ((String) -> Unit)?,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val currency = CurrencyCatalog.infoFor(state.currencyCode)
    val focusManager = LocalFocusManager.current
    val commentFocusRequester = remember { FocusRequester() }
    val amountFocusRequester = remember { FocusRequester() }
    val finishAmountEditing = { focusManager.clearFocus(force = true) }
    val doneLabel = stringResource(Res.string.keyboard_done)
    val amountSupportingText =
        if (currency.currency is DisplayCurrency.Fiat) {
            stringResource(Res.string.shortcut_fiat_amount_support, currency.code)
        } else {
            null
        }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionTitle(stringResource(Res.string.shortcut_contact_label))
        val selectedContact = state.selectedContact
        if (selectedContact == null) {
            OutlinedButton(
                onClick = onContactChange,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.shortcut_choose_contact))
            }
        } else {
            ShortcutSelectedContactRow(
                option = selectedContact,
                onClick = onContactChange
            )
        }
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.shortcut_title_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions =
                KeyboardActions(
                    onNext = { commentFocusRequester.requestFocus() }
                ),
            singleLine = true
        )
        OutlinedTextField(
            value = state.comment,
            onValueChange = onCommentChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(commentFocusRequester),
            label = { Text(stringResource(Res.string.shortcut_comment_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions =
                KeyboardActions(
                    onNext = { amountFocusRequester.requestFocus() }
                ),
            singleLine = true
        )
        OutlinedTextField(
            value = state.amount,
            onValueChange = onAmountChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocusRequester),
            label = { Text(stringResource(Res.string.shortcut_amount_label)) },
            trailingIcon = {
                ShortcutAmountCurrencyButton(
                    currencyCode = currency.code,
                    onClick = onCurrencyChange
                )
            },
            supportingText =
                amountSupportingText?.let { text ->
                    { Text(text) }
                },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    platformImeOptions =
                        doneKeyboardPlatformImeOptions(
                            doneLabel = doneLabel,
                            onDone = finishAmountEditing
                        )
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = { finishAmountEditing() }
                ),
            singleLine = true
        )
        state.error?.let { error ->
            ShortcutEditorErrorText(error)
        }
        onSave?.let { save ->
            val defaultTitle =
                state.selectedContact
                    ?.let { contact ->
                        stringResource(
                            Res.string.shortcut_default_title,
                            contact.displayName
                        )
                    }.orEmpty()
            Button(
                onClick = { save(defaultTitle) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.shortcut_save))
            }
        }
        onDelete?.let { delete ->
            OutlinedButton(
                onClick = delete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Text(
                    text = stringResource(Res.string.shortcut_delete),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentShortcutContactPickerScreen(
    state: PaymentShortcutsUiState,
    selectedContactId: String?,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onContactSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.testTag(PaymentShortcutsTestTags.CONTACT_PICKER),
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
        ContactListContent(
            contacts = state.contactOptions.map(PaymentShortcutContactOption::toListEntry),
            onContactClick = { contact -> onContactSelected(contact.id) },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .navigationBarsPadding()
                    .padding(AppListDefaults.ScreenPadding),
            showSearchBar = true,
            searchQuery = state.contactSearch,
            onSearchQueryChange = onSearchChange,
            searchLabel = stringResource(Res.string.shortcut_contact_search_label),
            selectedContactId = selectedContactId,
            showSelectedIndicator = true,
            emptyMessage =
                stringResource(
                    if (state.contactSearch.isBlank()) {
                        Res.string.shortcut_no_contacts
                    } else {
                        Res.string.shortcut_no_matching_contacts
                    }
                )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentShortcutCurrencyPickerScreen(
    selectedCode: String,
    searchQuery: String,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.testTag(PaymentShortcutsTestTags.CURRENCY_PICKER),
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
        CurrencyPicker(
            selectedCode = selectedCode,
            searchQuery = searchQuery,
            onQueryChange = onSearchChange,
            onCurrencySelected = onCurrencySelected,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .navigationBarsPadding()
                    .padding(AppListDefaults.ScreenPadding)
        )
    }
}

@Composable
private fun ShortcutAmountCurrencyButton(currencyCode: String, onClick: () -> Unit) {
    val description =
        stringResource(
            Res.string.shortcut_selected_currency_content_description,
            currencyCode
        )
    TextButton(
        onClick = onClick,
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(currencyCode)
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = null
        )
    }
}

@Composable
private fun ShortcutSelectedContactRow(option: PaymentShortcutContactOption, onClick: () -> Unit) {
    val changeLabel = stringResource(Res.string.shortcut_change)
    Surface(
        modifier =
            Modifier
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
            modifier =
                Modifier
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
private fun ShortcutSurfaceRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier =
                Modifier
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ShortcutEditorErrorText(error: PaymentShortcutEditorError) {
    Text(
        text = stringResource(error.stringResource),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

private val PaymentShortcutEditorError.stringResource: StringResource
    get() =
        when (this) {
            PaymentShortcutEditorError.NoContacts -> Res.string.shortcut_no_contacts

            PaymentShortcutEditorError.SelectContact -> Res.string.shortcut_error_select_contact

            PaymentShortcutEditorError.EnterAmount -> Res.string.shortcut_error_enter_amount

            PaymentShortcutEditorError.WholeAmountRequired ->
                Res.string.shortcut_error_whole_amount

            PaymentShortcutEditorError.EnterTitle -> Res.string.shortcut_error_enter_title
        }

private fun PaymentShortcutContactOption.toListEntry(): ContactListEntry = ContactListEntry(
    id = id,
    displayName = displayName,
    address = address
)

object PaymentShortcutsTestTags {
    const val EDITOR = "payment_shortcut_editor"
    const val CONTACT_PICKER = "payment_shortcut_contact_picker"
    const val CURRENCY_PICKER = "payment_shortcut_currency_picker"
}
