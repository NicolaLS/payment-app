package xyz.lilsus.raylsuite.feature.paymenthub.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.paymenthub.R
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasTileSize
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubTestTags
import xyz.lilsus.raylsuite.feature.paymenthub.render.hubInitials
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubMarkView
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubServiceMark
import xyz.lilsus.raylsuite.feature.paymenthub.ui.amountColor
import xyz.lilsus.raylsuite.feature.paymenthub.ui.amountText

/** Everything the compose-a-target flow can ask its host to do. */
data class NewTargetActions(
    val openContacts: () -> Unit,
    val openServices: () -> Unit,
    val selectContact: (String) -> Unit,
    val addManually: () -> Unit,
    val selectService: (String) -> Unit,
    val dismissComingSoon: () -> Unit,
    val updateQuery: (String) -> Unit,
    val updateTitle: (String) -> Unit,
    val updateAddress: (String) -> Unit,
    val selectAmount: (HubAmountChoice) -> Unit,
    val updateCustomAmount: (String) -> Unit,
    val selectCurrency: (String) -> Unit,
    val updateComment: (String) -> Unit,
    val selectSize: (CanvasTileSize) -> Unit,
    val submit: () -> Unit,
    val delete: () -> Unit,
    val back: () -> Unit
)

/**
 * Creates or edits one hub target. A person is two steps; the service catalogue only says its
 * packages are on the way, so nothing is ever half-created from it.
 *
 * [importButton] is an optional app-owned action pinned above the contact list. Blip supplies its
 * Blink contact import there; the other apps pass nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTargetFlow(
    state: NewTargetUiState,
    actions: NewTargetActions,
    modifier: Modifier = Modifier,
    importButton: (@Composable () -> Unit)? = null
) {
    NavigationEventHandler(
        state = rememberNavigationEventState(currentInfo = NewTargetNavigationInfo(state.view)),
        isForwardEnabled = false,
        isBackEnabled = true,
        onBackCompleted = actions.back
    )

    Scaffold(
        modifier = modifier.testTag(PaymentHubTestTags.NEW_TARGET),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text =
                            stringResource(
                                if (state.configure?.isEditing == true) {
                                    R.string.hub_new_edit_title
                                } else {
                                    R.string.hub_new_title
                                }
                            )
                    )
                },
                navigationIcon = { BackIconButton(onClick = actions.back) }
            )
        }
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
        ) {
            when (state.view) {
                NewTargetView.Launchpad -> Launchpad(state = state, actions = actions)

                NewTargetView.Contacts ->
                    ContactList(state = state, actions = actions, importButton = importButton)

                NewTargetView.Services -> ServiceList(state = state, actions = actions)

                NewTargetView.Configure ->
                    state.configure?.let { Configure(state = it, actions = actions) }
            }
        }
    }

    state.comingSoonService?.let { service ->
        AlertDialog(
            onDismissRequest = actions.dismissComingSoon,
            title = {
                Text(stringResource(R.string.hub_service_coming_soon_title, service.name))
            },
            text = { Text(stringResource(R.string.hub_service_coming_soon_body, service.name)) },
            confirmButton = {
                TextButton(onClick = actions.dismissComingSoon) {
                    Text(stringResource(R.string.hub_service_coming_soon_confirm))
                }
            }
        )
    }
}

@Composable
private fun Launchpad(state: NewTargetUiState, actions: NewTargetActions) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(LAUNCHPAD_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().height(LAUNCHPAD_GRID_HEIGHT)
        ) {
            item(key = "people") {
                LaunchpadActionCell(
                    label = stringResource(R.string.hub_new_section_people),
                    icon = Icons.Filled.People,
                    onClick = actions.openContacts
                )
            }
            state.featuredServices.forEach { service ->
                item(key = service.id) {
                    LaunchpadCell(
                        label = service.name,
                        onClick = { actions.selectService(service.id) }
                    ) {
                        HubServiceMark(initials = service.mark, size = LAUNCHPAD_MARK)
                    }
                }
            }
            item(key = "more") {
                LaunchpadActionCell(
                    label = stringResource(R.string.hub_new_more),
                    icon = Icons.Filled.MoreHoriz,
                    onClick = actions.openServices
                )
            }
        }
    }
}

@Composable
private fun LaunchpadCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mark: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().heightIn(min = 112.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            mark()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LaunchpadActionCell(label: String, icon: ImageVector, onClick: () -> Unit) {
    LaunchpadCell(label = label, onClick = onClick) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(LAUNCHPAD_MARK)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContactList(
    state: NewTargetUiState,
    actions: NewTargetActions,
    importButton: (@Composable () -> Unit)?
) {
    val matches = state.matchingContacts
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.hub_new_contacts_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp)
        )
        // These sit here, not in settings: this is the moment someone fails to find a name.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            importButton?.let { button ->
                Box(modifier = Modifier.weight(1f)) { button() }
            }
            OutlinedButton(
                onClick = actions.addManually,
                modifier = Modifier.weight(1f).testTag(PaymentHubTestTags.NEW_TARGET_MANUAL)
            ) {
                Text(stringResource(R.string.hub_new_add_manually))
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.updateQuery,
            singleLine = true,
            label = { Text(stringResource(R.string.hub_new_search)) },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
        if (matches.isEmpty()) {
            Text(
                text =
                    stringResource(
                        if (state.contacts.isEmpty()) {
                            R.string.hub_new_no_contacts
                        } else {
                            R.string.hub_new_no_matches
                        }
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
            ) {
                items(matches, key = HubContact::id) { contact ->
                    ContactRow(contact = contact, onClick = { actions.selectContact(contact.id) })
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: HubContact, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 12.dp)
    ) {
        HubMarkView(mark = contact.mark, size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = contact.address.full,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ServiceList(state: NewTargetUiState, actions: NewTargetActions) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.hub_new_services_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp)
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        ) {
            items(state.services, key = { it.id }) { service ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { actions.selectService(service.id) }
                            .padding(horizontal = 4.dp, vertical = 12.dp)
                ) {
                    HubServiceMark(initials = service.mark, size = 32.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = service.name,
                            style =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                            maxLines = 1
                        )
                        Text(
                            text = service.subtitle(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HubService.subtitle(): String = stringResource(
    R.string.hub_service_subtitle,
    stringResource(kind.label()),
    pluralStringResource(R.plurals.hub_service_option_count, optionCount, optionCount)
)

private fun HubServiceKind.label(): Int = when (this) {
    HubServiceKind.Mobile -> R.string.hub_service_kind_mobile
    HubServiceKind.EsimData -> R.string.hub_service_kind_esim
    HubServiceKind.Other -> R.string.hub_service_kind_other
}

@Composable
private fun Configure(state: NewTargetConfigureState, actions: NewTargetActions) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 22.dp)
        ) {
            Text(
                text = stringResource(R.string.hub_configure_title),
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = state.title,
                onValueChange = actions.updateTitle,
                singleLine = true,
                label = { Text(stringResource(R.string.hub_target_name_label)) },
                modifier = Modifier.fillMaxWidth().testTag(PaymentHubTestTags.CONFIGURE_NAME)
            )
            OutlinedTextField(
                value = state.address,
                onValueChange = actions.updateAddress,
                singleLine = true,
                label = { Text(stringResource(R.string.hub_target_address_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().testTag(PaymentHubTestTags.CONFIGURE_ADDRESS)
            )

            SectionLabel(stringResource(R.string.hub_target_amount_label))
            AmountChips(state = state, actions = actions)
            if (state.amount == HubAmountChoice.Custom) {
                CustomAmountField(state = state, actions = actions)
            }
            if (state.showsFiatHint) {
                Text(
                    text =
                        stringResource(
                            R.string.hub_target_amount_fiat_hint,
                            state.currencyCode
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = state.comment,
                onValueChange = actions.updateComment,
                singleLine = true,
                label = { Text(stringResource(R.string.hub_target_comment_label)) },
                modifier = Modifier.fillMaxWidth()
            )

            SectionLabel(stringResource(R.string.hub_configure_size))
            SizePicker(state = state, onSelect = actions.selectSize)
            Text(
                text = stringResource(state.size.hint()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.error?.let { error ->
                Text(
                    text = stringResource(error.message()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!state.isNew) {
                TextButton(onClick = actions.delete) {
                    Text(
                        text = stringResource(R.string.hub_configure_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        HorizontalDivider()
        Button(
            onClick = actions.submit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp)
                    .testTag(PaymentHubTestTags.CONFIGURE_SUBMIT)
        ) {
            Text(
                stringResource(
                    if (state.isNew) R.string.hub_configure_add else R.string.hub_configure_save
                )
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun AmountChips(state: NewTargetConfigureState, actions: NewTargetActions) {
    val formatter = rememberAmountFormatter()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        AmountChip(
            label = stringResource(R.string.hub_amount_ask_each_time),
            selected = state.amount == HubAmountChoice.AskEachTime,
            onClick = { actions.selectAmount(HubAmountChoice.AskEachTime) }
        )
        state.quickAmounts.forEach { amount ->
            val choice = HubAmountChoice.Quick(amount)
            AmountChip(
                label = formatter.format(amount),
                selected = state.amount == choice,
                onClick = { actions.selectAmount(choice) }
            )
        }
        AmountChip(
            label = stringResource(R.string.hub_amount_other),
            selected = state.amount == HubAmountChoice.Custom,
            onClick = { actions.selectAmount(HubAmountChoice.Custom) }
        )
    }
}

@Composable
private fun AmountChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surface
            },
        contentColor =
            if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        border =
            if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun CustomAmountField(state: NewTargetConfigureState, actions: NewTargetActions) {
    var expanded by remember { mutableStateOf(false) }
    val currencyDescription =
        stringResource(R.string.hub_target_currency_content_description, state.currencyCode)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = state.customAmount,
            onValueChange = actions.updateCustomAmount,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            label = { Text(stringResource(R.string.hub_target_amount_label)) },
            modifier = Modifier.weight(1f)
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier =
                    Modifier.semantics {
                        contentDescription = currencyDescription
                    }
            ) {
                Text(state.currencyCode)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CurrencyCatalog.supportedCodes.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(code) },
                        onClick = {
                            actions.selectCurrency(code)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/** This picker sets only the closed size; a container's open size follows what it holds. */
