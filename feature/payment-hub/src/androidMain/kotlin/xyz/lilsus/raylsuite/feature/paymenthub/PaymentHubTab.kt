package xyz.lilsus.raylsuite.feature.paymenthub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasLayoutRepository
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubCanvasActions
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubCanvasScreen
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubCanvasViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.create.HubContact
import xyz.lilsus.raylsuite.feature.paymenthub.create.NewTargetActions
import xyz.lilsus.raylsuite.feature.paymenthub.create.NewTargetEvent
import xyz.lilsus.raylsuite.feature.paymenthub.create.NewTargetFlow
import xyz.lilsus.raylsuite.feature.paymenthub.create.NewTargetViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.group.GroupEditorScreen
import xyz.lilsus.raylsuite.feature.paymenthub.group.GroupEditorViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.group.HubEditorEvent
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubIntent

/**
 * The Hub tab: the canvas plus the flow that composes a target. Selecting a target asks the
 * controller to emit a payment intent; the app decides what that means and shows the payment
 * itself on its own surface.
 *
 * [contacts] is an app-owned contact projection. Choosing one copies its editable values into a
 * new Hub target; the Hub never owns or deletes the source contact.
 *
 * [importButton] is an app-owned action offered where a user looks for a contact and does not
 * find one. Blip supplies its Blink import there; Flint and Lasr pass nothing.
 */
@Composable
fun PaymentHubTab(
    repository: PaymentHubRepository,
    canvasLayout: CanvasLayoutRepository,
    controller: PaymentHubController,
    preferredCurrencyCode: () -> String,
    contacts: Flow<List<HubContact>> = emptyFlow(),
    modifier: Modifier = Modifier,
    importButton: (@Composable () -> Unit)? = null
) {
    var destination by remember { mutableStateOf<HubDestination>(HubDestination.Canvas) }
    val canvasViewModel =
        remember(repository, canvasLayout) {
            HubCanvasViewModel(
                repository = repository,
                layoutRepository = canvasLayout
            )
        }
    DisposableEffect(canvasViewModel) {
        onDispose(canvasViewModel::clear)
    }
    val canvasState by canvasViewModel.uiState.collectAsStateWithLifecycle()

    NavigationEventHandler(
        state = rememberNavigationEventState(currentInfo = HubTabInfo(destination)),
        isForwardEnabled = false,
        isBackEnabled = destination is HubDestination.GroupEditor,
        onBackCompleted = { destination = HubDestination.Canvas }
    )

    when (val current = destination) {
        HubDestination.Canvas ->
            HubCanvasScreen(
                state = canvasState,
                actions =
                    HubCanvasActions(
                        pay = { controller.dispatch(PaymentHubIntent.SelectItem(it)) },
                        expand = canvasViewModel::toggleExpanded,
                        edit = { id ->
                            destination =
                                if (id.isGroupId()) {
                                    HubDestination.GroupEditor(id)
                                } else {
                                    HubDestination.NewTarget(id)
                                }
                        },
                        addTarget = { destination = HubDestination.NewTarget(null) },
                        startEditing = canvasViewModel::startEditing,
                        stopEditing = canvasViewModel::stopEditing,
                        resize = canvasViewModel::resize,
                        delete = canvasViewModel::delete,
                        move = canvasViewModel::move
                    ),
                modifier = modifier
            )

        is HubDestination.NewTarget ->
            NewTargetDestination(
                repository = repository,
                canvasLayout = canvasLayout,
                preferredCurrencyCode = preferredCurrencyCode,
                contacts = contacts,
                editTargetId = current.id,
                onClose = { destination = HubDestination.Canvas },
                importButton = importButton,
                modifier = modifier
            )

        is HubDestination.GroupEditor ->
            GroupEditorDestination(
                repository = repository,
                groupId = current.id,
                onClose = { destination = HubDestination.Canvas },
                modifier = modifier
            )
    }
}

@Composable
private fun NewTargetDestination(
    repository: PaymentHubRepository,
    canvasLayout: CanvasLayoutRepository,
    preferredCurrencyCode: () -> String,
    contacts: Flow<List<HubContact>>,
    editTargetId: HubItemId?,
    onClose: () -> Unit,
    importButton: (@Composable () -> Unit)?,
    modifier: Modifier
) {
    val viewModel =
        remember(repository, canvasLayout, contacts, editTargetId) {
            NewTargetViewModel(
                repository = repository,
                layoutRepository = canvasLayout,
                defaultCurrencyCode = preferredCurrencyCode,
                contacts = contacts,
                editTargetId = editTargetId
            )
        }
    DisposableEffect(viewModel) {
        onDispose(viewModel::clear)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                NewTargetEvent.Finished -> onClose()
            }
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NewTargetFlow(
        state = state,
        actions =
            NewTargetActions(
                openContacts = viewModel::openContacts,
                openServices = viewModel::openServices,
                selectContact = viewModel::selectContact,
                addManually = viewModel::addManually,
                selectService = viewModel::selectService,
                dismissComingSoon = viewModel::dismissComingSoon,
                updateQuery = viewModel::updateQuery,
                updateTitle = viewModel::updateTitle,
                updateAddress = viewModel::updateAddress,
                selectAmount = viewModel::selectAmount,
                updateCustomAmount = viewModel::updateCustomAmount,
                selectCurrency = viewModel::selectCurrency,
                updateComment = viewModel::updateComment,
                selectSize = viewModel::selectSize,
                submit = viewModel::submit,
                delete = viewModel::delete,
                back = { if (!viewModel.back()) onClose() }
            ),
        modifier = modifier,
        importButton = importButton
    )
}

@Composable
private fun GroupEditorDestination(
    repository: PaymentHubRepository,
    groupId: HubItemId,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val viewModel = remember(repository, groupId) { GroupEditorViewModel(repository, groupId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        onDispose(viewModel::clear)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                HubEditorEvent.Closed -> onClose()
            }
        }
    }
    GroupEditorScreen(
        state = state,
        onBack = onClose,
        onTitleChange = viewModel::updateTitle,
        onIconChange = viewModel::selectIcon,
        onAccentChange = viewModel::selectAccent,
        onAddMember = viewModel::addMember,
        onRemoveMember = viewModel::removeMember,
        onMoveMember = viewModel::moveMember,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        modifier = modifier
    )
}

private sealed interface HubDestination {
    data object Canvas : HubDestination

    data class NewTarget(val id: HubItemId?) : HubDestination

    data class GroupEditor(val id: HubItemId) : HubDestination
}

private data class HubTabInfo(val destination: HubDestination) : NavigationEventInfo()
