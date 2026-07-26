package xyz.lilsus.blip.presentation.common

import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.blip.domain.model.CurrencyCatalog
import xyz.lilsus.blip.domain.model.CurrencyInfo
import xyz.lilsus.blip.domain.model.DisplayAmount
import xyz.lilsus.blip.domain.model.DisplayCurrency
import xyz.lilsus.blip.domain.model.Result
import xyz.lilsus.blip.domain.model.convertMsatsToDisplayAmount
import xyz.lilsus.blip.domain.usecases.GetExchangeRateUseCase
import xyz.lilsus.blip.domain.usecases.ObserveSecondaryCurrencyPreferenceUseCase

class SecondaryCurrencyPreviewController(
    private val observeSecondaryCurrencyPreference: ObserveSecondaryCurrencyPreferenceUseCase,
    private val getExchangeRate: GetExchangeRateUseCase,
    private val scope: CoroutineScope,
    private val amountMsats: () -> Long,
    private val onDisplayAmountChanged: (DisplayAmount?) -> Unit
) {
    private var currencyInfo: CurrencyInfo =
        CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_SECONDARY_CODE)
    private var exchangeRate: Double? = null
    private var exchangeRateJob: Job? = null
    private var exchangeRateRequestId: Int = 0

    fun start() {
        scope.launch {
            observeSecondaryCurrencyPreference().collectLatest { currency ->
                updateCurrency(CurrencyCatalog.infoFor(currency))
            }
        }
    }

    fun refresh() {
        publishDisplayAmount()
    }

    fun clear() {
        exchangeRateJob?.cancel()
        exchangeRateJob = null
    }

    private fun updateCurrency(info: CurrencyInfo) {
        currencyInfo = info
        exchangeRate = null
        exchangeRateJob?.cancel()
        exchangeRateRequestId += 1
        publishDisplayAmount()

        if (info.currency !is DisplayCurrency.Fiat) return

        val requestId = exchangeRateRequestId
        exchangeRateJob = scope.launch {
            when (val result = getExchangeRate(info.code)) {
                is Result.Success -> {
                    if (requestId != exchangeRateRequestId) return@launch
                    exchangeRate = max(result.data.pricePerBitcoin, 0.0)
                    publishDisplayAmount()
                }

                is Result.Error -> {
                    if (requestId != exchangeRateRequestId) return@launch
                    exchangeRate = null
                    publishDisplayAmount()
                }

                Result.Loading -> Unit
            }
        }
    }

    private fun publishDisplayAmount() {
        onDisplayAmountChanged(
            convertMsatsToDisplayAmount(
                msats = amountMsats(),
                info = currencyInfo,
                fiatPricePerBitcoin = exchangeRate
            )
        )
    }
}
