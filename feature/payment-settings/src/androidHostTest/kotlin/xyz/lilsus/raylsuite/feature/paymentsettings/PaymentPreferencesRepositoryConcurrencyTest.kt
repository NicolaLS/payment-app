package xyz.lilsus.raylsuite.feature.paymentsettings

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.PaymentConfirmationMode

class PaymentPreferencesRepositoryConcurrencyTest {
    @Test
    fun serializesConcurrentPreferenceWrites() = runTest {
        val settings = ConcurrentWriteDetectingSettings()
        val repository = DefaultPaymentPreferencesRepository(settings)

        listOf(
            async(Dispatchers.Default) {
                repository.setConfirmationMode(PaymentConfirmationMode.Always)
            },
            async(Dispatchers.Default) {
                repository.setVibrateOnScan(false)
            },
            async(Dispatchers.Default) {
                repository.setVibrateOnPayment(false)
            }
        ).awaitAll()

        val preferences = repository.current()
        assertFalse(settings.concurrentWriteDetected.get())
        assertEquals(PaymentConfirmationMode.Always, preferences.confirmationMode)
        assertFalse(preferences.vibrateOnScan)
        assertFalse(preferences.vibrateOnPayment)
    }
}

private class ConcurrentWriteDetectingSettings(
    private val delegate: Settings = MapSettings(ConcurrentHashMap())
) : Settings by delegate {
    private val activeWrites = AtomicInteger()
    val concurrentWriteDetected = AtomicBoolean(false)

    override fun putLong(key: String, value: Long) {
        detectConcurrentWrite { delegate.putLong(key, value) }
    }

    override fun putString(key: String, value: String) {
        detectConcurrentWrite { delegate.putString(key, value) }
    }

    override fun putBoolean(key: String, value: Boolean) {
        detectConcurrentWrite { delegate.putBoolean(key, value) }
    }

    private fun detectConcurrentWrite(write: () -> Unit) {
        if (activeWrites.incrementAndGet() > 1) {
            concurrentWriteDetected.set(true)
        }
        try {
            Thread.sleep(2)
            write()
        } finally {
            activeWrites.decrementAndGet()
        }
    }
}
