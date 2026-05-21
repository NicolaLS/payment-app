package xyz.lilsus.papp.presentation.settings

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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.Contact
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.PaymentConfirmationMode
import xyz.lilsus.papp.domain.model.PaymentPreferences
import xyz.lilsus.papp.domain.model.PaymentShortcut
import xyz.lilsus.papp.domain.model.ShortcutAmount
import xyz.lilsus.papp.domain.usecases.DeleteShortcutUseCase
import xyz.lilsus.papp.domain.usecases.GetExchangeRateUseCase
import xyz.lilsus.papp.domain.usecases.ObserveContactPreferencesUseCase
import xyz.lilsus.papp.domain.usecases.ObserveContactsUseCase
import xyz.lilsus.papp.domain.usecases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObservePaymentPreferencesUseCase
import xyz.lilsus.papp.domain.usecases.ObserveSecondaryCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObserveShortcutsUseCase
import xyz.lilsus.papp.domain.usecases.SaveShortcutUseCase
import xyz.lilsus.papp.domain.usecases.SetAskToSaveContactsUseCase
import xyz.lilsus.papp.domain.usecases.SetConfirmManualEntryUseCase
import xyz.lilsus.papp.domain.usecases.SetConfirmShortcutPaymentsUseCase
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationModeUseCase
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationThresholdUseCase
import xyz.lilsus.papp.domain.usecases.SetVibrateOnPaymentUseCase
import xyz.lilsus.papp.domain.usecases.SetVibrateOnScanUseCase
import xyz.lilsus.papp.presentation.common.SecondaryCurrencyPreviewController
import xyz.lilsus.papp.presentation.common.ShortcutEditorError
import xyz.lilsus.papp.presentation.main.CurrencyManager

