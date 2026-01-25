package xyz.lilsus.papp.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import xyz.lilsus.papp.data.blink.BlinkApiClient
import xyz.lilsus.papp.data.blink.BlinkCredentialStore
import xyz.lilsus.papp.data.blink.BlinkPaymentRepository
import xyz.lilsus.papp.data.exchange.CoinGeckoExchangeRateRepository
import xyz.lilsus.papp.data.lnurl.LnurlRepositoryImpl
import xyz.lilsus.papp.data.network.createNwcHttpClient
import xyz.lilsus.papp.data.nwc.NwcClientFactory
import xyz.lilsus.papp.data.nwc.NwcWalletRepositoryImpl
import xyz.lilsus.papp.data.nwc.RealNwcClientFactory
import xyz.lilsus.papp.data.nwc.WalletDiscoveryRepositoryImpl
import xyz.lilsus.papp.data.nwc.WalletMetadataSynchronizer
import xyz.lilsus.papp.data.settings.CurrencyPreferencesRepositoryImpl
import xyz.lilsus.papp.data.settings.PaymentPreferencesRepositoryImpl
import xyz.lilsus.papp.data.settings.ThemePreferencesRepositoryImpl
import xyz.lilsus.papp.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.papp.data.settings.createLanguageRepository
import xyz.lilsus.papp.data.settings.createSecureSettings
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceParser
import xyz.lilsus.papp.domain.lnurl.LightningInputParser
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.repository.CurrencyPreferencesRepository
import xyz.lilsus.papp.domain.repository.ExchangeRateRepository
import xyz.lilsus.papp.domain.repository.LanguageRepository
import xyz.lilsus.papp.domain.repository.LnurlRepository
import xyz.lilsus.papp.domain.repository.NwcWalletRepository
import xyz.lilsus.papp.domain.repository.PaymentPreferencesRepository
import xyz.lilsus.papp.domain.repository.PaymentProvider
import xyz.lilsus.papp.domain.repository.ThemePreferencesRepository
import xyz.lilsus.papp.domain.repository.WalletDiscoveryRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.domain.service.PaymentService
import xyz.lilsus.papp.domain.usecases.ClearLanguageOverrideUseCase
import xyz.lilsus.papp.domain.usecases.ClearWalletConnectionUseCase
import xyz.lilsus.papp.domain.usecases.DiscoverWalletUseCase
import xyz.lilsus.papp.domain.usecases.FetchLnurlPayParamsUseCase
import xyz.lilsus.papp.domain.usecases.GetExchangeRateUseCase
import xyz.lilsus.papp.domain.usecases.GetWalletsUseCase
import xyz.lilsus.papp.domain.usecases.LookupPaymentUseCase
import xyz.lilsus.papp.domain.usecases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObserveLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObservePaymentPreferencesUseCase
import xyz.lilsus.papp.domain.usecases.ObserveThemePreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.usecases.ObserveWalletsUseCase
import xyz.lilsus.papp.domain.usecases.PayInvoiceUseCase
import xyz.lilsus.papp.domain.usecases.RefreshLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.usecases.RequestLnurlInvoiceUseCase
import xyz.lilsus.papp.domain.usecases.ResolveLightningAddressUseCase
import xyz.lilsus.papp.domain.usecases.SetActiveWalletUseCase
import xyz.lilsus.papp.domain.usecases.SetConfirmManualEntryUseCase
import xyz.lilsus.papp.domain.usecases.SetCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.usecases.SetLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationModeUseCase
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationThresholdUseCase
import xyz.lilsus.papp.domain.usecases.SetThemePreferenceUseCase
import xyz.lilsus.papp.domain.usecases.SetVibrateOnPaymentUseCase
import xyz.lilsus.papp.domain.usecases.SetVibrateOnScanUseCase
import xyz.lilsus.papp.domain.usecases.SetWalletConnectionUseCase
import xyz.lilsus.papp.domain.usecases.ShouldConfirmPaymentUseCase
import xyz.lilsus.papp.platform.HapticFeedbackManager
import xyz.lilsus.papp.platform.NetworkConnectivity
import xyz.lilsus.papp.platform.createHapticFeedbackManager
import xyz.lilsus.papp.platform.createNetworkConnectivity
import xyz.lilsus.papp.presentation.addconnection.ConnectWalletViewModel
import xyz.lilsus.papp.presentation.main.CurrencyManager
import xyz.lilsus.papp.presentation.main.MainViewModel
import xyz.lilsus.papp.presentation.main.PendingPaymentTracker
import xyz.lilsus.papp.presentation.main.amount.ManualAmountConfig
import xyz.lilsus.papp.presentation.main.amount.ManualAmountController
import xyz.lilsus.papp.presentation.settings.CurrencySettingsViewModel
import xyz.lilsus.papp.presentation.settings.LanguageSettingsViewModel
import xyz.lilsus.papp.presentation.settings.PaymentsSettingsViewModel
import xyz.lilsus.papp.presentation.settings.ThemeSettingsViewModel
import xyz.lilsus.papp.presentation.settings.addblink.AddBlinkWalletViewModel
import xyz.lilsus.papp.presentation.settings.addwallet.AddWalletViewModel
import xyz.lilsus.papp.presentation.settings.wallet.WalletSettingsViewModel

