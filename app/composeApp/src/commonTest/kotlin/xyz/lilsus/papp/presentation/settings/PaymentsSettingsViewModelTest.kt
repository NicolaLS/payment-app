@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.presentation.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.data.settings.ContactsRepositoryImpl
import xyz.lilsus.papp.data.settings.CurrencyPreferencesRepositoryImpl
import xyz.lilsus.papp.data.settings.PaymentPreferencesRepositoryImpl
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.model.ShortcutAmount
import xyz.lilsus.papp.domain.model.exchange.ExchangeRate
import xyz.lilsus.papp.domain.repository.ExchangeRateRepository
import xyz.lilsus.papp.domain.usecases.DeleteShortcutUseCase
import xyz.lilsus.papp.domain.usecases.GetExchangeRateUseCase
import xyz.lilsus.papp.domain.usecases.ObserveContactsUseCase
import xyz.lilsus.papp.domain.usecases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObservePaymentPreferencesUseCase
import xyz.lilsus.papp.domain.usecases.ObserveSecondaryCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObserveShortcutsUseCase
import xyz.lilsus.papp.domain.usecases.SaveShortcutUseCase
import xyz.lilsus.papp.domain.usecases.SetConfirmManualEntryUseCase
import xyz.lilsus.papp.domain.usecases.SetConfirmShortcutPaymentsUseCase
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationModeUseCase
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationThresholdUseCase
import xyz.lilsus.papp.domain.usecases.SetVibrateOnPaymentUseCase
import xyz.lilsus.papp.domain.usecases.SetVibrateOnScanUseCase
import xyz.lilsus.papp.presentation.main.CurrencyManager

class PaymentsSettingsViewModelTest {
    @Test
    fun editingExistingShortcutAutoSavesAndCanDelete() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(dispatcher)
        val contact = context.contactsRepository.saveContact(
            address = LightningAddress("alice", "blink.sv"),
            alias = "Alice",
            roles = emptySet()
        )
        val shortcut = assertNotNull(
            context.contactsRepository.saveShortcut(
                id = null,
                title = "Lunch",
                contactId = contact.id,
                amount = ShortcutAmount(minor = 21, currencyCode = "SAT"),
                comment = "old"
            )
        )
        advanceUntilIdle()

        context.viewModel.startEditShortcut(shortcut.id)
        context.viewModel.updateShortcutTitle("Coffee")
        context.viewModel.updateShortcutComment("morning")
        context.viewModel.updateShortcutAmount("42")
        advanceUntilIdle()

        val savedShortcut = context.contactsRepository.getShortcuts().single()
        assertEquals("Coffee", savedShortcut.title)
        assertEquals("morning", savedShortcut.comment)
        assertEquals(42, savedShortcut.amount.minor)
        assertNotNull(context.viewModel.uiState.value.shortcutEditor)

        context.viewModel.deleteShortcut(shortcut.id)
        advanceUntilIdle()

        assertEquals(emptyList(), context.contactsRepository.getShortcuts())
        assertNull(context.viewModel.uiState.value.shortcutEditor)

