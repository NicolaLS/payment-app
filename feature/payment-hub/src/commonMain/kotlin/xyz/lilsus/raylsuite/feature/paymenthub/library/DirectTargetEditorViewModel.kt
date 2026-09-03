package xyz.lilsus.raylsuite.feature.paymenthub.library

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
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.StoredAmount
import xyz.lilsus.raylsuite.feature.paymenthub.DirectTargetAmountRule
import xyz.lilsus.raylsuite.feature.paymenthub.DirectTargetDraft
import xyz.lilsus.raylsuite.feature.paymenthub.HubAccent
import xyz.lilsus.raylsuite.feature.paymenthub.HubIcon
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemAppearance
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubRepository

/** Creates or edits one direct target. Changes apply only on explicit save. */
class DirectTargetEditorViewModel(
    private val repository: PaymentHubRepository,
    targetId: HubItemId?,
    defaultCurrencyCode: String,
    initialAddress: LightningAddress? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val mutableUiState =
        MutableStateFlow(initialState(targetId, defaultCurrencyCode, initialAddress))
    val uiState: StateFlow<DirectTargetEditorState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<HubEditorEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HubEditorEvent> = mutableEvents.asSharedFlow()

    fun updateTitle(title: String) = update { it.copy(title = title, error = null) }

    fun updateAddress(address: String) = update { it.copy(address = address, error = null) }

    fun selectAmountMode(mode: TargetAmountMode) = update {
        it.copy(amountMode = mode, error = null)
    }

    fun updateAmount(amount: String) = update { state ->
        state.copy(
            amount = amount.cleanAmountInput(state.currency.fractionDigits),
            error = null
        )
    }

    fun selectCurrency(code: String) = update { state ->
        val currency = CurrencyCatalog.infoFor(code)
        state.copy(
            currencyCode = currency.code,
            amount = state.amount.cleanAmountInput(currency.fractionDigits),
            error = null
        )
    }

    fun updateComment(comment: String) = update { it.copy(comment = comment) }

    fun selectIcon(icon: HubIcon?) = update { it.copy(icon = icon) }

    fun selectAccent(accent: HubAccent?) = update { it.copy(accent = accent) }

    fun setPinned(pinned: Boolean) = update { it.copy(pinned = pinned) }

    fun toggleGroup(groupId: HubItemId) = update { state ->
        state.copy(
            groupIds =
                if (groupId in
                    state.groupIds
                ) {
                    state.groupIds - groupId
                } else {
                    state.groupIds + groupId
                }
        )
    }

    fun save() {
        val state = mutableUiState.value
        val draft = state.toDraft() ?: return
        scope.launch {
            val saved =
                state.targetId?.let { repository.updateTarget(it, draft) }
                    ?: repository.createTarget(draft)
            if (saved == null) {
                update { it.copy(error = TargetEditorError.InvalidAddress) }
            } else {
                mutableEvents.tryEmit(HubEditorEvent.Closed)
            }
        }
    }

    fun delete() {
        val id = mutableUiState.value.targetId ?: return
        scope.launch {
            repository.deleteTarget(id)
            mutableEvents.tryEmit(HubEditorEvent.Closed)
        }
    }

    fun clear() {
        scope.cancel()
    }

    private fun update(transform: (DirectTargetEditorState) -> DirectTargetEditorState) {
        mutableUiState.update(transform)
    }

    private fun DirectTargetEditorState.toDraft(): DirectTargetDraft? {
        if (title.isBlank()) {
            update { it.copy(error = TargetEditorError.EnterTitle) }
            return null
        }
        val parsedAddress = LightningAddress.parse(address)
        if (parsedAddress == null) {
            update { it.copy(error = TargetEditorError.InvalidAddress) }
            return null
        }
        val amountRule =
            when (amountMode) {
                TargetAmountMode.AskEveryTime -> DirectTargetAmountRule.AskEveryTime

                TargetAmountMode.Preset -> {
                    if (amount.hasFractionForWholeCurrency(currency.fractionDigits)) {
                        update { it.copy(error = TargetEditorError.WholeAmountRequired) }
                        return null
                    }
                    val minor = amount.parseMinorAmount(currency.fractionDigits)
                    if (minor == null || minor <= 0L) {
                        update { it.copy(error = TargetEditorError.EnterAmount) }
                        return null
                    }
                    DirectTargetAmountRule.Preset(StoredAmount(minor, currency.code))
                }
            }
        return DirectTargetDraft(
            title = title,
            address = parsedAddress,
            amountRule = amountRule,
            comment = comment.trim().takeIf(String::isNotEmpty),
            appearance = HubItemAppearance(icon = icon, accent = accent),
            pinned = pinned,
            groupIds = groupIds
        )
    }

    private fun initialState(
        targetId: HubItemId?,
        defaultCurrencyCode: String,
        initialAddress: LightningAddress?
    ): DirectTargetEditorState {
        val hub = repository.hub.value
        val groups = hub.groups.map { HubGroupOption(id = it.id, title = it.title) }
        val target = targetId?.let(hub::target)
        if (target == null) {
            return DirectTargetEditorState(
                targetId = null,
                title = initialAddress?.username.orEmpty(),
                address = initialAddress?.full.orEmpty(),
                currencyCode = CurrencyCatalog.infoFor(defaultCurrencyCode).code,
                groups = groups
            )
        }
        val preset = (target.amountRule as? DirectTargetAmountRule.Preset)?.amount
        val currency =
            CurrencyCatalog.infoFor(preset?.normalizedCurrencyCode ?: defaultCurrencyCode)
        return DirectTargetEditorState(
            targetId = target.id,
            title = target.title,
            address = target.address.full,
            amountMode =
                if (preset == null) TargetAmountMode.AskEveryTime else TargetAmountMode.Preset,
            amount = preset?.minor?.formatMinorAmount(currency.fractionDigits).orEmpty(),
            currencyCode = currency.code,
            comment = target.comment.orEmpty(),
            icon = target.appearance.icon,
            accent = target.appearance.accent,
            pinned = hub.isPinned(target.id),
            groupIds = hub.groupsContaining(target.id).map { it.id }.toSet(),
            groups = groups
        )
    }
}

data class DirectTargetEditorState(
    val targetId: HubItemId?,
    val title: String = "",
    val address: String = "",
    val amountMode: TargetAmountMode = TargetAmountMode.AskEveryTime,
    val amount: String = "",
    val currencyCode: String = CurrencyCatalog.DEFAULT_CODE,
    val comment: String = "",
    val icon: HubIcon? = null,
    val accent: HubAccent? = null,
    val pinned: Boolean = false,
    val groupIds: Set<HubItemId> = emptySet(),
    val groups: List<HubGroupOption> = emptyList(),
    val error: TargetEditorError? = null
) {
    val isNew: Boolean
        get() = targetId == null

    val currency
        get() = CurrencyCatalog.infoFor(currencyCode)
}

enum class TargetAmountMode {
    AskEveryTime,
    Preset
}

data class HubGroupOption(val id: HubItemId, val title: String)

enum class TargetEditorError {
    EnterTitle,
    InvalidAddress,
    EnterAmount,
    WholeAmountRequired
}

sealed interface HubEditorEvent {
    data object Closed : HubEditorEvent
}
