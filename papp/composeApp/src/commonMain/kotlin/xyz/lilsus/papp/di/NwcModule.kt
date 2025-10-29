package xyz.lilsus.papp.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import xyz.lilsus.papp.data.nwc.NwcWalletRepositoryImpl
import xyz.lilsus.papp.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.papp.data.settings.createSecureSettings
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceParser
import xyz.lilsus.papp.domain.repository.NwcWalletRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.domain.use_cases.ClearWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveWalletsUseCase
import xyz.lilsus.papp.domain.use_cases.PayInvoiceUseCase
import xyz.lilsus.papp.domain.use_cases.SetWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.SetActiveWalletUseCase
import xyz.lilsus.papp.presentation.main.MainViewModel
import xyz.lilsus.papp.presentation.settings.wallet.WalletSettingsViewModel
import xyz.lilsus.papp.presentation.add_connection.ConnectWalletViewModel

val nwcModule = module {
    single<CoroutineDispatcher> { Dispatchers.Default }
    single { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>()) }

    single { createSecureSettings() }
    single<WalletSettingsRepository> { WalletSettingsRepositoryImpl(get()) }

    single<NwcWalletRepository> {
        NwcWalletRepositoryImpl(
            walletSettingsRepository = get(),
            scope = get(),
        )
    }

    single { Bolt11InvoiceParser() }

    factory { PayInvoiceUseCase(repository = get(), dispatcher = get()) }
    factory { ObserveWalletConnectionUseCase(repository = get()) }
    factory { ObserveWalletsUseCase(repository = get()) }
    factory { SetWalletConnectionUseCase(repository = get()) }
    factory { SetActiveWalletUseCase(repository = get()) }
    factory { ClearWalletConnectionUseCase(repository = get()) }

    factory {
        MainViewModel(
            payInvoice = get(),
            dispatcher = get(),
            observeWalletConnection = get(),
            bolt11Parser = get(),
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
        ConnectWalletViewModel(
            setWalletConnection = get(),
            observeWalletConnection = get(),
        )
    }
}
