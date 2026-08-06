package xyz.lilsus.flint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import xyz.lilsus.flint.application.payment.ConfirmPaymentResult
import xyz.lilsus.flint.application.payment.PaymentActivity
import xyz.lilsus.flint.application.payment.PaymentAmountHandle
import xyz.lilsus.flint.application.payment.PaymentConfirmationMode as FlintConfirmationMode
import xyz.lilsus.flint.application.payment.PaymentConfirmationPolicy
import xyz.lilsus.flint.application.payment.PaymentCurrencyPreferences
import xyz.lilsus.flint.application.payment.PaymentDraftHandle
import xyz.lilsus.flint.application.payment.PaymentEngine
import xyz.lilsus.flint.application.payment.PaymentLinkInbox
import xyz.lilsus.flint.application.payment.PaymentOrigin
import xyz.lilsus.flint.application.payment.PreparePaymentResult
import xyz.lilsus.flint.application.payment.UnavailablePaymentAmountAssistant
import xyz.lilsus.flint.application.payment.UpdatePaymentPolicyResult
import xyz.lilsus.flint.application.payment.createPaymentLinkInbox
import xyz.lilsus.flint.application.wallet.ImportWalletResult
import xyz.lilsus.flint.application.wallet.RemoveWalletResult
import xyz.lilsus.flint.application.wallet.WalletAccess
import xyz.lilsus.flint.application.wallet.WalletAccessState
import xyz.lilsus.flint.feature.payment.PaymentCoordinator
import xyz.lilsus.flint.feature.wallet.WalletViewModel
import xyz.lilsus.raylsuite.core.model.PaymentConfirmationMode
import xyz.lilsus.raylsuite.core.model.Satoshi
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.settings.rememberAppSettings
import xyz.lilsus.raylsuite.core.ui.platform.rememberHapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.contacts.DefaultContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.DefaultCurrencyPreferences
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.paymentsettings.DefaultPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.themesettings.DefaultThemePreferences
import xyz.lilsus.raylsuite.integration.exchangerate.CoinGeckoBitcoinPriceProvider

@Composable
fun App(bootstrapConfig: AppBootstrapConfig, runtime: AppRuntime, paymentLinks: PaymentLinkInbox) {
    val appSettings = rememberAppSettings(FLINT_PREFERENCES)
    val themePreferences = remember(appSettings) { DefaultThemePreferences(appSettings) }
    val currencyPreferences = remember(appSettings) { DefaultCurrencyPreferences(appSettings) }
    val paymentPreferences =
        remember(appSettings) { DefaultPaymentPreferencesRepository(appSettings) }
    val contactsRepository = remember(appSettings) { DefaultContactsRepository(appSettings) }
    val bitcoinPriceProvider = remember { CoinGeckoBitcoinPriceProvider() }
    val themePreference by
        themePreferences.preference.collectAsState(initial = ThemePreference.System)
    val haptics = rememberHapticFeedbackManager()
    val walletAccess = runtime.walletAccess
    val paymentCoordinator =
        remember(
            walletAccess.payments,
            paymentLinks,
            bitcoinPriceProvider,
            currencyPreferences,
            paymentPreferences,
            contactsRepository,
            haptics
        ) {
            PaymentCoordinator(
                engine = walletAccess.payments,
                paymentLinks = paymentLinks,
                bitcoinPriceProvider = bitcoinPriceProvider,
                currencyPreferences = currencyPreferences,
                paymentPreferences = paymentPreferences,
                contactsRepository = contactsRepository,
                haptics = haptics
            )
        }
    val walletViewModel = viewModel { WalletViewModel(walletAccess) }
    val walletState by walletViewModel.state.collectAsState()
    val onboardingViewModel =
        remember(paymentPreferences, currencyPreferences, bitcoinPriceProvider) {
            OnboardingViewModel(
                paymentPreferences = paymentPreferences,
                currencyPreferences = currencyPreferences,
                bitcoinPriceProvider = bitcoinPriceProvider
            )
        }
    var navigationReady by remember { mutableStateOf(!walletState.access.isInitialising()) }

    DisposableEffect(onboardingViewModel) {
        onDispose(onboardingViewModel::clear)
    }
    DisposableEffect(paymentCoordinator) {
        onDispose(paymentCoordinator::clear)
    }
    LaunchedEffect(walletState.access) {
        if (!walletState.access.isInitialising()) navigationReady = true
    }
    LaunchedEffect(paymentPreferences, walletAccess) {
        paymentPreferences.preferences.collectLatest { preferences ->
            walletAccess.payments.updateConfirmationPolicy(
                PaymentConfirmationPolicy(
                    mode =
                        when (preferences.confirmationMode) {
                            PaymentConfirmationMode.Always -> FlintConfirmationMode.ALWAYS
                            PaymentConfirmationMode.Above -> FlintConfirmationMode.THRESHOLD
                        },
                    amountThresholdSats = Satoshi.positive(preferences.thresholdSats),
                    feeThresholdSats = Satoshi.nonNegative(Long.MAX_VALUE)
                )
            )
        }
    }
    LaunchedEffect(currencyPreferences, walletAccess) {
        combine(
            currencyPreferences.primaryCode,
            currencyPreferences.secondaryCode,
            ::Pair
        ).collectLatest { (primary, secondary) ->
            val resolvedSecondary =
                if (primary != secondary) {
                    secondary
                } else if (primary == "USD") {
                    "SAT"
                } else {
                    "USD"
                }
            walletAccess.payments.amountAssistant.updateCurrencyPreferences(
                PaymentCurrencyPreferences(primary, resolvedSecondary)
            )
        }
    }
    RaylSuiteTheme(themePreference = themePreference) {
        if (!navigationReady) {
            InitialWalletLoading()
        } else {
            val navController = rememberNavController()
            val startDestination =
                remember {
                    when (walletState.access) {
                        WalletAccessState.Connected -> FlintDestination.Home
                        WalletAccessState.NoWallet -> FlintDestination.Welcome
                        else -> FlintDestination.WalletRecovery
                    }
                }
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                flintOnboarding(
                    navController = navController,
                    onboardingViewModel = onboardingViewModel,
                    walletViewModel = walletViewModel
                )
                flintHome(
                    navController = navController,
                    paymentCoordinator = paymentCoordinator,
                    themePreferences = themePreferences,
                    bitcoinPriceProvider = bitcoinPriceProvider,
                    currencyPreferences = currencyPreferences,
                    paymentPreferences = paymentPreferences,
                    contactsRepository = contactsRepository,
                    walletViewModel = walletViewModel,
                    networkLabel = bootstrapConfig.environment.networkLabel
                )
            }
        }
    }
}

