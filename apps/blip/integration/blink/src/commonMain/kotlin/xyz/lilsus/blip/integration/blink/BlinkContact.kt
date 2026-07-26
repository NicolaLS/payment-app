package xyz.lilsus.blip.integration.blink

data class BlinkContact(
    val handle: String,
    val alias: String?,
    val transactionsCount: Int,
    val lightningAddressDomain: String
)
