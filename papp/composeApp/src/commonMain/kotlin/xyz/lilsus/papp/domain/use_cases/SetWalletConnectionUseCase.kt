package xyz.lilsus.papp.domain.use_cases

import io.github.nostr.nwc.NwcUri
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletMetadataSnapshot
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class SetWalletConnectionUseCase(private val repository: WalletSettingsRepository) {
    suspend operator fun invoke(
        uri: String,
        alias: String?,
        activate: Boolean = true,
        metadata: WalletMetadataSnapshot? = null
    ): WalletConnection {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) {
            throw AppErrorException(AppError.InvalidWalletUri())
        }
        val parsed = runCatching { NwcUri.parse(trimmed) }.getOrElse { error ->
            throw AppErrorException(AppError.InvalidWalletUri(error.message), error)
        }
        val connection = WalletConnection(
            uri = parsed.toUriString(),
            walletPublicKey = parsed.walletPublicKeyHex,
            relayUrl = parsed.relays.firstOrNull(),
            lud16 = parsed.lud16,
            alias = alias?.takeIf { it.isNotBlank() }?.trim(),
            metadata = metadata
        )
        repository.saveWalletConnection(connection, activate = activate)
        return connection
    }
}
