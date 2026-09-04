package xyz.lilsus.raylsuite.feature.paymenthub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.ui.format.AmountFormatter
import xyz.lilsus.raylsuite.core.ui.format.currentAmountFormatter
import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativePluralString
import xyz.lilsus.raylsuite.core.ui.resources.nativeString
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasLayoutRepository
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasTileSize
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubCanvasMessage
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubCanvasUiState
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubCanvasViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubGrid
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.HubGridSpan
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.gridRowCount
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.packHubGrid
import xyz.lilsus.raylsuite.feature.paymenthub.create.HubAmountChoice
import xyz.lilsus.raylsuite.feature.paymenthub.create.HubContact
import xyz.lilsus.raylsuite.feature.paymenthub.create.HubService
import xyz.lilsus.raylsuite.feature.paymenthub.create.HubServiceKind
import xyz.lilsus.raylsuite.feature.paymenthub.create.NewTargetConfigureState
import xyz.lilsus.raylsuite.feature.paymenthub.create.NewTargetError
import xyz.lilsus.raylsuite.feature.paymenthub.create.NewTargetEvent
import xyz.lilsus.raylsuite.feature.paymenthub.create.NewTargetUiState
import xyz.lilsus.raylsuite.feature.paymenthub.create.NewTargetView
import xyz.lilsus.raylsuite.feature.paymenthub.create.NewTargetViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.group.GroupEditorError
import xyz.lilsus.raylsuite.feature.paymenthub.group.GroupEditorState
import xyz.lilsus.raylsuite.feature.paymenthub.group.GroupEditorViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.group.HubEditorEvent
import xyz.lilsus.raylsuite.feature.paymenthub.group.HubMemberOption
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubIntent
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubAmountLine
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubMark
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubTileRenderModel
import xyz.lilsus.raylsuite.feature.paymenthub.render.allowedTileSizes
import xyz.lilsus.raylsuite.feature.paymenthub.render.hubInitials

data class NativePaymentHubSnapshot(
    val destination: String,
    val text: NativePaymentHubCopy,
    val canvas: NativeHubCanvas,
    val newTarget: NativeNewTarget?,
    val groupEditor: NativeHubGroupEditor?
)

data class NativeHubMark(val initials: String, val symbol: String?, val accent: String?)

data class NativeHubCanvas(
    val tiles: List<NativeHubTile>,
    val gridRows: Int,
    /** Placement of the add-target tile. */
    val addTargetColumn: Int,
    val addTargetRow: Int,
    val showsAddTarget: Boolean,
    val editing: Boolean,
    val hasItems: Boolean,
    val message: String?
)

/** The tile whose position a dragged tile will take when released. */
data class NativeHubDropTarget(val id: String)

/** One placed tile. The grid packing happens in Kotlin so both platforms lay out identically. */
data class NativeHubTile(
    val id: String,
    val label: String,
    val mark: NativeHubMark,
    val subtitle: String?,
    val amountLine: String?,
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int,
    val isContainer: Boolean,
    val memberCount: String,
    val showsMembers: Boolean,
    val expandable: Boolean,
    val members: List<NativeHubTileMember>,
    val sizes: List<NativeHubSizeOption>,
    val accessibilityLabel: String,
    val removeTitle: String,
    val removeBody: String
)

data class NativeHubTileMember(
    val id: String,
    val label: String,
    val mark: NativeHubMark,
    val amountLine: String
)

data class NativeNewTarget(
    val view: String,
    val title: String,
    val featuredServices: List<NativeHubService>,
    val contacts: List<NativeHubContact>,
    val services: List<NativeHubService>,
    val query: String,
    val hasContacts: Boolean,
    val comingSoon: NativeHubComingSoon?,
    val configure: NativeHubConfigure?
)

data class NativeHubContact(
    val id: String,
    val title: String,
    val subtitle: String,
    val mark: NativeHubMark
)

