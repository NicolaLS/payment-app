package xyz.lilsus.papp.data.blink

import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.BlinkContact
import xyz.lilsus.papp.domain.model.BlinkErrorType
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.BlinkWalletAccountRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class BlinkWalletAccountRepositoryImpl(
    private val apiClient: BlinkApiClient,
    private val credentialStore: BlinkCredentialStore,
    private val walletSettingsRepository: WalletSettingsRepository
) : BlinkWalletAccountRepository {

    override suspend fun connect(apiKey: String, alias: String): WalletConnection {
        ensureNoWalletConnected()
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
        ensureNoWalletConnected()
        credentialStore.storeApiKey(trimmedApiKey)
        credentialStore.storeDefaultWalletId(defaultWalletId)

        val connection = WalletConnection(
            walletPublicKey = BLINK_CONNECTION_ID,
            alias = trimmedAlias,
            type = WalletType.BLINK
        )
        try {
            walletSettingsRepository.saveWalletConnection(connection)
        } catch (error: Throwable) {
            credentialStore.clear()
            throw error
        }
        return connection
    }

    override suspend fun getCachedDefaultWalletId(): String? {
        requireBlinkConnection()
        return credentialStore.getDefaultWalletId()
    }

    override suspend fun refreshDefaultWalletId(): String {
        requireBlinkConnection()
        val apiKey = credentialStore.getApiKey()
            ?: throw AppErrorException(AppError.AuthenticationFailure("API key not found"))
        val defaultWalletId = apiClient.fetchDefaultWalletId(apiKey)
        credentialStore.storeDefaultWalletId(defaultWalletId)
        return defaultWalletId
    }

    override suspend fun fetchContacts(): List<BlinkContact> {
        requireBlinkConnection()
        val apiKey = credentialStore.getApiKey()
            ?: throw AppErrorException(AppError.AuthenticationFailure("API key not found"))
        return apiClient.fetchContacts(apiKey)
    }

    private suspend fun ensureNoWalletConnected() {
        if (walletSettingsRepository.getWalletConnection() != null) {
            throw AppErrorException(AppError.WalletAlreadyConnected)
        }
    }

    private suspend fun requireBlinkConnection(): WalletConnection {
        val wallet = walletSettingsRepository.getWalletConnection()
            ?: throw AppErrorException(AppError.MissingWalletConnection)
        if (!wallet.isBlink) throw AppErrorException(AppError.MissingWalletConnection)
        return wallet
    }

    private companion object {
        const val BLINK_CONNECTION_ID = "blink"
        const val REQUIRED_SCOPE = "WRITE"
    }
}