val nwcModule = module {
    single<CoroutineDispatcher> { Dispatchers.Default }
    single { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>()) }

    single { createSecureSettings() }
    single<WalletSettingsRepository> {
        WalletSettingsRepositoryImpl(
            settings = get(),
            dispatcher = get(),
            scope = get()
        )
    }
    single<PaymentPreferencesRepository> { PaymentPreferencesRepositoryImpl(get()) }
    single<CurrencyPreferencesRepository> { CurrencyPreferencesRepositoryImpl(get()) }
    single<ThemePreferencesRepository> { ThemePreferencesRepositoryImpl(get()) }
    single<LanguageRepository> { createLanguageRepository() }
    single<ExchangeRateRepository> { CoinGeckoExchangeRateRepository() }
    single<LnurlRepository> { LnurlRepositoryImpl() }
    single { createNwcHttpClient() }
    single<NetworkConnectivity> { createNetworkConnectivity() }
    single<NwcClientFactory> {
        RealNwcClientFactory(
            httpClient = get(),
            scope = get()
        )
    }

    single<NwcWalletRepository> {
        NwcWalletRepositoryImpl(
            walletSettingsRepository = get(),
            clientFactory = get(),
            scope = get(),
            networkConnectivity = get()
        )
    }
    single<WalletDiscoveryRepository> {
        WalletDiscoveryRepositoryImpl(
            dispatcher = get(),
            httpClient = get()
        )
    }
    single {
        WalletMetadataSynchronizer(
            scope = get(),
            discoveryRepository = get(),
            walletSettingsRepository = get()
        )
    }

    // Blink wallet support (temporary bridge)
    single { BlinkCredentialStore(secureSettings = get()) }
    single { BlinkApiClient(httpClient = get()) }
    single {
        BlinkPaymentRepository(
            apiClient = get(),
            credentialStore = get(),
            walletSettingsRepository = get(),
            scope = get()
        )
    }

    // Unified payment service that routes to NWC or Blink
    single<PaymentProvider> {
        PaymentService(
            walletSettingsRepository = get(),
            nwcRepository = get(),
            blinkRepository = get(),
            scope = get()
        )
    }

    single { Bolt11InvoiceParser() }
    factory { LightningInputParser() }
    single<HapticFeedbackManager> { createHapticFeedbackManager() }

    factory { PayInvoiceUseCase(paymentProvider = get()) }
    factory { LookupPaymentUseCase(paymentProvider = get()) }
    factory { ObserveWalletConnectionUseCase(repository = get()) }
    factory { ObservePaymentPreferencesUseCase(repository = get()) }
    factory { ObserveCurrencyPreferenceUseCase(repository = get()) }
    factory { ObserveLanguagePreferenceUseCase(repository = get()) }
    factory { ObserveThemePreferenceUseCase(repository = get()) }
    factory { ObserveWalletsUseCase(repository = get()) }
    factory { GetWalletsUseCase(repository = get()) }
    factory { DiscoverWalletUseCase(repository = get()) }
    factory { SetWalletConnectionUseCase(repository = get()) }
    factory { SetActiveWalletUseCase(repository = get()) }
    factory { ClearWalletConnectionUseCase(repository = get()) }
    factory { SetPaymentConfirmationModeUseCase(repository = get()) }
    factory { SetPaymentConfirmationThresholdUseCase(repository = get()) }
    factory { SetConfirmManualEntryUseCase(repository = get()) }
    factory { SetVibrateOnScanUseCase(repository = get()) }
    factory { SetVibrateOnPaymentUseCase(repository = get()) }
    factory { ShouldConfirmPaymentUseCase(repository = get()) }
    factory {
        val info = CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE)
        ManualAmountController(
            defaultConfig = ManualAmountConfig(
                info = info,
                exchangeRate = null
            )
        )
    }
    factory { SetCurrencyPreferenceUseCase(repository = get()) }
    factory { SetLanguagePreferenceUseCase(repository = get()) }
    factory { SetThemePreferenceUseCase(repository = get()) }
    factory { ClearLanguageOverrideUseCase(repository = get()) }
    factory { RefreshLanguagePreferenceUseCase(repository = get()) }
    factory { GetExchangeRateUseCase(repository = get()) }
    factory {
        CurrencyManager(
            getExchangeRate = get(),
            scope = get()
        )
    }
    factory {
        PendingPaymentTracker(
            lookupPayment = get(),
            currencyManager = get(),
            scope = get()
        )
    }
    factory { FetchLnurlPayParamsUseCase(repository = get()) }
    factory { ResolveLightningAddressUseCase(repository = get()) }
    factory { RequestLnurlInvoiceUseCase(repository = get()) }

    factory {
        MainViewModel(
            payInvoice = get(),
            observeWalletConnection = get(),
            observeCurrencyPreference = get(),
            currencyManager = get(),
            pendingTracker = get(),
            bolt11Parser = get(),
            manualAmount = get(),
            shouldConfirmPayment = get(),
            lightningInputParser = get(),
            fetchLnurlPayParams = get(),
            resolveLightningAddressUseCase = get(),
            requestLnurlInvoice = get(),
            observePaymentPreferences = get(),
            haptics = get(),
            dispatcher = get()
        )
    }

    factory {
        WalletSettingsViewModel(
            observeWallets = get(),
            observeActiveWallet = get(),
            setActiveWallet = get(),
            clearWalletConnection = get()
        )
    }

    factory { AddWalletViewModel(dispatcher = get()) }

    factory {
        AddBlinkWalletViewModel(
            walletSettingsRepository = get(),
            credentialStore = get(),
            apiClient = get(),
            dispatcher = get()
        )
    }

    factory {
        PaymentsSettingsViewModel(
            observePreferences = get(),
            setConfirmationMode = get(),
            setConfirmationThreshold = get(),
            setConfirmManualEntryPreference = get(),
            setVibrateOnScanUseCase = get(),
            setVibrateOnPaymentUseCase = get()
        )
    }

    factory {
        CurrencySettingsViewModel(
            observeCurrency = get(),
            setCurrency = get()
        )
    }

    factory {
        LanguageSettingsViewModel(
            observeLanguage = get(),
            setLanguage = get(),
            clearOverride = get(),
            refreshLanguage = get()
        )
    }

    factory {
        ThemeSettingsViewModel(
            observeTheme = get(),
            setTheme = get()
        )
    }

    factory {
        ConnectWalletViewModel(
            discoverWallet = get(),
            setWalletConnection = get(),
            getWallets = get()
        )
    }
}
