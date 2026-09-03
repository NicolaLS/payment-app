package xyz.lilsus.lasr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import xyz.lilsus.lasr.feature.onboarding.NativeLasrOnboardingPage
import xyz.lilsus.lasr.feature.onboarding.nativeLasrOnboardingText
import xyz.lilsus.lasr.feature.walletconnection.AddNwcWalletEvent
import xyz.lilsus.lasr.feature.walletconnection.AddNwcWalletViewModel
import xyz.lilsus.lasr.feature.walletconnection.ConnectNwcWalletEvent
import xyz.lilsus.lasr.feature.walletconnection.ConnectNwcWalletViewModel
import xyz.lilsus.lasr.feature.walletconnection.nativeLasrActiveEncryptionText
import xyz.lilsus.lasr.feature.walletconnection.nativeLasrWalletConnectionText
import xyz.lilsus.lasr.integration.nwc.NwcConnectionError
import xyz.lilsus.lasr.integration.nwc.NwcWalletDiscovery
import xyz.lilsus.raylsuite.core.camera.CameraAuthorizationState
import xyz.lilsus.raylsuite.core.camera.QrScannerController
import xyz.lilsus.raylsuite.core.camera.createNativeQrScannerController
import xyz.lilsus.raylsuite.core.camera.nativeCameraAuthorizationState
import xyz.lilsus.raylsuite.core.camera.requestNativeCameraPermission
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.model.PaymentConfirmationMode
import xyz.lilsus.raylsuite.core.model.PaymentPreferences
import xyz.lilsus.raylsuite.core.ui.format.currentAmountFormatter
import xyz.lilsus.raylsuite.feature.onboarding.nativeOnboardingText
import xyz.lilsus.raylsuite.feature.onboarding.nativeOnboardingThresholdLabel

data class LasrNativeOnboardingPage(val title: String, val subtitle: String, val body: String)

data class LasrNativeOnboardingSnapshot(
    val step: String,
    val settingsFlow: Boolean,
    val stepIndex: Int,
    val stepCount: Int,
    val backTitle: String,
    val welcomeTitle: String,
    val welcomeSubtitle: String,
    val welcomeDescription: String,
    val getStartedTitle: String,
    val featurePages: List<LasrNativeOnboardingPage>,
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
    val instructionSteps: List<String>,
    val connectWalletTitle: String,
    val addTitle: String,
    val addDescription: String,
    val uriLabel: String,
    val uriPlaceholder: String,
    val pasteTitle: String,
    val scanInstruction: String,
    val scanPermission: String,
    val scanAllowCamera: String,
    val scanOpenSettings: String,
    val scanRestricted: String,
    val uri: String,
    val canSubmitUri: Boolean,
    val uriError: String?,
    val cameraAuthorization: String,
    val scannerUnavailable: Boolean,
    val confirmationPresented: Boolean,
    val confirmTitle: String,
    val confirmDescription: String,
    val cancelTitle: String,
    val confirmActionTitle: String,
    val retryTitle: String,
    val aliasLabel: String,
    val alias: String,
    val discoveryLoading: Boolean,
    val discoveryLoadingTitle: String,
    val saving: Boolean,
    val canConfirm: Boolean,
    val connectionError: String?,
    val warningHeading: String,
    val warnings: List<String>,
    val publicKeyLabel: String,
    val walletPublicKey: String?,
    val relayLabel: String,
    val relay: String?,
    val lightningAddressLabel: String,
    val lightningAddress: String?,
    val methodsLabel: String,
    val methods: String?,
    val encryptionLabel: String,
    val encryptionSchemes: String?,
    val activeEncryption: String?
)

