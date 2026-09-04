package xyz.lilsus.raylsuite.feature.paymenthub.group

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.feature.paymenthub.DirectPaymentTarget
import xyz.lilsus.raylsuite.feature.paymenthub.GroupDraft
import xyz.lilsus.raylsuite.feature.paymenthub.HubAccent
import xyz.lilsus.raylsuite.feature.paymenthub.HubIcon
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemAppearance
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubRepository

/** Creates or edits one group. Members keep an explicit order; changes apply only on save. */
class GroupEditorViewModel(
    private val repository: PaymentHubRepository,
    groupId: HubItemId?,
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val mutableUiState = MutableStateFlow(initialState(groupId))
    val uiState: StateFlow<GroupEditorState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<HubEditorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HubEditorEvent> = mutableEvents.asSharedFlow()

    fun updateTitle(title: String) = update { it.copy(title = title, error = null) }

    fun selectIcon(icon: HubIcon?) = update { it.copy(icon = icon) }

    fun selectAccent(accent: HubAccent?) = update { it.copy(accent = accent) }

    fun addMember(id: HubItemId) = update { state ->
        if (state.memberIds.contains(id) || state.candidates.none { it.id == id }) {
            state
        } else {
            state.copy(memberIds = state.memberIds + id)
        }
    }

    fun removeMember(id: HubItemId) = update { state ->
        state.copy(memberIds = state.memberIds.filterNot { it == id })
    }

    fun moveMember(id: HubItemId, offset: Int) = update { state ->
        val index = state.memberIds.indexOf(id)
        val target = index + offset
        if (index < 0 || target !in state.memberIds.indices) return@update state
        val reordered = state.memberIds.toMutableList()
        reordered.removeAt(index)
        reordered.add(target, id)
        state.copy(memberIds = reordered)
    }

    fun save() {
        val state = mutableUiState.value
        if (state.title.isBlank()) {
            update { it.copy(error = GroupEditorError.EnterTitle) }
            return
        }
        val draft =
            GroupDraft(
                title = state.title,
                memberIds = state.memberIds,
                appearance = HubItemAppearance(icon = state.icon, accent = state.accent)
            )
        scope.launch {
            val saved =
                state.groupId?.let { repository.updateGroup(it, draft) }
                    ?: repository.createGroup(draft)
            if (saved == null) {
                update { it.copy(error = GroupEditorError.EnterTitle) }
            } else {
                mutableEvents.tryEmit(HubEditorEvent.Closed)
            }
        }
    }

    fun delete() {
        val id = mutableUiState.value.groupId ?: return
        scope.launch {
            repository.deleteGroup(id)
            mutableEvents.tryEmit(HubEditorEvent.Closed)
        }
    }

    fun clear() {
        scope.cancel()
    }

    private fun update(transform: (GroupEditorState) -> GroupEditorState) {
        mutableUiState.update(transform)
    }

    private fun initialState(groupId: HubItemId?): GroupEditorState {
        val hub = repository.hub.value
        val candidates =
            hub.targets
                .sortedBy { it.title.lowercase() }
                .map(DirectPaymentTarget::toMemberOption)
        val group = groupId?.let(hub::group)
        return if (group == null) {
            GroupEditorState(groupId = null, candidates = candidates)
        } else {
            GroupEditorState(
                groupId = group.id,
                title = group.title,
                icon = group.appearance.icon,
                accent = group.appearance.accent,
                memberIds = hub.members(group.id).map { it.id },
                candidates = candidates
            )
        }
    }
}

data class GroupEditorState(
    val groupId: HubItemId?,
    val title: String = "",
    val icon: HubIcon? = null,
    val accent: HubAccent? = null,
    val memberIds: List<HubItemId> = emptyList(),
    /** Every direct target, alphabetically. */
    val candidates: List<HubMemberOption> = emptyList(),
    val error: GroupEditorError? = null
) {
    val isNew: Boolean
        get() = groupId == null

    val members: List<HubMemberOption>
        get() = memberIds.mapNotNull { id -> candidates.firstOrNull { it.id == id } }

    val available: List<HubMemberOption>
        get() = candidates.filterNot { it.id in memberIds }
}

data class HubMemberOption(
    val id: HubItemId,
    val title: String,
    val address: String,
    val icon: HubIcon?,
    val accent: HubAccent?
)

enum class GroupEditorError {
    EnterTitle
}

sealed interface HubEditorEvent {
    data object Closed : HubEditorEvent
}

private fun DirectPaymentTarget.toMemberOption(): HubMemberOption = HubMemberOption(
    id = id,
    title = title,
    address = address.full,
    icon = appearance.icon,
    accent = appearance.accent
)
