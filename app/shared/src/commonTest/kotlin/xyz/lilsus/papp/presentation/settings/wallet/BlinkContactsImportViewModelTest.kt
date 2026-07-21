@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.presentation.settings.wallet

import com.russhwolf.settings.MapSettings
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.data.settings.ContactsRepositoryImpl
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.BlinkContact
import xyz.lilsus.papp.domain.repository.BlinkWalletAccountRepository
import xyz.lilsus.papp.domain.usecases.FetchBlinkContactsUseCase
import xyz.lilsus.papp.domain.usecases.GetContactsUseCase
import xyz.lilsus.papp.domain.usecases.SaveContactUseCase

class BlinkContactsImportViewModelTest {
    @Test
    fun loadBlinkContactsSelectsAllImportableContacts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(dispatcher)

        context.viewModel.loadBlinkContacts()
        advanceUntilIdle()

        val state = context.viewModel.uiState.value
        assertEquals(
            listOf("Alice", "bob"),
            state.items.map { it.displayName }
        )
        assertEquals(
            listOf("alice@blink.sv", "bob@example.com"),
            state.items.map { it.address }
        )
        assertEquals(
            state.items.map { it.id }.toSet(),
            state.selectedIds
        )

        context.viewModel.toggleAllBlinkContacts()
        assertEquals(emptySet(), context.viewModel.uiState.value.selectedIds)

        context.viewModel.toggleAllBlinkContacts()
        assertEquals(
            state.items.map { it.id }.toSet(),
            context.viewModel.uiState.value.selectedIds
        )

        context.viewModel.clear()
    }

    @Test
    fun loadBlinkContactsDisablesAlreadyAddedContacts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(dispatcher)
        context.contactsRepository.saveContact(
            address = LightningAddress(username = "alice", domain = "blink.sv"),
            alias = "Alice",
            roles = emptySet()
        )

        context.viewModel.loadBlinkContacts()
        advanceUntilIdle()

        val state = context.viewModel.uiState.value
        val alice = state.items.first { it.address == "alice@blink.sv" }
        val bob = state.items.first { it.address == "bob@example.com" }

        assertEquals(true, alice.alreadyAdded)
        assertEquals(false, alice.id in state.selectedIds)
        assertEquals(true, bob.id in state.selectedIds)

        context.viewModel.toggleBlinkContact(alice.id)
        assertEquals(false, alice.id in context.viewModel.uiState.value.selectedIds)

        context.viewModel.clear()
    }

    @Test
    fun importSelectedBlinkContactsSavesSelectedContactsAndEmitsEvent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(dispatcher)

        context.viewModel.loadBlinkContacts()
        advanceUntilIdle()

        val eventDeferred = async { context.viewModel.events.first() }
        val firstItem = context.viewModel.uiState.value.items.first()
        context.viewModel.toggleBlinkContact(firstItem.id)
        context.viewModel.importSelectedBlinkContacts()
        advanceUntilIdle()

        val event = eventDeferred.await() as BlinkContactsImportEvent.Imported
        val savedContacts = context.contactsRepository.getContacts()
        assertEquals(1, event.count)
        assertEquals(1, savedContacts.size)
        assertEquals("bob@example.com", savedContacts.first().address.full)
        assertEquals(null, savedContacts.first().alias)
        assertEquals(1, context.viewModel.uiState.value.importedCount)
        assertEquals(emptySet(), context.viewModel.uiState.value.selectedIds)

        context.viewModel.clear()
    }

    private fun createTestContext(
        dispatcher: CoroutineDispatcher,
        blinkContacts: List<BlinkContact> = listOf(
            BlinkContact(
                handle = "alice",
                alias = "Alice",
                transactionsCount = 3,
                lightningAddressDomain = "blink.sv"
            ),
            BlinkContact(
                handle = "bob@example.com",
                alias = null,
                transactionsCount = 1,
                lightningAddressDomain = "blink.sv"
            ),
            BlinkContact(
                handle = "not valid!",
                alias = "Skipped",
                transactionsCount = 10,
                lightningAddressDomain = "blink.sv"
            )
        )
    ): TestContext {
        val contactsRepository = ContactsRepositoryImpl(MapSettings())
        val blinkRepository = mock<BlinkWalletAccountRepository>()
        everySuspend { blinkRepository.fetchContacts() } returns blinkContacts
        val viewModel = BlinkContactsImportViewModel(
            fetchBlinkContacts = FetchBlinkContactsUseCase(blinkRepository),
            getContacts = GetContactsUseCase(contactsRepository),
            saveContact = SaveContactUseCase(contactsRepository),
            dispatcher = dispatcher
        )
        return TestContext(viewModel, contactsRepository)
    }

    private data class TestContext(val viewModel: BlinkContactsImportViewModel, val contactsRepository: ContactsRepositoryImpl)
}
