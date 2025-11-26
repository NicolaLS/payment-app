package xyz.lilsus.papp.di

import io.github.nostr.nwc.NwcSessionManager
import io.github.nostr.nwc.logging.ConsoleNwcLogger
import io.github.nostr.nwc.logging.NwcLog
import io.github.nostr.nwc.logging.NwcLogLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
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
import xyz.lilsus.papp.domain.repository.WalletDiscoveryRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.domain.use_cases.ClearLanguageOverrideUseCase
import xyz.lilsus.papp.domain.use_cases.ClearWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.DiscoverWalletUseCase
import xyz.lilsus.papp.domain.use_cases.FetchLnurlPayParamsUseCase
import xyz.lilsus.papp.domain.use_cases.GetExchangeRateUseCase
import xyz.lilsus.papp.domain.use_cases.GetWalletsUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.ObservePaymentPreferencesUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletsUseCase
import xyz.lilsus.papp.domain.use_cases.PayInvoiceUseCase
import xyz.lilsus.papp.domain.use_cases.RefreshLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.RequestLnurlInvoiceUseCase
import xyz.lilsus.papp.domain.use_cases.ResolveLightningAddressUseCase
import xyz.lilsus.papp.domain.use_cases.SetActiveWalletUseCase
import xyz.lilsus.papp.domain.use_cases.SetConfirmManualEntryUseCase
import xyz.lilsus.papp.domain.use_cases.SetCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.SetLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.SetPaymentConfirmationModeUseCase
import xyz.lilsus.papp.domain.use_cases.SetPaymentConfirmationThresholdUseCase
import xyz.lilsus.papp.domain.use_cases.SetVibrateOnPaymentUseCase
import xyz.lilsus.papp.domain.use_cases.SetVibrateOnScanUseCase
import xyz.lilsus.papp.domain.use_cases.SetWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.ShouldConfirmPaymentUseCase
import xyz.lilsus.papp.platform.HapticFeedbackManager
import xyz.lilsus.papp.platform.createHapticFeedbackManager
import xyz.lilsus.papp.presentation.add_connection.ConnectWalletViewModel
import xyz.lilsus.papp.presentation.main.MainViewModel
import xyz.lilsus.papp.presentation.main.amount.ManualAmountConfig
import xyz.lilsus.papp.presentation.main.amount.ManualAmountController
import xyz.lilsus.papp.presentation.settings.CurrencySettingsViewModel
import xyz.lilsus.papp.presentation.settings.LanguageSettingsViewModel
import xyz.lilsus.papp.presentation.settings.PaymentsSettingsViewModel
import xyz.lilsus.papp.presentation.settings.add_wallet.AddWalletViewModel
import xyz.lilsus.papp.presentation.settings.wallet.WalletSettingsViewModel

val nwcModule = module {
    // Only enable NWC logging in debug builds
    // R8/ProGuard will strip these calls in release builds automatically
    if (xyz.lilsus.papp.isDebugBuild) {
        NwcLog.setLogger(ConsoleNwcLogger)
        NwcLog.setMinimumLevel(NwcLogLevel.DEBUG)
    }

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
    single<LanguageRepository> { createLanguageRepository() }
    single<ExchangeRateRepository> { CoinGeckoExchangeRateRepository() }
    single<LnurlRepository> { LnurlRepositoryImpl() }
    single { createNwcHttpClient() }
    single { NwcSessionManager.create(scope = get(), httpClient = get()) }
    single<NwcClientFactory> {
        RealNwcClientFactory(
            sessionManager = get(),
            scope = get(),
            httpClient = get()
        )
    }

    single<NwcWalletRepository> {
        NwcWalletRepositoryImpl(
            walletSettingsRepository = get(),
            clientFactory = get(),
            scope = get()
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

    single { Bolt11InvoiceParser() }
    factory { LightningInputParser() }
    single<HapticFeedbackManager> { createHapticFeedbackManager() }

    factory { PayInvoiceUseCase(repository = get(), dispatcher = get()) }
    factory { ObserveWalletConnectionUseCase(repository = get()) }
    factory { ObservePaymentPreferencesUseCase(repository = get()) }
    factory { ObserveCurrencyPreferenceUseCase(repository = get()) }
    factory { ObserveLanguagePreferenceUseCase(repository = get()) }
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
    factory { ClearLanguageOverrideUseCase(repository = get()) }
    factory { RefreshLanguagePreferenceUseCase(repository = get()) }
    factory { GetExchangeRateUseCase(repository = get()) }
    factory { FetchLnurlPayParamsUseCase(repository = get()) }
    factory { ResolveLightningAddressUseCase(repository = get()) }
    factory { RequestLnurlInvoiceUseCase(repository = get()) }

    factory {
        MainViewModel(
            payInvoice = get(),
            observeWalletConnection = get(),
            observeCurrencyPreference = get(),
            getExchangeRate = get(),
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
        ConnectWalletViewModel(
            discoverWallet = get(),
            setWalletConnection = get(),
            getWallets = get()
        )
    }
}
