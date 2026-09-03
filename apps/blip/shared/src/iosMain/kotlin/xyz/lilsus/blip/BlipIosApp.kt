package xyz.lilsus.blip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import xyz.lilsus.blip.feature.blinkcontacts.BlipNativeContactsController
import xyz.lilsus.blip.feature.payment.PaymentDeepLinkEvents
import xyz.lilsus.blip.feature.payment.PaymentEvent
import xyz.lilsus.blip.feature.payment.PaymentUiState
import xyz.lilsus.blip.feature.payment.previousPaymentSituation
import xyz.lilsus.blip.feature.payment.toNativePaymentScreenState
import xyz.lilsus.blip.feature.payment.toNativeRecentItem
import xyz.lilsus.blip.generated.resources.Res
import xyz.lilsus.blip.generated.resources.app_name
import xyz.lilsus.blip.ui.generated.resources.Res as BlipUiRes
import xyz.lilsus.blip.ui.generated.resources.result_paid_fee_blink_hint
import xyz.lilsus.raylsuite.core.settings.createAppSettings
import xyz.lilsus.raylsuite.core.settings.createSecureSettings
import xyz.lilsus.raylsuite.core.ui.platform.createHapticFeedbackManager
import xyz.lilsus.raylsuite.feature.appshell.AppTab
import xyz.lilsus.raylsuite.feature.appshell.appTabTitles
import xyz.lilsus.raylsuite.feature.appshell.nativeColorSchemeValue
import xyz.lilsus.raylsuite.feature.paymenthub.NativePaymentHubController
import xyz.lilsus.raylsuite.feature.paymentui.NativePaymentRecentController
import xyz.lilsus.raylsuite.feature.paymentui.NativePaymentScanController
import xyz.lilsus.raylsuite.feature.paymentui.localizedMessage
import xyz.lilsus.raylsuite.feature.settings.NativeSettingsController
import xyz.lilsus.raylsuite.feature.settings.nativeSettingsAppVersionName

/**
 * The Kotlin side of Blip's native iOS shell. Swift owns the `TabView`; this owns the app scope
 * so every tab's view controller shares one runtime.
 */
object BlipIosApp {
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val runtime: BlipRuntime by lazy {
        BlipRuntime(
            appSettings = createAppSettings(BLIP_PREFERENCES),
            secureSettings = createSecureSettings(BLIP_CREDENTIALS),
            haptics = createHapticFeedbackManager()
        )
    }

    private val nativeSettingsController: NativeSettingsController by lazy {
        NativeSettingsController(
            themePreferences = runtime.themePreferences,
            languageRepository = runtime.languageRepository,
            currencyPreferences = runtime.currencyPreferences,
            paymentPreferences = runtime.paymentPreferences,
            bitcoinPriceProvider = runtime.bitcoinPriceProvider,
            legalLinks = BLIP_LEGAL_LINKS,
            appVersionName = nativeSettingsAppVersionName()
        )
    }

    private val blipNativeSettingsController: BlipNativeSettingsController by lazy {
        BlipNativeSettingsController(
            blinkWallet = runtime.blinkWallet,
            languageRepository = runtime.languageRepository,
            onRemoveWallet = ::removeWallet
        )
    }

    private val nativeScanController: NativePaymentScanController by lazy {
        createNativeScanController()
    }

    private val nativeRecentController: NativePaymentRecentController by lazy {
        createNativeRecentController()
    }

    private val nativeHubController: NativePaymentHubController by lazy {
        NativePaymentHubController(
            repository = runtime.paymentHubRepository,
            canvasLayout = runtime.canvasLayout,
            host = runtime.paymentHub,
            languageChanges = runtime.languageRepository.preference,
            currencyCodes = runtime.currencyPreferences.code
        )
    }

    private val nativeContactsController: BlipNativeContactsController by lazy {
        BlipNativeContactsController(
            blinkWallet = runtime.blinkWallet,
            paymentHub = runtime.paymentHubRepository,
            languageChanges = runtime.languageRepository.preference
        )
    }

    private val nativeOnboardingController: BlipNativeOnboardingController by lazy {
        BlipNativeOnboardingController(
            onboarding = runtime.onboardingViewModel,
            blinkWallet = runtime.blinkWallet,
            contacts = nativeContactsController,
            languageChanges = runtime.languageRepository.preference,
            initiallyCompleted = runtime.blinkWallet.connection.value != null
        )
    }

    /** Connected returning users enter immediately; new users finish contact import or skip it. */
    fun isOnboarded(): Boolean =
        runtime.blinkWallet.connection.value != null && nativeOnboardingController.isCompleted()

    fun observeOnboarded(onChange: (Boolean) -> Unit): () -> Unit {
        val job =
            observerScope.launch {
                combine(
                    runtime.blinkWallet.connection,
                    nativeOnboardingController.completion
                ) { connection, completed -> connection != null && completed }
                    .collect(onChange)
            }
        return { job.cancel() }
    }

