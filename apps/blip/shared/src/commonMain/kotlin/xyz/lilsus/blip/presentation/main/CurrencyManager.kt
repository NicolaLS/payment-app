package xyz.lilsus.blip.presentation.main

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
import xyz.lilsus.blip.data.exchange.currentTimeMillis
import xyz.lilsus.blip.domain.model.AppError
import xyz.lilsus.blip.domain.model.CurrencyCatalog
import xyz.lilsus.blip.domain.model.CurrencyInfo
import xyz.lilsus.blip.domain.model.DisplayAmount
import xyz.lilsus.blip.domain.model.DisplayCurrency
import xyz.lilsus.blip.domain.model.Result
import xyz.lilsus.blip.domain.model.ShortcutAmount
import xyz.lilsus.blip.domain.model.convertMsatsToDisplayAmount
import xyz.lilsus.blip.domain.usecases.GetExchangeRateUseCase

/**
 * Manages currency state and exchange rate fetching for the payment flow.
 * Provides conversion utilities for displaying amounts in the user's preferred currency.
 *
 * This is app-scoped so payment screens, settings, and pending payment tracking all format
 * amounts from the same currency preference and exchange-rate cache. Consumers should observe
 * [state] instead of registering callbacks.
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
    private val shortcutExchangeRates = mutableMapOf<String, CachedExchangeRate>()

    /**
     * Updates the preferred currency. Fetches exchange rate if needed for fiat currencies.
     */
    fun setPreferredCurrency(currency: DisplayCurrency) {
        val info = CurrencyCatalog.infoFor(currency)
        ensureExchangeRateIfNeeded(info)
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
        return convertMsatsToDisplayAmount(
            msats = msats,
            info = info,
            fiatPricePerBitcoin = currencyState.exchangeRate
        ) ?: DisplayAmount(msats / MSATS_PER_SAT, DisplayCurrency.Satoshi)
    }

    suspend fun convertShortcutAmountToMsats(amount: ShortcutAmount): Long? {
        val code = amount.normalizedCurrencyCode
        val supported = CurrencyCatalog.supportedCodes.any { it.equals(code, ignoreCase = true) }
        if (!supported || amount.minor <= 0L) return null
        val info = CurrencyCatalog.infoFor(code)
        return when (info.currency) {
            DisplayCurrency.Satoshi -> amount.minor * MSATS_PER_SAT

            DisplayCurrency.Bitcoin -> amount.minor * MSATS_PER_SAT

            is DisplayCurrency.Fiat -> {
                val rate = exchangeRateForShortcut(info) ?: return null
                if (rate <= 0.0) return null
                val factor = 10.0.pow(info.fractionDigits)
                val fiatMajor = amount.minor.toDouble() / factor
                val btc = fiatMajor / rate
                (btc * MSATS_PER_BTC).roundToLong().takeIf { it > 0L }
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
                }

                is Result.Error -> {
                    if (!shouldApplyExchangeRateResult(requestId)) return@launch
                    _state.value = CurrencyState(info = info, exchangeRate = null)
                    lastExchangeRateRefreshMs = null
                    _errors.tryEmit(result.error)
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

    private suspend fun exchangeRateForShortcut(info: CurrencyInfo): Double? {
        val code = info.code.uppercase()
        val current = _state.value
        if (
            current.info.code.equals(code, ignoreCase = true) &&
            current.exchangeRate != null &&
            !isExchangeRateStale()
        ) {
            return current.exchangeRate
        }
        shortcutExchangeRates[code]?.let { cached ->
            if ((currentTimeMillis() - cached.cachedAtMs) < EXCHANGE_RATE_MAX_AGE_MS) {
                return cached.pricePerBitcoin
            }
        }
        return when (val result = getExchangeRate(code)) {
            is Result.Success -> {
                val price = max(result.data.pricePerBitcoin, 0.0)
                shortcutExchangeRates[code] = CachedExchangeRate(
                    pricePerBitcoin = price,
                    cachedAtMs = currentTimeMillis()
                )
                if (_state.value.info.code.equals(code, ignoreCase = true)) {
                    _state.value = CurrencyState(info = info, exchangeRate = price)
                    markExchangeRateFresh()
                }
                price
            }

            is Result.Error -> {
                _errors.tryEmit(result.error)
                null
            }

            Result.Loading -> null
        }
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

private data class CachedExchangeRate(val pricePerBitcoin: Double, val cachedAtMs: Long)
