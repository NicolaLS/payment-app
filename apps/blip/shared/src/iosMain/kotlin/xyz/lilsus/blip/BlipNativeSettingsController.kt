package xyz.lilsus.blip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import xyz.lilsus.blip.feature.walletsettings.BlinkWalletSettingsViewModel
import xyz.lilsus.blip.feature.walletsettings.nativeBlinkWalletSettingsText
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.raylsuite.feature.languagesettings.LanguageRepository

data class BlipNativeWalletSettingsSnapshot(
    val refreshTitle: String,
    val isRefreshing: Boolean,
    val statusMessage: String?,
    val statusIsError: Boolean,
    val removeTitle: String,
    val removeDialogTitle: String,
    val removeDialogDescription: String,
    val removeConfirmTitle: String,
    val removeCancelTitle: String
)

/** Blip-owned presentation boundary for the optional actions in its native Settings tab. */
class BlipNativeSettingsController internal constructor(
    blinkWallet: BlinkWallet,
    private val languageRepository: LanguageRepository,
    private val onRemoveWallet: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val viewModel = BlinkWalletSettingsViewModel(blinkWallet)

    fun observe(onChange: (BlipNativeWalletSettingsSnapshot) -> Unit): () -> Unit {
        val job =
            scope.launch {
                combine(viewModel.uiState, languageRepository.preference) { state, _ -> state }
                    .collect { state ->
                        val text = nativeBlinkWalletSettingsText(state)
                        onChange(
                            BlipNativeWalletSettingsSnapshot(
                                refreshTitle = text.refreshTitle,
                                isRefreshing = state.isRefreshing,
                                statusMessage = text.statusMessage,
                                statusIsError = text.statusIsError,
                                removeTitle = text.removeTitle,
                                removeDialogTitle = text.removeDialogTitle,
                                removeDialogDescription = text.removeDialogDescription,
                                removeConfirmTitle = text.removeConfirmTitle,
                                removeCancelTitle = text.removeCancelTitle
                            )
                        )
                    }
            }
        return { job.cancel() }
    }

    fun refreshConnection() {
        viewModel.refreshConnection()
    }

    fun removeWallet() {
        onRemoveWallet()
    }

    fun clear() {
        viewModel.clear()
        scope.cancel()
    }
}
