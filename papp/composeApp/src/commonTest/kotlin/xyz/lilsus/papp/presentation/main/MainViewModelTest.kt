package xyz.lilsus.papp.presentation.main

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceParser
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceSummary
import xyz.lilsus.papp.domain.bolt11.Bolt11Memo
import xyz.lilsus.papp.domain.bolt11.Bolt11ParseResult
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.lnurl.LightningInputParser
import xyz.lilsus.papp.domain.lnurl.LnurlPayMetadata
import xyz.lilsus.papp.domain.lnurl.LnurlPayParams
import xyz.lilsus.papp.domain.model.*
import xyz.lilsus.papp.domain.model.exchange.ExchangeRate
import xyz.lilsus.papp.domain.repository.*
import xyz.lilsus.papp.domain.use_cases.*
import xyz.lilsus.papp.presentation.main.amount.ManualAmountConfig
import xyz.lilsus.papp.presentation.main.amount.ManualAmountController
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey
import kotlin.test.*

class MainViewModelTest {
    private lateinit var walletSettingsRepository: FakeWalletSettingsRepository
    private lateinit var walletConnection: ObserveWalletConnectionUseCase
    private val dispatcher = Dispatchers.Unconfined

    @BeforeTest
    fun setUp() {
        walletSettingsRepository = FakeWalletSettingsRepository()
        walletConnection = ObserveWalletConnectionUseCase(walletSettingsRepository)
    }

    @AfterTest
    fun tearDown() {
        walletSettingsRepository.reset()
    }

    @Test
    fun showsManualAmountWhenInvoiceOmitsAmount() = runBlocking {
        val parser = FakeBolt11InvoiceParser(
            mapOf(
                MANUAL_INVOICE_INPUT to Bolt11InvoiceSummary(
                    paymentRequest = MANUAL_PAYMENT_REQUEST,
                    amountMsats = null,
                    memo = Bolt11Memo.None,
                )
            )
        )
        val repository = RecordingNwcWalletRepository()
        val viewModel = createViewModel(parser, repository)
        try {
            viewModel.dispatch(MainIntent.InvoiceDetected(MANUAL_INVOICE_INPUT))
            val state = viewModel.uiState.first { it is MainUiState.EnterAmount }
            assertTrue(state is MainUiState.EnterAmount)
            assertNull(state.entry.amount)
        } finally {
            viewModel.clear()
        }
    }

    @Test
    fun manualAmountKeyPressBuildsAmountInSats() = runBlocking {
        val parser = FakeBolt11InvoiceParser(
            mapOf(
                MANUAL_INVOICE_INPUT to Bolt11InvoiceSummary(
                    paymentRequest = MANUAL_PAYMENT_REQUEST,
                    amountMsats = null,
                    memo = Bolt11Memo.None,
                )
            )
        )
        val repository = RecordingNwcWalletRepository()
        val viewModel = createViewModel(parser, repository)
        try {
            viewModel.dispatch(MainIntent.InvoiceDetected(MANUAL_INVOICE_INPUT))
            viewModel.uiState.first { it is MainUiState.EnterAmount }

            viewModel.dispatch(MainIntent.ManualAmountKeyPress(ManualAmountKey.Digit(1)))
            viewModel.dispatch(MainIntent.ManualAmountKeyPress(ManualAmountKey.Digit(2)))

            val state = viewModel.uiState.first {
                it is MainUiState.EnterAmount && it.entry.amount?.minor == 12L
            }
            val enterState = state as MainUiState.EnterAmount
            assertEquals(12L, enterState.entry.amount?.minor)
        } finally {
            viewModel.clear()
        }
    }

