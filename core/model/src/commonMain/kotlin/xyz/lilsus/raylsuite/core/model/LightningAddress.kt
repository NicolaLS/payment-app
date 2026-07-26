package xyz.lilsus.raylsuite.core.model

data class LightningAddress(val username: String, val domain: String, val tag: String? = null) {
    val full: String
        get() =
            buildString {
                append(username)
                tag?.let { append('+').append(it) }
                append('@')
                append(domain)
            }

    fun isSameAddressAs(other: LightningAddress): Boolean =
        full.equals(other.full, ignoreCase = true)
}
