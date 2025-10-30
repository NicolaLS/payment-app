package xyz.lilsus.papp.domain.repository

import kotlinx.coroutines.flow.Flow

interface CurrencyPreferencesRepository {
    val currencyCode: Flow<String>

    suspend fun getCurrencyCode(): String

    suspend fun setCurrencyCode(code: String)
}
