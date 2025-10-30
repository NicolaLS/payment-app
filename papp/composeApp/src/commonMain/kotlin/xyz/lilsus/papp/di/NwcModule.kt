package xyz.lilsus.papp.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import xyz.lilsus.papp.data.nwc.NwcWalletRepositoryImpl
import xyz.lilsus.papp.data.exchange.CoinGeckoExchangeRateRepository
import xyz.lilsus.papp.data.lnurl.LnurlRepositoryImpl
import xyz.lilsus.papp.data.settings.CurrencyPreferencesRepositoryImpl
import xyz.lilsus.papp.data.settings.PaymentPreferencesRepositoryImpl
import xyz.lilsus.papp.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.papp.data.settings.createSecureSettings
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceParser
import xyz.lilsus.papp.domain.lnurl.LightningInputParser
import xyz.lilsus.papp.domain.repository.NwcWalletRepository
import xyz.lilsus.papp.domain.repository.CurrencyPreferencesRepository
import xyz.lilsus.papp.domain.repository.PaymentPreferencesRepository
import xyz.lilsus.papp.domain.repository.ExchangeRateRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.domain.repository.LnurlRepository
import xyz.lilsus.papp.domain.use_cases.ClearWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.ObservePaymentPreferencesUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletsUseCase
import xyz.lilsus.papp.domain.use_cases.PayInvoiceUseCase
import xyz.lilsus.papp.domain.use_cases.SetPaymentConfirmationModeUseCase
import xyz.lilsus.papp.domain.use_cases.SetPaymentConfirmationThresholdUseCase
import xyz.lilsus.papp.domain.use_cases.SetConfirmManualEntryUseCase
import xyz.lilsus.papp.domain.use_cases.SetWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.SetActiveWalletUseCase
import xyz.lilsus.papp.domain.use_cases.ShouldConfirmPaymentUseCase
import xyz.lilsus.papp.domain.use_cases.SetCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.GetExchangeRateUseCase
import xyz.lilsus.papp.domain.use_cases.FetchLnurlPayParamsUseCase
import xyz.lilsus.papp.domain.use_cases.ResolveLightningAddressUseCase
import xyz.lilsus.papp.domain.use_cases.RequestLnurlInvoiceUseCase
import xyz.lilsus.papp.presentation.main.MainViewModel
import xyz.lilsus.papp.presentation.main.amount.ManualAmountController
import xyz.lilsus.papp.presentation.main.amount.ManualAmountConfig
import xyz.lilsus.papp.presentation.settings.PaymentsSettingsViewModel
import xyz.lilsus.papp.presentation.settings.CurrencySettingsViewModel
import xyz.lilsus.papp.presentation.settings.wallet.WalletSettingsViewModel
import xyz.lilsus.papp.presentation.add_connection.ConnectWalletViewModel
import xyz.lilsus.papp.domain.model.CurrencyCatalog

val nwcModule = module {
    single<CoroutineDispatcher> { Dispatchers.Default }
    single { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>()) }

    single { createSecureSettings() }
    single<WalletSettingsRepository> { WalletSettingsRepositoryImpl(get()) }
    single<PaymentPreferencesRepository> { PaymentPreferencesRepositoryImpl(get()) }
    single<CurrencyPreferencesRepository> { CurrencyPreferencesRepositoryImpl(get()) }
    single<ExchangeRateRepository> { CoinGeckoExchangeRateRepository() }
    single<LnurlRepository> { LnurlRepositoryImpl() }

    single<NwcWalletRepository> {
        NwcWalletRepositoryImpl(
            walletSettingsRepository = get(),
            scope = get(),
        )
    }

    single { Bolt11InvoiceParser() }
    factory { LightningInputParser() }

    factory { PayInvoiceUseCase(repository = get(), dispatcher = get()) }
    factory { ObserveWalletConnectionUseCase(repository = get()) }
    factory { ObservePaymentPreferencesUseCase(repository = get()) }
    factory { ObserveCurrencyPreferenceUseCase(repository = get()) }
    factory { ObserveWalletsUseCase(repository = get()) }
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
        ConnectWalletViewModel(
            setWalletConnection = get(),
            observeWalletConnection = get(),
        )
    }
}