data class NativeHubService(
    val id: String,
    val name: String,
    val mark: String,
    val subtitle: String
)

data class NativeHubComingSoon(val title: String, val body: String)

data class NativeHubConfigure(
    val isNew: Boolean,
    val title: String,
    val address: String,
    val comment: String,
    val amountChips: List<NativeHubAmountChip>,
    val showsCustomAmount: Boolean,
    val customAmount: String,
    val currencyCode: String,
    val currencyCodes: List<String>,
    val fiatHint: String?,
    val sizes: List<NativeHubSizeOption>,
    val sizeHint: String,
    val submitTitle: String,
    val error: String?
)

data class NativeHubAmountChip(val id: String, val label: String, val selected: Boolean)

data class NativeHubSizeOption(
    val id: String,
    val label: String,
    val columns: Int,
    val rows: Int,
    val selected: Boolean
)

data class NativeHubGroupEditor(
    val isNew: Boolean,
    val title: String,
    val icon: String?,
    val accent: String?,
    val members: List<NativeHubContact>,
    val available: List<NativeHubContact>,
    val error: String?
)

data class NativePaymentHubAppearanceOption(val id: String, val title: String)

/** Static copy the SwiftUI hub renders. Kotlin resolves the app's String Catalog. */
data class NativePaymentHubCopy(
    val edit: String,
    val done: String,
    val move: String,
    val addTarget: String,
    val removeConfirm: String,
    val removeCancel: String,
    val newTargetTitle: String,
    val editTargetTitle: String,
    val back: String,
    val sectionPeople: String,
    val more: String,
    val contactsTitle: String,
    val servicesTitle: String,
    val addManually: String,
    val search: String,
    val noContacts: String,
    val noMatches: String,
    val comingSoonConfirm: String,
    val configureTitle: String,
    val nameLabel: String,
    val addressLabel: String,
    val amountLabel: String,
    val commentLabel: String,
    val sizeLabel: String,
    val deleteTarget: String,
    val groupEditorNew: String,
    val groupEditorEdit: String,
    val groupNameLabel: String,
    val groupMembersLabel: String,
    val groupMembersEmpty: String,
    val groupAvailableLabel: String,
    val groupAvailableNone: String,
    val groupAvailableAllAdded: String,
    val appearanceIcon: String,
    val appearanceAccent: String,
    val appearanceNone: String,
    val iconOptions: List<NativePaymentHubAppearanceOption>,
    val accentOptions: List<NativePaymentHubAppearanceOption>,
    val moveUp: String,
    val moveDown: String,
    val addMember: String,
    val removeMember: String,
    val save: String,
    val delete: String
)

