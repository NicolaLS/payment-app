package xyz.lilsus.raylsuite.feature.paymenthub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.ui.format.currentAmountFormatter
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasLayoutRepository
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasTileSize
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_accent_amber
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_accent_blue
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_accent_green
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_accent_orange
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_accent_pink
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_accent_purple
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_accent_slate
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_accent_teal
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_add
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_move_down
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_move_up
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_pin
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_remove
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_action_unpin
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_appearance_accent
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_appearance_icon
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_appearance_none
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_add
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_add_all_placed
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_add_title
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_arrange
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_compact
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_done
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_empty_body
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_move_earlier
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_move_later
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_remove
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_reset
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_canvas_wide
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_editor_delete
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_editor_save
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_error_enter_amount
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_error_enter_title
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_error_invalid_address
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_error_whole_amount
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_available_all_added
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_available_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_available_none
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_editor_edit
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_editor_new
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_empty
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_member_count
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_members_empty
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_members_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_name_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_group_pick_member
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_bolt
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_coffee
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_gift
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_group
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_heart
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_home
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_person
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_restaurant
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_star
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_store
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_wallet
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_icon_work
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_add
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_add_group
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_add_target
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_arrange_pins
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_done
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_empty_body
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_empty_title
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_no_matches
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_search
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_section_groups
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_section_pinned
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_section_recent
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_section_targets
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_library_title
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_pin_description
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_pin_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_address_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_amount_fiat_hint
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_amount_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_amount_mode_ask
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_amount_mode_preset
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_comment_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_editor_edit
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_editor_new
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_groups_empty
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_groups_label
import xyz.lilsus.raylsuite.feature.paymenthub.generated.resources.hub_target_name_label
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubIntent
import xyz.lilsus.raylsuite.feature.paymenthub.library.DirectTargetEditorState
import xyz.lilsus.raylsuite.feature.paymenthub.library.DirectTargetEditorViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.library.GroupEditorError
import xyz.lilsus.raylsuite.feature.paymenthub.library.GroupEditorState
import xyz.lilsus.raylsuite.feature.paymenthub.library.GroupEditorViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.library.HubEditorEvent
import xyz.lilsus.raylsuite.feature.paymenthub.library.PaymentHubLibraryUiState
import xyz.lilsus.raylsuite.feature.paymenthub.library.PaymentHubLibraryViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.library.TargetAmountMode
import xyz.lilsus.raylsuite.feature.paymenthub.library.TargetEditorError
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubItemDetail
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubItemRenderModel

data class NativePaymentHubSnapshot(
    val destination: String,
    val text: NativePaymentHubCopy,
    val canvasTiles: List<NativePaymentHubTile>,
    val placeableItems: List<NativePaymentHubItem>,
    val library: NativePaymentHubLibrary,
    val targetEditor: NativePaymentHubTargetEditor?,
    val groupEditor: NativePaymentHubGroupEditor?,
    val groupSheet: NativePaymentHubGroupSheet?
)

data class NativePaymentHubCopy(
    val title: String,
    val arrange: String,
    val done: String,
    val addTiles: String,
    val reset: String,
    val emptyTitle: String,
    val emptyBody: String,
    val emptyCanvasBody: String,
    val addSheetTitle: String,
    val allTilesPlaced: String,
    val makeWide: String,
    val makeCompact: String,
    val removeTile: String,
    val moveEarlier: String,
    val moveLater: String,
    val search: String,
    val noMatches: String,
    val add: String,
    val addTarget: String,
    val addGroup: String,
    val arrangePins: String,
    val doneArrangingPins: String,
    val pinnedSection: String,
    val groupsSection: String,
    val recentSection: String,
    val targetsSection: String,
    val pin: String,
    val unpin: String,
    val moveUp: String,
    val moveDown: String,
    val groupPickerTitle: String,
    val emptyGroup: String,
    val newTarget: String,
    val editTarget: String,
    val targetName: String,
    val targetAddress: String,
    val amount: String,
    val askEveryTime: String,
    val presetAmount: String,
    val comment: String,
    val icon: String,
    val accent: String,
    val none: String,
    val iconOptions: List<NativePaymentHubAppearanceOption>,
    val accentOptions: List<NativePaymentHubAppearanceOption>,
    val pinLabel: String,
    val pinDescription: String,
    val targetGroups: String,
    val targetGroupsEmpty: String,
    val save: String,
    val delete: String,
    val newGroup: String,
    val editGroup: String,
    val groupName: String,
    val members: String,
    val membersEmpty: String,
    val availableTargets: String,
    val noAvailableTargets: String,
    val allTargetsAdded: String,
    val addMember: String,
    val removeMember: String
)

