package xyz.lilsus.raylsuite.feature.paymenthub.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.core.ui.keyboard.doneKeyboardPlatformImeOptions
import xyz.lilsus.raylsuite.feature.paymenthub.HubAccent
import xyz.lilsus.raylsuite.feature.paymenthub.HubIcon
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_editor_delete
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_editor_save
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_error_enter_amount
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_error_enter_title
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_error_invalid_address
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_error_whole_amount
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_keyboard_done
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_address_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_amount_fiat_hint
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_amount_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_amount_mode_ask
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_amount_mode_preset
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_comment_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_currency_content_description
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_editor_edit
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_editor_new
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_groups_empty
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_groups_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_name_label
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubTestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectTargetEditorScreen(
    state: DirectTargetEditorState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onAmountModeChange: (TargetAmountMode) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onCommentChange: (String) -> Unit,
    onIconChange: (HubIcon?) -> Unit,
    onAccentChange: (HubAccent?) -> Unit,
    onPinnedChange: (Boolean) -> Unit,
    onGroupToggle: (HubItemId) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val focusManager = LocalFocusManager.current
    val doneLabel = stringResource(Res.string.hub_keyboard_done)
    Scaffold(
        modifier = modifier.testTag(PaymentHubTestTags.TARGET_EDITOR),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                Res.string.hub_target_editor_new
                            } else {
                                Res.string.hub_target_editor_edit
                            }
                        )
                    )
                },
                navigationIcon = { BackIconButton(onClick = onBack) },
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
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.hub_target_name_label)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )
            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.hub_target_address_label)) },
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                singleLine = true
            )
            EditorSectionTitle(stringResource(Res.string.hub_target_amount_label))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TargetAmountMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.amountMode == mode,
                        onClick = { onAmountModeChange(mode) },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TargetAmountMode.entries.size
                            )
                    ) {
                        Text(stringResource(mode.label))
                    }
                }
            }
            if (state.amountMode == TargetAmountMode.Preset) {
                val fiatHint =
                    if (state.currency.currency is DisplayCurrency.Fiat) {
                        stringResource(Res.string.hub_target_amount_fiat_hint, state.currency.code)
                    } else {
                        null
                    }
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.hub_target_amount_label)) },
                    trailingIcon = {
                        CurrencyDropdown(
                            selectedCode = state.currency.code,
                            onCurrencySelected = onCurrencyChange
                        )
                    },
                    supportingText = fiatHint?.let { { Text(it) } },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                            platformImeOptions =
                                doneKeyboardPlatformImeOptions(
                                    doneLabel = doneLabel,
                                    onDone = { focusManager.clearFocus(force = true) }
                                )
                        ),
                    keyboardActions =
                        KeyboardActions(onDone = { focusManager.clearFocus(force = true) }),
                    singleLine = true
                )
            }
            OutlinedTextField(
                value = state.comment,
                onValueChange = onCommentChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.hub_target_comment_label)) },
                singleLine = true
            )
            AppearancePickers(
                icon = state.icon,
                accent = state.accent,
                previewText = state.title.take(1).uppercase().ifEmpty { "?" },
                onIconSelected = onIconChange,
                onAccentSelected = onAccentChange
            )
            PinToggleRow(pinned = state.pinned, onPinnedChange = onPinnedChange)
            EditorSectionTitle(stringResource(Res.string.hub_target_groups_label))
            if (state.groups.isEmpty()) {
                Text(
                    text = stringResource(Res.string.hub_target_groups_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                GroupChips(state = state, onGroupToggle = onGroupToggle)
            }
            state.error?.let { error -> EditorErrorText(stringResource(error.resource)) }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.hub_editor_save))
            }
            if (!state.isNew) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text(
                        text = stringResource(Res.string.hub_editor_delete),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupChips(state: DirectTargetEditorState, onGroupToggle: (HubItemId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.groups.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { group ->
                    FilterChip(
                        selected = group.id in state.groupIds,
                        onClick = { onGroupToggle(group.id) },
                        label = { Text(group.title, maxLines = 1) },
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp)
                    )
                }
                repeat(2 - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CurrencyDropdown(selectedCode: String, onCurrencySelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val description =
        stringResource(Res.string.hub_target_currency_content_description, selectedCode)
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = description
            },
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(selectedCode)
            Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CurrencyCatalog.supportedCodes.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        expanded = false
                        onCurrencySelected(code)
                    }
                )
            }
        }
    }
}

private val TargetAmountMode.label: StringResource
    get() =
        when (this) {
            TargetAmountMode.AskEveryTime -> Res.string.hub_target_amount_mode_ask
            TargetAmountMode.Preset -> Res.string.hub_target_amount_mode_preset
        }

private val TargetEditorError.resource: StringResource
    get() =
        when (this) {
            TargetEditorError.EnterTitle -> Res.string.hub_error_enter_title
            TargetEditorError.InvalidAddress -> Res.string.hub_error_invalid_address
            TargetEditorError.EnterAmount -> Res.string.hub_error_enter_amount
            TargetEditorError.WholeAmountRequired -> Res.string.hub_error_whole_amount
        }