    @Test
    fun manualAmountSubmitPaysInvoiceWithOverrideAmount() = runBlocking {
        val parser = FakeBolt11InvoiceParser(
            mapOf(
                MANUAL_INVOICE_INPUT to Bolt11InvoiceSummary(
                    paymentRequest = MANUAL_PAYMENT_REQUEST,
                    amountMsats = null,
                    memo = Bolt11Memo.None,
                )
            )
        )
        val repository = RecordingNwcWalletRepository()
        val viewModel = createViewModel(parser, repository)
        try {
            viewModel.dispatch(MainIntent.InvoiceDetected(MANUAL_INVOICE_INPUT))
            viewModel.uiState.first { it is MainUiState.EnterAmount }

            listOf(1, 2, 3).forEach { digit ->
                viewModel.dispatch(MainIntent.ManualAmountKeyPress(ManualAmountKey.Digit(digit)))
            }

            viewModel.dispatch(MainIntent.ManualAmountSubmit)

            val success = viewModel.uiState.first { it is MainUiState.Success } as MainUiState.Success
            assertEquals(MANUAL_PAYMENT_REQUEST, repository.lastInvoice)
            assertEquals(123_000L, repository.lastAmountMsats)
            assertEquals(123L, success.amountPaid.minor)
        } finally {
            viewModel.clear()
        }
    }

    @Test
    fun manualAmountSubmitConvertsFiatAmount() = runBlocking {
        val parser = FakeBolt11InvoiceParser(
            mapOf(
                MANUAL_INVOICE_INPUT to Bolt11InvoiceSummary(
                    paymentRequest = MANUAL_PAYMENT_REQUEST,
                    amountMsats = null,
                    memo = Bolt11Memo.None,
                )
            )
        )
        val repository = RecordingNwcWalletRepository()
        val exchangeRate = 60_000.0
        val viewModel = createViewModel(
            parser = parser,
            repository = repository,
            currencyCode = "USD",
            exchangeRateResult = Result.Success(
                ExchangeRate(currencyCode = "USD", pricePerBitcoin = exchangeRate),
            ),
        )
        try {
            viewModel.dispatch(MainIntent.InvoiceDetected(MANUAL_INVOICE_INPUT))
            viewModel.uiState.first { it is MainUiState.EnterAmount }

            viewModel.dispatch(MainIntent.ManualAmountKeyPress(ManualAmountKey.Digit(3)))
            viewModel.dispatch(MainIntent.ManualAmountKeyPress(ManualAmountKey.Digit(0)))
            viewModel.dispatch(MainIntent.ManualAmountKeyPress(ManualAmountKey.Decimal))
            viewModel.dispatch(MainIntent.ManualAmountKeyPress(ManualAmountKey.Digit(0)))
            viewModel.dispatch(MainIntent.ManualAmountKeyPress(ManualAmountKey.Digit(0)))

            viewModel.uiState.first {
                it is MainUiState.EnterAmount &&
                        it.entry.amount?.minor == 3_000L &&
                        it.entry.currency == DisplayCurrency.Fiat("USD")
            }

            viewModel.dispatch(MainIntent.ManualAmountSubmit)

            val success = viewModel.uiState.first { it is MainUiState.Success } as MainUiState.Success
            assertEquals(50_000_000L, repository.lastAmountMsats)
            assertEquals(DisplayCurrency.Fiat("USD"), success.amountPaid.currency)
            assertEquals(3_000L, success.amountPaid.minor)
        } finally {
            viewModel.clear()
        }
    }

    @Test
    fun manualAmountSubmitRequiresConfirmationWhenPreferenceEnabled() = runBlocking {
        val parser = FakeBolt11InvoiceParser(
            mapOf(
                MANUAL_INVOICE_INPUT to Bolt11InvoiceSummary(
                    paymentRequest = MANUAL_PAYMENT_REQUEST,
                    amountMsats = null,
                    memo = Bolt11Memo.None,
                )
            )
        )
        val repository = RecordingNwcWalletRepository()
        val viewModel = createViewModel(
            parser = parser,
            repository = repository,
            preferences = PaymentPreferences(
                confirmationMode = PaymentConfirmationMode.Above,
                thresholdSats = 50,
                confirmManualEntry = true,
            ),
        )
        try {
            viewModel.dispatch(MainIntent.InvoiceDetected(MANUAL_INVOICE_INPUT))
            viewModel.uiState.first { it is MainUiState.EnterAmount }

            listOf(1, 0, 0).forEach { digit ->
                viewModel.dispatch(MainIntent.ManualAmountKeyPress(ManualAmountKey.Digit(digit)))
            }

            viewModel.dispatch(MainIntent.ManualAmountSubmit)

            val confirm = viewModel.uiState.first { it is MainUiState.Confirm } as MainUiState.Confirm
            assertEquals(100L, confirm.amount.minor)
            assertNull(repository.lastInvoice)

            viewModel.dispatch(MainIntent.ConfirmPaymentSubmit)

            viewModel.uiState.first { it is MainUiState.Success }
            assertEquals(MANUAL_PAYMENT_REQUEST, repository.lastInvoice)
            assertEquals(100_000L, repository.lastAmountMsats)
        } finally {
            viewModel.clear()
        }
    }