data class NativePaymentHubAppearanceOption(val id: String, val title: String)

data class NativePaymentHubItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: String?,
    val icon: String?,
    val accent: String?,
    val isGroup: Boolean,
    val pinned: Boolean,
    val enabled: Boolean
)

data class NativePaymentHubTile(val item: NativePaymentHubItem, val size: String, val index: Int)

data class NativePaymentHubLibrary(
    val isEmpty: Boolean,
    val arrangingPins: Boolean,
    val pinned: List<NativePaymentHubItem>,
    val groups: List<NativePaymentHubItem>,
    val recent: List<NativePaymentHubItem>,
    val targets: List<NativePaymentHubItem>
)

data class NativePaymentHubSelection(val id: String, val title: String, val selected: Boolean)

data class NativePaymentHubTargetEditor(
    val isNew: Boolean,
    val title: String,
    val address: String,
    val amountMode: String,
    val amount: String,
    val currencyCode: String,
    val currencyCodes: List<String>,
    val fiatHint: String?,
    val comment: String,
    val icon: String?,
    val accent: String?,
    val pinned: Boolean,
    val groups: List<NativePaymentHubSelection>,
    val error: String?
)

data class NativePaymentHubGroupEditor(
    val isNew: Boolean,
    val title: String,
    val icon: String?,
    val accent: String?,
    val pinned: Boolean,
    val members: List<NativePaymentHubItem>,
    val available: List<NativePaymentHubItem>,
    val error: String?
)

data class NativePaymentHubGroupSheet(
    val group: NativePaymentHubItem,
    val members: List<NativePaymentHubItem>
)