class PaymentsSettingsViewModel internal constructor(
    observePreferences: ObservePaymentPreferencesUseCase,
    private val observeCurrencyPreference: ObserveCurrencyPreferenceUseCase,
    observeSecondaryCurrencyPreference: ObserveSecondaryCurrencyPreferenceUseCase,
    getExchangeRate: GetExchangeRateUseCase,
    private val currencyManager: CurrencyManager,
    private val setConfirmationMode: SetPaymentConfirmationModeUseCase,
    private val setConfirmationThreshold: SetPaymentConfirmationThresholdUseCase,
    private val setConfirmManualEntryPreference: SetConfirmManualEntryUseCase,
    private val setConfirmShortcutPaymentsUseCase: SetConfirmShortcutPaymentsUseCase,
    private val setVibrateOnScanUseCase: SetVibrateOnScanUseCase,
    private val setVibrateOnPaymentUseCase: SetVibrateOnPaymentUseCase,
    private val observeContactPreferences: ObserveContactPreferencesUseCase? = null,
    private val setAskToSaveContactsUseCase: SetAskToSaveContactsUseCase? = null,
    observeContacts: ObserveContactsUseCase? = null,
    observeShortcuts: ObserveShortcutsUseCase? = null,
    private val saveShortcut: SaveShortcutUseCase? = null,
    private val deleteShortcutUseCase: DeleteShortcutUseCase? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    autoSaveScope: CoroutineScope? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val durableAutoSaveScope = autoSaveScope ?: scope
    private var contacts: List<Contact> = emptyList()
    private var shortcuts: List<PaymentShortcut> = emptyList()
    private var shortcutEditorAutoSaveRevision: Int = 0
    private var pendingInitialShortcutContactId: String? = null
    private var pendingEditShortcutId: String? = null

    private val _uiState = MutableStateFlow(PaymentsSettingsUiState())
    val uiState: StateFlow<PaymentsSettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PaymentsSettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PaymentsSettingsEvent> = _events.asSharedFlow()

    private val thresholdPreview = SecondaryCurrencyPreviewController(
        observeSecondaryCurrencyPreference = observeSecondaryCurrencyPreference,
        getExchangeRate = getExchangeRate,
        scope = scope,
        amountMsats = { _uiState.value.thresholdSats * MSATS_PER_SAT },
        onDisplayAmountChanged = { amount ->
            _uiState.value = _uiState.value.copy(thresholdSecondaryEquivalent = amount)
        }
    )

    init {
        scope.launch {
            observePreferences().collectLatest { preferences ->
                _uiState.value = _uiState.value.copy(
                    confirmationMode = preferences.confirmationMode,
                    thresholdSats = preferences.thresholdSats,
                    confirmManualEntry = preferences.confirmManualEntry,
                    confirmShortcutPayments = preferences.confirmShortcutPayments,
                    vibrateOnScan = preferences.vibrateOnScan,
                    vibrateOnPayment = preferences.vibrateOnPayment
                )
                thresholdPreview.refresh()
            }
        }

        scope.launch {
            observeCurrencyPreference().collectLatest { currency ->
                currencyManager.setPreferredCurrency(currency)
            }
        }

        thresholdPreview.start()

        observeContactPreferences?.let { useCase ->
            scope.launch {
                useCase().collectLatest { preferences ->
                    _uiState.value = _uiState.value.copy(
                        askToSaveNewContacts = preferences.askToSaveNewContacts
                    )
                }
            }
        }
        observeContacts?.let { useCase ->
            scope.launch {
                useCase().collectLatest {
                    contacts = it
                    pendingInitialShortcutContactId?.let { contactId ->
                        if (openAddShortcutEditor(selectedContactId = contactId)) {
                            pendingInitialShortcutContactId = null
                        }
                    }
                    refreshShortcuts()
                }
            }
        }
        observeShortcuts?.let { useCase ->
            scope.launch {
                useCase().collectLatest {
                    shortcuts = it
                    pendingEditShortcutId?.let { shortcutId ->
                        if (openShortcutEditor(shortcutId)) {
                            pendingEditShortcutId = null
                        }
                    }
                    refreshShortcuts()
                }
            }
        }
    }

    fun selectMode(mode: PaymentConfirmationMode) {
        scope.launch {
            setConfirmationMode(mode)
        }
    }

    fun updateThreshold(thresholdSats: Long) {
        scope.launch {
            setConfirmationThreshold(thresholdSats)
        }
    }

    fun setConfirmManualEntry(enabled: Boolean) {
        scope.launch {
            setConfirmManualEntryPreference(enabled)
        }
    }

    fun setConfirmShortcutPayments(enabled: Boolean) {
        scope.launch { setConfirmShortcutPaymentsUseCase(enabled) }
    }

    fun setVibrateOnScan(enabled: Boolean) {
        scope.launch { setVibrateOnScanUseCase(enabled) }
    }

    fun setVibrateOnPayment(enabled: Boolean) {
        scope.launch { setVibrateOnPaymentUseCase(enabled) }
    }

    fun setAskToSaveNewContacts(enabled: Boolean) {
        scope.launch { setAskToSaveContactsUseCase?.invoke(enabled) }
    }

    fun startAddShortcutForContact(contactId: String) {
        val currentEditor = _uiState.value.shortcutEditor
        if (currentEditor != null && currentEditor.shortcutId == null) {
            refreshOpenShortcutEditor()
            return
        }
        if (openAddShortcutEditor(selectedContactId = contactId)) {
            pendingInitialShortcutContactId = null
        } else {
            pendingInitialShortcutContactId = contactId
        }
    }

    private fun openAddShortcutEditor(selectedContactId: String?): Boolean {
        val selectedContact = selectedContactId?.let(::shortcutContactOption)
        if (selectedContactId != null && selectedContact == null) return false
        val currencyCode = currencyManager.state.value.info.code
        shortcutEditorAutoSaveRevision++
        _uiState.value = _uiState.value.copy(
            shortcutEditor = ShortcutSettingsEditor(
                shortcutId = null,
                title = "",
                selectedContactId = selectedContact?.id,
                selectedContact = selectedContact,
                amount = "",
                currencyCode = currencyCode,
                comment = ""
            )
        )
        return true
    }

    fun startEditShortcut(id: String) {
        if (openShortcutEditor(id)) {
            pendingEditShortcutId = null
        } else {
            pendingEditShortcutId = id
        }
    }

    private fun openShortcutEditor(id: String): Boolean {
        val shortcut = shortcuts.firstOrNull { it.id == id } ?: return false
        val selectedContactId = shortcut.contactId
            ?: contacts.firstOrNull { it.address.sameAddressAs(shortcut.address) }?.id
        shortcutEditorAutoSaveRevision++
        _uiState.value = _uiState.value.copy(
            shortcutEditor = ShortcutSettingsEditor(
                shortcutId = shortcut.id,
                title = shortcut.title,
                selectedContactId = selectedContactId,
                selectedContact = selectedContactId?.let(::shortcutContactOption),
                amount = shortcut.amount.inputText(),
                currencyCode = CurrencyCatalog
                    .infoFor(shortcut.amount.normalizedCurrencyCode)
                    .code,
                comment = shortcut.comment.orEmpty()
            )
        )
        return true
    }

    fun deleteShortcut(id: String) {
        shortcutEditorAutoSaveRevision++
        scope.launch {
            deleteShortcutUseCase?.invoke(id)
            if (_uiState.value.shortcutEditor?.shortcutId == id) {
                _uiState.value = _uiState.value.copy(shortcutEditor = null)
            }
            refreshShortcuts()
            _events.emit(PaymentsSettingsEvent.CloseShortcutEditor)
        }
    }

    fun updateShortcutTitle(title: String) {
        updateShortcutEditor { it.copy(title = title, error = null) }
            ?.autoSaveExistingShortcut()
    }

    fun updateShortcutContact(contactId: String) {
        updateShortcutEditor {
            it.copy(
                selectedContactId = contactId,
                selectedContact = shortcutContactOption(contactId),
                error = null
            )
        }?.autoSaveExistingShortcut()
    }

    fun updateShortcutAmount(amount: String) {
        updateShortcutEditor { editor ->
            val info = CurrencyCatalog.infoFor(editor.currencyCode)
            editor.copy(
                amount = amount.cleanAmountInput(info.fractionDigits),
                error = null
            )
        }?.autoSaveExistingShortcut()
    }

    fun updateShortcutCurrency(currencyCode: String) {
        val supported = CurrencyCatalog.supportedCodes.any {
            it.equals(currencyCode, ignoreCase = true)
        }
        if (!supported) return
        val info = CurrencyCatalog.infoFor(currencyCode)
        updateShortcutEditor { editor ->
            val currencyChanged = !editor.currencyCode.equals(info.code, ignoreCase = true)
            editor.copy(
                currencyCode = info.code,
                amount = if (currencyChanged) {
                    ""
                } else {
                    editor.amount.cleanAmountInput(info.fractionDigits)
                },
                error = null
            )
        }?.autoSaveExistingShortcut()
    }

    fun updateShortcutComment(comment: String) {
        updateShortcutEditor { it.copy(comment = comment, error = null) }
            ?.autoSaveExistingShortcut()
    }

    fun saveShortcutEditor() {
        val editor = _uiState.value.shortcutEditor ?: return
        val request = editor.toSaveRequest(setError = true) ?: return
        scope.launch {
            saveShortcut?.invoke(
                id = request.id,
                title = request.title,
                contactId = request.contactId,
                amount = request.amount,
                comment = request.comment
            )
            _uiState.value = _uiState.value.copy(shortcutEditor = null)
            refreshShortcuts()
            _events.emit(PaymentsSettingsEvent.CloseShortcutEditor)
        }
    }

    fun clear() {
        thresholdPreview.clear()
        scope.cancel()
    }

    private fun updateShortcutEditor(
        transform: (ShortcutSettingsEditor) -> ShortcutSettingsEditor
    ): ShortcutSettingsEditor? {
        val editor = _uiState.value.shortcutEditor ?: return null
        _uiState.value = _uiState.value.copy(shortcutEditor = transform(editor))
        return _uiState.value.shortcutEditor
    }

    private fun ShortcutSettingsEditor.autoSaveExistingShortcut() {
        shortcutId ?: return
        val revision = ++shortcutEditorAutoSaveRevision
        val request = toSaveRequest(setError = true) ?: return
        durableAutoSaveScope.launch {
            if (revision == shortcutEditorAutoSaveRevision) {
                saveShortcut?.invoke(
                    id = request.id,
                    title = request.title,
                    contactId = request.contactId,
                    amount = request.amount,
                    comment = request.comment
                )
                refreshShortcuts()
            }
        }
    }

    private fun ShortcutSettingsEditor.toSaveRequest(setError: Boolean): ShortcutSaveRequest? {
        val contactId = selectedContactId
        if (contacts.isEmpty()) {
            setShortcutEditorErrorIfRequested(
                setError,
                ShortcutEditorError.NoContacts
            )
            return null
        }
        if (contactId == null) {
            setShortcutEditorErrorIfRequested(setError, ShortcutEditorError.SelectContact)
            return null
        }
        val amountInfo = CurrencyCatalog.infoFor(currencyCode)
        val amountMinor = amount.parseMinorAmount(amountInfo.fractionDigits)
        if (amountMinor == null || amountMinor <= 0L) {
            setShortcutEditorErrorIfRequested(setError, ShortcutEditorError.EnterAmount)
            return null
        }
        val contact = contacts.firstOrNull { it.id == contactId }
        if (contact == null) {
            setShortcutEditorErrorIfRequested(setError, ShortcutEditorError.SelectContact)
            return null
        }
        return ShortcutSaveRequest(
            id = shortcutId,
            title = title.ifBlank { "Pay ${contact.displayName}" },
            contactId = contact.id,
            amount = ShortcutAmount(
                minor = amountMinor,
                currencyCode = amountInfo.code
            ),
            comment = comment.takeIf { it.isNotBlank() }
        )
    }

    private fun setShortcutEditorErrorIfRequested(setError: Boolean, error: ShortcutEditorError) {
        if (setError) {
            updateShortcutEditor { it.copy(error = error) }
        }
    }

    private fun refreshShortcuts() {
        refreshOpenShortcutEditor()
        val shortcutItems = shortcuts
            .sortedWith(
                compareByDescending<PaymentShortcut> { it.stats.paymentCount }
                    .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
                    .thenBy { it.title.lowercase() }
            )
            .map { it.toSettingsItem() }
        _uiState.value = _uiState.value.copy(shortcuts = shortcutItems)
    }

    private fun refreshOpenShortcutEditor() {
        val editor = _uiState.value.shortcutEditor ?: return
        val selectedContactId = editor.selectedContactId?.takeIf { selected ->
            contacts.any { it.id == selected }
        }
        _uiState.value = _uiState.value.copy(
            shortcutEditor = editor.copy(
                selectedContactId = selectedContactId,
                selectedContact = selectedContactId?.let(::shortcutContactOption)
            )
        )
    }

    private fun shortcutContactOption(contactId: String): ShortcutContactOption? =
        contacts.firstOrNull { it.id == contactId }?.toShortcutContactOption()

    private fun ShortcutAmount.inputText(): String {
        val info = CurrencyCatalog.infoFor(normalizedCurrencyCode)
        return minor.formatMinorAmount(info.fractionDigits)
    }

    private fun ShortcutAmount.displayText(): String {
        val info = CurrencyCatalog.infoFor(normalizedCurrencyCode)
        val unit = if (info.code == CurrencyCatalog.DEFAULT_CODE) {
            "sats"
        } else {
            info.code
        }
        return "${minor.formatMinorAmount(info.fractionDigits)} $unit"
    }

    private fun String.cleanAmountInput(fractionDigits: Int): String {
        val normalized = replace(',', '.').filter { it.isDigit() || it == '.' }
        if (fractionDigits <= 0) return normalized.substringBefore('.').filter(Char::isDigit)
        val whole = normalized.substringBefore('.').filter(Char::isDigit)
        val hasDecimal = '.' in normalized
        val fraction = normalized.substringAfter('.', "").filter(Char::isDigit)
            .take(fractionDigits)
        return if (hasDecimal) {
            "$whole.$fraction"
        } else {
            whole
        }
    }

    private fun String.parseMinorAmount(fractionDigits: Int): Long? {
        val cleaned = cleanAmountInput(fractionDigits)
        if (cleaned.isBlank() || cleaned == ".") return null
        if (fractionDigits <= 0) return cleaned.toLongOrNull()
        val factor = decimalFactor(fractionDigits)
        val whole = cleaned.substringBefore('.').ifBlank { "0" }.toLongOrNull() ?: return null
        val fraction = cleaned.substringAfter('.', "")
            .padEnd(fractionDigits, '0')
            .take(fractionDigits)
            .ifBlank { "0" }
            .toLongOrNull() ?: return null
        return whole * factor + fraction
    }

    private fun Long.formatMinorAmount(fractionDigits: Int): String {
        if (fractionDigits <= 0) return toString()
        val factor = decimalFactor(fractionDigits)
        val whole = this / factor
        val fraction = (this % factor).toString().padStart(fractionDigits, '0').trimEnd('0')
        return if (fraction.isEmpty()) {
            whole.toString()
        } else {
            "$whole.$fraction"
        }
    }

    private fun decimalFactor(fractionDigits: Int): Long {
        var factor = 1L
        repeat(fractionDigits) { factor *= 10L }
        return factor
    }

    private fun PaymentShortcut.toSettingsItem(): ShortcutSettingsItem = ShortcutSettingsItem(
        id = id,
        title = title,
        amountText = amount.displayText(),
        contactName = displayName(),
        comment = comment
    )

    private fun PaymentShortcut.displayName(): String =
        contactId?.let { id -> contacts.firstOrNull { it.id == id } }?.displayName
            ?: address.username

    private fun LightningAddress.sameAddressAs(other: LightningAddress): Boolean =
        full.equals(other.full, ignoreCase = true)

    companion object {
        private const val MSATS_PER_SAT = 1_000L
    }
}

