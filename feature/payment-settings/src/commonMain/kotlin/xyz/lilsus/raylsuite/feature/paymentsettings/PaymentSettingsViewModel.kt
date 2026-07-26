package xyz.lilsus.raylsuite.feature.paymentsettings

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.CurrencyInfo
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.model.PaymentConfirmationMode
import xyz.lilsus.raylsuite.core.model.PaymentPreferences
import xyz.lilsus.raylsuite.core.model.convertMsatsToDisplayAmount
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences

class PaymentSettingsViewModel(
    private val paymentPreferences: PaymentPreferencesRepository,
    private val currencyPreferences: CurrencyPreferences,
    private val contactsRepository: ContactsRepository,
    private val bitcoinPriceProvider: BitcoinPriceProvider,
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var secondaryCurrency =
        CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_SECONDARY_CODE)
    private var pricePerBitcoin: Double? = null
    private var priceRequestId = 0

    private val mutableUiState = MutableStateFlow(PaymentSettingsUiState())
    val uiState: StateFlow<PaymentSettingsUiState> = mutableUiState.asStateFlow()

    init {
        scope.launch {
            paymentPreferences.preferences.collectLatest { preferences ->
                mutableUiState.value =
                    mutableUiState.value.copy(
                        confirmationMode = preferences.confirmationMode,
                        thresholdSats = preferences.thresholdSats,
                        confirmManualEntry = preferences.confirmManualEntry,
                        confirmShortcutPayments = preferences.confirmShortcutPayments,
                        vibrateOnScan = preferences.vibrateOnScan,
                        vibrateOnPayment = preferences.vibrateOnPayment
                    )
                publishThresholdPreview()
            }
        }
        scope.launch {
            contactsRepository.preferences.collectLatest { preferences ->
                mutableUiState.value =
                    mutableUiState.value.copy(
                        askToSaveNewContacts = preferences.askToSaveNewContacts
                    )
            }
        }
        scope.launch {
            currencyPreferences.secondaryCode.collectLatest(::updateSecondaryCurrency)
        }
    }

    fun selectConfirmationMode(mode: PaymentConfirmationMode) {
        scope.launch {
            paymentPreferences.setConfirmationMode(mode)
        }
    }

    fun updateConfirmationThreshold(thresholdSats: Long) {
        scope.launch {
            paymentPreferences.setConfirmationThreshold(thresholdSats)
        }
    }

    fun setConfirmManualEntry(enabled: Boolean) {
        scope.launch {
            paymentPreferences.setConfirmManualEntry(enabled)
        }
    }

    fun setConfirmShortcutPayments(enabled: Boolean) {
        scope.launch {
            paymentPreferences.setConfirmShortcutPayments(enabled)
        }
    }

    fun setVibrateOnScan(enabled: Boolean) {
        scope.launch {
            paymentPreferences.setVibrateOnScan(enabled)
        }
    }

    fun setVibrateOnPayment(enabled: Boolean) {
        scope.launch {
            paymentPreferences.setVibrateOnPayment(enabled)
        }
    }

    fun setAskToSaveNewContacts(enabled: Boolean) {
        scope.launch {
            contactsRepository.setAskToSaveNewContacts(enabled)
        }
    }

    fun clear() {
        scope.cancel()
    }

    private suspend fun updateSecondaryCurrency(code: String) {
        val currency = CurrencyCatalog.infoFor(code)
        val requestId = ++priceRequestId
        secondaryCurrency = currency
        pricePerBitcoin = null
        publishThresholdPreview()

        val fiat = currency.currency as? DisplayCurrency.Fiat ?: return
        val price = bitcoinPriceProvider.pricePerBitcoin(fiat.iso4217)
        if (requestId != priceRequestId) return

        pricePerBitcoin = price?.coerceAtLeast(0.0)
        publishThresholdPreview()
    }

    private fun publishThresholdPreview() {
        mutableUiState.value =
            mutableUiState.value.copy(
                thresholdSecondaryEquivalent =
                thresholdDisplayAmount(
                    thresholdSats = mutableUiState.value.thresholdSats,
                    currency = secondaryCurrency,
                    pricePerBitcoin = pricePerBitcoin
                )
            )
    }
}

data class PaymentSettingsUiState(
    val confirmationMode: PaymentConfirmationMode = PaymentPreferences().confirmationMode,
    val thresholdSats: Long = PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS,
    val confirmManualEntry: Boolean = PaymentPreferences().confirmManualEntry,
    val confirmShortcutPayments: Boolean = PaymentPreferences().confirmShortcutPayments,
    val vibrateOnScan: Boolean = PaymentPreferences().vibrateOnScan,
    val vibrateOnPayment: Boolean = PaymentPreferences().vibrateOnPayment,
    val askToSaveNewContacts: Boolean = true,
    val thresholdSecondaryEquivalent: DisplayAmount? = null
)

private fun thresholdDisplayAmount(
    thresholdSats: Long,
    currency: CurrencyInfo,
    pricePerBitcoin: Double?
): DisplayAmount? {
    if (thresholdSats < 0 || thresholdSats > Long.MAX_VALUE / MSATS_PER_SAT) return null
    val thresholdMsats = thresholdSats * MSATS_PER_SAT
    return convertMsatsToDisplayAmount(
        msats = thresholdMsats,
        info = currency,
        fiatPricePerBitcoin = pricePerBitcoin
    )
}

private const val MSATS_PER_SAT = 1_000L
