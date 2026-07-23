package xyz.lilsus.rayl.foundation.ui.domain.lnurl

/**
 * Presentation value used by the frozen UI contracts.
 *
 * Parsing and resolution belong to the app/domain implementation. Foundation UI only needs the
 * normalized address parts to render contacts and issue payment intents.
 */
data class LightningAddress(val username: String, val domain: String) {
    val full: String
        get() = "$username@$domain"

    fun sameAddressAs(other: LightningAddress): Boolean =
        username.equals(other.username, ignoreCase = true) &&
            domain.equals(other.domain, ignoreCase = true)
}
