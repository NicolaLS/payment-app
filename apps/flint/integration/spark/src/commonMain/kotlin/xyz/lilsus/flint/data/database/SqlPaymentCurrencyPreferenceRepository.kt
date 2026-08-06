package xyz.lilsus.flint.data.database

import app.cash.sqldelight.db.SqlDriver
import kotlinx.coroutines.CancellationException
import xyz.lilsus.flint.application.payment.LoadCurrencyPreferencesResult
import xyz.lilsus.flint.application.payment.PaymentCurrencyPreferenceRepository
import xyz.lilsus.flint.application.payment.PaymentCurrencyPreferences
import xyz.lilsus.flint.database.FlintDatabase

class SqlPaymentCurrencyPreferenceRepository(driver: SqlDriver) :
    PaymentCurrencyPreferenceRepository {
    private val database = FlintDatabase(driver)
    private val queries = database.paymentCurrencyPreferenceQueries

    override fun load(): LoadCurrencyPreferencesResult = try {
        val preferences = queries.selectCurrencyPreferences { primary, secondary ->
            PaymentCurrencyPreferences(primary, secondary)
        }.executeAsOneOrNull() ?: PaymentCurrencyPreferences.Default
        LoadCurrencyPreferencesResult.Available(preferences)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        LoadCurrencyPreferencesResult.StorageFailure
    }

    override fun store(preferences: PaymentCurrencyPreferences): Boolean = try {
        database.transactionWithResult {
            queries.upsertCurrencyPreferences(preferences.primaryCode, preferences.secondaryCode)
            queries.selectCurrencyPreferences().executeAsOne().let {
                it.primary_code == preferences.primaryCode &&
                    it.secondary_code == preferences.secondaryCode
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }
}
