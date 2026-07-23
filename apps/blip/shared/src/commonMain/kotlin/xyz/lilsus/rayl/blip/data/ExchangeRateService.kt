package xyz.lilsus.rayl.blip.data

import fr.acinq.lightning.MilliSatoshi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xyz.lilsus.rayl.blip.domain.AppClock
import xyz.lilsus.rayl.blip.domain.ConvertedAmount
import xyz.lilsus.rayl.blip.domain.CurrencyCode
import xyz.lilsus.rayl.blip.domain.ExchangeRateSnapshot
import xyz.lilsus.rayl.blip.domain.ExchangeRates
import xyz.lilsus.rayl.blip.domain.MSAT_PER_BITCOIN
import xyz.lilsus.rayl.blip.domain.MSAT_PER_SAT

class ExchangeRateService(
    private val client: HttpClient,
    private val clock: AppClock,
    private val json: Json = Json
) : ExchangeRates {
    private val mutex = Mutex()
    private val cache = mutableMapOf<CurrencyCode, ExchangeRateSnapshot>()

    override suspend fun snapshot(quote: CurrencyCode): ExchangeRateSnapshot? {
        if (quote == CurrencyCode.Sat || quote == CurrencyCode.Btc) return null
        return mutex.withLock {
            cache[quote]
                ?.takeIf { clock.nowMillis() - it.fetchedAtMillis <= RATE_FRESH_MILLIS }
                ?: fetch(quote)?.also { cache[quote] = it }
        }
    }

    override suspend fun toMilliSatoshi(value: String, currency: CurrencyCode): ConvertedAmount? {
        val normalized = value.trim()
        val converted = when (currency) {
            CurrencyCode.Sat -> parseFixed(normalized, 0)
                ?.checkedTimes(MSAT_PER_SAT)
                ?.let { ConvertedAmount(MilliSatoshi(it), null) }

            CurrencyCode.Btc -> parseFixed(normalized, 11)
                ?.let { ConvertedAmount(MilliSatoshi(it), null) }

            else -> {
                val rate = snapshot(currency) ?: return null
                val micros = parseFixed(normalized, 6) ?: return null
                mulDivCeil(micros, MSAT_PER_BITCOIN, rate.microsPerBitcoin)
                    ?.let { ConvertedAmount(MilliSatoshi(it), rate) }
            }
        }
        return converted?.takeIf { it.amount.msat > 0L }
    }

    override fun format(
        amount: MilliSatoshi,
        currency: CurrencyCode,
        snapshot: ExchangeRateSnapshot?
    ): String? = when (currency) {
        CurrencyCode.Sat -> "${amount.msat.roundUpToSats()} SAT"

        CurrencyCode.Btc -> formatFixed(amount.msat, 11, 8) + " BTC"

        else -> {
            val rate = snapshot?.takeIf { it.quote == currency } ?: return null
            val micros = mulDivFloor(
                amount.msat,
                rate.microsPerBitcoin,
                MSAT_PER_BITCOIN
            ) ?: return null
            formatFixed(micros, 6, 2) + " ${currency.value}"
        }
    }

    private suspend fun fetch(quote: CurrencyCode): ExchangeRateSnapshot? = try {
        val response = client.get(
            "$ENDPOINT?ids=bitcoin&vs_currencies=${quote.value.lowercase()}"
        )
        if (!response.status.isSuccess()) return null
        val raw = json.parseToJsonElement(response.body<String>())
            .jsonObject["bitcoin"]
            ?.jsonObject
            ?.get(quote.value.lowercase())
            ?.jsonPrimitive
            ?.content
            ?: return null
        val micros = parseFixed(raw, 6)?.takeIf { it > 0L } ?: return null
        ExchangeRateSnapshot(
            quote = quote,
            microsPerBitcoin = micros,
            fetchedAtMillis = clock.nowMillis()
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private companion object {
        const val ENDPOINT = "https://api.coingecko.com/api/v3/simple/price"
        const val RATE_FRESH_MILLIS = 5 * 60 * 1_000L
    }
}

private fun parseFixed(value: String, scale: Int): Long? {
    if (value.isEmpty() || value.startsWith('-') || value.startsWith('+')) return null
    val parts = value.split('.', limit = 2)
    if (parts.size > 2 || parts[0].any { !it.isDigit() }) return null
    val fraction = parts.getOrElse(1) { "" }
    if (fraction.any { !it.isDigit() } || fraction.length > scale) return null
    val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    val factor = powerOfTen(scale) ?: return null
    val wholeScaled = whole.checkedTimes(factor) ?: return null
    val fractionScaled = fraction
        .padEnd(scale, '0')
        .ifEmpty { "0" }
        .toLongOrNull()
        ?: return null
    if (wholeScaled > Long.MAX_VALUE - fractionScaled) return null
    return wholeScaled + fractionScaled
}

private fun formatFixed(value: Long, scale: Int, displayScale: Int): String {
    val factor = powerOfTen(scale) ?: return value.toString()
    val whole = value / factor
    val fraction = (value % factor).toString().padStart(scale, '0')
        .take(displayScale)
        .trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}

private fun powerOfTen(scale: Int): Long? {
    var value = 1L
    repeat(scale) {
        value = value.checkedTimes(10L) ?: return null
    }
    return value
}

private fun Long.checkedTimes(other: Long): Long? {
    if (this < 0L || other < 0L) return null
    if (this != 0L && other > Long.MAX_VALUE / this) return null
    return this * other
}

private fun mulDivCeil(a: Long, b: Long, denominator: Long): Long? {
    val floor = mulDivFloor(a, b, denominator) ?: return null
    val exact = runCatching {
        val reducedA = a / gcd(a, denominator)
        val reducedDenominator = denominator / gcd(a, denominator)
        val reducedB = b / gcd(b, reducedDenominator)
        val finalDenominator = reducedDenominator / gcd(b, reducedDenominator)
        finalDenominator == 1L &&
            reducedA.checkedTimes(reducedB)?.let { it == floor } == true
    }.getOrDefault(false)
    return if (exact) floor else floor.takeIf { it < Long.MAX_VALUE }?.plus(1L)
}

private fun mulDivFloor(a: Long, b: Long, denominator: Long): Long? {
    if (a < 0L || b < 0L || denominator <= 0L) return null
    var left = a
    var right = b
    var divisor = denominator
    val first = gcd(left, divisor)
    left /= first
    divisor /= first
    val second = gcd(right, divisor)
    right /= second
    divisor /= second
    val product = left.checkedTimes(right) ?: return null
    return product / divisor
}

private fun gcd(first: Long, second: Long): Long {
    var a = first
    var b = second
    while (b != 0L) {
        val next = a % b
        a = b
        b = next
    }
    return a
}

private fun Long.roundUpToSats(): Long = if (this <= 0L) 0L else ((this - 1L) / MSAT_PER_SAT) + 1L