    @Test
    fun invoiceAmountRequiresConfirmationWhenPreferenceDemands() = runBlocking {
        val parser = FakeBolt11InvoiceParser(
            mapOf(
                AMOUNT_INVOICE_INPUT to Bolt11InvoiceSummary(
                    paymentRequest = AMOUNT_PAYMENT_REQUEST,
                    amountMsats = 250_000L,
                    memo = Bolt11Memo.None,
                )
            )
        )
        val repository = RecordingNwcWalletRepository()
        val viewModel = createViewModel(
            parser = parser,
            repository = repository,
            preferences = PaymentPreferences(confirmationMode = PaymentConfirmationMode.Always),
        )
        try {
            viewModel.dispatch(MainIntent.InvoiceDetected(AMOUNT_INVOICE_INPUT))

            val confirm = viewModel.uiState.first { it is MainUiState.Confirm } as MainUiState.Confirm
            assertEquals(250L, confirm.amount.minor)
            assertNull(repository.lastInvoice)

            viewModel.dispatch(MainIntent.ConfirmPaymentSubmit)

            viewModel.uiState.first { it is MainUiState.Success }
            assertEquals(AMOUNT_PAYMENT_REQUEST, repository.lastInvoice)
            assertNull(repository.lastAmountMsats)
        } finally {
            viewModel.clear()
        }
    }

    @Test
    fun ignoresNewInvoiceWhilePaymentInFlight() {
        runBlocking {
            val parser = FakeBolt11InvoiceParser(
                mapOf(
                    AMOUNT_INVOICE_INPUT to Bolt11InvoiceSummary(
                        paymentRequest = AMOUNT_PAYMENT_REQUEST,
                        amountMsats = 10_000L,
                        memo = Bolt11Memo.None,
                    )
                )
            )
            val repository = BlockingNwcWalletRepository()
            val viewModel = createViewModel(parser, repository)
            try {
                viewModel.dispatch(MainIntent.InvoiceDetected(AMOUNT_INVOICE_INPUT))
                viewModel.uiState.first { it is MainUiState.Loading }

                viewModel.dispatch(MainIntent.InvoiceDetected("ignored-invoice"))

                assertEquals(
                    listOf(AMOUNT_PAYMENT_REQUEST to null),
                    repository.invoices,
                )

                repository.complete()
                viewModel.uiState.first { it is MainUiState.Success }
                assertEquals(
                    listOf(AMOUNT_PAYMENT_REQUEST to null),
                    repository.invoices,
                )
            } finally {
                repository.completeIfNeeded()
                viewModel.clear()
            }
        }
    }

