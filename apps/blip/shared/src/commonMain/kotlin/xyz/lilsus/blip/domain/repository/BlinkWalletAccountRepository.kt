package xyz.lilsus.blip.domain.repository

import xyz.lilsus.blip.domain.model.BlinkContact
import xyz.lilsus.blip.domain.model.WalletConnection

/** Manages the single Blink wallet connection and its credentials. */
interface BlinkWalletAccountRepository {
    suspend fun connect(apiKey: String, alias: String): WalletConnection

    suspend fun getCachedDefaultWalletId(): String?

    suspend fun refreshDefaultWalletId(): String

    suspend fun fetchContacts(): List<BlinkContact>
}