/** Native iOS Hub boundary. Repository policy stays in Kotlin; SwiftUI owns all presentation. */
class NativePaymentHubController(
    private val repository: PaymentHubRepository,
    private val canvasLayout: CanvasLayoutRepository,
    private val host: PaymentHubController,
    private val languageChanges: Flow<*>,
    currencyCodes: Flow<String>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val snapshot = MutableStateFlow<NativePaymentHubSnapshot?>(null)
    private val destination = MutableStateFlow(DESTINATION_CANVAS)
    private val library = PaymentHubLibraryViewModel(repository)

    private var text: NativePaymentHubCopy? = null
    private var targetEditor: DirectTargetEditorViewModel? = null
    private var targetEditorState: DirectTargetEditorState? = null
    private var targetEditorJobs: List<Job> = emptyList()
    private var groupEditor: GroupEditorViewModel? = null
    private var groupEditorState: GroupEditorState? = null
    private var groupEditorJobs: List<Job> = emptyList()
    private var preferredCurrencyCode = CurrencyCatalog.DEFAULT_CODE

    init {
        scope.launch {
            languageChanges.collect {
                text = loadCopy()
                publish()
            }
        }
        observe(host.state)
        observe(canvasLayout.layout)
        observe(library.uiState)
        observe(destination)
        scope.launch {
            currencyCodes.collect { preferredCurrencyCode = it }
        }
    }

    fun observe(onChange: (NativePaymentHubSnapshot) -> Unit): () -> Unit {
        val job = scope.launch { snapshot.filterNotNull().collect(onChange) }
        return { job.cancel() }
    }

    fun openLibrary() {
        destination.value = DESTINATION_LIBRARY
    }

    fun closeLibrary() {
        clearEditors()
        destination.value = DESTINATION_CANVAS
    }

    fun selectCanvasItem(id: String) {
        hubItemId(id)?.let { host.dispatch(PaymentHubIntent.SelectItem(it)) }
    }

    fun dismissGroup() {
        host.dispatch(PaymentHubIntent.DismissGroup)
    }

    fun selectGroupMember(id: String) {
        hubItemId(id)?.let { host.dispatch(PaymentHubIntent.SelectItem(it)) }
    }

    fun addTile(id: String) {
        val itemId = hubItemId(id) ?: return
        scope.launch { canvasLayout.update { it.place(itemId) } }
    }

    fun removeTile(id: String) {
        val itemId = hubItemId(id) ?: return
        scope.launch { canvasLayout.update { it.remove(itemId) } }
    }

    fun resizeTile(id: String) {
        val itemId = hubItemId(id) ?: return
        scope.launch {
            canvasLayout.update { layout ->
                val tile = layout.tiles.firstOrNull { it.id == itemId } ?: return@update layout
                val size =
                    if (tile.size == CanvasTileSize.Wide) {
                        CanvasTileSize.Compact
                    } else {
                        CanvasTileSize.Wide
                    }
                layout.resize(itemId, size)
            }
        }
    }

    fun moveTile(index: Int, offset: Int) {
        scope.launch { canvasLayout.update { it.move(index, offset) } }
    }

    fun resetCanvas() {
        scope.launch { canvasLayout.reset() }
    }

    fun updateSearch(query: String) {
        library.updateSearch(query)
    }

    fun toggleArrangePins() {
        library.toggleArrangePins()
    }

    fun setPinned(id: String, pinned: Boolean) {
        hubItemId(id)?.let { library.setPinned(it, pinned) }
    }

    fun movePinned(id: String, offset: Int) {
        hubItemId(id)?.let { library.movePinned(it, offset) }
    }

    fun openTargetEditor(id: String?) {
        clearEditors()
        val viewModel =
            DirectTargetEditorViewModel(
                repository = repository,
                targetId = id?.let(::hubItemId),
                defaultCurrencyCode = preferredCurrencyCode
            )
        targetEditor = viewModel
        targetEditorJobs =
            listOf(
                scope.launch {
                    viewModel.uiState.collect {
                        targetEditorState = it
                        publish()
                    }
                },
                scope.launch {
                    viewModel.events.collect { event ->
                        if (event == HubEditorEvent.Closed) closeEditor()
                    }
                }
            )
        destination.value = DESTINATION_TARGET_EDITOR
    }

    fun openGroupEditor(id: String?) {
        clearEditors()
        val viewModel = GroupEditorViewModel(repository, id?.let(::hubItemId))
        groupEditor = viewModel
        groupEditorJobs =
            listOf(
                scope.launch {
                    viewModel.uiState.collect {
                        groupEditorState = it
                        publish()
                    }
                },
                scope.launch {
                    viewModel.events.collect { event ->
                        if (event == HubEditorEvent.Closed) closeEditor()
                    }
                }
            )
        destination.value = DESTINATION_GROUP_EDITOR
    }

    fun closeEditor() {
        clearEditors()
        destination.value = DESTINATION_LIBRARY
    }

    fun updateTargetTitle(value: String) {
        targetEditor?.updateTitle(value)
    }

    fun updateTargetAddress(value: String) {
        targetEditor?.updateAddress(value)
    }

    fun updateTargetAmountMode(value: String) {
        targetEditor?.selectAmountMode(
            if (value == AMOUNT_PRESET) TargetAmountMode.Preset else TargetAmountMode.AskEveryTime
        )
    }

    fun updateTargetAmount(value: String) {
        targetEditor?.updateAmount(value)
    }

    fun updateTargetCurrency(value: String) {
        targetEditor?.selectCurrency(value)
    }

    fun updateTargetComment(value: String) {
        targetEditor?.updateComment(value)
    }

    fun updateTargetIcon(value: String?) {
        targetEditor?.selectIcon(HubIcon.fromStoredValue(value))
    }

    fun updateTargetAccent(value: String?) {
        targetEditor?.selectAccent(HubAccent.fromStoredValue(value))
    }

    fun updateTargetPinned(value: Boolean) {
        targetEditor?.setPinned(value)
    }

    fun toggleTargetGroup(id: String) {
        hubItemId(id)?.let { targetEditor?.toggleGroup(it) }
    }

    fun saveTarget() {
        targetEditor?.save()
    }

    fun deleteTarget() {
        targetEditor?.delete()
    }

    fun updateGroupTitle(value: String) {
        groupEditor?.updateTitle(value)
    }

    fun updateGroupIcon(value: String?) {
        groupEditor?.selectIcon(HubIcon.fromStoredValue(value))
    }

    fun updateGroupAccent(value: String?) {
        groupEditor?.selectAccent(HubAccent.fromStoredValue(value))
    }

    fun updateGroupPinned(value: Boolean) {
        groupEditor?.setPinned(value)
    }

    fun addGroupMember(id: String) {
        hubItemId(id)?.let { groupEditor?.addMember(it) }
    }

    fun removeGroupMember(id: String) {
        hubItemId(id)?.let { groupEditor?.removeMember(it) }
    }

    fun moveGroupMember(id: String, offset: Int) {
        hubItemId(id)?.let { groupEditor?.moveMember(it, offset) }
    }

    fun saveGroup() {
        groupEditor?.save()
    }

    fun deleteGroup() {
        groupEditor?.delete()
    }

    fun clear() {
        clearEditors()
        library.clear()
        scope.cancel()
    }

    private fun <T> observe(flow: Flow<T>) {
        scope.launch { flow.collect { publish() } }
    }

    private suspend fun publish() {
        val labels = text ?: return
        val render = host.state.value.render
        val layout = canvasLayout.layout.value.normalized(render.allItems.map { it.id }.toSet())
        val items = render.allItems.associate { it.id to it.toNativeItem() }
        val libraryState = library.uiState.value
        snapshot.value =
            NativePaymentHubSnapshot(
                destination = destination.value,
                text = labels,
                canvasTiles =
                    layout.tiles.mapIndexedNotNull { index, tile ->
                        items[tile.id]?.let {
                            NativePaymentHubTile(it, tile.size.storedValue, index)
                        }
                    },
                placeableItems =
                    render.allItems
                        .filterNot { it.id in layout.placedItemIds }
                        .map { items.getValue(it.id) },
                library = libraryState.toNativeLibrary(),
                targetEditor = targetEditorState?.toNativeEditor(),
                groupEditor = groupEditorState?.toNativeEditor(),
                groupSheet =
                    host.state.value.groupSheet?.let { sheet ->
                        NativePaymentHubGroupSheet(
                            group = sheet.group.toNativeItem(),
                            members = sheet.members.map { it.toNativeItem() }
                        )
                    }
            )
    }

    private suspend fun HubItemRenderModel.toNativeItem(): NativePaymentHubItem {
        val target = detail as? HubItemDetail.Target
        val group = detail as? HubItemDetail.Group
        return NativePaymentHubItem(
            id = id.value,
            title = title,
            subtitle =
                target?.address
                    ?: group?.let {
                        getPluralString(
                            Res.plurals.hub_group_member_count,
                            it.memberCount,
                            it.memberCount
                        )
                    }.orEmpty(),
            amount = target?.presetAmount?.let { currentAmountFormatter().format(it) },
            icon = icon?.storedValue,
            accent = accent?.storedValue,
            isGroup = isGroup,
            pinned = pinned,
            enabled = enabled
        )
    }

    private suspend fun PaymentHubLibraryUiState.toNativeLibrary() = NativePaymentHubLibrary(
        isEmpty = isEmpty,
        arrangingPins = arrangingPins,
        pinned = pinned.map { it.toNativeItem() },
        groups = groups.map { it.toNativeItem() },
        recent = recent.map { it.toNativeItem() },
        targets = targets.map { it.toNativeItem() }
    )

    private suspend fun DirectTargetEditorState.toNativeEditor(): NativePaymentHubTargetEditor =
        NativePaymentHubTargetEditor(
            isNew = isNew,
            title = title,
            address = address,
            amountMode = if (amountMode == TargetAmountMode.Preset) AMOUNT_PRESET else AMOUNT_ASK,
            amount = amount,
            currencyCode = currencyCode,
            currencyCodes = CurrencyCatalog.supportedCodes,
            fiatHint =
                getString(Res.string.hub_target_amount_fiat_hint, currencyCode)
                    .takeIf {
                        currency.currency is xyz.lilsus.raylsuite.core.model.DisplayCurrency.Fiat
                    },
            comment = comment,
            icon = icon?.storedValue,
            accent = accent?.storedValue,
            pinned = pinned,
            groups =
                groups.map {
                    NativePaymentHubSelection(it.id.value, it.title, it.id in groupIds)
                },
            error =
                when (error) {
                    TargetEditorError.EnterTitle -> getString(Res.string.hub_error_enter_title)

                    TargetEditorError.InvalidAddress ->
                        getString(Res.string.hub_error_invalid_address)

                    TargetEditorError.EnterAmount -> getString(Res.string.hub_error_enter_amount)

                    TargetEditorError.WholeAmountRequired ->
                        getString(Res.string.hub_error_whole_amount)

                    null -> null
                }
        )

    private suspend fun GroupEditorState.toNativeEditor(): NativePaymentHubGroupEditor =
        NativePaymentHubGroupEditor(
            isNew = isNew,
            title = title,
            icon = icon?.storedValue,
            accent = accent?.storedValue,
            pinned = pinned,
            members = members.map { it.toNativeItem() },
            available = available.map { it.toNativeItem() },
            error =
                when (error) {
                    GroupEditorError.EnterTitle -> getString(Res.string.hub_error_enter_title)
                    null -> null
                }
        )

    private fun xyz.lilsus.raylsuite.feature.paymenthub.library.HubMemberOption.toNativeItem() =
        NativePaymentHubItem(
            id = id.value,
            title = title,
            subtitle = address,
            amount = null,
            icon = icon?.storedValue,
            accent = accent?.storedValue,
            isGroup = false,
            pinned = false,
            enabled = true
        )

    private fun clearEditors() {
        targetEditorJobs.forEach(Job::cancel)
        targetEditorJobs = emptyList()
        targetEditor?.clear()
        targetEditor = null
        targetEditorState = null
        groupEditorJobs.forEach(Job::cancel)
        groupEditorJobs = emptyList()
        groupEditor?.clear()
        groupEditor = null
        groupEditorState = null
    }
}