    fun selectedTab(): String = runtime.tabState.selectedTab.value.storedValue

    fun observeSelectedTab(onChange: (String) -> Unit): () -> Unit {
        val job =
            observerScope.launch {
                runtime.tabState.selectedTab.collect { onChange(it.storedValue) }
            }
        return { job.cancel() }
    }

    fun observeTheme(onChange: (String) -> Unit): () -> Unit {
        val job =
            observerScope.launch {
                runtime.themePreferences.preference.collect {
                    onChange(it.nativeColorSchemeValue())
                }
            }
        return { job.cancel() }
    }

    fun recentBadgeCount(): Int = runtime.paymentCoordinator.newSessionTransactionCount.value

    fun observeRecentBadgeCount(onChange: (Int) -> Unit): () -> Unit {
        val job =
            observerScope.launch {
                runtime.paymentCoordinator.newSessionTransactionCount.collect(onChange)
            }
        return { job.cancel() }
    }

    /** Localized tab titles keyed by tab ID, so the native tab bar uses one set of strings. */
    fun observeTabTitles(onChange: (Map<String, String>) -> Unit): () -> Unit {
        val job =
            observerScope.launch {
                runtime.languageRepository.preference.collect { onChange(appTabTitles()) }
            }
        return { job.cancel() }
    }

    fun selectTab(tab: String) {
        runtime.tabState.select(AppTab.fromStoredValue(tab))
    }

    fun settingsController(): NativeSettingsController = nativeSettingsController

    fun blipSettingsController(): BlipNativeSettingsController = blipNativeSettingsController

    fun scanController(): NativePaymentScanController = nativeScanController

    fun recentController(): NativePaymentRecentController = nativeRecentController

    fun hubController(): NativePaymentHubController = nativeHubController

    fun contactsController(): BlipNativeContactsController = nativeContactsController

    fun onboardingController(): BlipNativeOnboardingController = nativeOnboardingController

    private fun removeWallet() {
        runtime.resetPaymentSession()
        PaymentDeepLinkEvents.clear()
        // Hub targets and app preferences intentionally survive disconnect.
        nativeOnboardingController.reset()
        runtime.blinkWallet.disconnect()
    }

    private fun createNativeScanController(): NativePaymentScanController {
        val controller =
            NativePaymentScanController(
                onPaymentIntent = runtime.paymentCoordinator::dispatch,
                onHubIntent = runtime.paymentHub::dispatch,
                canOpenPreviousPayment = false,
                // Blip has no Recent tab, so Scan owns the way into this session's payments.
                offersRecentEntryPoint = true
            )
        observerScope.launch {
            combine(
                runtime.paymentCoordinator.uiState,
                runtime.paymentCoordinator.sessionTransactions,
                runtime.paymentCoordinator.newSessionTransactionCount,
                runtime.paymentHub.state,
                runtime.languageRepository.preference
            ) { payment, transactions, newTransactionCount, hubState, _ ->
                val previousSituation =
                    (payment as? PaymentUiState.PendingRetry)?.id?.let { id ->
                        transactions.firstOrNull { it.id == id }?.previousPaymentSituation()
                    }
                controller.update(
                    payment = payment.toNativePaymentScreenState(),
                    appTitle = getString(Res.string.app_name),
                    estimatedFeeHint =
                        getString(BlipUiRes.string.result_paid_fee_blink_hint),
                    previousPaymentSituation = previousSituation,
                    savePrompt = hubState.savePrompt,
                    recentCount = transactions.size,
                    newRecentCount = newTransactionCount
                )
            }.collect {}
        }
        observerScope.launch {
            runtime.paymentCoordinator.events.collect { event ->
                val message =
                    when (event) {
                        is PaymentEvent.ShowError ->
                            xyz.lilsus.blip.feature.payment.getBlipPaymentErrorMessageFor(
                                event.error
                            )

                        is PaymentEvent.ShowToast -> event.message.localizedMessage()
                    }
                controller.emitMessage(message)
            }
        }
        return controller
    }

    private fun createNativeRecentController(): NativePaymentRecentController {
        val controller =
            NativePaymentRecentController(
                onIntent = runtime.paymentCoordinator::dispatch,
                onSelectTransaction = runtime.tabState::selectTransaction
            )
        observerScope.launch {
            combine(
                runtime.paymentCoordinator.sessionTransactions,
                runtime.tabState.selectedTransactionId,
                runtime.languageRepository.preference
            ) { transactions, selectedId, _ ->
                controller.update(
                    items = transactions.map { it.toNativeRecentItem() },
                    selectedTransactionId = selectedId,
                    estimatedFeeHint =
                        getString(BlipUiRes.string.result_paid_fee_blink_hint)
                )
            }.collect {}
        }
        return controller
    }
}
