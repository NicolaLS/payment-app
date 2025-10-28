package xyz.lilsus.papp.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import xyz.lilsus.papp.data.nwc.NwcWalletRepositoryImpl
import xyz.lilsus.papp.domain.repository.NwcWalletRepository
import xyz.lilsus.papp.domain.use_cases.PayInvoiceUseCase

val nwcModule = module {
    single<CoroutineDispatcher> { Dispatchers.Default }
    single { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>()) }

    factory<NwcWalletRepository> { (connectUri: String) ->
        NwcWalletRepositoryImpl(
            connectUri = connectUri,
            scope = get(),
        )
    }

    factory { (connectUri: String) ->
        PayInvoiceUseCase(
            repository = get<NwcWalletRepository> { parametersOf(connectUri) },
            dispatcher = get(),
        )
    }
}
