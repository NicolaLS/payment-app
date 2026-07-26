package xyz.lilsus.blip.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import xyz.lilsus.blip.data.blink.BlinkApiClient
import xyz.lilsus.blip.data.blink.BlinkCredentialStore
import xyz.lilsus.blip.data.blink.BlinkPaymentRepository
import xyz.lilsus.blip.data.blink.BlinkWalletAccountRepositoryImpl
import xyz.lilsus.blip.data.exchange.CoinGeckoExchangeRateRepository
import xyz.lilsus.blip.data.lnurl.LnurlRepositoryImpl
import xyz.lilsus.blip.data.network.createNwcHttpClient
import xyz.lilsus.blip.data.nwc.NwcClientFactory
import xyz.lilsus.blip.data.nwc.NwcConnectionManager
import xyz.lilsus.blip.data.nwc.NwcWalletRepositoryImpl
import xyz.lilsus.blip.data.nwc.RealNwcClientFactory
import xyz.lilsus.blip.data.nwc.WalletDiscoveryRepositoryImpl
import xyz.lilsus.blip.data.settings.ContactsRepositoryImpl
import xyz.lilsus.blip.data.settings.CurrencyPreferencesRepositoryImpl
import xyz.lilsus.blip.data.settings.OnboardingRepositoryImpl
import xyz.lilsus.blip.data.settings.PaymentPreferencesRepositoryImpl
import xyz.lilsus.blip.data.settings.ThemePreferencesRepositoryImpl
import xyz.lilsus.blip.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.blip.data.settings.createLanguageRepository
import xyz.lilsus.blip.data.settings.createOnboardingSettings
import xyz.lilsus.blip.data.settings.createSecureSettings
import xyz.lilsus.blip.domain.lnurl.LightningInputParser
import xyz.lilsus.blip.domain.model.CurrencyCatalog
import xyz.lilsus.blip.domain.model.WalletType
import xyz.lilsus.blip.domain.repository.BlinkWalletAccountRepository
import xyz.lilsus.blip.domain.repository.BlinkWalletRepository
import xyz.lilsus.blip.domain.repository.ContactsRepository
import xyz.lilsus.blip.domain.repository.CurrencyPreferencesRepository
import xyz.lilsus.blip.domain.repository.ExchangeRateRepository
import xyz.lilsus.blip.domain.repository.LanguageRepository
import xyz.lilsus.blip.domain.repository.LnurlRepository
import xyz.lilsus.blip.domain.repository.NwcWalletRepository
import xyz.lilsus.blip.domain.repository.OnboardingRepository
import xyz.lilsus.blip.domain.repository.PaymentPreferencesRepository
import xyz.lilsus.blip.domain.repository.PaymentProvider
import xyz.lilsus.blip.domain.repository.ThemePreferencesRepository
import xyz.lilsus.blip.domain.repository.WalletDiscoveryRepository
import xyz.lilsus.blip.domain.repository.WalletSettingsRepository
import xyz.lilsus.blip.domain.service.PaymentService
import xyz.lilsus.blip.domain.usecases.ClearLanguageOverrideUseCase
import xyz.lilsus.blip.domain.usecases.ConnectBlinkWalletUseCase
import xyz.lilsus.blip.domain.usecases.DeleteContactUseCase
import xyz.lilsus.blip.domain.usecases.DeleteShortcutUseCase
import xyz.lilsus.blip.domain.usecases.DiscoverWalletUseCase
import xyz.lilsus.blip.domain.usecases.FetchBlinkContactsUseCase
import xyz.lilsus.blip.domain.usecases.FetchLnurlPayParamsUseCase
import xyz.lilsus.blip.domain.usecases.GetBlinkDefaultWalletIdUseCase
import xyz.lilsus.blip.domain.usecases.GetContactsUseCase
import xyz.lilsus.blip.domain.usecases.GetExchangeRateUseCase
import xyz.lilsus.blip.domain.usecases.LookupPaymentUseCase
import xyz.lilsus.blip.domain.usecases.ObserveContactPreferencesUseCase
import xyz.lilsus.blip.domain.usecases.ObserveContactsUseCase
import xyz.lilsus.blip.domain.usecases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.blip.domain.usecases.ObserveLanguagePreferenceUseCase
import xyz.lilsus.blip.domain.usecases.ObserveOnboardingRequiredUseCase
import xyz.lilsus.blip.domain.usecases.ObservePaymentPreferencesUseCase
import xyz.lilsus.blip.domain.usecases.ObserveSecondaryCurrencyPreferenceUseCase
import xyz.lilsus.blip.domain.usecases.ObserveShortcutsUseCase
import xyz.lilsus.blip.domain.usecases.ObserveThemePreferenceUseCase
import xyz.lilsus.blip.domain.usecases.ObserveWalletConnectionUseCase
import xyz.lilsus.blip.domain.usecases.PayInvoiceUseCase
import xyz.lilsus.blip.domain.usecases.RecordContactPaymentUseCase
import xyz.lilsus.blip.domain.usecases.RecordShortcutPaymentUseCase
import xyz.lilsus.blip.domain.usecases.RefreshBlinkDefaultWalletIdUseCase
import xyz.lilsus.blip.domain.usecases.RefreshLanguagePreferenceUseCase
import xyz.lilsus.blip.domain.usecases.RemoveWalletConnectionUseCase
import xyz.lilsus.blip.domain.usecases.RequestLnurlInvoiceUseCase
import xyz.lilsus.blip.domain.usecases.ResolveLightningAddressUseCase
import xyz.lilsus.blip.domain.usecases.SaveContactUseCase
import xyz.lilsus.blip.domain.usecases.SaveShortcutUseCase
import xyz.lilsus.blip.domain.usecases.SetAskToSaveContactsUseCase
import xyz.lilsus.blip.domain.usecases.SetConfirmManualEntryUseCase
import xyz.lilsus.blip.domain.usecases.SetConfirmShortcutPaymentsUseCase
import xyz.lilsus.blip.domain.usecases.SetCurrencyPreferenceUseCase
import xyz.lilsus.blip.domain.usecases.SetLanguagePreferenceUseCase
import xyz.lilsus.blip.domain.usecases.SetPaymentConfirmationModeUseCase
import xyz.lilsus.blip.domain.usecases.SetPaymentConfirmationThresholdUseCase
import xyz.lilsus.blip.domain.usecases.SetSecondaryCurrencyPreferenceUseCase
import xyz.lilsus.blip.domain.usecases.SetThemePreferenceUseCase
import xyz.lilsus.blip.domain.usecases.SetVibrateOnPaymentUseCase
import xyz.lilsus.blip.domain.usecases.SetVibrateOnScanUseCase
import xyz.lilsus.blip.domain.usecases.SetWalletConnectionUseCase
import xyz.lilsus.blip.domain.usecases.ShouldConfirmPaymentUseCase
import xyz.lilsus.blip.domain.usecases.UpdateContactUseCase
import xyz.lilsus.blip.platform.HapticFeedbackManager
import xyz.lilsus.blip.platform.NetworkConnectivity
import xyz.lilsus.blip.platform.createAppLifecycleObserver
import xyz.lilsus.blip.platform.createHapticFeedbackManager
import xyz.lilsus.blip.platform.createNetworkConnectivity
import xyz.lilsus.blip.presentation.addconnection.ConnectWalletViewModel
import xyz.lilsus.blip.presentation.main.CurrencyManager
import xyz.lilsus.blip.presentation.main.MainViewModel
import xyz.lilsus.blip.presentation.main.amount.ManualAmountConfig
import xyz.lilsus.blip.presentation.main.amount.ManualAmountController
import xyz.lilsus.blip.presentation.onboarding.OnboardingViewModel
import xyz.lilsus.blip.presentation.settings.ContactsSettingsViewModel
import xyz.lilsus.blip.presentation.settings.CurrencySettingsViewModel
import xyz.lilsus.blip.presentation.settings.LanguageSettingsViewModel
import xyz.lilsus.blip.presentation.settings.PaymentsSettingsViewModel
import xyz.lilsus.blip.presentation.settings.ShortcutContactPickerViewModel
import xyz.lilsus.blip.presentation.settings.ThemeSettingsViewModel
import xyz.lilsus.blip.presentation.settings.addblink.AddBlinkWalletViewModel
import xyz.lilsus.blip.presentation.settings.addwallet.AddWalletViewModel
import xyz.lilsus.blip.presentation.settings.wallet.BlinkContactsImportViewModel
import xyz.lilsus.blip.presentation.settings.wallet.WalletSettingsViewModel

