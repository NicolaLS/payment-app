package xyz.lilsus.raylsuite.feature.paymenthub.library

/** Digit/decimal-only editing of a minor-unit amount for a currency with [fractionDigits]. */
internal fun String.cleanAmountInput(fractionDigits: Int): String {
    val normalized = replace(',', '.').filter { it.isDigit() || it == '.' }
    if (fractionDigits <= 0) return normalized.substringBefore('.').filter(Char::isDigit)
    val whole = normalized.substringBefore('.').filter(Char::isDigit)
    val hasDecimal = '.' in normalized
    val fraction =
        normalized
            .substringAfter('.', "")
            .filter(Char::isDigit)
            .take(fractionDigits)
    return if (hasDecimal) "$whole.$fraction" else whole
}

internal fun String.hasFractionForWholeCurrency(fractionDigits: Int): Boolean {
    val normalized = replace(',', '.').filter { it.isDigit() || it == '.' }
    return fractionDigits <= 0 && '.' in normalized && normalized != "."
}

internal fun String.parseMinorAmount(fractionDigits: Int): Long? {
    val cleaned = cleanAmountInput(fractionDigits)
    if (cleaned.isBlank() || cleaned == ".") return null
    if (fractionDigits <= 0) return cleaned.toLongOrNull()

    val factor = decimalFactor(fractionDigits)
    val whole = cleaned.substringBefore('.').ifBlank { "0" }.toLongOrNull() ?: return null
    val fraction =
        cleaned
            .substringAfter('.', "")
            .padEnd(fractionDigits, '0')
            .take(fractionDigits)
            .ifBlank { "0" }
            .toLongOrNull()
            ?: return null
    if (whole > (Long.MAX_VALUE - fraction) / factor) return null
    return whole * factor + fraction
}

internal fun Long.formatMinorAmount(fractionDigits: Int): String {
    if (fractionDigits <= 0) return toString()
    val factor = decimalFactor(fractionDigits)
    val whole = this / factor
    val fraction =
        (this % factor)
            .toString()
            .padStart(fractionDigits, '0')
            .trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}

private fun decimalFactor(fractionDigits: Int): Long {
    var factor = 1L
    repeat(fractionDigits) {
        if (factor > Long.MAX_VALUE / 10L) return Long.MAX_VALUE
        factor *= 10L
    }
    return factor
}
