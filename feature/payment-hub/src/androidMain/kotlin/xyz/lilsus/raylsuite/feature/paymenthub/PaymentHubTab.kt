package xyz.lilsus.raylsuite.feature.paymenthub

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasLayoutRepository
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.PaymentHubCanvasScreen
import xyz.lilsus.raylsuite.feature.paymenthub.host.HubGroupBottomSheet
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubIntent
import xyz.lilsus.raylsuite.feature.paymenthub.library.PaymentHubLibraryFlow

/**
 * The Hub tab: the arranged canvas plus the library it pushes to. Selecting a target asks the
 * controller to emit a payment intent; the app decides what that means and shows the payment
 * itself on its own surface.
 */
@Composable
fun PaymentHubTab(
    repository: PaymentHubRepository,
    canvasLayout: CanvasLayoutRepository,
    controller: PaymentHubController,
    preferredCurrencyCode: () -> String,
    modifier: Modifier = Modifier,
    libraryActions: @Composable ColumnScope.() -> Unit = {}
) {
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    val layout by canvasLayout.layout.collectAsStateWithLifecycle()
    val hubState by controller.state.collectAsStateWithLifecycle()
    val hub by repository.hub.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val existingIds =
        remember(hub) { (hub.targets.map { it.id } + hub.groups.map { it.id }).toSet() }

    NavigationEventHandler(
        state = rememberNavigationEventState(currentInfo = HubTabInfo(showLibrary)),
        isForwardEnabled = false,
        // The library owns back handling for its own editors.
        isBackEnabled = false,
        onBackCompleted = {}
    )

    if (showLibrary) {
        PaymentHubLibraryFlow(
            repository = repository,
            preferredCurrencyCode = preferredCurrencyCode,
            onBack = { showLibrary = false },
            modifier = modifier,
            libraryActions = libraryActions
        )
    } else {
        PaymentHubCanvasScreen(
            state = hubState.render,
            layout = remember(layout, existingIds) { layout.normalized(existingIds) },
            onSelectItem = { controller.dispatch(PaymentHubIntent.SelectItem(it)) },
            onOpenGroup = { controller.dispatch(PaymentHubIntent.OpenGroup(it)) },
            onOpenLibrary = { showLibrary = true },
            onUpdateLayout = { transform -> scope.launch { canvasLayout.update(transform) } },
            onResetLayout = { scope.launch { canvasLayout.reset() } },
            modifier = modifier
        )
    }

    hubState.groupSheet?.let { sheet ->
        HubGroupBottomSheet(
            sheet = sheet,
            onMemberSelected = { controller.dispatch(PaymentHubIntent.SelectItem(it)) },
            onDismiss = { controller.dispatch(PaymentHubIntent.DismissGroup) }
        )
    }
}

private data class HubTabInfo(val showLibrary: Boolean) : NavigationEventInfo()
