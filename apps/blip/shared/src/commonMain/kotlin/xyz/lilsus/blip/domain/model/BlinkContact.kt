package xyz.lilsus.blip.domain.model

data class BlinkContact(
    val handle: String,
    val alias: String?,
    val transactionsCount: Int,
    val lightningAddressDomain: String
)
