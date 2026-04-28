package xyz.lilsus.papp.data.blink

import kotlin.random.Random
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.BlinkErrorType
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.BlinkWalletAccountRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class BlinkWalletAccountRepositoryImpl(
    private val apiClient: BlinkApiClient,
    private val credentialStore: BlinkCredentialStore,
    private val walletSettingsRepository: WalletSettingsRepository,
    private val walletIdGenerator: () -> String = ::generateBlinkWalletId
) : BlinkWalletAccountRepository {

    override suspend fun connect(apiKey: String, alias: String): WalletConnection {
        val trimmedApiKey = apiKey.trim()
        val trimmedAlias = alias.trim()

        if (trimmedAlias.isBlank()) {
            throw AppErrorException(AppError.InvalidWalletUri("Alias is required"))
        }
        if (trimmedApiKey.isBlank()) {
            throw AppErrorException(AppError.AuthenticationFailure("API key is required"))
        }

        val scopes = apiClient.fetchAuthorizationScopes(trimmedApiKey)
        if (!scopes.contains(REQUIRED_SCOPE)) {
            throw AppErrorException(AppError.BlinkError(BlinkErrorType.PermissionDenied))
        }

        val defaultWalletId = apiClient.fetchDefaultWalletId(trimmedApiKey)
        val walletId = walletIdGenerator()

        credentialStore.storeApiKey(walletId, trimmedApiKey)
        credentialStore.storeDefaultWalletId(walletId, defaultWalletId)

        val connection = WalletConnection(
            walletPublicKey = walletId,
            alias = trimmedAlias,
            type = WalletType.BLINK
        )
        walletSettingsRepository.saveWalletConnection(connection, activate = true)
        return connection
    }

    override suspend fun getCachedDefaultWalletId(walletId: String): String? {
        val wallet = walletSettingsRepository.getWallets()
            .firstOrNull { it.walletPublicKey == walletId }
            ?: return null
        if (!wallet.isBlink) return null
        return credentialStore.getDefaultWalletId(walletId)
    }

    override suspend fun refreshDefaultWalletId(walletId: String): String {
        val wallet = walletSettingsRepository.getWallets()
            .firstOrNull { it.walletPublicKey == walletId }
            ?: throw AppErrorException(AppError.MissingWalletConnection)
        if (!wallet.isBlink) {
            throw AppErrorException(AppError.MissingWalletConnection)
        }

        val apiKey = credentialStore.getApiKey(walletId)
            ?: throw AppErrorException(AppError.AuthenticationFailure("API key not found"))
        val defaultWalletId = apiClient.fetchDefaultWalletId(apiKey)
        credentialStore.storeDefaultWalletId(walletId, defaultWalletId)
        return defaultWalletId
    }

    private companion object {
        private const val HEX_CHARS = "0123456789abcdef"
        private const val REQUIRED_SCOPE = "WRITE"

        private fun generateBlinkWalletId(): String {
            val randomPart = buildString {
                repeat(32) {
                    append(HEX_CHARS[Random.nextInt(HEX_CHARS.length)])
                }
            }
            return "blink-$randomPart"
        }
    }
}
