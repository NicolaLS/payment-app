package xyz.lilsus.raylsuite.feature.paymenthub.library

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.collectLatest
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.isGroupId

/**
 * Full-screen hub library with its target and group editors. Hosts own navigation to it;
 * this flow owns only the library-internal destinations.
 */
@Composable
fun PaymentHubLibraryFlow(
    repository: PaymentHubRepository,
    preferredCurrencyCode: () -> String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    libraryActions: @Composable ColumnScope.() -> Unit = {}
) {
    var destination by remember { mutableStateOf<LibraryDestination>(LibraryDestination.Library) }
    val libraryViewModel = remember(repository) { PaymentHubLibraryViewModel(repository) }
    val libraryState by libraryViewModel.uiState.collectAsState()
    DisposableEffect(libraryViewModel) {
        onDispose(libraryViewModel::clear)
    }

    fun navigateBack() {
        when (destination) {
            LibraryDestination.Library -> onBack()
            else -> destination = LibraryDestination.Library
        }
    }

    NavigationEventHandler(
        state = rememberNavigationEventState(currentInfo = LibraryNavigationInfo(destination)),
        isForwardEnabled = false,
        onBackCompleted = ::navigateBack
    )

    when (val current = destination) {
        LibraryDestination.Library ->
            PaymentHubLibraryScreen(
                state = libraryState,
                onBack = onBack,
                onSearchChange = libraryViewModel::updateSearch,
                onAddTarget = { destination = LibraryDestination.TargetEditor(null) },
                onAddGroup = { destination = LibraryDestination.GroupEditor(null) },
                onOpenItem = { id ->
                    destination =
                        if (id.isGroupId()) {
                            LibraryDestination.GroupEditor(id)
                        } else {
                            LibraryDestination.TargetEditor(id)
                        }
                },
                onSetPinned = libraryViewModel::setPinned,
                onMovePinned = libraryViewModel::movePinned,
                onToggleArrangePins = libraryViewModel::toggleArrangePins,
                modifier = modifier,
                additionalActions = libraryActions
            )

        is LibraryDestination.TargetEditor ->
            TargetEditorDestination(
                repository = repository,
                targetId = current.id,
                preferredCurrencyCode = preferredCurrencyCode,
                onClose = { destination = LibraryDestination.Library },
                modifier = modifier
            )

        is LibraryDestination.GroupEditor ->
            GroupEditorDestination(
                repository = repository,
                groupId = current.id,
                onClose = { destination = LibraryDestination.Library },
                modifier = modifier
            )
    }
}

@Composable
private fun TargetEditorDestination(
    repository: PaymentHubRepository,
    targetId: HubItemId?,
    preferredCurrencyCode: () -> String,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val viewModel =
        remember(repository, targetId) {
            DirectTargetEditorViewModel(
                repository = repository,
                targetId = targetId,
                defaultCurrencyCode = preferredCurrencyCode()
            )
        }
    val state by viewModel.uiState.collectAsState()
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
    DirectTargetEditorScreen(
        state = state,
        onBack = onClose,
        onTitleChange = viewModel::updateTitle,
        onAddressChange = viewModel::updateAddress,
        onAmountModeChange = viewModel::selectAmountMode,
        onAmountChange = viewModel::updateAmount,
        onCurrencyChange = viewModel::selectCurrency,
        onCommentChange = viewModel::updateComment,
        onIconChange = viewModel::selectIcon,
        onAccentChange = viewModel::selectAccent,
        onPinnedChange = viewModel::setPinned,
        onGroupToggle = viewModel::toggleGroup,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        modifier = modifier
    )
}

@Composable
private fun GroupEditorDestination(
    repository: PaymentHubRepository,
    groupId: HubItemId?,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val viewModel = remember(repository, groupId) { GroupEditorViewModel(repository, groupId) }
    val state by viewModel.uiState.collectAsState()
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
        onPinnedChange = viewModel::setPinned,
        onAddMember = viewModel::addMember,
        onRemoveMember = viewModel::removeMember,
        onMoveMember = viewModel::moveMember,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        modifier = modifier
    )
}

private sealed interface LibraryDestination {
    data object Library : LibraryDestination

    data class TargetEditor(val id: HubItemId?) : LibraryDestination

    data class GroupEditor(val id: HubItemId?) : LibraryDestination
}

private data class LibraryNavigationInfo(val destination: LibraryDestination) :
    NavigationEventInfo()
