package xyz.lilsus.papp.presentation.settings

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.Contact
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.domain.model.PaymentConfirmationMode
import xyz.lilsus.papp.domain.model.PaymentPreferences
import xyz.lilsus.papp.domain.model.PaymentShortcut
import xyz.lilsus.papp.domain.model.ShortcutAmount
import xyz.lilsus.papp.domain.usecases.DeleteShortcutUseCase
import xyz.lilsus.papp.domain.usecases.ObserveContactPreferencesUseCase
import xyz.lilsus.papp.domain.usecases.ObserveContactsUseCase
import xyz.lilsus.papp.domain.usecases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.usecases.ObservePaymentPreferencesUseCase
import xyz.lilsus.papp.domain.usecases.ObserveShortcutsUseCase
import xyz.lilsus.papp.domain.usecases.SaveShortcutUseCase
import xyz.lilsus.papp.domain.usecases.SetAskToSaveContactsUseCase
import xyz.lilsus.papp.domain.usecases.SetConfirmManualEntryUseCase
import xyz.lilsus.papp.domain.usecases.SetConfirmShortcutPaymentsUseCase
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationModeUseCase
import xyz.lilsus.papp.domain.usecases.SetPaymentConfirmationThresholdUseCase
import xyz.lilsus.papp.domain.usecases.SetVibrateOnPaymentUseCase
import xyz.lilsus.papp.domain.usecases.SetVibrateOnScanUseCase
import xyz.lilsus.papp.presentation.main.CurrencyManager