private fun hubItemId(value: String): HubItemId? =
    value.trim().takeIf(String::isNotEmpty)?.let(::HubItemId)

private suspend fun loadCopy() = NativePaymentHubCopy(
    title = getString(Res.string.hub_library_title),
    arrange = getString(Res.string.hub_canvas_arrange),
    done = getString(Res.string.hub_canvas_done),
    addTiles = getString(Res.string.hub_canvas_add),
    reset = getString(Res.string.hub_canvas_reset),
    emptyTitle = getString(Res.string.hub_library_empty_title),
    emptyBody = getString(Res.string.hub_library_empty_body),
    emptyCanvasBody = getString(Res.string.hub_canvas_empty_body),
    addSheetTitle = getString(Res.string.hub_canvas_add_title),
    allTilesPlaced = getString(Res.string.hub_canvas_add_all_placed),
    makeWide = getString(Res.string.hub_canvas_wide),
    makeCompact = getString(Res.string.hub_canvas_compact),
    removeTile = getString(Res.string.hub_canvas_remove),
    moveEarlier = getString(Res.string.hub_canvas_move_earlier),
    moveLater = getString(Res.string.hub_canvas_move_later),
    search = getString(Res.string.hub_library_search),
    noMatches = getString(Res.string.hub_library_no_matches),
    add = getString(Res.string.hub_library_add),
    addTarget = getString(Res.string.hub_library_add_target),
    addGroup = getString(Res.string.hub_library_add_group),
    arrangePins = getString(Res.string.hub_library_arrange_pins),
    doneArrangingPins = getString(Res.string.hub_library_done),
    pinnedSection = getString(Res.string.hub_library_section_pinned),
    groupsSection = getString(Res.string.hub_library_section_groups),
    recentSection = getString(Res.string.hub_library_section_recent),
    targetsSection = getString(Res.string.hub_library_section_targets),
    pin = getString(Res.string.hub_action_pin),
    unpin = getString(Res.string.hub_action_unpin),
    moveUp = getString(Res.string.hub_action_move_up),
    moveDown = getString(Res.string.hub_action_move_down),
    groupPickerTitle = getString(Res.string.hub_group_pick_member),
    emptyGroup = getString(Res.string.hub_group_empty),
    newTarget = getString(Res.string.hub_target_editor_new),
    editTarget = getString(Res.string.hub_target_editor_edit),
    targetName = getString(Res.string.hub_target_name_label),
    targetAddress = getString(Res.string.hub_target_address_label),
    amount = getString(Res.string.hub_target_amount_label),
    askEveryTime = getString(Res.string.hub_target_amount_mode_ask),
    presetAmount = getString(Res.string.hub_target_amount_mode_preset),
    comment = getString(Res.string.hub_target_comment_label),
    icon = getString(Res.string.hub_appearance_icon),
    accent = getString(Res.string.hub_appearance_accent),
    none = getString(Res.string.hub_appearance_none),
    iconOptions =
        listOf(
            NativePaymentHubAppearanceOption("person", getString(Res.string.hub_icon_person)),
            NativePaymentHubAppearanceOption("group", getString(Res.string.hub_icon_group)),
            NativePaymentHubAppearanceOption("store", getString(Res.string.hub_icon_store)),
            NativePaymentHubAppearanceOption(
                "restaurant",
                getString(Res.string.hub_icon_restaurant)
            ),
            NativePaymentHubAppearanceOption("coffee", getString(Res.string.hub_icon_coffee)),
            NativePaymentHubAppearanceOption("gift", getString(Res.string.hub_icon_gift)),
            NativePaymentHubAppearanceOption("heart", getString(Res.string.hub_icon_heart)),
            NativePaymentHubAppearanceOption("star", getString(Res.string.hub_icon_star)),
            NativePaymentHubAppearanceOption("bolt", getString(Res.string.hub_icon_bolt)),
            NativePaymentHubAppearanceOption("home", getString(Res.string.hub_icon_home)),
            NativePaymentHubAppearanceOption("wallet", getString(Res.string.hub_icon_wallet)),
            NativePaymentHubAppearanceOption("work", getString(Res.string.hub_icon_work))
        ),
    accentOptions =
        listOf(
            NativePaymentHubAppearanceOption("orange", getString(Res.string.hub_accent_orange)),
            NativePaymentHubAppearanceOption("blue", getString(Res.string.hub_accent_blue)),
            NativePaymentHubAppearanceOption("green", getString(Res.string.hub_accent_green)),
            NativePaymentHubAppearanceOption("purple", getString(Res.string.hub_accent_purple)),
            NativePaymentHubAppearanceOption("pink", getString(Res.string.hub_accent_pink)),
            NativePaymentHubAppearanceOption("teal", getString(Res.string.hub_accent_teal)),
            NativePaymentHubAppearanceOption("amber", getString(Res.string.hub_accent_amber)),
            NativePaymentHubAppearanceOption("slate", getString(Res.string.hub_accent_slate))
        ),
    pinLabel = getString(Res.string.hub_pin_label),
    pinDescription = getString(Res.string.hub_pin_description),
    targetGroups = getString(Res.string.hub_target_groups_label),
    targetGroupsEmpty = getString(Res.string.hub_target_groups_empty),
    save = getString(Res.string.hub_editor_save),
    delete = getString(Res.string.hub_editor_delete),
    newGroup = getString(Res.string.hub_group_editor_new),
    editGroup = getString(Res.string.hub_group_editor_edit),
    groupName = getString(Res.string.hub_group_name_label),
    members = getString(Res.string.hub_group_members_label),
    membersEmpty = getString(Res.string.hub_group_members_empty),
    availableTargets = getString(Res.string.hub_group_available_label),
    noAvailableTargets = getString(Res.string.hub_group_available_none),
    allTargetsAdded = getString(Res.string.hub_group_available_all_added),
    addMember = getString(Res.string.hub_action_add),
    removeMember = getString(Res.string.hub_action_remove)
)

private const val DESTINATION_CANVAS = "canvas"
private const val DESTINATION_LIBRARY = "library"
private const val DESTINATION_TARGET_EDITOR = "targetEditor"
private const val DESTINATION_GROUP_EDITOR = "groupEditor"
private const val AMOUNT_ASK = "ask"
private const val AMOUNT_PRESET = "preset"