class LasrNativeOnboardingController internal constructor(private val runtime: LasrRuntime) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val addWallet = AddNwcWalletViewModel()
    private val connectWallet = ConnectNwcWalletViewModel(runtime.nwcWallet)
    private val scanner: QrScannerController = createNativeQrScannerController()
    private val snapshot = MutableStateFlow<LasrNativeOnboardingSnapshot?>(null)
    private var currentStep = STEP_WELCOME
    private var confirmationPresented = false
    private var scannerActive = false
    private var scannerStarted = false
    private var scannerUnavailable = false
    private var settingsFlow = false

    init {
        scope.launch {
            combine(
                runtime.onboardingViewModel.uiState,
                addWallet.uiState,
                connectWallet.uiState,
                runtime.languageRepository.preference
            ) { _, _, _, _ -> Unit }.collect { publishSnapshot() }
        }
        scope.launch {
            addWallet.events.collect { event ->
                when (event) {
                    is AddNwcWalletEvent.Confirm -> beginConfirmation(event.uri)
                }
            }
        }
        scope.launch {
            connectWallet.events.collect { event ->
                when (event) {
                    is ConnectNwcWalletEvent.Success -> {
                        confirmationPresented = false
                        runtime.connectionDraft.clear()
                        if (settingsFlow) {
                            settingsFlow = false
                            runtime.walletFlowHandled()
                        }
                        stopScanner()
                    }

                    ConnectNwcWalletEvent.Cancelled -> {
                        confirmationPresented = false
                        runtime.connectionDraft.clear()
                        publishSnapshot()
                        reconcileScanner()
                    }
                }
            }
        }
        scope.launch {
            runtime.onboardingWalletFlow.collect { requested ->
                if (!requested) return@collect
                val uri = runtime.connectionDraft.uri
                runtime.walletFlowHandled()
                uri?.let(::beginConfirmation)
            }
        }
    }

    fun observe(onChange: (LasrNativeOnboardingSnapshot) -> Unit): () -> Unit {
        val job = scope.launch { snapshot.filterNotNull().collect(onChange) }
        return { job.cancel() }
    }

    fun continueWelcome() = moveTo(STEP_FEATURES)

    fun setFeaturePage(page: Int) {
        runtime.onboardingViewModel.setFeaturesPage(page)
    }

    fun continueFeatures() = moveTo(STEP_AUTO_PAY)

    fun setConfirmationMode(value: String) {
        runtime.onboardingViewModel.setConfirmationMode(
            if (value == CONFIRMATION_ALWAYS) {
                PaymentConfirmationMode.Always
            } else {
                PaymentConfirmationMode.Above
            }
        )
    }

    fun setThresholdIndex(index: Int) {
        PaymentPreferences.THRESHOLD_STEPS
            .getOrNull(index)
            ?.let(runtime.onboardingViewModel::setThreshold)
    }

    fun continueAutoPay() {
        runtime.onboardingViewModel.persistAutoPaySettings()
        moveTo(STEP_AGREEMENT)
    }

    fun setAgreement(agreed: Boolean) {
        runtime.onboardingViewModel.setAgreement(agreed)
    }

    fun continueAgreement() {
        if (runtime.onboardingViewModel.uiState.value.hasAgreed) moveTo(STEP_INSTRUCTIONS)
    }

    fun showWalletConnection() = moveTo(STEP_WALLET)

    fun updateUri(uri: String) {
        addWallet.updateUri(uri)
    }

    fun pasteUri(candidate: String?) {
        addWallet.prefillUriIfValid(candidate)
    }

    fun submitUri() {
        addWallet.submit()
    }

    fun updateAlias(alias: String) {
        connectWallet.updateAlias(alias)
    }

    fun retryDiscovery() {
        connectWallet.retryDiscovery()
    }

    fun confirmConnection() {
        connectWallet.confirm()
    }

    fun cancelConnection() {
        connectWallet.cancel()
    }

    fun setScannerActive(active: Boolean) {
        scannerActive = active
        reconcileScanner()
        scope.launch { publishSnapshot() }
    }

    fun requestCameraAccess() {
        requestNativeCameraPermission {
            scope.launch {
                publishSnapshot()
                reconcileScanner()
            }
        }
    }

    fun startSettingsWalletFlow() {
        settingsFlow = true
        currentStep = STEP_WALLET
        runtime.connectionDraft.uri?.let {
            beginConfirmation(it)
            return
        }
        scope.launch {
            publishSnapshot()
            reconcileScanner()
        }
    }

    fun finishSettingsWalletFlow() {
        settingsFlow = false
        confirmationPresented = false
        runtime.connectionDraft.clear()
        runtime.walletFlowHandled()
        stopScanner()
        currentStep = STEP_WELCOME
        scope.launch { publishSnapshot() }
    }

    fun back() {
        when (currentStep) {
            STEP_FEATURES -> moveTo(STEP_WELCOME)
            STEP_AUTO_PAY -> moveTo(STEP_FEATURES)
            STEP_AGREEMENT -> moveTo(STEP_AUTO_PAY)
            STEP_INSTRUCTIONS -> moveTo(STEP_AGREEMENT)
            STEP_WALLET -> moveTo(STEP_INSTRUCTIONS)
        }
    }

    private fun moveTo(step: String) {
        currentStep = step
        if (step != STEP_WALLET) stopScanner()
        scope.launch {
            publishSnapshot()
            reconcileScanner()
        }
    }

    private fun beginConfirmation(uri: String) {
        runtime.connectionDraft.set(uri)
        currentStep = STEP_WALLET
        confirmationPresented = true
        stopScanner()
        connectWallet.load(uri)
        scope.launch { publishSnapshot() }
    }

    private fun reconcileScanner() {
        val shouldRun =
            scannerActive && currentStep == STEP_WALLET && !confirmationPresented
        if (
            !shouldRun ||
            nativeCameraAuthorizationState() != CameraAuthorizationState.AUTHORIZED
        ) {
            if (scannerStarted) scanner.stop()
            scannerStarted = false
            return
        }
        if (scannerStarted) return
        scannerUnavailable = false
        scannerStarted =
            scanner.start(
                onQrCodeScanned = addWallet::handleScannedValue,
                onCameraPermissionMissing = {
                    scannerStarted = false
                    scope.launch { publishSnapshot() }
                },
                onScannerUnavailable = {
                    scannerStarted = false
                    scannerUnavailable = true
                    scope.launch { publishSnapshot() }
                }
            )
    }

    private fun stopScanner() {
        if (scannerStarted) scanner.stop()
        scannerStarted = false
    }

    private suspend fun publishSnapshot() {
        val state = runtime.onboardingViewModel.uiState.value
        val add = addWallet.uiState.value
        val connect = connectWallet.uiState.value
        val common = nativeOnboardingText()
        val lasr = nativeLasrOnboardingText()
        val wallet = nativeLasrWalletConnectionText()
        val formatter = currentAmountFormatter()
        val sats = formatter.format(DisplayAmount(state.thresholdSats, DisplayCurrency.Satoshi))
        val thresholdAmount =
            state.thresholdCurrencyEquivalent
                ?.let(formatter::format)
                ?.let { "$sats ($it)" }
                ?: sats
        val discovery = connect.discovery

        snapshot.value =
            LasrNativeOnboardingSnapshot(
                step = currentStep,
                settingsFlow = settingsFlow,
                stepIndex = stepIndex(currentStep),
                stepCount = ONBOARDING_STEP_COUNT,
                backTitle = common.back,
                welcomeTitle = lasr.welcomeTitle,
                welcomeSubtitle = lasr.welcomeSubtitle,
                welcomeDescription = lasr.welcomeDescription,
                getStartedTitle = common.getStarted,
                featurePages = lasr.featurePages.map(NativeLasrOnboardingPage::toSnapshot),
                featurePage = state.featuresPage,
                featuresNextTitle = common.featuresContinue,
                autoPayTitle = common.autoPayTitle,
                autoPayBody = lasr.autoPayBody,
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
                agreementBody = lasr.agreementBody,
                agreementCheckboxTitle = common.agreementCheckbox,
                agreementNextTitle = common.agreementContinue,
                hasAgreed = state.hasAgreed,
                instructionsTitle = lasr.instructionsTitle,
                instructionsIntro = lasr.instructionsIntro,
                instructionSteps = lasr.instructionSteps,
                connectWalletTitle = wallet.addTitle,
                addTitle = wallet.addTitle,
                addDescription = wallet.addDescription,
                uriLabel = wallet.uriLabel,
                uriPlaceholder = wallet.uriPlaceholder,
                pasteTitle = wallet.paste,
                scanInstruction = wallet.scanInstruction,
                scanPermission = wallet.scanPermission,
                scanAllowCamera = wallet.scanAllowCamera,
                scanOpenSettings = wallet.scanOpenSettings,
                scanRestricted = wallet.scanRestricted,
                uri = add.uri,
                canSubmitUri = add.isUriValid,
                uriError = add.error?.message(wallet),
                cameraAuthorization = nativeCameraAuthorizationState().nativeValue(),
                scannerUnavailable = scannerUnavailable,
                confirmationPresented = confirmationPresented,
                confirmTitle = wallet.confirmTitle,
                confirmDescription = wallet.confirmDescription,
                cancelTitle = wallet.cancel,
                confirmActionTitle = wallet.confirm,
                retryTitle = wallet.retry,
                aliasLabel = wallet.aliasLabel,
                alias = connect.alias,
                discoveryLoading = connect.isDiscoveryLoading,
                discoveryLoadingTitle = wallet.loading,
                saving = connect.isSaving,
                canConfirm =
                    discovery?.supportsRequiredMethods == true &&
                        !connect.isSaving &&
                        !connect.isDiscoveryLoading,
                connectionError = connect.error?.message(wallet),
                warningHeading = wallet.warningHeading,
                warnings = discovery?.warnings(wallet).orEmpty(),
                publicKeyLabel = wallet.publicKeyLabel,
                walletPublicKey = discovery?.walletPublicKey,
                relayLabel = wallet.relayLabel,
                relay = discovery?.relayUrl,
                lightningAddressLabel = wallet.lightningAddressLabel,
                lightningAddress = discovery?.lightningAddress,
                methodsLabel = wallet.methodsLabel,
                methods = discovery?.metadata?.methods?.sorted()?.joinToString(),
                encryptionLabel = wallet.encryptionLabel,
                encryptionSchemes =
                    discovery?.metadata?.encryptionSchemes?.sorted()?.joinToString(),
                activeEncryption =
                    discovery?.metadata?.negotiatedEncryption?.let {
                        nativeLasrActiveEncryptionText(it)
                    }
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
    }
}