/** Native iOS Hub boundary. Repository policy stays in Kotlin; SwiftUI owns all presentation. */
class NativePaymentHubController(
    private val repository: PaymentHubRepository,
    private val canvasLayout: CanvasLayoutRepository,
    private val host: PaymentHubController,
    private val languageChanges: Flow<*>,
    currencyCodes: Flow<String>,
    private val contacts: Flow<List<HubContact>> = emptyFlow()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val snapshot = MutableStateFlow<NativePaymentHubSnapshot?>(null)
    private val destination = MutableStateFlow(DESTINATION_CANVAS)
    private var preferredCurrencyCode = CurrencyCatalog.DEFAULT_CODE

    private val canvas =
        HubCanvasViewModel(
            repository = repository,
            layoutRepository = canvasLayout
        )

    private var text: NativePaymentHubCopy? = null
    private var newTarget: NewTargetViewModel? = null
    private var newTargetState: NewTargetUiState? = null
    private var newTargetJobs: List<Job> = emptyList()
    private var groupEditor: GroupEditorViewModel? = null
    private var groupEditorState: GroupEditorState? = null
    private var groupEditorJobs: List<Job> = emptyList()

    init {
        scope.launch {
            languageChanges.collect {
                text = loadCopy()
                publish()
            }
        }
        observe(canvas.uiState)
        observe(destination)
        scope.launch { currencyCodes.collect { preferredCurrencyCode = it } }
    }

    fun observe(onChange: (NativePaymentHubSnapshot) -> Unit): () -> Unit {
        val job = scope.launch { snapshot.filterNotNull().collect(onChange) }
        return { job.cancel() }
    }

    // ── Canvas ───────────────────────────────────────────────────────────────

    fun payTile(id: String) {
        hubItemId(id)?.let { host.dispatch(PaymentHubIntent.SelectItem(it)) }
    }

    fun expandTile(id: String) {
        hubItemId(id)?.let(canvas::toggleExpanded)
    }

    fun startEditing() = canvas.startEditing()

    fun stopEditing() = canvas.stopEditing()

    fun resizeTile(id: String, size: String) {
        hubItemId(id)?.let { canvas.resize(it, CanvasTileSize.fromStoredValue(size)) }
    }

    fun removeTile(id: String) {
        hubItemId(id)?.let(canvas::delete)
    }

    /** Resolves the tile whose position a dragged tile will take when released. */
    fun resolveDrop(
        draggedId: String,
        x: Double,
        y: Double,
        columnWidth: Double,
        rowHeight: Double,
        gap: Double
    ): NativeHubDropTarget? {
        val tiles = snapshot.value?.canvas?.tiles ?: return null
        tiles.forEach { tile ->
            if (tile.id == draggedId) return@forEach
            val left = (columnWidth + gap) * tile.column
            val top = (rowHeight + gap) * tile.row
            val width = columnWidth * tile.columns + gap * (tile.columns - 1)
            val height = rowHeight * tile.rows + gap * (tile.rows - 1)
            if (x < left || x > left + width || y < top || y > top + height) return@forEach
            return NativeHubDropTarget(id = tile.id)
        }
        return null
    }

    fun moveTile(id: String, onto: String) {
        val dragged = hubItemId(id) ?: return
        val target = hubItemId(onto) ?: return
        canvas.move(dragged, target)
    }

    // ── Composing a target ───────────────────────────────────────────────────

    fun openNewTarget() = openNewTarget(null)

    /** Opens the tile's own settings: the target editor, or the group editor for a container. */
    fun editTile(id: String) {
        val itemId = hubItemId(id) ?: return
        if (itemId.isGroupId()) openGroupEditor(id) else openNewTarget(itemId)
    }

    fun closeNewTarget() {
        clearEditors()
        destination.value = DESTINATION_CANVAS
    }

    fun stepBack() {
        if (newTarget?.back() != true) closeNewTarget()
    }

    fun openContacts() = newTarget?.openContacts() ?: Unit

    fun openServices() = newTarget?.openServices() ?: Unit

    fun selectContact(id: String) {
        newTarget?.selectContact(id)
    }

    fun addManually() = newTarget?.addManually() ?: Unit

    fun selectService(id: String) = newTarget?.selectService(id) ?: Unit

    fun dismissComingSoon() = newTarget?.dismissComingSoon() ?: Unit

    fun updateQuery(value: String) = newTarget?.updateQuery(value) ?: Unit

    fun updateTargetTitle(value: String) = newTarget?.updateTitle(value) ?: Unit

    fun updateTargetAddress(value: String) = newTarget?.updateAddress(value) ?: Unit

    fun updateTargetComment(value: String) = newTarget?.updateComment(value) ?: Unit

    fun selectAmountChip(id: String) {
        val model = newTarget ?: return
        val state = newTargetState?.configure ?: return
        model.selectAmount(state.choiceFor(id) ?: return)
    }

    fun updateCustomAmount(value: String) = newTarget?.updateCustomAmount(value) ?: Unit

    fun selectCurrency(code: String) = newTarget?.selectCurrency(code) ?: Unit

    fun selectSize(id: String) {
        newTarget?.selectSize(CanvasTileSize.fromStoredValue(id))
    }

    fun submitTarget() = newTarget?.submit() ?: Unit

    fun deleteTarget() = newTarget?.delete() ?: Unit

    // ── Group editor ─────────────────────────────────────────────────────────

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
                        if (event == HubEditorEvent.Closed) closeNewTarget()
                    }
                }
            )
        destination.value = DESTINATION_GROUP_EDITOR
    }

    fun updateGroupTitle(value: String) = groupEditor?.updateTitle(value) ?: Unit

    fun updateGroupIcon(value: String?) {
        groupEditor?.selectIcon(HubIcon.fromStoredValue(value))
    }

    fun updateGroupAccent(value: String?) {
        groupEditor?.selectAccent(HubAccent.fromStoredValue(value))
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

    fun saveGroup() = groupEditor?.save() ?: Unit

    fun deleteGroup() = groupEditor?.delete() ?: Unit

    fun clear() {
        clearEditors()
        canvas.clear()
        scope.cancel()
    }

    private fun openNewTarget(editTargetId: HubItemId?) {
        clearEditors()
        val viewModel =
            NewTargetViewModel(
                repository = repository,
                layoutRepository = canvasLayout,
                defaultCurrencyCode = { preferredCurrencyCode },
                contacts = contacts,
                editTargetId = editTargetId
            )
        newTarget = viewModel
        newTargetJobs =
            listOf(
                scope.launch {
                    viewModel.uiState.collect {
                        newTargetState = it
                        publish()
                    }
                },
                scope.launch {
                    viewModel.events.collect { event ->
                        if (event == NewTargetEvent.Finished) closeNewTarget()
                    }
                }
            )
        destination.value = DESTINATION_NEW_TARGET
    }

    private fun <T> observe(flow: Flow<T>) {
        scope.launch { flow.collect { publish() } }
    }

    private fun publish() {
        val labels = text ?: return
        val formatter = currentAmountFormatter()
        snapshot.value =
            NativePaymentHubSnapshot(
                destination = destination.value,
                text = labels,
                canvas = canvas.uiState.value.toNative(formatter),
                newTarget =
                    newTargetState
                        ?.takeIf { destination.value == DESTINATION_NEW_TARGET }
                        ?.toNative(labels, formatter),
                groupEditor =
                    groupEditorState
                        ?.takeIf { destination.value == DESTINATION_GROUP_EDITOR }
                        ?.toNative()
            )
    }

    private fun HubCanvasUiState.toNative(formatter: AmountFormatter): NativeHubCanvas {
        // The add-target tile joins the packing so both platforms place it identically.
        val entries: List<HubTileRenderModel?> = tiles + null
        val placements =
            packHubGrid(entries) { entry ->
                entry?.let { HubGridSpan(it.columns, it.rows) } ?: HubGridSpan(1, 1)
            }
        val addTarget = placements.firstOrNull { it.value == null }
        return NativeHubCanvas(
            tiles =
                placements.mapNotNull { placement ->
                    val tile = placement.value ?: return@mapNotNull null
                    val sizes = allowedTileSizes(tile.isContainer, tile.memberCount)
                    NativeHubTile(
                        id = tile.id.value,
                        label = tile.label,
                        mark = tile.mark.toNative(),
                        subtitle = tile.subtitle,
                        amountLine = tile.amountLine?.toNative(formatter),
                        column = placement.column,
                        row = placement.row,
                        columns = placement.columns,
                        rows = placement.rows,
                        isContainer = tile.isContainer,
                        memberCount =
                            nativePluralString(
                                resource("hub_group_member_count"),
                                tile.memberCount
                            ),
                        showsMembers = tile.showsMembers,
                        expandable = tile.expandable,
                        members =
                            tile.members.map { member ->
                                NativeHubTileMember(
                                    id = member.id.value,
                                    label = member.label,
                                    mark = member.mark.toNative(),
                                    amountLine = member.amountLine.toNative(formatter)
                                )
                            },
                        sizes =
                            sizes.map { size ->
                                NativeHubSizeOption(
                                    id = size.storedValue,
                                    label = string(size.labelKey()),
                                    columns = size.columns,
                                    rows = size.rows,
                                    selected = size == tile.storedSize
                                )
                            },
                        accessibilityLabel =
                            nativeString(
                                resource(
                                    when {
                                        editing -> "hub_canvas_move_item"
                                        tile.isContainer -> "hub_canvas_open_group"
                                        else -> "hub_canvas_pay"
                                    }
                                ),
                                tile.label
                            ),
                        removeTitle =
                            nativeString(resource("hub_canvas_remove_title"), tile.label),
                        removeBody = nativeString(resource("hub_canvas_remove_body"))
                    )
                },
            gridRows = placements.gridRowCount(),
            addTargetColumn = addTarget?.column ?: 0,
            addTargetRow = addTarget?.row ?: 0,
            showsAddTarget = addTarget != null,
            editing = editing,
            hasItems = hasItems,
            message = message?.let { nativeString(resource(it.key())) }
        )
    }

    private fun NewTargetUiState.toNative(
        labels: NativePaymentHubCopy,
        formatter: AmountFormatter
    ): NativeNewTarget = NativeNewTarget(
        view =
            when (view) {
                NewTargetView.Launchpad -> VIEW_LAUNCHPAD
                NewTargetView.Contacts -> VIEW_CONTACTS
                NewTargetView.Services -> VIEW_SERVICES
                NewTargetView.Configure -> VIEW_CONFIGURE
            },
        title =
            if (configure?.isEditing == true) labels.editTargetTitle else labels.newTargetTitle,
        featuredServices = featuredServices.map { it.toNative() },
        contacts = matchingContacts.map { it.toNativeContact() },
        services = services.map { it.toNative() },
        query = query,
        hasContacts = contacts.isNotEmpty(),
        comingSoon =
            comingSoonService?.let { service ->
                NativeHubComingSoon(
                    title =
                        nativeString(resource("hub_service_coming_soon_title"), service.name),
                    body = nativeString(resource("hub_service_coming_soon_body"), service.name)
                )
            },
        configure = configure?.toNative(labels, formatter)
    )

    private fun NewTargetConfigureState.toNative(
        labels: NativePaymentHubCopy,
        formatter: AmountFormatter
    ): NativeHubConfigure = NativeHubConfigure(
        isNew = isNew,
        title = title,
        address = address,
        comment = comment,
        amountChips = amountChips(formatter),
        showsCustomAmount = amount == HubAmountChoice.Custom,
        customAmount = customAmount,
        currencyCode = currencyCode,
        currencyCodes = CurrencyCatalog.supportedCodes,
        fiatHint =
            if (showsFiatHint) {
                nativeString(resource("hub_target_amount_fiat_hint"), currencyCode)
            } else {
                null
            },
        sizes =
            sizeOptions.map { option ->
                NativeHubSizeOption(
                    id = option.storedValue,
                    label = nativeString(resource(option.labelKey())),
                    columns = option.columns,
                    rows = option.rows,
                    selected = option == size
                )
            },
        sizeHint = nativeString(resource(size.hintKey())),
        submitTitle =
            nativeString(
                resource(if (isNew) "hub_configure_add" else "hub_configure_save")
            ),
        error = error?.let { nativeString(resource(it.key())) }
    )

    private fun NewTargetConfigureState.amountChips(
        formatter: AmountFormatter
    ): List<NativeHubAmountChip> = buildList {
        add(
            NativeHubAmountChip(
                id = CHIP_ASK,
                label = nativeString(resource("hub_amount_ask_each_time")),
                selected = amount == HubAmountChoice.AskEachTime
            )
        )
        quickAmounts.forEachIndexed { index, quick ->
            add(
                NativeHubAmountChip(
                    id = "$CHIP_QUICK$index",
                    label = formatter.format(quick),
                    selected = amount == HubAmountChoice.Quick(quick)
                )
            )
        }
        add(
            NativeHubAmountChip(
                id = CHIP_CUSTOM,
                label = nativeString(resource("hub_amount_other")),
                selected = amount == HubAmountChoice.Custom
            )
        )
    }

    private fun NewTargetConfigureState.choiceFor(id: String): HubAmountChoice? = when {
        id == CHIP_ASK -> HubAmountChoice.AskEachTime

        id == CHIP_CUSTOM -> HubAmountChoice.Custom

        id.startsWith(CHIP_QUICK) ->
            id.removePrefix(CHIP_QUICK).toIntOrNull()
                ?.let(quickAmounts::getOrNull)
                ?.let(HubAmountChoice::Quick)

        else -> null
    }

    private fun GroupEditorState.toNative(): NativeHubGroupEditor = NativeHubGroupEditor(
        isNew = isNew,
        title = title,
        icon = (icon ?: HubIcon.Group).storedValue,
        accent = accent?.storedValue,
        members = members.map { it.toNativeContact() },
        available = available.map { it.toNativeContact() },
        error =
            when (error) {
                GroupEditorError.EnterTitle ->
                    nativeString(resource("hub_error_enter_title"))

                null -> null
            }
    )

    private fun HubContact.toNativeContact(): NativeHubContact = NativeHubContact(
        id = id,
        title = title,
        subtitle = address.full,
        mark = mark.toNative()
    )

    private fun HubMemberOption.toNativeContact(): NativeHubContact = NativeHubContact(
        id = id.value,
        title = title,
        subtitle = address,
        mark = NativeHubMark(hubInitials(title), icon?.storedValue, accent?.storedValue)
    )

    private fun HubService.toNative(): NativeHubService = NativeHubService(
        id = id,
        name = name,
        mark = mark,
        subtitle =
            nativeString(
                resource("hub_service_subtitle"),
                nativeString(resource(kind.key())),
                nativePluralString(resource("hub_service_option_count"), optionCount)
            )
    )

    private fun clearEditors() {
        newTargetJobs.forEach(Job::cancel)
        newTargetJobs = emptyList()
        newTarget?.clear()
        newTarget = null
        newTargetState = null
        groupEditorJobs.forEach(Job::cancel)
        groupEditorJobs = emptyList()
        groupEditor?.clear()
        groupEditor = null
        groupEditorState = null
    }
}

