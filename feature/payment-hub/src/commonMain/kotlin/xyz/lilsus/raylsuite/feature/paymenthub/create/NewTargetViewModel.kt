package xyz.lilsus.raylsuite.feature.paymenthub.create

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.CurrencyInfo
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.StoredAmount
import xyz.lilsus.raylsuite.feature.paymenthub.DirectTargetAmountRule
import xyz.lilsus.raylsuite.feature.paymenthub.DirectTargetDraft
import xyz.lilsus.raylsuite.feature.paymenthub.HubIcon
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemAppearance
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasLayoutRepository
import xyz.lilsus.raylsuite.feature.paymenthub.canvas.CanvasTileSize
import xyz.lilsus.raylsuite.feature.paymenthub.render.HubMark
import xyz.lilsus.raylsuite.feature.paymenthub.render.allowedTileSizes
import xyz.lilsus.raylsuite.feature.paymenthub.render.hubInitials

/**
 * Composes one hub target: choose who or what to pay, then configure the tile. A service is never
 * itself a target, so the catalogue only announces that its packages are on the way.
 */
class NewTargetViewModel(
    private val repository: PaymentHubRepository,
    private val layoutRepository: CanvasLayoutRepository,
    private val defaultCurrencyCode: () -> String,
    contacts: Flow<List<HubContact>> = emptyFlow(),
    editTargetId: HubItemId? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val mutableUiState = MutableStateFlow(initialState(editTargetId))
    val uiState: StateFlow<NewTargetUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<NewTargetEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<NewTargetEvent> = mutableEvents.asSharedFlow()

    init {
        scope.launch {
            contacts.collectLatest { updated ->
                mutableUiState.update { it.copy(contacts = updated.distinctBy(HubContact::id)) }
            }
        }
    }

    fun openContacts() = mutableUiState.update { it.copy(view = NewTargetView.Contacts) }

    fun openServices() = mutableUiState.update { it.copy(view = NewTargetView.Services) }

    fun updateQuery(query: String) = mutableUiState.update { it.copy(query = query) }

    /** Steps back inside the flow. Returns false when the host should close the flow instead. */
    fun back(): Boolean {
        val state = mutableUiState.value
        val previous =
            when (state.view) {
                NewTargetView.Launchpad -> return false

                NewTargetView.Contacts, NewTargetView.Services -> NewTargetView.Launchpad

                NewTargetView.Configure -> {
                    val configure = state.configure ?: return false
                    if (configure.isEditing) return false
                    if (configure.startedFromContacts) {
                        NewTargetView.Contacts
                    } else {
                        NewTargetView.Launchpad
                    }
                }
            }
        mutableUiState.update { it.copy(view = previous, configure = null) }
        return true
    }

    fun selectContact(id: String) {
        val contact = mutableUiState.value.contacts.firstOrNull { it.id == id } ?: return
        openConfigure(contact = contact)
    }

    fun addManually() = openConfigure()

    fun selectService(id: String) {
        val service = HubServiceCatalog.service(id) ?: return
        mutableUiState.update { it.copy(comingSoonService = service) }
    }

    fun dismissComingSoon() = mutableUiState.update { it.copy(comingSoonService = null) }

    fun updateTitle(value: String) = configure { it.copy(title = value, error = null) }

    fun updateAddress(value: String) = configure { it.copy(address = value, error = null) }

    fun selectAmount(choice: HubAmountChoice) = configure { it.copy(amount = choice, error = null) }

    fun updateCustomAmount(value: String) = configure { state ->
        state.copy(
            customAmount = value.cleanAmountInput(state.currency.fractionDigits),
            error = null
        )
    }

    fun selectCurrency(code: String) = configure { state ->
        val currency = CurrencyCatalog.infoFor(code)
        state.copy(
            currencyCode = currency.code,
            customAmount = state.customAmount.cleanAmountInput(currency.fractionDigits),
            quickAmounts = quickAmounts(currency),
            amount =
                if (state.amount is HubAmountChoice.Quick) {
                    HubAmountChoice.AskEachTime
                } else {
                    state.amount
                },
            error = null
        )
    }

    fun updateComment(value: String) = configure { it.copy(comment = value) }

    fun selectSize(size: CanvasTileSize) = configure { it.copy(size = size) }

    fun submit() {
        val state = mutableUiState.value.configure ?: return
        val title = state.title.trim()
        if (title.isEmpty()) {
            configure { it.copy(error = NewTargetError.EnterName) }
            return
        }
        val address = LightningAddress.parse(state.address)
        if (address == null) {
            configure { it.copy(error = NewTargetError.InvalidAddress) }
            return
        }
        val amountRule = state.amountRule() ?: return
        scope.launch {
            val draft =
                DirectTargetDraft(
                    title = title,
                    address = address,
                    amountRule = amountRule,
                    comment = state.comment.trim().takeIf(String::isNotEmpty),
                    appearance = state.appearance
                )
            val saved =
                state.targetId?.let { repository.updateTarget(it, draft) }
                    ?: repository.createTarget(draft)
            if (saved == null) {
                configure { it.copy(error = NewTargetError.InvalidAddress) }
                return@launch
            }
            layoutRepository.update { it.place(saved.id, state.size) }
            mutableEvents.tryEmit(NewTargetEvent.Finished)
        }
    }

    fun delete() {
        val id = mutableUiState.value.configure?.targetId ?: return
        scope.launch {
            repository.deleteTarget(id)
            layoutRepository.update { it.remove(id) }
            mutableEvents.tryEmit(NewTargetEvent.Finished)
        }
    }

    fun clear() {
        scope.cancel()
    }

    private fun openConfigure(contact: HubContact? = null) {
        mutableUiState.update { state ->
            state.copy(
                view = NewTargetView.Configure,
                configure =
                    configureState(
                        targetId = null,
                        startedFromContacts = state.view == NewTargetView.Contacts,
                        contact = contact
                    )
            )
        }
    }

    private fun configure(transform: (NewTargetConfigureState) -> NewTargetConfigureState) {
        mutableUiState.update { state -> state.copy(configure = state.configure?.let(transform)) }
    }

    private fun NewTargetConfigureState.amountRule(): DirectTargetAmountRule? {
        val minor =
            when (val choice = amount) {
                HubAmountChoice.AskEachTime -> return DirectTargetAmountRule.AskEveryTime

                is HubAmountChoice.Quick -> choice.amount.minor

                HubAmountChoice.Custom -> {
                    if (customAmount.hasFractionForWholeCurrency(currency.fractionDigits)) {
                        configure { it.copy(error = NewTargetError.WholeAmountRequired) }
                        return null
                    }
                    customAmount.parseMinorAmount(currency.fractionDigits) ?: run {
                        configure { it.copy(error = NewTargetError.EnterAmount) }
                        return null
                    }
                }
            }
        if (minor <= 0L) {
            configure { it.copy(error = NewTargetError.EnterAmount) }
            return null
        }
        return DirectTargetAmountRule.Preset(StoredAmount(minor, currency.code))
    }

    private fun initialState(editTargetId: HubItemId?): NewTargetUiState {
        val base = NewTargetUiState(services = HubServiceCatalog.services)
        if (editTargetId == null || repository.hub.value.target(editTargetId) == null) return base
        return base.copy(
            view = NewTargetView.Configure,
            configure =
                configureState(
                    targetId = editTargetId,
                    startedFromContacts = false,
                    editing = true
                )
        )
    }

    private fun configureState(
        targetId: HubItemId?,
        startedFromContacts: Boolean,
        editing: Boolean = false,
        contact: HubContact? = null
    ): NewTargetConfigureState {
        val target = targetId?.let(repository.hub.value::target)
        val preset = (target?.amountRule as? DirectTargetAmountRule.Preset)?.amount
        val currency =
            CurrencyCatalog.infoFor(preset?.normalizedCurrencyCode ?: defaultCurrencyCode())
        val quick = quickAmounts(currency)
        val presetAmount = preset?.let { DisplayAmount(it.minor, currency.currency) }
        val choice =
            when {
                presetAmount == null -> HubAmountChoice.AskEachTime
                presetAmount in quick -> HubAmountChoice.Quick(presetAmount)
                else -> HubAmountChoice.Custom
            }
        return NewTargetConfigureState(
            targetId = targetId,
            title = target?.title ?: contact?.title.orEmpty(),
            address = target?.address?.full ?: contact?.address?.full.orEmpty(),
            amount = choice,
            quickAmounts = quick,
            customAmount =
                preset
                    ?.takeIf { choice == HubAmountChoice.Custom }
                    ?.minor
                    ?.formatMinorAmount(currency.fractionDigits)
                    .orEmpty(),
            currencyCode = currency.code,
            comment = target?.comment.orEmpty(),
            appearance = target?.appearance ?: contact?.appearance ?: HubItemAppearance.None,
            size =
                targetId?.let { layoutRepository.layout.value.size(it) } ?: CanvasTileSize.Small,
            sizeOptions = allowedTileSizes(isContainer = false, memberCount = 0),
            startedFromContacts = startedFromContacts,
            isEditing = editing
        )
    }
}

