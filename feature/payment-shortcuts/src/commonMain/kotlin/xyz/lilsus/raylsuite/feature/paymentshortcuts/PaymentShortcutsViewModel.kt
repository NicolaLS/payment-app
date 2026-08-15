package xyz.lilsus.raylsuite.feature.paymentshortcuts

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
import xyz.lilsus.raylsuite.core.model.Contact
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.PaymentShortcut
import xyz.lilsus.raylsuite.core.model.ShortcutAmount
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository

class PaymentShortcutsViewModel(
    private val repository: ContactsRepository,
    private val preferredCurrencyCode: () -> String = { CurrencyCatalog.DEFAULT_CODE },
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var contacts: List<Contact> = emptyList()
    private var shortcuts: List<PaymentShortcut> = emptyList()
    private var pendingContactId: String? = null
    private var pendingShortcutId: String? = null

    private val mutableUiState = MutableStateFlow(PaymentShortcutsUiState())
    val uiState: StateFlow<PaymentShortcutsUiState> = mutableUiState.asStateFlow()

    private val mutableEvents = MutableSharedFlow<PaymentShortcutsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PaymentShortcutsEvent> = mutableEvents.asSharedFlow()

    init {
        scope.launch {
            repository.contacts.collectLatest { updatedContacts ->
                contacts = updatedContacts
                pendingContactId?.let { contactId ->
                    if (openNewEditor(contactId)) {
                        pendingContactId = null
                    }
                }
                refresh()
            }
        }
        scope.launch {
            repository.shortcuts.collectLatest { updatedShortcuts ->
                shortcuts = updatedShortcuts
                pendingShortcutId?.let { shortcutId ->
                    if (openExistingEditor(shortcutId)) {
                        pendingShortcutId = null
                    }
                }
                refresh()
            }
        }
    }

    fun startAdd(contactId: String? = null) {
        if (openNewEditor(contactId)) {
            pendingContactId = null
        } else {
            pendingContactId = contactId
        }
    }

    fun startEdit(shortcutId: String) {
        if (openExistingEditor(shortcutId)) {
            pendingShortcutId = null
        } else {
            pendingShortcutId = shortcutId
        }
    }

    fun updateTitle(title: String) {
        updateEditor { it.copy(title = title, error = null) }
            ?.autoSave()
    }

    fun selectContact(contactId: String) {
        val contact = contacts.firstOrNull { it.id == contactId } ?: return
        updateEditor {
            it.copy(
                selectedContact = contact.toOption(),
                error = null
            )
        }?.autoSave()
    }

    fun updateAmount(amount: String) {
        updateEditor { editor ->
            val currency = CurrencyCatalog.infoFor(editor.currencyCode)
            editor.copy(
                amount = amount.cleanAmountInput(currency.fractionDigits),
                error = null
            )
        }?.autoSave()
    }

    fun selectCurrency(currencyCode: String) {
        val normalizedCode = currencyCode.supportedCurrencyCodeOrNull() ?: return
        updateEditor { editor ->
            val currency = CurrencyCatalog.infoFor(normalizedCode)
            editor.copy(
                currencyCode = currency.code,
                amount = editor.amount.cleanAmountInput(currency.fractionDigits),
                error = editor.amount.validationError(currency.fractionDigits)
            )
        }?.autoSave()
    }

    fun updateComment(comment: String) {
        updateEditor { it.copy(comment = comment, error = null) }
            ?.autoSave()
    }

    fun updateContactSearch(query: String) {
        mutableUiState.value = mutableUiState.value.copy(contactSearch = query)
        refreshContactOptions()
    }

    fun saveEditor(defaultTitle: String) {
        val editor = mutableUiState.value.editor ?: return
        val request = editor.toSaveRequest(defaultTitle) ?: return
        scope.launch {
            repository.saveShortcut(
                id = request.id,
                title = request.title,
                contactId = request.contactId,
                amount = request.amount,
                comment = request.comment
            )
            closeEditor()
        }
    }

    fun deleteEditedShortcut() {
        val shortcutId = mutableUiState.value.editor?.shortcutId ?: return
        scope.launch {
            repository.deleteShortcut(shortcutId)
            closeEditor()
        }
    }

    fun dismissEditor() {
        closeEditor()
    }

    fun clear() {
        scope.cancel()
    }

    private fun openNewEditor(contactId: String?): Boolean {
        val contact = contactId?.let { id -> contacts.firstOrNull { it.id == id } }
        if (contactId != null && contact == null) return false
        mutableUiState.value =
            mutableUiState.value.copy(
                editor =
                    PaymentShortcutEditorState(
                        shortcutId = null,
                        title = "",
                        selectedContact = contact?.toOption(),
                        amount = "",
                        currencyCode = defaultCurrencyCode(),
                        comment = ""
                    )
            )
        return true
    }

    private fun openExistingEditor(shortcutId: String): Boolean {
        val shortcut = shortcuts.firstOrNull { it.id == shortcutId } ?: return false
        val contact = contacts.firstOrNull { it.id == shortcut.contactId } ?: return false
        mutableUiState.value =
            mutableUiState.value.copy(
                editor =
                    PaymentShortcutEditorState(
                        shortcutId = shortcut.id,
                        title = shortcut.title,
                        selectedContact = contact.toOption(),
                        amount = shortcut.amount.inputText(),
                        currencyCode =
                            CurrencyCatalog
                                .infoFor(shortcut.amount.normalizedCurrencyCode)
                                .code,
                        comment = shortcut.comment.orEmpty()
                    )
            )
        return true
    }

    private fun updateEditor(
        transform: (PaymentShortcutEditorState) -> PaymentShortcutEditorState
    ): PaymentShortcutEditorState? {
        val editor = mutableUiState.value.editor ?: return null
        val updated = transform(editor)
        mutableUiState.value = mutableUiState.value.copy(editor = updated)
        return updated
    }

    private fun PaymentShortcutEditorState.autoSave() {
        val shortcutId = shortcutId ?: return
        val request = toSaveRequest(defaultTitle = title) ?: return
        scope.launch {
            repository.saveShortcut(
                id = shortcutId,
                title = request.title,
                contactId = request.contactId,
                amount = request.amount,
                comment = request.comment
            )
        }
    }

    private fun PaymentShortcutEditorState.toSaveRequest(
        defaultTitle: String
    ): ShortcutSaveRequest? {
        val contact = selectedContact
        if (contacts.isEmpty()) {
            setEditorError(PaymentShortcutEditorError.NoContacts)
            return null
        }
        if (contact == null || contacts.none { it.id == contact.id }) {
            setEditorError(PaymentShortcutEditorError.SelectContact)
            return null
        }
        val currency = CurrencyCatalog.infoFor(currencyCode)
        amount.validationError(currency.fractionDigits)?.let { error ->
            setEditorError(error)
            return null
        }
        val amountMinor = amount.parseMinorAmount(currency.fractionDigits)
        if (amountMinor == null || amountMinor <= 0) {
            setEditorError(PaymentShortcutEditorError.EnterAmount)
            return null
        }
        val resolvedTitle = title.trim().ifEmpty { defaultTitle.trim() }
        if (resolvedTitle.isEmpty()) {
            setEditorError(PaymentShortcutEditorError.EnterTitle)
            return null
        }
        return ShortcutSaveRequest(
            id = shortcutId,
            title = resolvedTitle,
            contactId = contact.id,
            amount =
                ShortcutAmount(
                    minor = amountMinor,
                    currencyCode = currency.code
                ),
            comment = comment.trim().takeIf(String::isNotEmpty)
        )
    }

    private fun setEditorError(error: PaymentShortcutEditorError) {
        updateEditor { it.copy(error = error) }
    }

    private fun closeEditor() {
        mutableUiState.value = mutableUiState.value.copy(editor = null)
        mutableEvents.tryEmit(PaymentShortcutsEvent.CloseEditor)
    }

    private fun refresh() {
        val items =
            shortcuts
                .sortedWith(SHORTCUT_ORDER)
                .mapNotNull { shortcut ->
                    val contact =
                        contacts.firstOrNull { contact -> contact.id == shortcut.contactId }
                            ?: return@mapNotNull null
                    PaymentShortcutItem(
                        id = shortcut.id,
                        title = shortcut.title,
                        amount = shortcut.amount,
                        contactName = contact.displayName,
                        comment = shortcut.comment
                    )
                }
        val editor =
            mutableUiState.value.editor?.let { current ->
                val contactId = current.selectedContact?.id
                current.copy(
                    selectedContact =
                        contactId
                            ?.let { id -> contacts.firstOrNull { it.id == id } }
                            ?.toOption()
                )
            }
        mutableUiState.value =
            mutableUiState.value.copy(
                shortcuts = items,
                editor = editor
            )
        refreshContactOptions()
    }

    private fun refreshContactOptions() {
        val query = mutableUiState.value.contactSearch.trim().lowercase()
        val options =
            contacts
                .asSequence()
                .filter { contact ->
                    query.isBlank() ||
                        contact.displayName.lowercase().contains(query) ||
                        contact.address.full.lowercase().contains(query)
                }.sortedWith(CONTACT_ORDER)
                .map(Contact::toOption)
                .toList()
        mutableUiState.value = mutableUiState.value.copy(contactOptions = options)
    }

    private fun defaultCurrencyCode(): String = shortcuts
        .maxByOrNull(PaymentShortcut::updatedAtMs)
        ?.amount
        ?.normalizedCurrencyCode
        .supportedCurrencyCodeOrNull()
        ?: preferredCurrencyCode().supportedCurrencyCodeOrNull()
        ?: CurrencyCatalog.DEFAULT_CODE
}

