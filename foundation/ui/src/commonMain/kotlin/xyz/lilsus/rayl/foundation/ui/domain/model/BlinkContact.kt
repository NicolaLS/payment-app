package xyz.lilsus.rayl.foundation.ui.domain.model

data class BlinkContact(
    val handle: String,
    val alias: String?,
    val transactionsCount: Int,
    val lightningAddressDomain: String
)