    @Test
    fun lnurlFixedAmountPaysInvoice() = runBlocking {
        val amountMsats = 50_000L
        val lnurlInvoice = "lnbc1lnurlfixed"
        val params = LnurlPayParams(
            callback = LNURL_CALLBACK,
            minSendable = amountMsats,
            maxSendable = amountMsats,
            metadataRaw = LNURL_METADATA_RAW,
            metadata = LNURL_METADATA,
            commentAllowed = null,
            domain = "example.com",
        )
        val lnurlRepository = FakeLnurlRepository().apply {
            stubEndpoint(LNURL_ENDPOINT, Result.Success(params))
            stubInvoice(LNURL_CALLBACK, amountMsats, Result.Success(lnurlInvoice))
        }
        val parser = FakeBolt11InvoiceParser(
            mapOf(
                lnurlInvoice to Bolt11InvoiceSummary(
                    paymentRequest = lnurlInvoice,
                    amountMsats = amountMsats,
                    memo = Bolt11Memo.Text("Payment"),
                )
            )
        )
        val repository = RecordingNwcWalletRepository()
        val viewModel = createViewModel(
            parser = parser,
            repository = repository,
            lnurlRepository = lnurlRepository,
        )
        try {
            viewModel.dispatch(MainIntent.InvoiceDetected(LNURL_ENDPOINT))

            val success = viewModel.uiState.first { it is MainUiState.Success } as MainUiState.Success
            assertEquals(lnurlInvoice, repository.lastInvoice)
            assertEquals(amountMsats, repository.lastAmountMsats)
            assertEquals(amountMsats / MSATS_PER_SAT, success.amountPaid.minor)
        } finally {
            viewModel.clear()
        }
    }

    @Test
    fun lnurlRangeRequestsManualAmount() = runBlocking {
        val minMsats = 1_000L
        val maxMsats = 5_000L
        val chosenMsats = 2_000L
        val lnurlInvoice = "lnbc1lnurlrange"
        val params = LnurlPayParams(
            callback = LNURL_CALLBACK,
            minSendable = minMsats,
            maxSendable = maxMsats,
            metadataRaw = LNURL_METADATA_RAW,
            metadata = LNURL_METADATA,
            commentAllowed = null,
            domain = "example.com",
        )
        val lnurlRepository = FakeLnurlRepository().apply {
            stubEndpoint(LNURL_ENDPOINT, Result.Success(params))
            stubInvoice(LNURL_CALLBACK, chosenMsats, Result.Success(lnurlInvoice))
        }
        val parser = FakeBolt11InvoiceParser(
            mapOf(
                lnurlInvoice to Bolt11InvoiceSummary(
                    paymentRequest = lnurlInvoice,
                    amountMsats = chosenMsats,
                    memo = Bolt11Memo.Text("Payment"),
                )
            )
        )
        val repository = RecordingNwcWalletRepository()
        val viewModel = createViewModel(
            parser = parser,
            repository = repository,
            lnurlRepository = lnurlRepository,
        )
        try {
            viewModel.dispatch(MainIntent.InvoiceDetected(LNURL_ENDPOINT))
            viewModel.uiState.first { it is MainUiState.EnterAmount }

            viewModel.dispatch(MainIntent.ManualAmountKeyPress(ManualAmountKey.Digit(2)))
            viewModel.dispatch(MainIntent.ManualAmountSubmit)

            val success = viewModel.uiState.first { it is MainUiState.Success } as MainUiState.Success
            assertEquals(lnurlInvoice, repository.lastInvoice)
            assertEquals(chosenMsats, repository.lastAmountMsats)
            assertEquals(chosenMsats / MSATS_PER_SAT, success.amountPaid.minor)
        } finally {
            viewModel.clear()
        }
    }