        context.clear()
    }

    @Test
    fun invalidExistingShortcutAmountDoesNotOverwriteSavedAmount() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(dispatcher)
        val contact = context.contactsRepository.saveContact(
            address = LightningAddress("alice", "blink.sv"),
            alias = "Alice",
            roles = emptySet()
        )
        val shortcut = assertNotNull(
            context.contactsRepository.saveShortcut(
                id = null,
                title = "Lunch",
                contactId = contact.id,
                amount = ShortcutAmount(minor = 21, currencyCode = "SAT"),
                comment = null
            )
        )
        advanceUntilIdle()

        context.viewModel.startEditShortcut(shortcut.id)
        context.viewModel.updateShortcutAmount("")
        advanceUntilIdle()

        val savedShortcut = context.contactsRepository.getShortcuts().single()
        assertEquals(21, savedShortcut.amount.minor)
        assertEquals(
            "Enter an amount.",
            assertNotNull(context.viewModel.uiState.value.shortcutEditor).error
        )

        context.clear()
    }

    @Test
    fun startingShortcutForContactOpensEditorWithContactPreselected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(dispatcher)
        val contact = context.contactsRepository.saveContact(
            address = LightningAddress("alice", "blink.sv"),
            alias = "Alice",
            roles = emptySet()
        )
        advanceUntilIdle()

        context.viewModel.startAddShortcutForContact(contact.id)
        val editor = assertNotNull(context.viewModel.uiState.value.shortcutEditor)

        assertEquals(null, editor.shortcutId)
        assertEquals(contact.id, editor.selectedContactId)
        assertEquals("Alice", assertNotNull(editor.selectedContact).displayName)

        context.clear()
    }

    @Test
    fun thresholdEquivalentUsesSecondaryCurrencyDefaultUsd() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(dispatcher)
        advanceUntilIdle()

        val equivalent = assertNotNull(
            context.viewModel.uiState.value.thresholdSecondaryEquivalent
        )
        assertEquals(DisplayCurrency.Fiat("USD"), equivalent.currency)
        assertEquals(600L, equivalent.minor)

        context.clear()
    }

    private fun createTestContext(dispatcher: CoroutineDispatcher): TestContext {
        val contactsRepository = ContactsRepositoryImpl(MapSettings())
        val paymentPreferencesRepository = PaymentPreferencesRepositoryImpl(MapSettings())
        val currencyPreferencesRepository = CurrencyPreferencesRepositoryImpl(MapSettings())
        val currencyScope = CoroutineScope(SupervisorJob() + dispatcher)
        val currencyManager = CurrencyManager(
            getExchangeRate = GetExchangeRateUseCase(FakeExchangeRateRepository()),
            scope = currencyScope
        )
        val viewModel = PaymentsSettingsViewModel(
            observePreferences = ObservePaymentPreferencesUseCase(paymentPreferencesRepository),
            observeCurrencyPreference = ObserveCurrencyPreferenceUseCase(
                currencyPreferencesRepository
            ),
            observeSecondaryCurrencyPreference = ObserveSecondaryCurrencyPreferenceUseCase(
                currencyPreferencesRepository
            ),
            getExchangeRate = GetExchangeRateUseCase(FakeExchangeRateRepository()),
            currencyManager = currencyManager,
            setConfirmationMode = SetPaymentConfirmationModeUseCase(
                paymentPreferencesRepository
            ),
            setConfirmationThreshold = SetPaymentConfirmationThresholdUseCase(
                paymentPreferencesRepository
            ),
            setConfirmManualEntryPreference = SetConfirmManualEntryUseCase(
                paymentPreferencesRepository
            ),
            setConfirmShortcutPaymentsUseCase = SetConfirmShortcutPaymentsUseCase(
                paymentPreferencesRepository
            ),
            setVibrateOnScanUseCase = SetVibrateOnScanUseCase(paymentPreferencesRepository),
            setVibrateOnPaymentUseCase = SetVibrateOnPaymentUseCase(paymentPreferencesRepository),
            observeContacts = ObserveContactsUseCase(contactsRepository),
            observeShortcuts = ObserveShortcutsUseCase(contactsRepository),
            saveShortcut = SaveShortcutUseCase(contactsRepository),
            deleteShortcutUseCase = DeleteShortcutUseCase(contactsRepository),
            dispatcher = dispatcher
        )
        return TestContext(viewModel, contactsRepository, currencyScope)
    }

    private data class TestContext(
        val viewModel: PaymentsSettingsViewModel,
        val contactsRepository: ContactsRepositoryImpl,
        val currencyScope: CoroutineScope
    ) {
        fun clear() {
            viewModel.clear()
            currencyScope.cancel()
        }
    }

    private class FakeExchangeRateRepository : ExchangeRateRepository {
        override suspend fun getExchangeRate(currencyCode: String): Result<ExchangeRate> =
            Result.success(ExchangeRate(currencyCode = currencyCode, pricePerBitcoin = 60_000.0))
    }
}