private fun HubMark.toNative(): NativeHubMark =
    NativeHubMark(initials, icon?.storedValue, accent?.storedValue)

private fun HubAmountLine.toNative(formatter: AmountFormatter): String = when (this) {
    HubAmountLine.AskEachTime -> nativeString(resource("hub_amount_ask"))
    is HubAmountLine.Preset -> formatter.format(amount)
}

private fun CanvasTileSize.labelKey(): String = when (this) {
    CanvasTileSize.Small -> "hub_size_small"
    CanvasTileSize.Wide -> "hub_size_wide"
    CanvasTileSize.Large -> "hub_size_large"
}

private fun CanvasTileSize.hintKey(): String = when (this) {
    CanvasTileSize.Small -> "hub_size_hint_small"
    CanvasTileSize.Wide -> "hub_size_hint_wide"
    CanvasTileSize.Large -> "hub_size_hint_large"
}

private fun HubCanvasMessage.key(): String = when (this) {
    HubCanvasMessage.Deleted -> "hub_canvas_message_removed"
}

private fun NewTargetError.key(): String = when (this) {
    NewTargetError.EnterName -> "hub_error_enter_title"
    NewTargetError.InvalidAddress -> "hub_error_invalid_address"
    NewTargetError.EnterAmount -> "hub_error_enter_amount"
    NewTargetError.WholeAmountRequired -> "hub_error_whole_amount"
}

