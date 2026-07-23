package xyz.lilsus.rayl.blip.domain

enum class ConfirmationMode {
    Always,
    AboveThreshold
}

data class PaymentPreferences(
    val confirmationMode: ConfirmationMode = ConfirmationMode.AboveThreshold,
    val thresholdSats: Long = 10_000L,
    val confirmManualEntry: Boolean = false,
    val confirmShortcutPayments: Boolean = false,
    val vibrateOnScan: Boolean = true,
    val vibrateOnPayment: Boolean = true
)

fun shouldConfirmPayment(
    amount: Long,
    origin: PaymentOrigin,
    preferences: PaymentPreferences
): Boolean {
    require(amount > 0L)
    val amountSatsRoundedUp = ((amount - 1L) / 1_000L) + 1L

    if (origin == PaymentOrigin.AppLink) return true
    if (origin == PaymentOrigin.Shortcut && preferences.confirmShortcutPayments) return true
    if (origin == PaymentOrigin.Manual && !preferences.confirmManualEntry) return false

    return when (preferences.confirmationMode) {
        ConfirmationMode.Always -> true

        ConfirmationMode.AboveThreshold ->
            amountSatsRoundedUp > preferences.thresholdSats
    }
}

data class Contact(
    val id: ContactId,
    val name: String,
    val lightningAddress: String,
    val source: ContactSource,
    val createdAtMillis: Long
)

enum class ContactSource {
    Local,
    Blink
}

data class BlinkContactCandidate(
    val name: String,
    val lightningAddress: String,
    val alreadyAdded: Boolean
)

data class PaymentShortcut(
    val id: ShortcutId,
    val contactId: ContactId?,
    val label: String,
    val lightningAddress: String,
    val amount: Long?,
    val currencyCode: String?,
    val createdAtMillis: Long
) {
    fun amountInput(): String? {
        val encoded = amount ?: return null
        val currency = currencyCode?.let(CurrencyCode::parse)
        if (currency == null) {
            return if (currencyCode == "MSAT") {
                formatEncodedAmount(encoded, 3)
            } else {
                null
            }
        }
        val scale = when (currency) {
            CurrencyCode.Sat -> 0
            CurrencyCode.Btc -> 11
            else -> 6
        }
        return formatEncodedAmount(encoded, scale)
    }

    fun currency(): CurrencyCode? = currencyCode?.let(CurrencyCode::parse)
        ?: if (currencyCode == "MSAT") CurrencyCode.Sat else null
}

private fun formatEncodedAmount(value: Long, scale: Int): String {
    if (scale == 0) return value.toString()
    var factor = 1L
    repeat(scale) { factor *= 10L }
    val whole = value / factor
    val fraction = (value % factor).toString().padStart(scale, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}
