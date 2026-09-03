package xyz.lilsus.blip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import xyz.lilsus.blip.feature.walletsettings.BlinkWalletSettingsViewModel
import xyz.lilsus.blip.feature.walletsettings.NativeBlinkWalletSettingsText
import xyz.lilsus.blip.feature.walletsettings.nativeBlinkWalletSettingsText
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.blip.integration.blink.BlinkWalletCurrency
import xyz.lilsus.raylsuite.feature.languagesettings.LanguageRepository

data class BlipNativeWalletSettingsSnapshot(
    val fundingWalletTitle: String,
    val selectedFundingWalletTitle: String,
    val fundingWalletPickerTitle: String,
    val isLoadingFundingWallets: Boolean,
    val fundingWalletOptions: List<BlipNativeFundingWalletOption>,
    val fundingWalletErrorMessage: String?,
    val fundingWalletUnavailableMessage: String?,
    val fundingWalletLoadingTitle: String,
    val fundingWalletCloseTitle: String,
    val removeTitle: String,
    val removeDialogTitle: String,
    val removeDialogDescription: String,
    val removeConfirmTitle: String,
    val removeCancelTitle: String
)

data class BlipNativeFundingWalletOption(val id: String, val title: String, val selected: Boolean)

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
                                fundingWalletTitle = text.fundingWalletTitle,
                                selectedFundingWalletTitle =
                                    state.selectedWallet?.currency?.let { currency ->
                                        currency.title(text)
                                    } ?: text.chooseFundingWalletTitle,
                                fundingWalletPickerTitle = text.fundingWalletPickerTitle,
                                isLoadingFundingWallets = state.isLoading,
                                fundingWalletOptions =
                                    state.wallets.map { wallet ->
                                        BlipNativeFundingWalletOption(
                                            id = wallet.id,
                                            title = wallet.currency.title(text),
                                            selected = state.selectedWallet?.id == wallet.id
                                        )
                                    },
                                fundingWalletErrorMessage = text.errorMessage,
                                fundingWalletUnavailableMessage =
                                    text.unavailableMessage.takeIf {
                                        state.selectionUnavailable
                                    },
                                fundingWalletLoadingTitle = text.loadingTitle,
                                fundingWalletCloseTitle = text.closeTitle,
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

    fun loadFundingWallets() {
        viewModel.loadFundingWallets()
    }

    fun selectFundingWallet(id: String) {
        viewModel.selectFundingWallet(id)
    }

    fun removeWallet() {
        onRemoveWallet()
    }

    fun clear() {
        viewModel.clear()
        scope.cancel()
    }
}

private fun BlinkWalletCurrency.title(text: NativeBlinkWalletSettingsText): String = when (this) {
    BlinkWalletCurrency.BTC -> text.bitcoinTitle
    BlinkWalletCurrency.USD -> text.stablesatsTitle
}
