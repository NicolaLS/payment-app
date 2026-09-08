package xyz.lilsus.blip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import xyz.lilsus.blip.feature.onboarding.nativeBlipInstructionProgress
import xyz.lilsus.blip.feature.onboarding.nativeBlipOnboardingText
import xyz.lilsus.blip.feature.walletconnection.AddBlinkWalletEvent
import xyz.lilsus.blip.feature.walletconnection.AddBlinkWalletViewModel
import xyz.lilsus.blip.feature.walletconnection.nativeBlinkWalletConnectionText
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.blip.ui.nativeBlinkErrorMessageFor
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.model.PaymentConfirmationMode
import xyz.lilsus.raylsuite.core.model.PaymentPreferences
import xyz.lilsus.raylsuite.core.ui.format.currentAmountFormatter
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.onboarding.nativeOnboardingText
import xyz.lilsus.raylsuite.feature.onboarding.nativeOnboardingThresholdLabel

data class BlipNativeOnboardingPage(
    val title: String,
    val subtitle: String,
    val body: String,
    val imageName: String?
)

data class BlipNativeOnboardingSnapshot(
    val step: String,
    val canGoBack: Boolean,
    val stepIndex: Int,
    val stepCount: Int,
    val backTitle: String,
    val welcomeTitle: String,
    val welcomeSubtitle: String,
    val welcomeDescription: String,
    val getStartedTitle: String,
    val featurePages: List<BlipNativeOnboardingPage>,
    val featurePage: Int,
    val featuresNextTitle: String,
    val autoPayTitle: String,
    val autoPayBody: String,
    val autoPayAlwaysTitle: String,
    val autoPayThresholdTitle: String,
    val autoPayThresholdLabel: String,
    val autoPayHint: String,
    val autoPayNextTitle: String,
    val confirmationMode: String,
    val thresholdIndex: Int,
    val thresholdStepCount: Int,
    val agreementTitle: String,
    val agreementBody: String,
    val agreementCheckboxTitle: String,
    val agreementNextTitle: String,
    val hasAgreed: Boolean,
    val instructionsTitle: String,
    val instructionsIntro: String,
    val instructionPages: List<BlipNativeOnboardingPage>,
    val instructionPage: Int,
    val instructionProgress: String,
    val previousStepTitle: String,
    val nextStepTitle: String,
    val dashboardTitle: String,
    val dashboardUrl: String,
    val enterKeyTitle: String,
    val walletTitle: String,
    val walletDescription: String,
    val apiKeyLabel: String,
    val apiKeyPlaceholder: String,
    val showApiKeyTitle: String,
    val hideApiKeyTitle: String,
    val pasteTitle: String,
    val connectTitle: String,
    val apiKey: String,
    val canConnect: Boolean,
    val isConnecting: Boolean,
    val connectionError: String?
)

/**
 * Blip-owned state bridge for native onboarding. Kotlin retains provider behavior and settings;
 * SwiftUI receives only localized values to render and sends user intent back here.
 */
