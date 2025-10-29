package xyz.lilsus.papp.domain.use_cases

import io.github.nostr.nwc.parseNwcUri
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class SetWalletConnectionUseCase(
    private val repository: WalletSettingsRepository,
) {
    suspend operator fun invoke(uri: String): WalletConnection {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) {
            throw AppErrorException(AppError.InvalidWalletUri())
        }
        val credentials = runCatching { parseNwcUri(trimmed) }.getOrElse { error ->
            throw AppErrorException(AppError.InvalidWalletUri(error.message), error)
        }
        val walletPublicKey = runCatching { extractWalletPublicKey(trimmed) }.getOrElse { error ->
            throw AppErrorException(AppError.InvalidWalletUri(error.message), error)
        }
        val connection = WalletConnection(
            uri = trimmed,
            walletPublicKey = walletPublicKey,
            relayUrl = credentials.relays.firstOrNull(),
            lud16 = credentials.lud16,
        )
        repository.saveWalletConnection(connection)
        return connection
    }

    private fun extractWalletPublicKey(uri: String): String {
        val trimmed = uri.trim()
        val schemeSeparator = trimmed.indexOf("://")
        val remainder = if (schemeSeparator >= 0) {
            trimmed.substring(schemeSeparator + 3)
        } else {
            val colonIndex = trimmed.indexOf(':')
            require(colonIndex > 0) { "Nostr Wallet Connect URI missing scheme" }
            trimmed.substring(colonIndex + 1)
        }
        val withoutSlashes = remainder.removePrefix("//")
        val pubKeyPart = withoutSlashes.substringBefore('?')
        require(pubKeyPart.isNotBlank()) { "Nostr Wallet Connect URI missing wallet public key" }
        return pubKeyPart.lowercase()
    }
}
