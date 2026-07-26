@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.blip.domain.service

import com.russhwolf.settings.MapSettings
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.Bolt11Invoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import xyz.lilsus.blip.data.blink.BlinkApiClient
import xyz.lilsus.blip.data.blink.BlinkApolloTestTransport
import xyz.lilsus.blip.data.blink.BlinkCredentialStore
import xyz.lilsus.blip.data.blink.BlinkPaymentRepository
import xyz.lilsus.blip.data.blink.createBlinkApolloTestClient
import xyz.lilsus.blip.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.blip.domain.model.AppError
import xyz.lilsus.blip.domain.model.PaidInvoice
import xyz.lilsus.blip.domain.model.PayInvoiceRequest
import xyz.lilsus.blip.domain.model.PayInvoiceRequestState
import xyz.lilsus.blip.domain.model.PaymentLookupResult
import xyz.lilsus.blip.domain.model.WalletConnection
import xyz.lilsus.blip.domain.model.WalletType
import xyz.lilsus.blip.domain.repository.NwcWalletRepository
import xyz.lilsus.blip.platform.NetworkConnectivity
import xyz.lilsus.blip.testInvoice

class PaymentServiceTest {

    @Test
    fun startPayInvoiceRequestRoutesImmediatelyToConnectedNwcWallet() = runTest {
        val settings = MapSettings()
        val walletSettings = WalletSettingsRepositoryImpl(settings = settings)
        walletSettings.saveWalletConnection(nwcWallet("pubkey-1"))

        val nwcRepository = RecordingNwcWalletRepository()
        val paymentService = PaymentService(
            walletSettingsRepository = walletSettings,
            nwcRepository = nwcRepository,
            blinkRepository = createBlinkRepository(walletSettings, this),
            scope = backgroundScope
        )

        paymentService.startPayInvoiceRequest(testInvoice("lnbc1test"))

        assertEquals(1, nwcRepository.startCalls)
        backgroundScope.cancel()
    }

    @Test
    fun payInvoiceRoutesImmediatelyToConnectedNwcWallet() = runTest {
        val settings = MapSettings()
        val walletSettings = WalletSettingsRepositoryImpl(settings = settings)
        walletSettings.saveWalletConnection(nwcWallet("pubkey-2"))

        val nwcRepository = RecordingNwcWalletRepository()
        val paymentService = PaymentService(
            walletSettingsRepository = walletSettings,
            nwcRepository = nwcRepository,
            blinkRepository = createBlinkRepository(walletSettings, this),
            scope = backgroundScope
        )

        paymentService.payInvoice(testInvoice("lnbc1test"))

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

        override fun startPayInvoiceRequest(invoice: Bolt11Invoice, amount: MilliSatoshi?): PayInvoiceRequest {
            startCalls += 1
            return object : PayInvoiceRequest {
                override val state = MutableStateFlow<PayInvoiceRequestState>(
                    PayInvoiceRequestState.Loading
                )

                override fun cancel() = Unit
            }
        }

        override suspend fun payInvoice(invoice: Bolt11Invoice, amount: MilliSatoshi?): PaidInvoice {
            payCalls += 1
            return PaidInvoice(preimage = null, feesPaid = null)
        }

        override suspend fun lookupPayment(paymentHash: ByteVector32): PaymentLookupResult =
            PaymentLookupResult.LookupError(AppError.Unexpected("unused"))
    }
}
