@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.domain.service

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import xyz.lilsus.papp.data.blink.BlinkApiClient
import xyz.lilsus.papp.data.blink.BlinkCredentialStore
import xyz.lilsus.papp.data.blink.BlinkPaymentRepository
import xyz.lilsus.papp.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.model.PaymentLookupResult
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.NwcWalletRepository
import xyz.lilsus.papp.platform.NetworkConnectivity

class PaymentServiceTest {

    @Test
    fun startPayInvoiceRequestRoutesImmediatelyToActiveNwcWallet() = runTest {
        val settings = MapSettings()
        val walletSettings = WalletSettingsRepositoryImpl(settings = settings)
        walletSettings.saveWalletConnection(nwcWallet("pubkey-1"), activate = true)

        val nwcRepository = RecordingNwcWalletRepository()
        val paymentService = PaymentService(
            walletSettingsRepository = walletSettings,
            nwcRepository = nwcRepository,
            blinkRepository = createBlinkRepository(walletSettings, this),
            scope = backgroundScope
        )

        paymentService.startPayInvoiceRequest("lnbc1test")

        assertEquals(1, nwcRepository.startCalls)
        backgroundScope.cancel()
    }

    @Test
    fun payInvoiceRoutesImmediatelyToActiveNwcWallet() = runTest {
        val settings = MapSettings()
        val walletSettings = WalletSettingsRepositoryImpl(settings = settings)
        walletSettings.saveWalletConnection(nwcWallet("pubkey-2"), activate = true)

        val nwcRepository = RecordingNwcWalletRepository()
        val paymentService = PaymentService(
            walletSettingsRepository = walletSettings,
            nwcRepository = nwcRepository,
            blinkRepository = createBlinkRepository(walletSettings, this),
            scope = backgroundScope
        )

        paymentService.payInvoice("lnbc1test")

        assertEquals(1, nwcRepository.payCalls)
        backgroundScope.cancel()
    }

    private fun nwcWallet(pubkey: String): WalletConnection = WalletConnection(
        walletPublicKey = pubkey,
        alias = "wallet-$pubkey",
        type = WalletType.NWC,
        uri = "nostr+walletconnect://$pubkey?relay=wss://relay.example&secret=sec"
    )

    private fun createBlinkRepository(
        walletSettingsRepository: WalletSettingsRepositoryImpl,
        scope: CoroutineScope
    ): BlinkPaymentRepository {
        val engine = MockEngine { _ ->
            respond(
                content = """{"data":{}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
            )
        }
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return BlinkPaymentRepository(
            apiClient = BlinkApiClient(httpClient),
            credentialStore = BlinkCredentialStore(MapSettings()),
            walletSettingsRepository = walletSettingsRepository,
            networkConnectivity = object : NetworkConnectivity {
                override fun isNetworkAvailable(): Boolean = true
            },
            scope = scope
        )
    }

    private class RecordingNwcWalletRepository : NwcWalletRepository {
        var startCalls: Int = 0
        var payCalls: Int = 0

        override fun startPayInvoiceRequest(invoice: String, amountMsats: Long?): PayInvoiceRequest {
            startCalls += 1
            return object : PayInvoiceRequest {
                override val state = MutableStateFlow<PayInvoiceRequestState>(
                    PayInvoiceRequestState.Loading
                )

                override fun cancel() = Unit
            }
        }

        override suspend fun payInvoice(invoice: String, amountMsats: Long?): PaidInvoice {
            payCalls += 1
            return PaidInvoice(preimage = null, feesPaidMsats = null)
        }

        override suspend fun lookupPayment(paymentHash: String, walletUri: String?, walletType: WalletType?): PaymentLookupResult =
            PaymentLookupResult.LookupError(AppError.Unexpected("unused"))
    }
}
