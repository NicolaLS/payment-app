package xyz.lilsus.papp.domain.use_cases

import io.github.nostr.nwc.parseNwcUri
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.domain.util.parseWalletPublicKey

class SetWalletConnectionUseCase(
    private val repository: WalletSettingsRepository,
) {
    suspend operator fun invoke(
        uri: String,
        alias: String?,
        activate: Boolean = true,
    ): WalletConnection {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) {
            throw AppErrorException(AppError.InvalidWalletUri())
        }
        val credentials = runCatching { parseNwcUri(trimmed) }.getOrElse { error ->
            throw AppErrorException(AppError.InvalidWalletUri(error.message), error)
        }
        val walletPublicKey = runCatching { parseWalletPublicKey(trimmed) }.getOrElse { error ->
            throw AppErrorException(AppError.InvalidWalletUri(error.message), error)
        }
        val connection = WalletConnection(
            uri = trimmed,
            walletPublicKey = walletPublicKey,
            relayUrl = credentials.relays.firstOrNull(),
            lud16 = credentials.lud16,
            alias = alias?.takeIf { it.isNotBlank() }?.trim(),
        )
        repository.saveWalletConnection(connection, activate = activate)
        return connection
    }
}
