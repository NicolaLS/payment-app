package xyz.lilsus.raylsuite.feature.paymenthub.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.ui.components.AppListRow
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.feature.paymenthub.HubAccent
import xyz.lilsus.raylsuite.feature.paymenthub.HubIcon
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_add
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_move_down
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_move_up
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_remove
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_editor_delete
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_editor_save
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_error_enter_title
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_available_all_added
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_available_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_available_none
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_editor_edit
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_editor_new
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_members_empty
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_members_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_name_label
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubTestTags
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubGlyph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditorScreen(
    state: GroupEditorState,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onIconChange: (HubIcon?) -> Unit,
    onAccentChange: (HubAccent?) -> Unit,
    onPinnedChange: (Boolean) -> Unit,
    onAddMember: (HubItemId) -> Unit,
    onRemoveMember: (HubItemId) -> Unit,
    onMoveMember: (HubItemId, Int) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.testTag(PaymentHubTestTags.GROUP_EDITOR),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                Res.string.hub_group_editor_new
                            } else {
                                Res.string.hub_group_editor_edit
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
                label = { Text(stringResource(Res.string.hub_group_name_label)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine = true
            )
            AppearancePickers(
                icon = state.icon ?: HubIcon.Group,
                accent = state.accent,
                previewText = state.title.take(1).uppercase().ifEmpty { "?" },
                onIconSelected = onIconChange,
                onAccentSelected = onAccentChange
            )
            PinToggleRow(pinned = state.pinned, onPinnedChange = onPinnedChange)
            EditorSectionTitle(stringResource(Res.string.hub_group_members_label))
            if (state.members.isEmpty()) {
                Text(
                    text = stringResource(Res.string.hub_group_members_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            state.members.forEachIndexed { index, member ->
                MemberRow(member = member) {
                    IconButton(
                        onClick = { onMoveMember(member.id, -1) },
                        enabled = index > 0
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = stringResource(Res.string.hub_action_move_up)
                        )
                    }
                    IconButton(
                        onClick = { onMoveMember(member.id, 1) },
                        enabled = index < state.members.lastIndex
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = stringResource(Res.string.hub_action_move_down)
                        )
                    }
                    IconButton(onClick = { onRemoveMember(member.id) }) {
                        RemoveIcon()
                        // Content description lives on the button semantics below.
                    }
                }
            }
            EditorSectionTitle(stringResource(Res.string.hub_group_available_label))
            when {
                state.candidates.isEmpty() ->
                    Text(
                        text = stringResource(Res.string.hub_group_available_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                state.available.isEmpty() ->
                    Text(
                        text = stringResource(Res.string.hub_group_available_all_added),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
            }
            state.available.forEach { candidate ->
                MemberRow(member = candidate) {
                    IconButton(onClick = { onAddMember(candidate.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(Res.string.hub_action_add)
                        )
                    }
                }
            }
            state.error?.let { error ->
                EditorErrorText(
                    stringResource(
                        when (error) {
                            GroupEditorError.EnterTitle -> Res.string.hub_error_enter_title
                        }
                    )
                )
            }
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
private fun MemberRow(
    member: HubMemberOption,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    AppListRow(minHeight = 56.dp) {
        HubGlyph(
            icon = member.icon,
            accent = member.accent,
            fallbackText = member.title.take(1).uppercase(),
            size = 36.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = member.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        actions()
    }
}