enum class NewTargetView {
    Launchpad,
    Contacts,
    Services,
    Configure
}

@Immutable
data class NewTargetUiState(
    val view: NewTargetView = NewTargetView.Launchpad,
    val contacts: List<HubContact> = emptyList(),
    val services: List<HubService> = emptyList(),
    val query: String = "",
    val comingSoonService: HubService? = null,
    val configure: NewTargetConfigureState? = null
) {
    /** People have their own entry; the remaining launchpad slots preview the service catalogue. */
    val featuredServices: List<HubService>
        get() = services.take(LAUNCHPAD_SERVICE_ITEMS)

    val matchingContacts: List<HubContact>
        get() {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return contacts
            return contacts.filter { item ->
                item.title.contains(trimmed, ignoreCase = true) ||
                    item.address.full.contains(trimmed, ignoreCase = true)
            }
        }

    private companion object {
        const val LAUNCHPAD_SERVICE_ITEMS = 4
    }
}

/**
 * A contact offered as one possible source for a new Hub target. Its identity belongs to the
 * contact store, never to the Hub; choosing it copies these editable presentation values into a
 * new target.
 */
@Immutable
data class HubContact(
    val id: String,
    val title: String,
    val address: LightningAddress,
    val appearance: HubItemAppearance = HubItemAppearance(icon = HubIcon.Person)
) {
    init {
        require(id.isNotBlank()) { "Contact ID must not be blank" }
    }

    val mark: HubMark
        get() = HubMark(hubInitials(title), appearance.icon, appearance.accent)
}

