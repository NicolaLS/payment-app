@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.raylsuite.feature.contacts

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.ContactRole

class ContactsViewModelTest {
    @Test
    fun createsSearchesAndEditsAContact() = runTest {
        val repository =
            DefaultContactsRepository(
                settings = MapSettings(),
                clock = { 100L },
                idGenerator = { "contact-1" }
            )
        val viewModel = ContactsViewModel(repository, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()

        viewModel.startAddContact()
        viewModel.updateEditorAddress("lightning:Alice@EXAMPLE.COM")
        viewModel.updateEditorAlias("Alice")
        viewModel.toggleEditorRole(ContactRole.Favorite)
        viewModel.saveNewContact()
        advanceUntilIdle()

        viewModel.updateSearch("alice@example")
        assertEquals(listOf("Alice"), viewModel.uiState.value.contacts.map { it.displayName })

        viewModel.startEditContact("contact-1")
        viewModel.updateEditorAlias("Al")
        advanceUntilIdle()

        assertEquals("Al", repository.getContacts().single().alias)
        assertEquals(
            setOf(ContactRole.Favorite),
            assertNotNull(viewModel.uiState.value.editor).roles
        )

        viewModel.clear()
    }
}
