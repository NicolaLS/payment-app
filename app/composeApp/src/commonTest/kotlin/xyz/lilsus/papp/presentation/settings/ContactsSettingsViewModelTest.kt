@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.presentation.settings

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
import xyz.lilsus.papp.data.settings.ContactsRepositoryImpl
import xyz.lilsus.papp.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.usecases.DeleteContactUseCase
import xyz.lilsus.papp.domain.usecases.ObserveContactsUseCase
import xyz.lilsus.papp.domain.usecases.ObserveWalletsUseCase
import xyz.lilsus.papp.domain.usecases.SaveContactUseCase
import xyz.lilsus.papp.domain.usecases.UpdateContactUseCase

class ContactsSettingsViewModelTest {
    @Test
    fun blinkImportHiddenWhenThereAreNoBlinkWallets() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallets = listOf(nwcWallet("nwc-1"))
        )
        advanceUntilIdle()

        assertEquals(false, context.viewModel.uiState.value.hasBlinkWallets)
        assertEquals(emptyList(), context.viewModel.uiState.value.blinkWallets)

        context.viewModel.clear()
    }

    @Test
    fun blinkImportWithOneBlinkWalletOpensImportDirectly() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallets = listOf(
                nwcWallet("nwc-1"),
                blinkWallet("blink-1", "Personal Blink")
            )
        )
        advanceUntilIdle()

        val eventDeferred = async { context.viewModel.events.first() }
        context.viewModel.startBlinkContactsImport()
        advanceUntilIdle()

        assertEquals(
            ContactsSettingsEvent.OpenBlinkContactsImport("blink-1"),
            eventDeferred.await()
        )
        assertNull(context.viewModel.uiState.value.blinkWalletChooser)

        context.viewModel.clear()
    }

    @Test
    fun blinkImportWithMultipleBlinkWalletsRequiresWalletSelection() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallets = listOf(
                blinkWallet("blink-1", "Personal Blink"),
                blinkWallet("blink-2", "Business Blink")
            )
        )
        advanceUntilIdle()

        context.viewModel.startBlinkContactsImport()
        val chooser = assertNotNull(context.viewModel.uiState.value.blinkWalletChooser)
        assertEquals(
            listOf("Personal Blink", "Business Blink"),
            chooser.wallets.map { it.displayName }
        )

        val eventDeferred = async { context.viewModel.events.first() }
        context.viewModel.selectBlinkWalletForImport("blink-2")
        advanceUntilIdle()

        assertEquals(
            ContactsSettingsEvent.OpenBlinkContactsImport("blink-2"),
            eventDeferred.await()
        )
        assertNull(context.viewModel.uiState.value.blinkWalletChooser)

        context.viewModel.clear()
    }

    @Test
    fun contactSearchFiltersByNameAddressAndRole() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallets = emptyList()
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
            wallets = emptyList()
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
    fun editingExistingContactAutoSavesAndCanDelete() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            dispatcher = dispatcher,
            wallets = emptyList()
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

    private suspend fun createTestContext(dispatcher: CoroutineDispatcher, wallets: List<WalletConnection>): TestContext {
        val contactsRepository = ContactsRepositoryImpl(MapSettings())
        val walletRepository = WalletSettingsRepositoryImpl(MapSettings())
        wallets.forEachIndexed { index, wallet ->
            walletRepository.saveWalletConnection(wallet, activate = index == 0)
        }
        val viewModel = ContactsSettingsViewModel(
            observeContacts = ObserveContactsUseCase(contactsRepository),
            observeWallets = ObserveWalletsUseCase(walletRepository),
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
