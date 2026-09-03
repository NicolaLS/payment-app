package xyz.lilsus.raylsuite.feature.paymenthub.host

import org.jetbrains.compose.resources.getString
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_save_prompt_body
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_save_prompt_not_now
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_save_prompt_save
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_save_prompt_title
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_name_label

/** Native presentation values for the optional save-target sheet shown by the Scan tab. */
data class NativeHubSavePromptPresentation(
    val title: String,
    val body: String,
    val nameLabel: String,
    val targetName: String,
    val dismissTitle: String,
    val saveTitle: String
)

suspend fun HubSavePrompt.toNativePresentation(): NativeHubSavePromptPresentation =
    NativeHubSavePromptPresentation(
        title = getString(Res.string.hub_save_prompt_title),
        body = getString(Res.string.hub_save_prompt_body, address.full),
        nameLabel = getString(Res.string.hub_target_name_label),
        targetName = title,
        dismissTitle = getString(Res.string.hub_save_prompt_not_now),
        saveTitle = getString(Res.string.hub_save_prompt_save)
    )
