package xyz.lilsus.papp.domain.repository

import kotlinx.coroutines.flow.Flow

interface CurrencyPreferencesRepository {
    val currencyCode: Flow<String>
    val secondaryCurrencyCode: Flow<String>

    suspend fun getCurrencyCode(): String

    suspend fun setCurrencyCode(code: String)

    suspend fun getSecondaryCurrencyCode(): String

    suspend fun setSecondaryCurrencyCode(code: String)
}
