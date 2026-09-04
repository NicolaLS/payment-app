package xyz.lilsus.lasr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import xyz.lilsus.lasr.feature.payment.PaymentEvent
import xyz.lilsus.lasr.feature.payment.PaymentUiState
import xyz.lilsus.lasr.feature.payment.previousPaymentSituation
import xyz.lilsus.lasr.feature.payment.toNativePaymentScreenState
import xyz.lilsus.lasr.feature.payment.toNativeRecentItem
import xyz.lilsus.raylsuite.core.settings.createAppSettings
import xyz.lilsus.raylsuite.core.settings.createSecureSettings
import xyz.lilsus.raylsuite.core.ui.platform.createHapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString
import xyz.lilsus.raylsuite.feature.appshell.AppTab
import xyz.lilsus.raylsuite.feature.appshell.appTabTitles
import xyz.lilsus.raylsuite.feature.appshell.nativeColorSchemeValue
import xyz.lilsus.raylsuite.feature.paymenthub.NativePaymentHubController
import xyz.lilsus.raylsuite.feature.paymentui.NativePaymentRecentController
import xyz.lilsus.raylsuite.feature.paymentui.NativePaymentScanController
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.paymentui.localizedMessage
import xyz.lilsus.raylsuite.feature.settings.NativeSettingsController
import xyz.lilsus.raylsuite.feature.settings.nativeSettingsAppVersionName

/**
 * The Kotlin side of Lasr's native iOS shell. Swift owns the `TabView`; this owns the app scope
 * so every tab's view controller shares one runtime.
 */
object LasrIosApp {
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val runtime: LasrRuntime by lazy {
        LasrRuntime(
            appSettings = createAppSettings(LASR_PREFERENCES),
            secureSettings = createSecureSettings(LASR_CREDENTIALS),
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
            legalLinks = LASR_LEGAL_LINKS,
            appVersionName = nativeSettingsAppVersionName()
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

    private val nativeOnboardingController: LasrNativeOnboardingController by lazy {
        LasrNativeOnboardingController(runtime)
    }

    private val nativeWalletSettingsController: LasrNativeWalletSettingsController by lazy {
        LasrNativeWalletSettingsController(runtime, nativeOnboardingController)
    }

    /** `true` once onboarding is complete, independently of the current wallet connection. */
    fun isOnboarded(): Boolean = runtime.onboardingState.completed.value

    fun observeOnboarded(onChange: (Boolean) -> Unit): () -> Unit {
        val job =
            observerScope.launch {
                runtime.onboardingState.completed.collect(onChange)
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

    fun scanController(): NativePaymentScanController = nativeScanController

    fun recentController(): NativePaymentRecentController = nativeRecentController

    fun hubController(): NativePaymentHubController = nativeHubController

    fun onboardingController(): LasrNativeOnboardingController = nativeOnboardingController

    fun walletSettingsController(): LasrNativeWalletSettingsController =
        nativeWalletSettingsController

    private fun createNativeScanController(): NativePaymentScanController {
        val controller =
            NativePaymentScanController(
                onPaymentIntent = runtime.paymentCoordinator::dispatch,
                onHubIntent = runtime.paymentHub::dispatch
            )
        observerScope.launch {
            combine(
                runtime.paymentCoordinator.uiState,
                runtime.paymentCoordinator.sessionTransactions,
                runtime.paymentHub.state,
                runtime.languageRepository.preference
            ) { payment, transactions, hubState, _ ->
                val previousSituation =
                    (payment as? PaymentUiState.PendingRetry)?.id?.let { id ->
                        transactions.firstOrNull { it.id == id }?.previousPaymentSituation()
                    }
                controller.update(
                    payment = payment.toNativePaymentScreenState(),
                    appTitle = nativeString(
                        NativeStringResource(table = "LasrApp", key = "app_name")
                    ),
                    estimatedFeeHint = null,
                    previousPaymentSituation = previousSituation,
                    savePrompt = hubState.savePrompt
                )
            }.collect {}
        }
        observerScope.launch {
            runtime.paymentCoordinator.events.collect { event ->
                val message =
                    when (event) {
                        is PaymentEvent.ShowError ->
                            xyz.lilsus.lasr.feature.payment.getLasrPaymentErrorMessageFor(
                                event.error
                            )

                        is PaymentEvent.ShowToast -> event.message.localizedMessage()
                    }
                controller.emitMessage(message)
            }
        }
        observerScope.launch {
            runtime.paymentCoordinator.transactionDetailNavigationTarget.collect { id ->
                if (id == null) return@collect
                runtime.tabState.openTransaction(id)
                runtime.paymentCoordinator.dispatch(
                    PaymentIntent.TransactionDetailNavigationHandled(id)
                )
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
                    estimatedFeeHint = null
                )
            }.collect {}
        }
        return controller
    }
}
