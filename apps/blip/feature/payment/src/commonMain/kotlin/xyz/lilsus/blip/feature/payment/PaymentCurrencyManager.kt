package xyz.lilsus.blip.feature.payment

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.CurrencyInfo
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.model.ShortcutAmount
import xyz.lilsus.raylsuite.core.model.convertMsatsToDisplayAmount
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider

class PaymentCurrencyManager(
    private val bitcoinPriceProvider: BitcoinPriceProvider,
    private val scope: CoroutineScope,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
) {
    private val mutableState =
        MutableStateFlow(
            CurrencyState(
                info = CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE),
                exchangeRate = null
            )
        )
    val state: StateFlow<CurrencyState> = mutableState.asStateFlow()

    private val mutableErrors = MutableSharedFlow<PaymentUiError>(extraBufferCapacity = 4)
    val errors: SharedFlow<PaymentUiError> = mutableErrors.asSharedFlow()

    private var exchangeRateJob: Job? = null
    private var exchangeRateRequestId: Int = 0
    private var lastExchangeRateRefresh: ComparableTimeMark? = null
    private val shortcutExchangeRates = mutableMapOf<String, CachedExchangeRate>()

    fun setPreferredCurrency(currency: DisplayCurrency) {
        ensureExchangeRateIfNeeded(CurrencyCatalog.infoFor(currency))
    }

    fun ensureExchangeRateIfNeeded(info: CurrencyInfo = mutableState.value.info) {
        if (info.currency !is DisplayCurrency.Fiat) {
            invalidateExchangeRateJob()
            mutableState.value = mutableState.value.copy(exchangeRate = null, info = info)
            lastExchangeRateRefresh = null
            return
        }
        val current = mutableState.value
        if (
            current.info.code.equals(info.code, ignoreCase = true) &&
            current.exchangeRate != null &&
            !isExchangeRateStale()
        ) {
            if (current.info != info) {
                mutableState.value = current.copy(info = info)
            }
            return
        }
        fetchExchangeRate(info)
    }

    fun convertMsatsToDisplay(msats: Long): DisplayAmount =
        convertMsatsToDisplay(msats, mutableState.value)

    fun convertMsatsToDisplay(msats: Long, currencyState: CurrencyState): DisplayAmount =
        convertMsatsToDisplayAmount(
            msats = msats,
            info = currencyState.info,
            fiatPricePerBitcoin = currencyState.exchangeRate
        ) ?: DisplayAmount(msats / MSATS_PER_SAT, DisplayCurrency.Satoshi)

    suspend fun convertShortcutAmountToMsats(amount: ShortcutAmount): Long? {
        val code = amount.normalizedCurrencyCode
        if (code !in CurrencyCatalog.supportedCodes || amount.minor <= 0L) return null
        val info = CurrencyCatalog.infoFor(code)
        return when (info.currency) {
            DisplayCurrency.Satoshi,
            DisplayCurrency.Bitcoin -> amount.minor * MSATS_PER_SAT

            is DisplayCurrency.Fiat -> {
                val rate = exchangeRateForShortcut(info)?.takeIf { it > 0.0 } ?: return null
                val fiatMajor = amount.minor.toDouble() / 10.0.pow(info.fractionDigits)
                (fiatMajor / rate * MSATS_PER_BITCOIN).roundToLong().takeIf { it > 0L }
            }
        }
    }

    fun needsExchangeRate(info: CurrencyInfo = mutableState.value.info): Boolean {
        if (info.currency !is DisplayCurrency.Fiat) return false
        val current = mutableState.value
        if (!current.info.code.equals(info.code, ignoreCase = true)) return true
        return current.exchangeRate == null || isExchangeRateStale()
    }

    private fun fetchExchangeRate(info: CurrencyInfo) {
        invalidateExchangeRateJob()
        mutableState.value = CurrencyState(info = info, exchangeRate = null)
        val requestId = exchangeRateRequestId
        exchangeRateJob =
            scope.launch {
                val price = bitcoinPriceProvider.pricePerBitcoin(info.code)
                if (requestId != exchangeRateRequestId) return@launch
                if (price == null) {
                    lastExchangeRateRefresh = null
                    mutableErrors.tryEmit(
                        PaymentUiError.Unexpected("Exchange rate unavailable")
                    )
                    return@launch
                }
                mutableState.value =
                    CurrencyState(
                        info = info,
                        exchangeRate = max(price, 0.0)
                    )
                lastExchangeRateRefresh = timeSource.markNow()
            }
    }

    private fun invalidateExchangeRateJob() {
        exchangeRateJob?.cancel()
        exchangeRateJob = null
        exchangeRateRequestId += 1
    }

    private fun isExchangeRateStale(): Boolean =
        lastExchangeRateRefresh?.elapsedNow()?.let { it >= EXCHANGE_RATE_MAX_AGE } ?: true

    private suspend fun exchangeRateForShortcut(info: CurrencyInfo): Double? {
        val code = info.code.uppercase()
        val current = mutableState.value
        if (
            current.info.code.equals(code, ignoreCase = true) &&
            current.exchangeRate != null &&
            !isExchangeRateStale()
        ) {
            return current.exchangeRate
        }
        shortcutExchangeRates[code]?.let { cached ->
            if (cached.storedAt.elapsedNow() < EXCHANGE_RATE_MAX_AGE) {
                return cached.pricePerBitcoin
            }
        }

        val price =
            bitcoinPriceProvider
                .pricePerBitcoin(code)
                ?.let { max(it, 0.0) }
                ?: run {
                    mutableErrors.tryEmit(
                        PaymentUiError.Unexpected("Exchange rate unavailable")
                    )
                    return null
                }
        shortcutExchangeRates[code] =
            CachedExchangeRate(
                pricePerBitcoin = price,
                storedAt = timeSource.markNow()
            )
        if (mutableState.value.info.code.equals(code, ignoreCase = true)) {
            mutableState.value = CurrencyState(info = info, exchangeRate = price)
            lastExchangeRateRefresh = timeSource.markNow()
        }
        return price
    }

    private companion object {
        const val MSATS_PER_SAT = 1_000L
        const val MSATS_PER_BITCOIN = 100_000_000_000L
        val EXCHANGE_RATE_MAX_AGE = 60.seconds
    }
}

data class CurrencyState(val info: CurrencyInfo, val exchangeRate: Double?)

private data class CachedExchangeRate(val pricePerBitcoin: Double, val storedAt: ComparableTimeMark)
