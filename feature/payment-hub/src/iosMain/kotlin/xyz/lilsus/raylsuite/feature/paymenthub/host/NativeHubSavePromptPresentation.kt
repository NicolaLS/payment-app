package xyz.lilsus.raylsuite.feature.paymenthub.host

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

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
        title = nativeString(
            NativeStringResource(table = "PaymentHub", key = "hub_save_prompt_title")
        ),
        body = nativeString(
            NativeStringResource(table = "PaymentHub", key = "hub_save_prompt_body"),
            address.full
        ),
        nameLabel = nativeString(
            NativeStringResource(table = "PaymentHub", key = "hub_target_name_label")
        ),
        targetName = title,
        dismissTitle = nativeString(
            NativeStringResource(table = "PaymentHub", key = "hub_save_prompt_not_now")
        ),
        saveTitle = nativeString(
            NativeStringResource(table = "PaymentHub", key = "hub_save_prompt_save")
        )
    )
