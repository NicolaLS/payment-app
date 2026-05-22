package xyz.lilsus.papp.domain.usecases

import io.github.nicolals.nwc.NwcConnectionUri
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
        val parsed = NwcConnectionUri.parse(trimmed)
            ?: throw AppErrorException(AppError.InvalidWalletUri())

        val connection = WalletConnection(
            uri = parsed.raw,
            walletPublicKey = parsed.walletPubkey.hex,
            relayUrl = parsed.relays.firstOrNull(),
            lud16 = parsed.lud16,
            alias = alias?.takeIf { it.isNotBlank() }?.trim(),
            metadata = metadata
        )
        repository.saveWalletConnection(connection, activate = activate)
        return connection
    }
}
