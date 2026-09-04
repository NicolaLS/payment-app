package xyz.lilsus.raylsuite.feature.paymenthub.create

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.feature.paymenthub.DefaultPaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.DefaultCanvasLayoutRepository

@OptIn(ExperimentalCoroutinesApi::class)
class NewTargetViewModelTest {
    @Test
    fun selectingAContactCreatesAnIndependentHubTarget() = runTest {
        val settings = MapSettings()
        val hub =
            DefaultPaymentHubRepository(
                settings = settings,
                clock = { 1_000L },
                idGenerator = { "target-1" }
            )
        val contacts =
            MutableStateFlow(
                listOf(
                    HubContact(
                        id = "contact-1",
                        title = "Alice",
                        address = assertNotNull(LightningAddress.parse("alice@example.com"))
                    )
                )
            )
        val viewModel =
            NewTargetViewModel(
                repository = hub,
                layoutRepository = DefaultCanvasLayoutRepository(settings),
                defaultCurrencyCode = { "SAT" },
                contacts = contacts,
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        advanceUntilIdle()
        assertTrue(hub.hub.value.isEmpty)
        viewModel.selectContact("contact-1")
        val draft = assertNotNull(viewModel.uiState.value.configure)
        assertTrue(draft.isNew)
        assertEquals("Alice", draft.title)
        assertEquals("alice@example.com", draft.address)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, hub.hub.value.targets.size)
        assertEquals("contact-1", contacts.value.single().id)
        viewModel.clear()
    }
}
