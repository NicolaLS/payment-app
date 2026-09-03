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
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.ui.format.currentAmountFormatter
import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativePluralString
import xyz.lilsus.raylsuite.core.ui.resources.nativeString
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasLayoutRepository
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasTileSize
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
                        nativePluralString(
                            NativeStringResource(
                                table = "PaymentHub",
                                key = "hub_group_member_count"
                            ),
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
                nativeString(
                    NativeStringResource(table = "PaymentHub", key = "hub_target_amount_fiat_hint"),
                    currencyCode
                )
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
                    TargetEditorError.EnterTitle -> nativeString(
                        NativeStringResource(table = "PaymentHub", key = "hub_error_enter_title")
                    )

                    TargetEditorError.InvalidAddress ->
                        nativeString(
                            NativeStringResource(
                                table = "PaymentHub",
                                key = "hub_error_invalid_address"
                            )
                        )

                    TargetEditorError.EnterAmount -> nativeString(
                        NativeStringResource(table = "PaymentHub", key = "hub_error_enter_amount")
                    )

                    TargetEditorError.WholeAmountRequired ->
                        nativeString(
                            NativeStringResource(
                                table = "PaymentHub",
                                key = "hub_error_whole_amount"
                            )
                        )

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
                    GroupEditorError.EnterTitle -> nativeString(
                        NativeStringResource(table = "PaymentHub", key = "hub_error_enter_title")
                    )

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
    title = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_library_title")),
    arrange = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_canvas_arrange")),
    done = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_canvas_done")),
    addTiles = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_canvas_add")),
    reset = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_canvas_reset")),
    emptyTitle = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_library_empty_title")
    ),
    emptyBody = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_library_empty_body")
    ),
    emptyCanvasBody = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_canvas_empty_body")
    ),
    addSheetTitle = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_canvas_add_title")
    ),
    allTilesPlaced = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_canvas_add_all_placed")
    ),
    makeWide = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_canvas_wide")),
    makeCompact = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_canvas_compact")
    ),
    removeTile = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_canvas_remove")
    ),
    moveEarlier = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_canvas_move_earlier")
    ),
    moveLater = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_canvas_move_later")
    ),
    search = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_library_search")),
    noMatches = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_library_no_matches")
    ),
    add = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_library_add")),
    addTarget = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_library_add_target")
    ),
    addGroup = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_library_add_group")
    ),
    arrangePins = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_library_arrange_pins")
    ),
    doneArrangingPins = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_library_done")
    ),
    pinnedSection = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_library_section_pinned")
    ),
    groupsSection = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_library_section_groups")
    ),
    recentSection = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_library_section_recent")
    ),
    targetsSection = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_library_section_targets")
    ),
    pin = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_action_pin")),
    unpin = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_action_unpin")),
    moveUp = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_action_move_up")),
    moveDown = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_action_move_down")
    ),
    groupPickerTitle = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_group_pick_member")
    ),
    emptyGroup = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_group_empty")),
    newTarget = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_target_editor_new")
    ),
    editTarget = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_target_editor_edit")
    ),
    targetName = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_target_name_label")
    ),
    targetAddress = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_target_address_label")
    ),
    amount = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_target_amount_label")
    ),
    askEveryTime = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_target_amount_mode_ask")
    ),
    presetAmount = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_target_amount_mode_preset")
    ),
    comment = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_target_comment_label")
    ),
    icon = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_appearance_icon")),
    accent = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_appearance_accent")
    ),
    none = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_appearance_none")),
    iconOptions =
        listOf(
            NativePaymentHubAppearanceOption(
                "person",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_icon_person"))
            ),
            NativePaymentHubAppearanceOption(
                "group",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_icon_group"))
            ),
            NativePaymentHubAppearanceOption(
                "store",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_icon_store"))
            ),
            NativePaymentHubAppearanceOption(
                "restaurant",
                nativeString(
                    NativeStringResource(table = "PaymentHub", key = "hub_icon_restaurant")
                )
            ),
            NativePaymentHubAppearanceOption(
                "coffee",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_icon_coffee"))
            ),
            NativePaymentHubAppearanceOption(
                "gift",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_icon_gift"))
            ),
            NativePaymentHubAppearanceOption(
                "heart",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_icon_heart"))
            ),
            NativePaymentHubAppearanceOption(
                "star",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_icon_star"))
            ),
            NativePaymentHubAppearanceOption(
                "bolt",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_icon_bolt"))
            ),
            NativePaymentHubAppearanceOption(
                "home",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_icon_home"))
            ),
            NativePaymentHubAppearanceOption(
                "wallet",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_icon_wallet"))
            ),
            NativePaymentHubAppearanceOption(
                "work",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_icon_work"))
            )
        ),
    accentOptions =
        listOf(
            NativePaymentHubAppearanceOption(
                "orange",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_accent_orange"))
            ),
            NativePaymentHubAppearanceOption(
                "blue",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_accent_blue"))
            ),
            NativePaymentHubAppearanceOption(
                "green",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_accent_green"))
            ),
            NativePaymentHubAppearanceOption(
                "purple",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_accent_purple"))
            ),
            NativePaymentHubAppearanceOption(
                "pink",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_accent_pink"))
            ),
            NativePaymentHubAppearanceOption(
                "teal",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_accent_teal"))
            ),
            NativePaymentHubAppearanceOption(
                "amber",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_accent_amber"))
            ),
            NativePaymentHubAppearanceOption(
                "slate",
                nativeString(NativeStringResource(table = "PaymentHub", key = "hub_accent_slate"))
            )
        ),
    pinLabel = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_pin_label")),
    pinDescription = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_pin_description")
    ),
    targetGroups = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_target_groups_label")
    ),
    targetGroupsEmpty = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_target_groups_empty")
    ),
    save = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_editor_save")),
    delete = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_editor_delete")),
    newGroup = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_group_editor_new")
    ),
    editGroup = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_group_editor_edit")
    ),
    groupName = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_group_name_label")
    ),
    members = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_group_members_label")
    ),
    membersEmpty = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_group_members_empty")
    ),
    availableTargets = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_group_available_label")
    ),
    noAvailableTargets = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_group_available_none")
    ),
    allTargetsAdded = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_group_available_all_added")
    ),
    addMember = nativeString(NativeStringResource(table = "PaymentHub", key = "hub_action_add")),
    removeMember = nativeString(
        NativeStringResource(table = "PaymentHub", key = "hub_action_remove")
    )
)

private const val DESTINATION_CANVAS = "canvas"
private const val DESTINATION_LIBRARY = "library"
private const val DESTINATION_TARGET_EDITOR = "targetEditor"
private const val DESTINATION_GROUP_EDITOR = "groupEditor"
private const val AMOUNT_ASK = "ask"
private const val AMOUNT_PRESET = "preset"
