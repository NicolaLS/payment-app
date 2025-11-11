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
import xyz.lilsus.papp.data.nwc.*
import xyz.lilsus.papp.data.settings.*
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceParser
import xyz.lilsus.papp.domain.lnurl.LightningInputParser
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.repository.*
import xyz.lilsus.papp.domain.use_cases.*
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
    NwcLog.setLogger(ConsoleNwcLogger)
    NwcLog.setMinimumLevel(NwcLogLevel.DEBUG)

    single<CoroutineDispatcher> { Dispatchers.Default }
    single { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>()) }

    single { createSecureSettings() }
    single<WalletSettingsRepository> { WalletSettingsRepositoryImpl(get()) }
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
            httpClient = get(),
        )
    }

    single<NwcWalletRepository> {
        NwcWalletRepositoryImpl(
            walletSettingsRepository = get(),
            clientFactory = get(),
        )
    }
    single<WalletDiscoveryRepository> {
        WalletDiscoveryRepositoryImpl(
            dispatcher = get(),
            httpClient = get(),
        )
    }
    single {
        WalletMetadataSynchronizer(
            scope = get(),
            discoveryRepository = get(),
            walletSettingsRepository = get(),
        )
    }

    single { Bolt11InvoiceParser() }
    factory { LightningInputParser() }

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
    factory { ShouldConfirmPaymentUseCase(repository = get()) }
    factory {
        val info = CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE)
        ManualAmountController(
            defaultConfig = ManualAmountConfig(
                info = info,
                exchangeRate = null,
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
            dispatcher = get(),
        )
    }

    factory {
        WalletSettingsViewModel(
            observeWallets = get(),
            observeActiveWallet = get(),
            setActiveWallet = get(),
            clearWalletConnection = get(),
        )
    }

    factory { AddWalletViewModel(dispatcher = get()) }

    factory {
        PaymentsSettingsViewModel(
            observePreferences = get(),
            setConfirmationMode = get(),
            setConfirmationThreshold = get(),
            setConfirmManualEntryPreference = get(),
        )
    }

    factory {
        CurrencySettingsViewModel(
            observeCurrency = get(),
            setCurrency = get(),
        )
    }

    factory {
        LanguageSettingsViewModel(
            observeLanguage = get(),
            setLanguage = get(),
            clearOverride = get(),
            refreshLanguage = get(),
        )
    }

    factory {
        ConnectWalletViewModel(
            discoverWallet = get(),
            setWalletConnection = get(),
            getWallets = get(),
        )
    }
}