data class PaymentsSettingsUiState(
    val confirmationMode: PaymentConfirmationMode = PaymentPreferences().confirmationMode,
    val thresholdSats: Long = PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS,
    val confirmManualEntry: Boolean = PaymentPreferences().confirmManualEntry,
    val confirmShortcutPayments: Boolean = PaymentPreferences().confirmShortcutPayments,
    val vibrateOnScan: Boolean = PaymentPreferences().vibrateOnScan,
    val vibrateOnPayment: Boolean = PaymentPreferences().vibrateOnPayment,
    val askToSaveNewContacts: Boolean = true,
    val thresholdSecondaryEquivalent: DisplayAmount? = null,
    val shortcuts: List<ShortcutSettingsItem> = emptyList(),
    val shortcutEditor: ShortcutSettingsEditor? = null
)

data class ShortcutSettingsItem(
    val id: String,
    val title: String,
    val amountText: String,
    val contactName: String,
    val comment: String?
)

data class ShortcutSettingsEditor(
    val shortcutId: String?,
    val title: String,
    val selectedContactId: String?,
    val selectedContact: ShortcutContactOption? = null,
    val amount: String,
    val currencyCode: String,
    val comment: String,
    val error: ShortcutEditorError? = null
)

data class ShortcutContactOption(val id: String, val displayName: String, val address: String)