    private fun createViewModel(
        parser: Bolt11InvoiceParser,
        repository: NwcWalletRepository,
        preferences: PaymentPreferences = PaymentPreferences(),
        currencyCode: String = CurrencyCatalog.DEFAULT_CODE,
        exchangeRateResult: Result<ExchangeRate>? = null,
        lnurlRepository: FakeLnurlRepository = FakeLnurlRepository(),
    ): MainViewModel {
        val payInvoice = PayInvoiceUseCase(repository, dispatcher = dispatcher)
        val paymentPreferencesRepository = FakePaymentPreferencesRepository(preferences)
        val shouldConfirm = ShouldConfirmPaymentUseCase(paymentPreferencesRepository)
        val currencyPreferencesRepository = FakeCurrencyPreferencesRepository(currencyCode)
        val observeCurrencyPreference = ObserveCurrencyPreferenceUseCase(currencyPreferencesRepository)
        val exchangeRateRepository = FakeExchangeRateRepository(exchangeRateResult)
        val getExchangeRate = GetExchangeRateUseCase(exchangeRateRepository)
        val fetchLnurl = FetchLnurlPayParamsUseCase(lnurlRepository)
        val resolveLightningAddress = ResolveLightningAddressUseCase(lnurlRepository)
        val requestLnurlInvoice = RequestLnurlInvoiceUseCase(lnurlRepository)
        val manualAmount = ManualAmountController(
            ManualAmountConfig(
                info = CurrencyCatalog.infoFor(currencyCode),
                exchangeRate = null,
            )
        )
        return MainViewModel(
            payInvoice = payInvoice,
            observeWalletConnection = walletConnection,
            observeCurrencyPreference = observeCurrencyPreference,
            getExchangeRate = getExchangeRate,
            bolt11Parser = parser,
            manualAmount = manualAmount,
            shouldConfirmPayment = shouldConfirm,
            lightningInputParser = LightningInputParser(),
            fetchLnurlPayParams = fetchLnurl,
            resolveLightningAddressUseCase = resolveLightningAddress,
            requestLnurlInvoice = requestLnurlInvoice,
            dispatcher = dispatcher,
        )
    }
}

private const val MANUAL_INVOICE_INPUT = "manual-invoice"
private const val MANUAL_PAYMENT_REQUEST = "lnbc1manual"
private const val AMOUNT_INVOICE_INPUT = "amount-invoice"
private const val AMOUNT_PAYMENT_REQUEST = "lnbc1amount"

private class RecordingNwcWalletRepository(
    private val result: PaidInvoice = PaidInvoice(preimage = "preimage", feesPaidMsats = 5_000L),
) : NwcWalletRepository {
    var lastInvoice: String? = null
        private set
    var lastAmountMsats: Long? = null
        private set

    override suspend fun payInvoice(invoice: String, amountMsats: Long?): PaidInvoice {
        lastInvoice = invoice
        lastAmountMsats = amountMsats
        return result
    }
}

private class BlockingNwcWalletRepository : NwcWalletRepository {
    private val completion = CompletableDeferred<PaidInvoice>()
    private val recorded = mutableListOf<Pair<String, Long?>>()
    val invoices: List<Pair<String, Long?>> get() = recorded.toList()

    override suspend fun payInvoice(invoice: String, amountMsats: Long?): PaidInvoice {
        recorded += invoice to amountMsats
        return completion.await()
    }

    fun complete(result: PaidInvoice = PaidInvoice(preimage = "blocking-preimage", feesPaidMsats = null)) {
        completeIfNeeded(result)
    }

    fun completeIfNeeded(result: PaidInvoice = PaidInvoice(preimage = "blocking-preimage", feesPaidMsats = null)) {
        if (!completion.isCompleted) {
            completion.complete(result)
        }
    }
}

private class FakeBolt11InvoiceParser(
    private val summaries: Map<String, Bolt11InvoiceSummary>,
) : Bolt11InvoiceParser() {
    override fun parse(invoice: String): Bolt11ParseResult {
        return summaries[invoice]?.let { Bolt11ParseResult.Success(it) }
            ?: Bolt11ParseResult.Failure("Invoice not stubbed: $invoice")
    }
}

private class FakePaymentPreferencesRepository(
    initial: PaymentPreferences,
) : PaymentPreferencesRepository {
    private val state = MutableStateFlow(initial)
    override val preferences: Flow<PaymentPreferences> = state

    override suspend fun getPreferences(): PaymentPreferences = state.value

    override suspend fun setConfirmationMode(mode: PaymentConfirmationMode) {
        state.value = state.value.copy(confirmationMode = mode)
    }

    override suspend fun setConfirmationThreshold(thresholdSats: Long) {
        state.value = state.value.copy(thresholdSats = thresholdSats)
    }

    override suspend fun setConfirmManualEntry(enabled: Boolean) {
        state.value = state.value.copy(confirmManualEntry = enabled)
    }
}