private fun NativeLasrOnboardingPage.toSnapshot(): LasrNativeOnboardingPage =
    LasrNativeOnboardingPage(title = title, subtitle = subtitle, body = body)

private fun CameraAuthorizationState.nativeValue(): String = when (this) {
    CameraAuthorizationState.NOT_DETERMINED -> "notDetermined"
    CameraAuthorizationState.AUTHORIZED -> "authorized"
    CameraAuthorizationState.DENIED -> "denied"
    CameraAuthorizationState.RESTRICTED -> "restricted"
    CameraAuthorizationState.UNAVAILABLE -> "unavailable"
}

private fun NwcConnectionError.message(
    text: xyz.lilsus.lasr.feature.walletconnection.NativeLasrWalletConnectionText
): String = when (this) {
    NwcConnectionError.AlreadyConnected -> text.errorAlreadyConnected
    NwcConnectionError.InvalidUri -> text.errorInvalidUri
    NwcConnectionError.RequiredMethodsMissing -> text.requiredMethods
    is NwcConnectionError.ConnectionFailed -> text.errorConnection
}

private fun NwcWalletDiscovery.warnings(
    text: xyz.lilsus.lasr.feature.walletconnection.NativeLasrWalletConnectionText
): List<String> = buildList {
    if (!supportsRequiredMethods) add(text.requiredMethods)
    if (!supportsPayInvoice) add(text.warningMissingPayInvoice)
    if (!supportsLookupInvoice) add(text.warningMissingLookupInvoice)
    when {
        usesLegacyEncryption && metadata.encryptionDefaultedToNip04 ->
            add(text.warningLegacyNip04Default)

        usesLegacyEncryption -> add(text.warningLegacyNip04)

        !supportsNip44 -> add(text.warningMissingNip44)
    }
}
