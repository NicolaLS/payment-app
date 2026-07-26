@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.raylsuite.feature.paymentshortcuts

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.feature.contacts.DefaultContactsRepository

class PaymentShortcutsViewModelTest {
    @Test
    fun createsShortcutForSharedContact() = runTest {
        val repository =
            DefaultContactsRepository(
                settings = MapSettings(),
                clock = { 100L },
                idGenerator = sequenceOf("contact-1", "shortcut-1").iterator()::next
            )
        repository.saveContact(
            address = LightningAddress("alice", "example.com"),
            alias = "Alice",
            roles = emptySet()
        )
        val viewModel =
            PaymentShortcutsViewModel(
                repository = repository,
                preferredCurrencyCode = { "USD" },
                dispatcher = StandardTestDispatcher(testScheduler)
            )
        advanceUntilIdle()

        viewModel.startAdd("contact-1")
        viewModel.updateAmount("12.50")
        viewModel.saveEditor(defaultTitle = "Pay Alice")
        advanceUntilIdle()

        val shortcut = repository.getShortcuts().single()
        assertEquals("Pay Alice", shortcut.title)
        assertEquals(1_250L, shortcut.amount.minor)
        assertEquals("USD", shortcut.amount.normalizedCurrencyCode)

        viewModel.clear()
    }
}
