package xyz.lilsus.lasr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import xyz.lilsus.lasr.feature.walletdetails.nativeNwcWalletDetailsText
import xyz.lilsus.lasr.integration.nwc.NwcWalletConnection
import xyz.lilsus.raylsuite.feature.walletmanagement.nativeWalletManagementText

data class LasrNativeWalletSettingsSnapshot(
    val settingsTitle: String,
    val settingsSubtitle: String,
    val screenTitle: String,
    val emptyDescription: String,
    val addTitle: String,
    val removeTitle: String,
    val removeConfirmationTitle: String,
    val removeConfirmationBody: String,
    val cancelTitle: String,
    val walletId: String?,
    val walletTitle: String?,
    val walletDetails: List<String>,
    val detailsTitle: String,
    val walletTypeLabel: String,
    val walletType: String,
    val connectionIdLabel: String,
    val walletFlowPresented: Boolean,
    val isWorking: Boolean
)

class LasrNativeWalletSettingsController internal constructor(
    private val runtime: LasrRuntime,
    private val onboarding: LasrNativeOnboardingController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val snapshot = MutableStateFlow<LasrNativeWalletSettingsSnapshot?>(null)
    private var isWorking = false
    private var walletFlowPrepared = false

    init {
        scope.launch {
            combine(
                runtime.nwcWallet.connection,
                runtime.settingsWalletFlow,
                runtime.languageRepository.preference
            ) { _, walletFlowPresented, _ -> walletFlowPresented }
                .collect { walletFlowPresented ->
                    if (walletFlowPresented && !walletFlowPrepared) {
                        walletFlowPrepared = true
                        onboarding.startSettingsWalletFlow()
                    } else if (!walletFlowPresented) {
                        walletFlowPrepared = false
                    }
                    publishSnapshot()
                }
        }
    }

    fun observe(onChange: (LasrNativeWalletSettingsSnapshot) -> Unit): () -> Unit {
        val job = scope.launch { snapshot.filterNotNull().collect(onChange) }
        return { job.cancel() }
    }

    fun requestWalletConnection() {
        walletFlowPrepared = true
        onboarding.startSettingsWalletFlow()
        runtime.requestSettingsWalletFlow()
    }

    fun finishWalletConnection() {
        walletFlowPrepared = false
        onboarding.finishSettingsWalletFlow()
    }

    fun removeWallet() {
        if (isWorking) return
        isWorking = true
        scope.launch {
            publishSnapshot()
            try {
                runtime.resetPaymentSession()
                runtime.nwcWallet.disconnect()
            } finally {
                isWorking = false
                publishSnapshot()
            }
        }
    }

    private suspend fun publishSnapshot() {
        val management = nativeWalletManagementText()
        val details = nativeNwcWalletDetailsText()
        val connection = runtime.nwcWallet.connection.value

        snapshot.value =
            LasrNativeWalletSettingsSnapshot(
                settingsTitle = management.settingsTitle,
                settingsSubtitle =
                    connection?.settingsSubtitle() ?: management.disconnectedSubtitle,
                screenTitle = management.screenTitle,
                emptyDescription = management.emptyDescription,
                addTitle = management.addTitle,
                removeTitle = management.removeTitle,
                removeConfirmationTitle = management.removeConfirmationTitle,
                removeConfirmationBody = management.removeConfirmationBody,
                cancelTitle = management.cancelTitle,
                walletId = connection?.walletPublicKey,
                walletTitle =
                    connection?.alias?.takeIf(String::isNotBlank)
                        ?: connection?.walletPublicKey,
                walletDetails = emptyList(),
                detailsTitle = details.title,
                walletTypeLabel = details.typeLabel,
                walletType = details.walletType,
                connectionIdLabel = details.connectionIdLabel,
                walletFlowPresented = runtime.settingsWalletFlow.value,
                isWorking = isWorking
            )
    }
}

private fun NwcWalletConnection.settingsSubtitle(): String {
    alias?.takeIf(String::isNotBlank)?.let { return it }
    return if (walletPublicKey.length <= 12) {
        walletPublicKey
    } else {
        "${walletPublicKey.take(6)}…${walletPublicKey.takeLast(4)}"
    }
}
