package xyz.lilsus.raylsuite.feature.paymenthub.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.ui.platform.enableTestTagsAsResourceId
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_empty
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_pick_member
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_save_prompt_body
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_save_prompt_not_now
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_save_prompt_save
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_save_prompt_title
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_name_label
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubItemGlyph
import xyz.lilsus.raylsuite.feature.paymenthub.ui.HubItemRow

/** Expanded group: the user chooses exactly one member. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubGroupBottomSheet(
    sheet: HubGroupSheet,
    onMemberSelected: (HubItemId) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.enableTestTagsAsResourceId()
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .testTag(PaymentHubTestTags.GROUP_SHEET)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HubItemGlyph(item = sheet.group)
                Column {
                    Text(
                        text = sheet.group.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = stringResource(Res.string.hub_group_pick_member),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (sheet.members.isEmpty()) {
                Text(
                    text = stringResource(Res.string.hub_group_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sheet.members, key = { it.id.value }) { member ->
                        HubItemRow(
                            item = member,
                            onClick = { onMemberSelected(member.id) },
                            testTag = PaymentHubTestTags.item(member.id)
                        )
                    }
                }
            }
        }
    }
}

/** Offered after a successful payment to an address that is not a saved target yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubSavePromptBottomSheet(
    prompt: HubSavePrompt,
    onTitleChange: (String) -> Unit,
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
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(PaymentHubTestTags.SAVE_PROMPT)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding()
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(Res.string.hub_save_prompt_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(Res.string.hub_save_prompt_body, prompt.address.full),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = prompt.title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.hub_target_name_label)) },
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.hub_save_prompt_not_now))
                }
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text(stringResource(Res.string.hub_save_prompt_save))
                }
            }
        }
    }
}

object PaymentHubTestTags {
    const val GROUP_SHEET = "payment_hub_group_sheet"
    const val SAVE_PROMPT = "payment_hub_save_prompt"
    const val CANVAS = "payment_hub_canvas"
    const val CANVAS_ARRANGE = "payment_hub_canvas_arrange"
    const val CANVAS_LIBRARY = "payment_hub_canvas_library"
    const val LIBRARY = "payment_hub_library"
    const val LIBRARY_ADD = "payment_hub_library_add"
    const val TARGET_EDITOR = "payment_hub_target_editor"
    const val GROUP_EDITOR = "payment_hub_group_editor"

    fun item(id: HubItemId): String = "payment_hub_item_" + id.value.stableTagToken()
}

private fun String.stableTagToken(): String = lowercase()
    .map { character -> if (character.isLetterOrDigit()) character else '-' }
    .joinToString(separator = "")
    .trim('-')
    .ifBlank { "item" }