data class PaymentShortcutsUiState(
    val shortcuts: List<PaymentShortcutItem> = emptyList(),
    val editor: PaymentShortcutEditorState? = null,
    val contactSearch: String = "",
    val contactOptions: List<PaymentShortcutContactOption> = emptyList()
)

data class PaymentShortcutItem(
    val id: String,
    val title: String,
    val amount: ShortcutAmount,
    val contactName: String,
    val comment: String?
)

data class PaymentShortcutEditorState(
    val shortcutId: String?,
    val title: String,
    val selectedContact: PaymentShortcutContactOption?,
    val amount: String,
    val currencyCode: String,
    val comment: String,
    val error: PaymentShortcutEditorError? = null
)

data class PaymentShortcutContactOption(
    val id: String,
    val displayName: String,
    val address: String
)

enum class PaymentShortcutEditorError {
    NoContacts,
    SelectContact,
    EnterAmount,
    WholeAmountRequired,
    EnterTitle
}

sealed interface PaymentShortcutsEvent {
    data object CloseEditor : PaymentShortcutsEvent
}

private data class ShortcutSaveRequest(
    val id: String?,
    val title: String,
    val contactId: String,
    val amount: ShortcutAmount,
    val comment: String?
)

private val SHORTCUT_ORDER =
    compareByDescending<PaymentShortcut> { it.stats.paymentCount }
        .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
        .thenBy { it.title.lowercase() }