val nwcModule = module {
    single<CoroutineDispatcher> { Dispatchers.Default }
    single { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>()) }

    single { createSecureSettings() }
    single<WalletSettingsRepository> {
        val blinkCredentialStore = get<BlinkCredentialStore>()
        val koinScope = this
        WalletSettingsRepositoryImpl(
            settings = get(),
            onWalletRemoved = { wallet ->
                when (wallet.type) {
                    WalletType.BLINK -> {
                        blinkCredentialStore.clear()
                    }

                    WalletType.NWC -> {
                        if (wallet.uri.isNotBlank()) {
                            koinScope.get<NwcConnectionManager>().evict()
                        }
                    }
                }
            },
            onLegacyWalletsMigrated = { retained, discarded ->
                blinkCredentialStore.migrateLegacyWallets(
                    retainedWalletId = retained?.takeIf { it.isBlink }?.walletPublicKey,
                    discardedWalletIds = discarded.filter { it.isBlink }.map {
                        it.walletPublicKey
                    }
                )
            }
        )
    }
    single<OnboardingRepository> {
        OnboardingRepositoryImpl(settings = createOnboardingSettings())
    }
    single<PaymentPreferencesRepository> { PaymentPreferencesRepositoryImpl(get()) }
    single<ContactsRepository> { ContactsRepositoryImpl(get()) }
    single<CurrencyPreferencesRepository> { CurrencyPreferencesRepositoryImpl(get()) }
    single<ThemePreferencesRepository> { ThemePreferencesRepositoryImpl(get()) }
    single<LanguageRepository> { createLanguageRepository() }
    single<ExchangeRateRepository> { CoinGeckoExchangeRateRepository() }
    single<LnurlRepository> { LnurlRepositoryImpl(networkConnectivity = get()) }
    single { createNwcHttpClient() }
    single<NetworkConnectivity> { createNetworkConnectivity() }

    single { createAppLifecycleObserver() }

    single<NwcClientFactory> {
        RealNwcClientFactory(
            httpClient = get(),
            scope = get()
        )
    }

    single(createdAtStart = true) {
        NwcConnectionManager(
            appLifecycle = get(),
            walletSettings = get(),
            clientFactory = get(),
            scope = get()
        )
    }

    single<NwcWalletRepository> {
        NwcWalletRepositoryImpl(
            connectionManager = get(),
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
    // Blink wallet support
    single { BlinkCredentialStore(secureSettings = get()) }
    single { BlinkApiClient() }
    single<BlinkWalletRepository> {
        BlinkPaymentRepository(
            apiClient = get(),
            credentialStore = get(),
            walletSettingsRepository = get(),
            networkConnectivity = get(),
            scope = get()
        )
    }
    single<BlinkWalletAccountRepository> {
        BlinkWalletAccountRepositoryImpl(
            apiClient = get(),
            credentialStore = get(),
            walletSettingsRepository = get()
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

    factory { LightningInputParser() }
    single<HapticFeedbackManager> { createHapticFeedbackManager() }

    factory { PayInvoiceUseCase(paymentProvider = get()) }
    factory { LookupPaymentUseCase(paymentProvider = get()) }
    factory { ConnectBlinkWalletUseCase(repository = get()) }
    factory { GetBlinkDefaultWalletIdUseCase(repository = get()) }
    factory { RefreshBlinkDefaultWalletIdUseCase(repository = get()) }
    factory { ObserveWalletConnectionUseCase(repository = get()) }
    factory { ObservePaymentPreferencesUseCase(repository = get()) }
    factory { ObserveCurrencyPreferenceUseCase(repository = get()) }
    factory { ObserveSecondaryCurrencyPreferenceUseCase(repository = get()) }
    factory { ObserveLanguagePreferenceUseCase(repository = get()) }
    factory { ObserveThemePreferenceUseCase(repository = get()) }
    factory {
        ObserveOnboardingRequiredUseCase(
            onboardingRepository = get(),
            walletSettingsRepository = get()
        )
    }
    factory { DiscoverWalletUseCase(repository = get()) }
    factory { SetWalletConnectionUseCase(repository = get()) }
    factory { RemoveWalletConnectionUseCase(repository = get()) }
    factory { SetPaymentConfirmationModeUseCase(repository = get()) }
    factory { SetPaymentConfirmationThresholdUseCase(repository = get()) }
    factory { SetConfirmManualEntryUseCase(repository = get()) }
    factory { SetConfirmShortcutPaymentsUseCase(repository = get()) }
    factory { SetVibrateOnScanUseCase(repository = get()) }
    factory { SetVibrateOnPaymentUseCase(repository = get()) }
    factory { ShouldConfirmPaymentUseCase(repository = get()) }
    factory { ObserveContactsUseCase(repository = get()) }
    factory { GetContactsUseCase(repository = get()) }
    factory { ObserveShortcutsUseCase(repository = get()) }
    factory { ObserveContactPreferencesUseCase(repository = get()) }
    factory { SaveContactUseCase(repository = get()) }
    factory { UpdateContactUseCase(repository = get()) }
    factory { DeleteContactUseCase(repository = get()) }
    factory { SaveShortcutUseCase(repository = get()) }
    factory { DeleteShortcutUseCase(repository = get()) }
    factory { RecordContactPaymentUseCase(repository = get()) }
    factory { RecordShortcutPaymentUseCase(repository = get()) }
    factory { SetAskToSaveContactsUseCase(repository = get()) }
    factory { FetchBlinkContactsUseCase(repository = get()) }
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
    factory { SetSecondaryCurrencyPreferenceUseCase(repository = get()) }
    factory { SetLanguagePreferenceUseCase(repository = get()) }
    factory { SetThemePreferenceUseCase(repository = get()) }
    factory { ClearLanguageOverrideUseCase(repository = get()) }
    factory { RefreshLanguagePreferenceUseCase(repository = get()) }
    factory { GetExchangeRateUseCase(repository = get()) }
    single {
        CurrencyManager(
            getExchangeRate = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        )
    }
    factory { FetchLnurlPayParamsUseCase(repository = get()) }
    factory { ResolveLightningAddressUseCase(repository = get()) }
    factory { RequestLnurlInvoiceUseCase(repository = get()) }

    factory {
        MainViewModel(
            payInvoice = get(),
            lookupPayment = get(),
            observeWalletConnection = get(),
            observeCurrencyPreference = get(),
            currencyManager = get(),
            manualAmount = get(),
            shouldConfirmPayment = get(),
            lightningInputParser = get(),
            fetchLnurlPayParams = get(),
            resolveLightningAddressUseCase = get(),
            requestLnurlInvoice = get(),
            observePaymentPreferences = get(),
            haptics = get(),
            observeContacts = get(),
            observeShortcuts = get(),
            observeContactPreferences = get(),
            saveContact = get(),
            updateContact = get(),
            deleteContact = get(),
            recordContactPayment = get(),
            recordShortcutPayment = get()
        )
    }

    factory {
        WalletSettingsViewModel(
            observeWalletConnection = get(),
            removeWalletConnection = get()
        )
    }

    factory { AddWalletViewModel(dispatcher = get()) }

    factory {
        AddBlinkWalletViewModel(
            connectBlinkWallet = get(),
            dispatcher = get()
        )
    }

    factory {
        PaymentsSettingsViewModel(
            observePreferences = get(),
            observeCurrencyPreference = get(),
            observeSecondaryCurrencyPreference = get(),
            getExchangeRate = get(),
            currencyManager = get(),
            setConfirmationMode = get(),
            setConfirmationThreshold = get(),
            setConfirmManualEntryPreference = get(),
            setConfirmShortcutPaymentsUseCase = get(),
            setVibrateOnScanUseCase = get(),
            setVibrateOnPaymentUseCase = get(),
            observeContactPreferences = get(),
            setAskToSaveContactsUseCase = get(),
            observeContacts = get(),
            observeShortcuts = get(),
            saveShortcut = get(),
            deleteShortcutUseCase = get(),
            autoSaveScope = get()
        )
    }

    factory {
        ShortcutContactPickerViewModel(
            observeContacts = get(),
            dispatcher = get()
        )
    }

    factory {
        ContactsSettingsViewModel(
            observeContacts = get(),
            observeWalletConnection = get(),
            observeShortcuts = get(),
            saveContact = get(),
            updateContact = get(),
            deleteContactUseCase = get(),
            lightningInputParser = get(),
            dispatcher = get(),
            autoSaveScope = get()
        )
    }

    factory {
        BlinkContactsImportViewModel(
            fetchBlinkContacts = get(),
            getContacts = get(),
            saveContact = get(),
            lightningInputParser = get(),
            dispatcher = get()
        )
    }

    factory {
        CurrencySettingsViewModel(
            observeCurrency = get(),
            observeSecondaryCurrency = get(),
            setCurrency = get(),
            setSecondaryCurrency = get()
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
            setWalletConnection = get()
        )
    }

    factory {
        OnboardingViewModel(
            persistConfirmationMode = get(),
            persistConfirmationThreshold = get(),
            observeSecondaryCurrencyPreference = get(),
            getExchangeRate = get()
        )
    }
}