sealed interface PaymentsSettingsEvent {
    data object CloseShortcutEditor : PaymentsSettingsEvent
}

data class ShortcutContactPickerUiState(
    val query: String = "",
    val options: List<ShortcutContactOption> = emptyList()
)

class ShortcutContactPickerViewModel internal constructor(
    observeContacts: ObserveContactsUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var contacts: List<Contact> = emptyList()

    private val _uiState = MutableStateFlow(ShortcutContactPickerUiState())
    val uiState: StateFlow<ShortcutContactPickerUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            observeContacts().collectLatest {
                contacts = it
                refresh()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        refresh()
    }

    fun clear() {
        scope.cancel()
    }

    private fun refresh() {
        _uiState.value = _uiState.value.copy(
            options = contacts.toShortcutContactOptions(_uiState.value.query)
        )
    }
}

private data class ShortcutSaveRequest(
    val id: String?,
    val title: String,
    val contactId: String,
    val amount: ShortcutAmount,
    val comment: String?
)

private fun List<Contact>.toShortcutContactOptions(
    query: String = ""
): List<ShortcutContactOption> {
    val normalizedQuery = query.trim().lowercase()
    return asSequence()
        .filter { contact ->
            normalizedQuery.isBlank() ||
                contact.displayName.lowercase().contains(normalizedQuery) ||
                contact.address.full.lowercase().contains(normalizedQuery)
        }
        .sortedWith(
            compareByDescending<Contact> { it.stats.paymentCount }
                .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
                .thenBy { it.displayName.lowercase() }
        )
        .map { contact -> contact.toShortcutContactOption() }
        .toList()
}

private fun Contact.toShortcutContactOption(): ShortcutContactOption = ShortcutContactOption(
    id = id,
    displayName = displayName,
    address = address.full
)
