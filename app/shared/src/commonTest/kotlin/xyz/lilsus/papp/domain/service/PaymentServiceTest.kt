@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.domain.service

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.data.blink.BlinkApiClient
import xyz.lilsus.papp.data.blink.BlinkApolloTestTransport
import xyz.lilsus.papp.data.blink.BlinkCredentialStore
import xyz.lilsus.papp.data.blink.BlinkPaymentRepository
import xyz.lilsus.papp.data.blink.createBlinkApolloTestClient
import xyz.lilsus.papp.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.model.PaymentLookupResult
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletPaymentTarget
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
        val transport = BlinkApolloTestTransport {
            error("Blink repository should not be called by NWC routing tests")
        }
        return BlinkPaymentRepository(
            apiClient = BlinkApiClient(createBlinkApolloTestClient(transport)),
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

        override fun startPayInvoiceRequest(invoice: String, amountMsats: Long?, walletTarget: WalletPaymentTarget?): PayInvoiceRequest {
            startCalls += 1
            return object : PayInvoiceRequest {
                override val state = MutableStateFlow<PayInvoiceRequestState>(
                    PayInvoiceRequestState.Loading
                )

                override fun cancel() = Unit
            }
        }

        override suspend fun payInvoice(invoice: String, amountMsats: Long?, walletTarget: WalletPaymentTarget?): PaidInvoice {
            payCalls += 1
            return PaidInvoice(preimage = null, feesPaidMsats = null)
        }

        override suspend fun lookupPayment(paymentHash: String, walletTarget: WalletPaymentTarget?): PaymentLookupResult =
            PaymentLookupResult.LookupError(AppError.Unexpected("unused"))
    }
}
