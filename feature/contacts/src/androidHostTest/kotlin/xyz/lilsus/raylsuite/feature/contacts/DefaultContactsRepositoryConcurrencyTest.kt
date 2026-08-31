package xyz.lilsus.raylsuite.feature.contacts

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.LightningAddress

class DefaultContactsRepositoryConcurrencyTest {
    @Test
    fun serializesConcurrentContactWrites() = runTest {
        val settings = ConcurrentWriteDetectingSettings()
        val nextId = AtomicInteger()
        val clock = AtomicLong()
        val repository =
            DefaultContactsRepository(
                settings = settings,
                clock = clock::incrementAndGet,
                idGenerator = { "contact-${nextId.incrementAndGet()}" }
            )

        (1..CONTACT_COUNT)
            .map { index ->
                async(Dispatchers.Default) {
                    repository.saveContact(
                        address = LightningAddress("user-$index", "example.com"),
                        alias = null,
                        roles = emptySet()
                    )
                }
            }.awaitAll()

        assertFalse(settings.concurrentWriteDetected.get())
        assertEquals(CONTACT_COUNT, repository.getContacts().size)
    }

    private companion object {
        const val CONTACT_COUNT = 24
    }
}

private class ConcurrentWriteDetectingSettings(
    private val delegate: Settings = MapSettings(ConcurrentHashMap())
) : Settings by delegate {
    private val activeWrites = AtomicInteger()
    val concurrentWriteDetected = AtomicBoolean(false)

    override fun putString(key: String, value: String) {
        detectConcurrentWrite { delegate.putString(key, value) }
    }

    private fun detectConcurrentWrite(write: () -> Unit) {
        if (activeWrites.incrementAndGet() > 1) {
            concurrentWriteDetected.set(true)
        }
        try {
            Thread.sleep(5)
            write()
        } finally {
            activeWrites.decrementAndGet()
        }
    }
}