private class FakeCurrencyPreferencesRepository(
    initialCode: String,
) : CurrencyPreferencesRepository {
    private val initial = CurrencyCatalog.infoFor(initialCode).code
    private val state = MutableStateFlow(initial)
    override val currencyCode: Flow<String> = state

    override suspend fun getCurrencyCode(): String = state.value

    override suspend fun setCurrencyCode(code: String) {
        state.value = CurrencyCatalog.infoFor(code).code
    }
}

private class FakeLnurlRepository(
    private val endpointResponses: MutableMap<String, Result<LnurlPayParams>> = mutableMapOf(),
    private val addressResponses: MutableMap<String, Result<LnurlPayParams>> = mutableMapOf(),
    private val invoiceResponses: MutableMap<String, Result<String>> = mutableMapOf(),
) : LnurlRepository {

    fun stubEndpoint(endpoint: String, result: Result<LnurlPayParams>) {
        endpointResponses[endpoint] = result
    }

    fun stubAddress(address: String, result: Result<LnurlPayParams>) {
        addressResponses[address] = result
    }

    fun stubInvoice(callback: String, amountMsats: Long, result: Result<String>) {
        invoiceResponses["$callback:$amountMsats"] = result
    }

    override suspend fun fetchPayParams(endpoint: String): Result<LnurlPayParams> {
        return endpointResponses[endpoint]
            ?: Result.Error(AppError.InvalidWalletUri("LNURL not stubbed: $endpoint"))
    }

    override suspend fun fetchPayParams(address: LightningAddress): Result<LnurlPayParams> {
        return addressResponses[address.full]
            ?: Result.Error(AppError.InvalidWalletUri("Lightning address not stubbed: ${address.full}"))
    }

    override suspend fun requestInvoice(callback: String, amountMsats: Long, comment: String?): Result<String> {
        return invoiceResponses["$callback:$amountMsats"]
            ?: invoiceResponses[callback]
            ?: Result.Error(AppError.InvalidWalletUri("Invoice not stubbed"))
    }
}

private class FakeExchangeRateRepository(
    private val result: Result<ExchangeRate>?,
) : ExchangeRateRepository {
    override suspend fun getExchangeRate(currencyCode: String): Result<ExchangeRate> {
        return result ?: Result.Error(AppError.Unexpected("Missing stub for $currencyCode"))
    }
}

private class FakeWalletSettingsRepository : WalletSettingsRepository {
    private val connections = MutableStateFlow<WalletConnection?>(null)
    private val stored = MutableStateFlow<List<WalletConnection>>(emptyList())

    override val wallets: Flow<List<WalletConnection>> = stored
    override val walletConnection: Flow<WalletConnection?> = connections

    override suspend fun getWalletConnection(): WalletConnection? = connections.value

    override suspend fun saveWalletConnection(connection: WalletConnection, activate: Boolean) {
        stored.value = stored.value + connection
        connections.value = connection
    }

    override suspend fun setActiveWallet(walletPublicKey: String) {
        connections.value = stored.value.firstOrNull { it.walletPublicKey == walletPublicKey }
    }

    override suspend fun removeWallet(walletPublicKey: String) {
        stored.value = stored.value.filterNot { it.walletPublicKey == walletPublicKey }
        if (connections.value?.walletPublicKey == walletPublicKey) {
            connections.value = null
        }
    }

    override suspend fun getWallets(): List<WalletConnection> = stored.value

    override suspend fun clearWalletConnection() {
        connections.value = null
    }

    fun reset() {
        connections.value = null
        stored.value = emptyList()
    }
}

private const val LNURL_ENDPOINT = "https://example.com/lnurl"
private const val LNURL_CALLBACK = "https://example.com/callback"
private const val LNURL_METADATA_RAW = "[[\"text/plain\",\"Payment\"]]"
private val LNURL_METADATA = LnurlPayMetadata(
    plainText = "Payment",
    longText = null,
    imagePng = null,
    imageJpeg = null,
    identifier = null,
    email = null,
    tag = null,
)
private const val MSATS_PER_SAT = 1_000L