class BlipNativeOnboardingController internal constructor(
    private val onboarding: OnboardingViewModel,
    blinkWallet: BlinkWallet,
    languageChanges: Flow<*>,
    private val appName: String,
    private val welcomeCompleted: Boolean,
    private val connectionOnly: Boolean,
    private val onCompleted: () -> Unit,
    private val canConnectWallet: () -> Boolean,
    initiallyCompleted: Boolean
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val addWallet = AddBlinkWalletViewModel(blinkWallet)
    private val snapshot = MutableStateFlow<BlipNativeOnboardingSnapshot?>(null)
    private val completed = MutableStateFlow(initiallyCompleted)
    internal val completion: StateFlow<Boolean> = completed
    private var currentStep = when {
        connectionOnly -> STEP_WALLET
        welcomeCompleted -> STEP_FEATURES
        else -> STEP_WELCOME
    }
    private var currentInstructionPage = 0

    init {
        scope.launch {
            combine(onboarding.uiState, addWallet.uiState, languageChanges) { _, _, _ -> Unit }
                .collect { publishSnapshot() }
        }
        scope.launch {
            addWallet.events.collect { event ->
                when (event) {
                    AddBlinkWalletEvent.Success -> {
                        addWallet.updateApiKey("")
                        finish()
                    }

                    AddBlinkWalletEvent.Cancelled -> currentStep = STEP_INSTRUCTIONS
                }
                publishSnapshot()
            }
        }
    }

    fun observe(onChange: (BlipNativeOnboardingSnapshot) -> Unit): () -> Unit {
        val job = scope.launch { snapshot.filterNotNull().collect(onChange) }
        return { job.cancel() }
    }

    fun isCompleted(): Boolean = completed.value

    fun observeCompleted(onChange: (Boolean) -> Unit): () -> Unit {
        val job = scope.launch { completed.collect(onChange) }
        return { job.cancel() }
    }

    fun continueWelcome() {
        moveTo(STEP_FEATURES)
    }

    fun setFeaturePage(page: Int) {
        onboarding.setFeaturesPage(page)
    }

    fun continueFeatures() {
        moveTo(STEP_AUTO_PAY)
    }

    fun setConfirmationMode(value: String) {
        onboarding.setConfirmationMode(
            if (value == CONFIRMATION_ALWAYS) {
                PaymentConfirmationMode.Always
            } else {
                PaymentConfirmationMode.Above
            }
        )
    }

    fun setThresholdIndex(index: Int) {
        PaymentPreferences.THRESHOLD_STEPS.getOrNull(index)?.let(onboarding::setThreshold)
    }

    fun continueAutoPay() {
        onboarding.persistAutoPaySettings()
        moveTo(STEP_AGREEMENT)
    }

    fun setAgreement(agreed: Boolean) {
        onboarding.setAgreement(agreed)
    }

    fun continueAgreement() {
        if (onboarding.uiState.value.hasAgreed) moveTo(STEP_INSTRUCTIONS)
    }

    fun setInstructionPage(page: Int) {
        currentInstructionPage = page.coerceIn(0, INSTRUCTION_PAGE_COUNT - 1)
        refresh()
    }

    fun showWalletConnection() {
        moveTo(STEP_WALLET)
    }

    fun updateApiKey(apiKey: String) {
        addWallet.updateApiKey(apiKey)
    }

    fun connectWallet() {
        if (canConnectWallet()) addWallet.submit()
    }

    fun back() {
        if (connectionOnly) return
        when (currentStep) {
            STEP_FEATURES -> moveTo(STEP_WELCOME)
            STEP_AUTO_PAY -> moveTo(STEP_FEATURES)
            STEP_AGREEMENT -> moveTo(STEP_AUTO_PAY)
            STEP_INSTRUCTIONS -> moveTo(STEP_AGREEMENT)
            STEP_WALLET -> addWallet.cancel()
        }
    }

    fun finish() {
        onCompleted()
        completed.value = true
    }

    fun clear() {
        addWallet.clear()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun moveTo(step: String) {
        currentStep = step
        refresh()
    }

    private fun refresh() {
        scope.launch { publishSnapshot() }
    }

    private suspend fun publishSnapshot() {
        val state = onboarding.uiState.value
        val walletState = addWallet.uiState.value
        val common = nativeOnboardingText()
        val blip = nativeBlipOnboardingText(appName)
        val wallet = nativeBlinkWalletConnectionText(appName)
        val formatter = currentAmountFormatter()
        val sats =
            formatter.format(
                DisplayAmount(state.thresholdSats, DisplayCurrency.Satoshi)
            )
        val thresholdAmount =
            state.thresholdCurrencyEquivalent
                ?.let(formatter::format)
                ?.let { "$sats ($it)" }
                ?: sats
        val instructionPages =
            blip.instructions.map { page ->
                BlipNativeOnboardingPage(
                    title = page.title,
                    subtitle = page.subtitle,
                    body = page.body,
                    imageName = page.imageName
                )
            }
        snapshot.value =
            BlipNativeOnboardingSnapshot(
                step = currentStep,
                canGoBack = !connectionOnly && currentStep != STEP_WELCOME &&
                    !(welcomeCompleted && currentStep == STEP_FEATURES),
                stepIndex = stepIndex(currentStep),
                stepCount = ONBOARDING_STEP_COUNT,
                backTitle = common.back,
                welcomeTitle = blip.welcomeTitle,
                welcomeSubtitle = blip.welcomeSubtitle,
                welcomeDescription = blip.welcomeDescription,
                getStartedTitle = common.getStarted,
                featurePages =
                    blip.features.map { page ->
                        BlipNativeOnboardingPage(
                            title = page.title,
                            subtitle = page.subtitle,
                            body = page.body,
                            imageName = page.imageName
                        )
                    },
                featurePage = state.featuresPage,
                featuresNextTitle = common.featuresContinue,
                autoPayTitle = common.autoPayTitle,
                autoPayBody = blip.autoPayBody,
                autoPayAlwaysTitle = common.autoPayAlways,
                autoPayThresholdTitle = common.autoPayThreshold,
                autoPayThresholdLabel = nativeOnboardingThresholdLabel(thresholdAmount),
                autoPayHint = common.autoPayHint,
                autoPayNextTitle = common.autoPayContinue,
                confirmationMode =
                    if (state.confirmationMode == PaymentConfirmationMode.Always) {
                        CONFIRMATION_ALWAYS
                    } else {
                        CONFIRMATION_ABOVE
                    },
                thresholdIndex = PaymentPreferences.thresholdToStepIndex(state.thresholdSats),
                thresholdStepCount = PaymentPreferences.THRESHOLD_STEPS.size,
                agreementTitle = common.agreementTitle,
                agreementBody = blip.agreementBody,
                agreementCheckboxTitle = common.agreementCheckbox,
                agreementNextTitle = common.agreementContinue,
                hasAgreed = state.hasAgreed,
                instructionsTitle = blip.instructionsTitle,
                instructionsIntro = blip.instructionsIntro,
                instructionPages = instructionPages,
                instructionPage = currentInstructionPage,
                instructionProgress =
                    nativeBlipInstructionProgress(
                        currentInstructionPage + 1,
                        instructionPages.size
                    ),
                previousStepTitle = blip.previousStep,
                nextStepTitle = blip.nextStep,
                dashboardTitle = blip.dashboardButton,
                dashboardUrl = BLINK_DASHBOARD_URL,
                enterKeyTitle = blip.enterKeyButton,
                walletTitle = wallet.title,
                walletDescription = wallet.description,
                apiKeyLabel = wallet.apiKeyLabel,
                apiKeyPlaceholder = wallet.apiKeyPlaceholder,
                showApiKeyTitle = wallet.showApiKey,
                hideApiKeyTitle = wallet.hideApiKey,
                pasteTitle = wallet.paste,
                connectTitle = wallet.connect,
                apiKey = walletState.apiKey,
                canConnect = walletState.canSubmit && canConnectWallet(),
                isConnecting = walletState.isSaving,
                connectionError = walletState.error?.let { nativeBlinkErrorMessageFor(it) }
            )
    }

    private fun stepIndex(step: String): Int = when (step) {
        STEP_WELCOME -> 0
        STEP_FEATURES -> 1
        STEP_AUTO_PAY -> 2
        STEP_AGREEMENT -> 3
        else -> 4
    }

    private companion object {
        const val STEP_WELCOME = "welcome"
        const val STEP_FEATURES = "features"
        const val STEP_AUTO_PAY = "autoPay"
        const val STEP_AGREEMENT = "agreement"
        const val STEP_INSTRUCTIONS = "instructions"
        const val STEP_WALLET = "wallet"
        const val CONFIRMATION_ALWAYS = "always"
        const val CONFIRMATION_ABOVE = "above"
        const val ONBOARDING_STEP_COUNT = 5
        const val INSTRUCTION_PAGE_COUNT = 4
        const val BLINK_DASHBOARD_URL = "https://dashboard.blink.sv/api-keys"
    }
}
