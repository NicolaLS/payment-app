package xyz.lilsus.papp.domain.repository

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.model.PaymentConfirmationMode
import xyz.lilsus.papp.domain.model.PaymentPreferences

interface PaymentPreferencesRepository {
    val preferences: Flow<PaymentPreferences>

    suspend fun getPreferences(): PaymentPreferences

    suspend fun setConfirmationMode(mode: PaymentConfirmationMode)

    suspend fun setConfirmationThreshold(thresholdSats: Long)

    suspend fun setConfirmManualEntry(enabled: Boolean)
}