@Immutable
data class NewTargetConfigureState(
    val targetId: HubItemId?,
    val title: String,
    val address: String,
    val amount: HubAmountChoice,
    val quickAmounts: List<DisplayAmount>,
    val customAmount: String,
    val currencyCode: String,
    val comment: String,
    val appearance: HubItemAppearance,
    val size: CanvasTileSize,
    val sizeOptions: List<CanvasTileSize>,
    /** Reached through the contact list, which is where back should return. */
    val startedFromContacts: Boolean,
    /** Opened straight from a tile, so there is no choose step behind it. */
    val isEditing: Boolean,
    val error: NewTargetError? = null
) {
    val currency: CurrencyInfo
        get() = CurrencyCatalog.infoFor(currencyCode)

    val isNew: Boolean
        get() = targetId == null

    /** A fiat preset is quoted at payment time, which is worth saying next to the chips. */
    val showsFiatHint: Boolean
        get() = amount != HubAmountChoice.AskEachTime && currency.currency is DisplayCurrency.Fiat
}

/** The amount chips. Leaving a target on [AskEachTime] yields the plain contact tile. */
@Immutable
sealed interface HubAmountChoice {
    data object AskEachTime : HubAmountChoice

    data class Quick(val amount: DisplayAmount) : HubAmountChoice

    data object Custom : HubAmountChoice
}

enum class NewTargetError {
    EnterName,
    InvalidAddress,
    EnterAmount,
    WholeAmountRequired
}

sealed interface NewTargetEvent {
    data object Finished : NewTargetEvent
}

private fun quickAmounts(currency: CurrencyInfo): List<DisplayAmount> {
    val minors =
        when (currency.currency) {
            DisplayCurrency.Satoshi -> listOf(1_000L, 10_000L)

            DisplayCurrency.Bitcoin -> emptyList()

            is DisplayCurrency.Fiat -> {
                var unit = 1L
                repeat(currency.fractionDigits) { unit *= 10L }
                listOf(unit, unit * 5L)
            }
        }
    return minors.map { DisplayAmount(it, currency.currency) }
}
