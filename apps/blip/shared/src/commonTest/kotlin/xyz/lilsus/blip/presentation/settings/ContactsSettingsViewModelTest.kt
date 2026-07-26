@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.blip.presentation.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.lilsus.blip.data.settings.ContactsRepositoryImpl
import xyz.lilsus.blip.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.blip.domain.lnurl.LightningAddress
import xyz.lilsus.blip.domain.model.ContactRole
import xyz.lilsus.blip.domain.model.ShortcutAmount
import xyz.lilsus.blip.domain.model.WalletConnection
import xyz.lilsus.blip.domain.model.WalletType
import xyz.lilsus.blip.domain.usecases.DeleteContactUseCase
import xyz.lilsus.blip.domain.usecases.ObserveContactsUseCase
import xyz.lilsus.blip.domain.usecases.ObserveShortcutsUseCase
import xyz.lilsus.blip.domain.usecases.ObserveWalletConnectionUseCase
import xyz.lilsus.blip.domain.usecases.SaveContactUseCase
import xyz.lilsus.blip.domain.usecases.UpdateContactUseCase

class ContactsSettingsViewModelTest {
    @Test
    fun blinkImportHiddenWhenTheConnectedWalletIsNotBlink() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallet = nwcWallet("nwc-1")
        )
        advanceUntilIdle()

        assertEquals(false, context.viewModel.uiState.value.hasBlinkWallet)

        context.viewModel.clear()
    }

    @Test
    fun blinkImportWithConnectedBlinkWalletOpensImport() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallet = blinkWallet("blink-1", "Personal Blink")
        )
        advanceUntilIdle()

        val eventDeferred = async { context.viewModel.events.first() }
        context.viewModel.startBlinkContactsImport()
        advanceUntilIdle()

        assertEquals(
            ContactsSettingsEvent.OpenBlinkContactsImport,
            eventDeferred.await()
        )

        context.viewModel.clear()
    }

    @Test
    fun contactSearchFiltersByNameAddressAndRole() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallet = null
        )
        context.contactsRepository.saveContact(
            address = LightningAddress("alice", "blink.sv"),
            alias = "Alice",
            roles = setOf(ContactRole.People, ContactRole.Favorite)
        )
        context.contactsRepository.saveContact(
            address = LightningAddress("bob", "blink.sv"),
            alias = "Bob",
            roles = setOf(ContactRole.People)
        )
        context.contactsRepository.saveContact(
            address = LightningAddress("other", "blink.sv"),
            alias = "Cafe",
            roles = setOf(ContactRole.Merchants)
        )
        advanceUntilIdle()

        context.viewModel.updateSearchQuery("alice")
        advanceUntilIdle()
        assertEquals(listOf("Alice"), context.viewModel.uiState.value.contacts.map { it.displayName })

        context.viewModel.updateSearchQuery("bob@blink")
        advanceUntilIdle()
        assertEquals(listOf("Bob"), context.viewModel.uiState.value.contacts.map { it.displayName })

        context.viewModel.updateSearchQuery("merchant")
        advanceUntilIdle()
        assertEquals(listOf("Cafe"), context.viewModel.uiState.value.contacts.map { it.displayName })

        context.viewModel.updateSearchQuery("")
        advanceUntilIdle()
        assertEquals(listOf("Alice", "Bob", "Cafe"), context.viewModel.uiState.value.contacts.map { it.displayName })

        context.viewModel.clear()
    }

    @Test
    fun favoritesRankFirstWhenFavoriteFilterIsNotSelected() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallet = null
        )
        context.contactsRepository.saveContact(
            address = LightningAddress("bob", "blink.sv"),
            alias = "Bob",
            roles = setOf(ContactRole.People)
        )
        context.contactsRepository.saveContact(
            address = LightningAddress("alice", "blink.sv"),
            alias = "Alice",
            roles = setOf(ContactRole.People, ContactRole.Favorite)
        )
        context.contactsRepository.saveContact(
            address = LightningAddress("cafe", "blink.sv"),
            alias = "Cafe",
            roles = setOf(ContactRole.Merchants)
        )
        advanceUntilIdle()

        assertEquals(
            listOf("Alice", "Bob", "Cafe"),
            context.viewModel.uiState.value.contacts.map { it.displayName }
        )

        context.viewModel.clear()
    }

    @Test
    fun contactLabelsCanBeCombinedAndToggled() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallet = null
        )
        advanceUntilIdle()

        context.viewModel.startAddContact()
        context.viewModel.updateContactEditorRole(ContactRole.Favorite)
        context.viewModel.updateContactEditorRole(ContactRole.People)
        context.viewModel.updateContactEditorRole(ContactRole.Merchants)

        assertEquals(
            setOf(ContactRole.Favorite, ContactRole.People, ContactRole.Merchants),
            assertNotNull(context.viewModel.uiState.value.contactEditor).roles
        )

        context.viewModel.updateContactEditorRole(ContactRole.People)
        assertEquals(
            setOf(ContactRole.Favorite, ContactRole.Merchants),
            assertNotNull(context.viewModel.uiState.value.contactEditor).roles
        )

        context.viewModel.updateContactEditorRole(null)
        assertEquals(
            emptySet(),
            assertNotNull(context.viewModel.uiState.value.contactEditor).roles
        )

        context.viewModel.clear()
    }

    @Test
    fun editingExistingContactAutoSavesAndCanDelete() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallet = null
        )
        val contact = context.contactsRepository.saveContact(
            address = LightningAddress("alice", "blink.sv"),
            alias = "Alice",
            roles = emptySet()
        )
        advanceUntilIdle()

        context.viewModel.startEditContact(contact.id)
        context.viewModel.updateContactEditorAlias("Al")
        context.viewModel.updateContactEditorRole(ContactRole.Favorite)
        advanceUntilIdle()

        val savedContact = context.contactsRepository.getContacts().single()
        assertEquals("Al", savedContact.alias)
        assertEquals(setOf(ContactRole.Favorite), savedContact.roles)

        context.viewModel.deleteContactEditor()
        advanceUntilIdle()

        assertEquals(emptyList(), context.contactsRepository.getContacts())
        assertNull(context.viewModel.uiState.value.contactEditor)

        context.viewModel.clear()
    }

    @Test
    fun editingContactShowsLinkedShortcutsAndCanStartShortcutCreation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallet = null
        )
        val contact = context.contactsRepository.saveContact(
            address = LightningAddress("alice", "blink.sv"),
            alias = "Alice",
            roles = emptySet()
        )
        context.contactsRepository.saveShortcut(
            id = null,
            title = "Coffee",
            contactId = contact.id,
            amount = ShortcutAmount(minor = 42, currencyCode = "SAT"),
            comment = "morning"
        )
        advanceUntilIdle()

        context.viewModel.startEditContact(contact.id)
        val editor = assertNotNull(context.viewModel.uiState.value.contactEditor)
        assertEquals(listOf("Coffee"), editor.shortcuts.map { it.title })
        assertEquals(listOf("42 sats"), editor.shortcuts.map { it.amountText })

        val eventDeferred = async { context.viewModel.events.first() }
        context.viewModel.createShortcutForCurrentContact()
        advanceUntilIdle()

        assertEquals(
            ContactsSettingsEvent.CreateShortcutForContact(contact.id),
            eventDeferred.await()
        )

        context.viewModel.clear()
    }

    private suspend fun createTestContext(dispatcher: CoroutineDispatcher, wallet: WalletConnection?): TestContext {
        val contactsRepository = ContactsRepositoryImpl(MapSettings())
        val walletRepository = WalletSettingsRepositoryImpl(MapSettings())
        wallet?.let { walletRepository.saveWalletConnection(it) }
        val viewModel = ContactsSettingsViewModel(
            observeContacts = ObserveContactsUseCase(contactsRepository),
            observeWalletConnection = ObserveWalletConnectionUseCase(walletRepository),
            observeShortcuts = ObserveShortcutsUseCase(contactsRepository),
            saveContact = SaveContactUseCase(contactsRepository),
            updateContact = UpdateContactUseCase(contactsRepository),
            deleteContactUseCase = DeleteContactUseCase(contactsRepository),
            dispatcher = dispatcher
        )
        return TestContext(viewModel, contactsRepository)
    }

    private data class TestContext(val viewModel: ContactsSettingsViewModel, val contactsRepository: ContactsRepositoryImpl)

    private fun blinkWallet(id: String, alias: String): WalletConnection = WalletConnection(
        walletPublicKey = id,
        alias = alias,
        type = WalletType.BLINK
    )

    private fun nwcWallet(id: String): WalletConnection = WalletConnection(
        walletPublicKey = id,
        alias = "NWC wallet",
        type = WalletType.NWC,
        uri = "nostr+walletconnect://$id"
    )
}