class PaymentsSettingsViewModel internal constructor(
    observePreferences: ObservePaymentPreferencesUseCase,
    private val observeCurrencyPreference: ObserveCurrencyPreferenceUseCase,
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
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var contacts: List<Contact> = emptyList()
    private var shortcuts: List<PaymentShortcut> = emptyList()
    private var shortcutEditorAutoSaveRevision: Int = 0
    private var pendingInitialShortcutContactId: String? = null

    private val _uiState = MutableStateFlow(PaymentsSettingsUiState())
    val uiState: StateFlow<PaymentsSettingsUiState> = _uiState.asStateFlow()

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
                updateFiatEquivalent()
            }
        }

        scope.launch {
            observeCurrencyPreference().collectLatest { currency ->
                currencyManager.setPreferredCurrency(currency)
            }
        }

        scope.launch {
            currencyManager.state.collectLatest {
                updateFiatEquivalent()
            }
        }
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

    fun startAddShortcut() {
        openAddShortcutEditor(selectedContactId = null)
    }

    fun startAddShortcutForContact(contactId: String) {
        if (openAddShortcutEditor(selectedContactId = contactId)) {
            pendingInitialShortcutContactId = null
        } else {
            pendingInitialShortcutContactId = contactId
        }
    }

    private fun openAddShortcutEditor(selectedContactId: String?): Boolean {
        val selectedContact = selectedContactId?.let(::shortcutContactOption)
        if (selectedContactId != null && selectedContact == null) return false
        val options = shortcutContactOptions()
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
                comment = "",
                contactQuery = "",
                contactOptions = options
            )
        )
        return true
    }

    fun startEditShortcut(id: String) {
        val shortcut = shortcuts.firstOrNull { it.id == id } ?: return
        val options = shortcutContactOptions()
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
                comment = shortcut.comment.orEmpty(),
                contactQuery = "",
                contactOptions = options
            )
        )
    }

    fun deleteShortcut(id: String) {
        shortcutEditorAutoSaveRevision++
        scope.launch {
            deleteShortcutUseCase?.invoke(id)
            if (_uiState.value.shortcutEditor?.shortcutId == id) {
                _uiState.value = _uiState.value.copy(shortcutEditor = null)
            }
            refreshShortcuts()
        }
    }

    fun updateShortcutTitle(title: String) {
        updateShortcutEditor { it.copy(title = title, error = null) }
            ?.autoSaveExistingShortcut()
    }

    fun updateShortcutContact(contactId: String) {
        val options = shortcutContactOptions()
        updateShortcutEditor {
            it.copy(
                selectedContactId = contactId,
                selectedContact = shortcutContactOption(contactId),
                contactQuery = "",
                contactOptions = options,
                error = null
            )
        }?.autoSaveExistingShortcut()
    }

    fun updateShortcutContactQuery(query: String) {
        updateShortcutEditor { editor ->
            val options = shortcutContactOptions(query)
            editor.copy(
                contactQuery = query,
                contactOptions = options,
                error = null
            )
        }
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
            editor.copy(
                currencyCode = info.code,
                amount = editor.amount.cleanAmountInput(info.fractionDigits),
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
        }
    }

    fun dismissShortcutEditor() {
        shortcutEditorAutoSaveRevision++
        _uiState.value = _uiState.value.copy(shortcutEditor = null)
    }

    fun clear() {
        scope.cancel()
    }

    private fun updateFiatEquivalent() {
        val currencyState = currencyManager.state.value
        val isFiat = currencyState.info.currency is DisplayCurrency.Fiat
        if (!isFiat || currencyState.exchangeRate == null) {
            _uiState.value = _uiState.value.copy(thresholdFiatEquivalent = null)
            return
        }
        val thresholdMsats = _uiState.value.thresholdSats * MSATS_PER_SAT
        val fiatAmount = currencyManager.convertMsatsToDisplay(thresholdMsats, currencyState)
        _uiState.value = _uiState.value.copy(thresholdFiatEquivalent = fiatAmount)
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
        scope.launch {
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
                "Add a contact before creating a shortcut."
            )
            return null
        }
        if (contactId == null) {
            setShortcutEditorErrorIfRequested(setError, "Select a contact.")
            return null
        }
        val amountInfo = CurrencyCatalog.infoFor(currencyCode)
        val amountMinor = amount.parseMinorAmount(amountInfo.fractionDigits)
        if (amountMinor == null || amountMinor <= 0L) {
            setShortcutEditorErrorIfRequested(setError, "Enter an amount.")
            return null
        }
        val contact = contacts.firstOrNull { it.id == contactId }
        if (contact == null) {
            setShortcutEditorErrorIfRequested(setError, "Select a contact.")
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

    private fun setShortcutEditorErrorIfRequested(setError: Boolean, message: String) {
        if (setError) {
            updateShortcutEditor { it.copy(error = message) }
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
        val options = shortcutContactOptions(editor.contactQuery)
        val selectedContactId = editor.selectedContactId?.takeIf { selected ->
            contacts.any { it.id == selected }
        }
        _uiState.value = _uiState.value.copy(
            shortcutEditor = editor.copy(
                selectedContactId = selectedContactId,
                selectedContact = selectedContactId?.let(::shortcutContactOption),
                contactOptions = options
            )
        )
    }

    private fun shortcutContactOptions(query: String = ""): List<ShortcutContactOption> {
        val normalizedQuery = query.trim().lowercase()
        return contacts
            .asSequence()
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
            .map { contact ->
                contact.toShortcutContactOption()
            }
            .toList()
    }

    private fun shortcutContactOption(contactId: String): ShortcutContactOption? =
        contacts.firstOrNull { it.id == contactId }?.toShortcutContactOption()

    private fun Contact.toShortcutContactOption(): ShortcutContactOption = ShortcutContactOption(
        id = id,
        displayName = displayName,
        address = address.full
    )

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
        val normalized = filter { it.isDigit() || it == '.' }
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
    val thresholdFiatEquivalent: DisplayAmount? = null,
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
    val contactQuery: String,
    val contactOptions: List<ShortcutContactOption>,
    val error: String? = null
)

data class ShortcutContactOption(val id: String, val displayName: String, val address: String)

private data class ShortcutSaveRequest(
    val id: String?,
    val title: String,
    val contactId: String,
    val amount: ShortcutAmount,
    val comment: String?
)
