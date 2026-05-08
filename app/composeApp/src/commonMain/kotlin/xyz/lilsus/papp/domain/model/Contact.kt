package xyz.lilsus.papp.domain.model

import xyz.lilsus.papp.domain.lnurl.LightningAddress

enum class ContactRole {
    Favorite,
    Personal,
    Work,
    People,
    Merchants,
    Bills
}

data class Contact(
    val id: String,
    val address: LightningAddress,
    val alias: String? = null,
    val roles: Set<ContactRole> = emptySet(),
    val stats: ContactStats = ContactStats(),
    val createdAtMs: Long,
    val updatedAtMs: Long
) {
    val displayName: String
        get() = alias?.takeIf { it.isNotBlank() } ?: address.username
}

data class ContactStats(val paymentCount: Int = 0, val lastPaidAtMs: Long? = null)

data class PaymentShortcut(
    val id: String,
    val title: String,
    val contactId: String?,
    val address: LightningAddress,
    val amount: ShortcutAmount,
    val comment: String? = null,
    val stats: ShortcutStats = ShortcutStats(),
    val createdAtMs: Long,
    val updatedAtMs: Long
)

data class ShortcutAmount(val minor: Long, val currencyCode: String) {
    val normalizedCurrencyCode: String
        get() = currencyCode.trim().uppercase()
}

data class ShortcutStats(val paymentCount: Int = 0, val lastPaidAtMs: Long? = null)

data class ContactPreferences(val askToSaveNewContacts: Boolean = true)

data class ContactPaymentRecord(
    val address: LightningAddress,
    val amountMsats: Long,
    val comment: String?,
    val paidAtMs: Long
)