@Composable
private fun SizePicker(state: NewTargetConfigureState, onSelect: (CanvasTileSize) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        state.sizeOptions.forEach { size ->
            val selected = size == state.size
            Surface(
                onClick = { onSelect(size) },
                shape = MaterialTheme.shapes.small,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                border =
                    BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier.height(38.dp).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        SizeGlyph(size = size, selected = selected)
                    }
                    Text(
                        text = stringResource(size.label()),
                        style =
                            MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SizeGlyph(size: CanvasTileSize, selected: Boolean) {
    val color =
        if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.outline
        }
    val width = if (size.columns >= 2) 46.dp else 22.dp
    val height = if (size.rows >= 2) 38.dp else 22.dp
    Box(
        modifier =
            Modifier
                .width(width)
                .height(height)
                .background(color.copy(alpha = 0.18f), MaterialTheme.shapes.extraSmall)
                .padding(4.dp)
    ) {
        // A large tile draws its internal rows to read as "always open".
        if (size == CanvasTileSize.Large) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(color))
                }
            }
        }
    }
}

private fun CanvasTileSize.label(): Int = when (this) {
    CanvasTileSize.Small -> R.string.hub_size_small
    CanvasTileSize.Wide -> R.string.hub_size_wide
    CanvasTileSize.Large -> R.string.hub_size_large
}

private fun CanvasTileSize.hint(): Int = when (this) {
    CanvasTileSize.Small -> R.string.hub_size_hint_small
    CanvasTileSize.Wide -> R.string.hub_size_hint_wide
    CanvasTileSize.Large -> R.string.hub_size_hint_large
}

private fun NewTargetError.message(): Int = when (this) {
    NewTargetError.EnterName -> R.string.hub_error_enter_title
    NewTargetError.InvalidAddress -> R.string.hub_error_invalid_address
    NewTargetError.EnterAmount -> R.string.hub_error_enter_amount
    NewTargetError.WholeAmountRequired -> R.string.hub_error_whole_amount
}

private val LAUNCHPAD_MARK = 58.dp
private val LAUNCHPAD_GRID_HEIGHT = 356.dp
private const val LAUNCHPAD_COLUMNS = 2

private data class NewTargetNavigationInfo(val view: NewTargetView) : NavigationEventInfo()