@Composable
private fun InitialWalletLoading() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text(
                text = "Flint",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun WalletAccessState.isInitialising(): Boolean =
    this == WalletAccessState.Loading || this == WalletAccessState.Connecting

fun previewWalletAccess(): WalletAccess = object : WalletAccess {
    override val state: StateFlow<WalletAccessState> = MutableStateFlow(WalletAccessState.NoWallet)
    override val payments: PaymentEngine = object : PaymentEngine {
        override val activity: StateFlow<List<PaymentActivity>> = MutableStateFlow(emptyList())
        override val confirmationPolicy = MutableStateFlow(PaymentConfirmationPolicy.Default)
        override val amountAssistant = UnavailablePaymentAmountAssistant
        override suspend fun prepare(input: String, origin: PaymentOrigin) =
            PreparePaymentResult.WalletUnavailable
        override suspend fun prepareAmount(handle: PaymentAmountHandle, amountSats: Satoshi) =
            PreparePaymentResult.WalletUnavailable
        override suspend fun updateConfirmationPolicy(policy: PaymentConfirmationPolicy) =
            UpdatePaymentPolicyResult.STORAGE_FAILURE
        override suspend fun cancel(handle: PaymentDraftHandle) = Unit
        override suspend fun cancel(handle: PaymentAmountHandle) = Unit
        override suspend fun autoPay(handle: PaymentDraftHandle) =
            ConfirmPaymentResult.WalletUnavailable
        override suspend fun confirm(handle: PaymentDraftHandle) =
            ConfirmPaymentResult.WalletUnavailable
        override fun requestRefresh() = Unit
        override suspend fun refresh() = Unit
    }
    override fun start() = Unit
    override suspend fun importWallet(mnemonic: String) = ImportWalletResult.CONNECTION_FAILED
    override suspend fun retryConnection() = Unit
    override suspend fun removeWallet() = RemoveWalletResult.REMOVED
}

fun previewAppRuntime(): AppRuntime = AppRuntime(walletAccess = previewWalletAccess())

fun createAppPaymentLinkInbox(): PaymentLinkInbox = createPaymentLinkInbox()

internal const val FLINT_PREFERENCES = "flint_preferences"
