package xyz.lilsus.papp.presentation.main

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.lilsus.papp.data.exchange.currentTimeMillis
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.model.CurrencyInfo
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.usecases.GetExchangeRateUseCase

/**
 * Manages currency state and exchange rate fetching for the payment flow.
 * Provides conversion utilities for displaying amounts in the user's preferred currency.
 */
class CurrencyManager(
    private val getExchangeRate: GetExchangeRateUseCase,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(
        CurrencyState(
            info = CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE),
            exchangeRate = null
        )
    )
    val state: StateFlow<CurrencyState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 4)
    val errors: SharedFlow<AppError> = _errors.asSharedFlow()

    private var exchangeRateJob: Job? = null
    private var exchangeRateRequestId: Int = 0
    private var lastExchangeRateRefreshMs: Long? = null

    /** Callback invoked when currency state changes and displays need refresh. */
    var onStateChanged: (() -> Unit)? = null

    /**
     * Updates the preferred currency. Fetches exchange rate if needed for fiat currencies.
     */
    fun setPreferredCurrency(currency: DisplayCurrency) {
        val info = CurrencyCatalog.infoFor(currency)
        if (info.currency is DisplayCurrency.Fiat) {
            fetchExchangeRate(info)
        } else {
            invalidateExchangeRateJob()
            _state.value = CurrencyState(info = info, exchangeRate = null)
            lastExchangeRateRefreshMs = null
            onStateChanged?.invoke()
        }
    }

    /**
     * Ensures an exchange rate is available for the given currency info.
     * Skips fetch if we already have a fresh rate for the same currency.
     */
    fun ensureExchangeRateIfNeeded(info: CurrencyInfo = _state.value.info) {
        if (info.currency !is DisplayCurrency.Fiat) {
            invalidateExchangeRateJob()
            _state.value = _state.value.copy(exchangeRate = null, info = info)
            lastExchangeRateRefreshMs = null
            onStateChanged?.invoke()
            return
        }
        val current = _state.value
        // Skip fetch if we have a fresh rate for the same currency
        if (current.info.code.equals(info.code, ignoreCase = true) &&
            current.exchangeRate != null &&
            !isExchangeRateStale()
        ) {
            if (current.info != info) {
                _state.value = current.copy(info = info)
                onStateChanged?.invoke()
            }
            return
        }
        fetchExchangeRate(info)
    }

    /**
     * Converts millisatoshis to a display amount using current currency state.
     */
    fun convertMsatsToDisplay(msats: Long): DisplayAmount =
        convertMsatsToDisplay(msats, _state.value)

    /**
     * Converts millisatoshis to a display amount using provided currency state.
     */
    fun convertMsatsToDisplay(msats: Long, currencyState: CurrencyState): DisplayAmount {
        val info = currencyState.info
        return when (val currency = info.currency) {
            DisplayCurrency.Satoshi -> DisplayAmount(msats / MSATS_PER_SAT, currency)

            DisplayCurrency.Bitcoin -> DisplayAmount(msats / MSATS_PER_SAT, currency)

            is DisplayCurrency.Fiat -> {
                val rate = currencyState.exchangeRate
                if (rate == null) {
                    DisplayAmount(msats / MSATS_PER_SAT, DisplayCurrency.Satoshi)
                } else {
                    val btc = msats.toDouble() / MSATS_PER_BTC
                    val fiatMajor = btc * rate
                    val factor = 10.0.pow(info.fractionDigits)
                    val minor = (fiatMajor * factor).roundToLong()
                    val clamped = if (minor <= 0 && msats > 0) 1 else minor
                    DisplayAmount(clamped, currency)
                }
            }
        }
    }

    /**
     * Returns true if an exchange rate is needed but not available or stale.
     */
    fun needsExchangeRate(info: CurrencyInfo = _state.value.info): Boolean {
        if (info.currency !is DisplayCurrency.Fiat) return false
        val current = _state.value
        if (!current.info.code.equals(info.code, ignoreCase = true)) return true
        return current.exchangeRate == null || isExchangeRateStale()
    }

    private fun fetchExchangeRate(info: CurrencyInfo) {
        invalidateExchangeRateJob()
        _state.value = CurrencyState(info = info, exchangeRate = null)
        val requestId = exchangeRateRequestId
        exchangeRateJob = scope.launch {
            when (val result = getExchangeRate(info.code)) {
                is Result.Success -> {
                    if (!shouldApplyExchangeRateResult(requestId)) return@launch
                    _state.value = CurrencyState(
                        info = info,
                        exchangeRate = max(result.data.pricePerBitcoin, 0.0)
                    )
                    markExchangeRateFresh()
                    onStateChanged?.invoke()
                }

                is Result.Error -> {
                    if (!shouldApplyExchangeRateResult(requestId)) return@launch
                    _state.value = CurrencyState(info = info, exchangeRate = null)
                    lastExchangeRateRefreshMs = null
                    _errors.tryEmit(result.error)
                    onStateChanged?.invoke()
                }

                Result.Loading -> Unit
            }
        }
    }

    private fun invalidateExchangeRateJob() {
        exchangeRateJob?.cancel()
        exchangeRateJob = null
        exchangeRateRequestId += 1
    }

    private fun shouldApplyExchangeRateResult(requestId: Int): Boolean =
        requestId == exchangeRateRequestId

    private fun markExchangeRateFresh() {
        lastExchangeRateRefreshMs = currentTimeMillis()
    }

    private fun isExchangeRateStale(): Boolean {
        val last = lastExchangeRateRefreshMs ?: return true
        return (currentTimeMillis() - last) >= EXCHANGE_RATE_MAX_AGE_MS
    }

    companion object {
        private const val MSATS_PER_SAT = 1_000L
        private const val MSATS_PER_BTC = 100_000_000_000L
        private const val EXCHANGE_RATE_MAX_AGE_MS = 60_000L
    }
}

/**
 * Immutable snapshot of currency state for display purposes.
 */
data class CurrencyState(val info: CurrencyInfo, val exchangeRate: Double?)