private val CONTACT_ORDER =
    compareByDescending<Contact> { it.stats.paymentCount }
        .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
        .thenBy { it.displayName.lowercase() }

private fun Contact.toOption(): PaymentShortcutContactOption = PaymentShortcutContactOption(
    id = id,
    displayName = displayName,
    address = address.full
)

private fun ShortcutAmount.inputText(): String {
    val currency = CurrencyCatalog.infoFor(normalizedCurrencyCode)
    return minor.formatMinorAmount(currency.fractionDigits)
}

private fun String.cleanAmountInput(fractionDigits: Int): String {
    val normalized = replace(',', '.').filter { it.isDigit() || it == '.' }
    if (fractionDigits <= 0) return normalized.substringBefore('.').filter(Char::isDigit)
    val whole = normalized.substringBefore('.').filter(Char::isDigit)
    val hasDecimal = '.' in normalized
    val fraction =
        normalized
            .substringAfter('.', "")
            .filter(Char::isDigit)
            .take(fractionDigits)
    return if (hasDecimal) "$whole.$fraction" else whole
}

private fun String.validationError(fractionDigits: Int): PaymentShortcutEditorError? {
    val normalized = replace(',', '.').filter { it.isDigit() || it == '.' }
    if (normalized.isBlank() || normalized == ".") return null
    if (fractionDigits <= 0 && '.' in normalized) {
        return PaymentShortcutEditorError.WholeAmountRequired
    }
    return null
}

private fun String.parseMinorAmount(fractionDigits: Int): Long? {
    val cleaned = cleanAmountInput(fractionDigits)
    if (cleaned.isBlank() || cleaned == ".") return null
    if (fractionDigits <= 0) return cleaned.toLongOrNull()

    val factor = decimalFactor(fractionDigits)
    val whole = cleaned.substringBefore('.').ifBlank { "0" }.toLongOrNull() ?: return null
    val fraction =
        cleaned
            .substringAfter('.', "")
            .padEnd(fractionDigits, '0')
            .take(fractionDigits)
            .ifBlank { "0" }
            .toLongOrNull()
            ?: return null
    if (whole > (Long.MAX_VALUE - fraction) / factor) return null
    return whole * factor + fraction
}

private fun Long.formatMinorAmount(fractionDigits: Int): String {
    if (fractionDigits <= 0) return toString()
    val factor = decimalFactor(fractionDigits)
    val whole = this / factor
    val fraction =
        (this % factor)
            .toString()
            .padStart(fractionDigits, '0')
            .trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}

private fun decimalFactor(fractionDigits: Int): Long {
    var factor = 1L
    repeat(fractionDigits) {
        if (factor > Long.MAX_VALUE / 10L) return Long.MAX_VALUE
        factor *= 10L
    }
    return factor
}

private fun String?.supportedCurrencyCodeOrNull(): String? {
    val candidate = this ?: return null
    return CurrencyCatalog.supportedCodes
        .firstOrNull { it.equals(candidate, ignoreCase = true) }
}