private fun HubServiceKind.key(): String = when (this) {
    HubServiceKind.Mobile -> "hub_service_kind_mobile"
    HubServiceKind.EsimData -> "hub_service_kind_esim"
    HubServiceKind.Other -> "hub_service_kind_other"
}

private fun hubItemId(value: String): HubItemId? =
    value.trim().takeIf(String::isNotEmpty)?.let(::HubItemId)

private fun resource(key: String) = NativeStringResource(table = "PaymentHub", key = key)

private fun string(key: String) = nativeString(resource(key))

private fun loadCopy() = NativePaymentHubCopy(
    edit = string("hub_canvas_edit"),
    done = string("hub_canvas_done"),
    move = string("hub_canvas_move"),
    addTarget = string("hub_canvas_add_target"),
    removeConfirm = string("hub_canvas_remove_confirm"),
    removeCancel = string("hub_canvas_remove_cancel"),
    newTargetTitle = string("hub_new_title"),
    editTargetTitle = string("hub_new_edit_title"),
    back = string("hub_new_back"),
    sectionPeople = string("hub_new_section_people"),
    more = string("hub_new_more"),
    contactsTitle = string("hub_new_contacts_title"),
    servicesTitle = string("hub_new_services_title"),
    addManually = string("hub_new_add_manually"),
    search = string("hub_new_search"),
    noContacts = string("hub_new_no_contacts"),
    noMatches = string("hub_new_no_matches"),
    comingSoonConfirm = string("hub_service_coming_soon_confirm"),
    configureTitle = string("hub_configure_title"),
    nameLabel = string("hub_target_name_label"),
    addressLabel = string("hub_target_address_label"),
    amountLabel = string("hub_target_amount_label"),
    commentLabel = string("hub_target_comment_label"),
    sizeLabel = string("hub_configure_size"),
    deleteTarget = string("hub_configure_delete"),
    groupEditorNew = string("hub_group_editor_new"),
    groupEditorEdit = string("hub_group_editor_edit"),
    groupNameLabel = string("hub_group_name_label"),
    groupMembersLabel = string("hub_group_members_label"),
    groupMembersEmpty = string("hub_group_members_empty"),
    groupAvailableLabel = string("hub_group_available_label"),
    groupAvailableNone = string("hub_group_available_none"),
    groupAvailableAllAdded = string("hub_group_available_all_added"),
    appearanceIcon = string("hub_appearance_icon"),
    appearanceAccent = string("hub_appearance_accent"),
    appearanceNone = string("hub_appearance_none"),
    iconOptions =
        HubIcon.entries.map {
            NativePaymentHubAppearanceOption(it.storedValue, string("hub_icon_${it.storedValue}"))
        },
    accentOptions =
        HubAccent.entries.map {
            NativePaymentHubAppearanceOption(
                it.storedValue,
                string("hub_accent_${it.storedValue}")
            )
        },
    moveUp = string("hub_action_move_up"),
    moveDown = string("hub_action_move_down"),
    addMember = string("hub_action_add"),
    removeMember = string("hub_action_remove"),
    save = string("hub_editor_save"),
    delete = string("hub_editor_delete")
)

private const val DESTINATION_CANVAS = "canvas"
private const val DESTINATION_NEW_TARGET = "newTarget"
private const val DESTINATION_GROUP_EDITOR = "groupEditor"
private const val VIEW_LAUNCHPAD = "launchpad"
private const val VIEW_CONTACTS = "contacts"
private const val VIEW_SERVICES = "services"
private const val VIEW_CONFIGURE = "configure"
private const val CHIP_ASK = "ask"
private const val CHIP_CUSTOM = "custom"
private const val CHIP_QUICK = "quick:"

/** Grid geometry the SwiftUI canvas lays out with. */
object NativeHubGrid {
    val columns = HubGrid.COLUMNS
    val rowHeight = HubGrid.ROW_HEIGHT.toDouble()
    val gap = HubGrid.GAP.toDouble()
    val gutter = HubGrid.GUTTER.toDouble()
}
