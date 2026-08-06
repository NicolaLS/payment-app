package xyz.lilsus.flint.feature.payment

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.flint.feature.payment.contacts.ContactSavePromptUiState
import xyz.lilsus.flint.feature.payment.contacts.PaymentContactListItem
import xyz.lilsus.flint.feature.payment.contacts.PaymentContactsUiState
import xyz.lilsus.flint.feature.payment.contacts.PaymentSheetTab
import xyz.lilsus.flint.feature.payment.contacts.PaymentShortcutListItem
import xyz.lilsus.raylsuite.core.model.Contact
import xyz.lilsus.raylsuite.core.model.ContactPaymentRecord
import xyz.lilsus.raylsuite.core.model.ContactPreferences
import xyz.lilsus.raylsuite.core.model.ContactRole
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.PaymentShortcut
import xyz.lilsus.raylsuite.core.model.ShortcutAmount
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository

internal class PaymentContactsController(
    private val repository: ContactsRepository,
    private val currencyManager: PaymentCurrencyManager,
    private val scope: CoroutineScope,
    private val onPaymentRequested: (
        address: LightningAddress,
        context: PaymentContactContext,
        amountMsats: Long?,
        comment: String?
    ) -> Unit,
    private val onError: (PaymentUiError) -> Unit,
    private val clock: () -> Long = ::platformCurrentTimeMillis
) {
    private val mutableState = MutableStateFlow(PaymentContactsUiState())
    val state: StateFlow<PaymentContactsUiState> = mutableState.asStateFlow()

    private var contacts: List<Contact> = emptyList()
    private var shortcuts: List<PaymentShortcut> = emptyList()
    private var preferences = ContactPreferences()
    private val pendingContexts = mutableMapOf<String, PaymentContactContext>()
    private var pendingSaveContact: PendingSaveContact? = null

    init {
        scope.launch {
            repository.contacts.collectLatest {
                contacts = it
                refreshDisplay()
            }
        }
        scope.launch {
            repository.shortcuts.collectLatest {
                shortcuts = it
                refreshDisplay()
            }
        }
        scope.launch {
            repository.preferences.collectLatest { preferences = it }
        }
    }

    fun contextFor(address: LightningAddress, allowSavePrompt: Boolean): PaymentContactContext {
        val existing = contacts.firstOrNull { it.address.sameAddressAs(address) }
        return PaymentContactContext(
            address = address,
            shortcutId = null,
            displayName = existing?.displayName ?: address.username,
            allowSavePrompt = allowSavePrompt
        )
    }

    fun open() {
        mutableState.value =
            mutableState.value.copy(
                isOpen = true,
                selectedTab = PaymentSheetTab.Shortcuts,
                selectedRoles = emptySet()
            )
        refreshDisplay()
    }

    fun dismiss() {
        mutableState.value = mutableState.value.copy(isOpen = false)
    }

    fun selectTab(tab: PaymentSheetTab) {
        mutableState.value = mutableState.value.copy(selectedTab = tab)
        refreshDisplay()
    }

    fun selectRole(role: ContactRole?) {
        val current = mutableState.value
        mutableState.value =
            current.copy(selectedRoles = current.selectedRoles.toggle(role))
        refreshDisplay()
    }

    fun selectContact(id: String) {
        val contact = contacts.firstOrNull { it.id == id } ?: return
        dismiss()
        onPaymentRequested(
            contact.address,
            contextFor(contact.address, allowSavePrompt = false),
            null,
            null
        )
    }

    fun selectShortcut(id: String) {
        val shortcut = shortcuts.firstOrNull { it.id == id } ?: return
        dismiss()
        scope.launch {
            val amountMsats = currencyManager.convertShortcutAmountToMsats(shortcut.amount)
            if (amountMsats == null || amountMsats <= 0L) {
                onError(PaymentUiError.InvalidInvoice("Shortcut amount could not be converted"))
                return@launch
            }
            onPaymentRequested(
                shortcut.address,
                PaymentContactContext(
                    address = shortcut.address,
                    shortcutId = shortcut.id,
                    displayName = shortcut.displayName(),
                    allowSavePrompt = false,
                    comment = shortcut.comment
                ),
                roundToFullSatoshis(amountMsats),
                shortcut.comment
            )
        }
    }

    fun bindPendingPayment(id: String, context: PaymentContactContext?) {
        if (context != null) pendingContexts[id] = context
    }

    fun paymentFailed(id: String) {
        pendingContexts.remove(id)
    }

    fun paymentSucceeded(id: String, amountMsats: Long) {
        if (amountMsats <= 0L) {
            pendingContexts.remove(id)
            return
        }
        val context = pendingContexts.remove(id) ?: return
        val paidAtMs = clock()
        val paymentRecord =
            ContactPaymentRecord(
                address = context.address,
                amountMsats = amountMsats,
                comment = context.comment,
                paidAtMs = paidAtMs
            )
        context.shortcutId?.let { shortcutId ->
            scope.launch { repository.recordShortcutPayment(shortcutId, paidAtMs) }
        }
        if (contacts.any { it.address.sameAddressAs(context.address) }) {
            scope.launch { repository.recordPayment(paymentRecord) }
            return
        }
        if (!context.allowSavePrompt || !preferences.askToSaveNewContacts) return

        pendingSaveContact =
            PendingSaveContact(
                address = context.address,
                amountMsats = amountMsats,
                comment = context.comment,
                paidAtMs = paidAtMs
            )
        mutableState.value =
            mutableState.value.copy(
                savePrompt =
                    ContactSavePromptUiState(
                        address = context.address.full,
                        alias = context.displayName,
                        selectedRoles = emptySet()
                    )
            )
    }

    fun updateSavePromptAlias(alias: String) {
        val prompt = mutableState.value.savePrompt ?: return
        mutableState.value =
            mutableState.value.copy(savePrompt = prompt.copy(alias = alias, error = null))
    }

    fun updateSavePromptRole(role: ContactRole?) {
        val prompt = mutableState.value.savePrompt ?: return
        mutableState.value =
            mutableState.value.copy(
                savePrompt =
                    prompt.copy(selectedRoles = prompt.selectedRoles.toggle(role))
            )
    }

    fun savePrompt() {
        val prompt = mutableState.value.savePrompt ?: return
        val pending = pendingSaveContact ?: return
        scope.launch {
            val contact =
                repository.saveContact(
                    address = pending.address,
                    alias = prompt.alias,
                    roles = prompt.selectedRoles
                )
            repository.recordPayment(
                ContactPaymentRecord(
                    address = contact.address,
                    amountMsats = pending.amountMsats,
                    comment = pending.comment,
                    paidAtMs = pending.paidAtMs
                )
            )
            pendingSaveContact = null
            mutableState.value = mutableState.value.copy(savePrompt = null)
            refreshDisplay()
        }
    }

    fun dismissSavePrompt() {
        pendingSaveContact = null
        mutableState.value = mutableState.value.copy(savePrompt = null)
    }

    private fun refreshDisplay() {
        val current = mutableState.value
        val filteredContacts =
            contacts
                .asSequence()
                .filter { contact ->
                    current.selectedRoles.isEmpty() ||
                        contact.roles.containsAll(current.selectedRoles)
                }.sortedWith(
                    compareByDescending<Contact> { ContactRole.Favorite in it.roles }
                        .thenByDescending { it.stats.paymentCount }
                        .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
                        .thenBy { it.displayName.lowercase() }
                        .thenBy { it.address.full.lowercase() }
                ).map { contact -> contact.toListItem() }
                .toList()
        val displayedShortcuts =
            shortcuts
                .sortedWith(
                    compareByDescending<PaymentShortcut> { it.stats.paymentCount }
                        .thenByDescending { it.stats.lastPaidAtMs ?: 0L }
                        .thenBy { it.title.lowercase() }
                ).map { shortcut -> shortcut.toListItem() }
        mutableState.value =
            current.copy(
                hasContacts = contacts.isNotEmpty(),
                contactCount = contacts.size,
                contacts = filteredContacts,
                shortcuts = displayedShortcuts
            )
    }

    private fun Contact.toListItem(): PaymentContactListItem = PaymentContactListItem(
        id = id,
        displayName = displayName,
        address = address.full,
        roles = roles,
        paymentCount = stats.paymentCount,
        lastPaidAtMs = stats.lastPaidAtMs
    )

    private fun PaymentShortcut.toListItem(): PaymentShortcutListItem = PaymentShortcutListItem(
        id = id,
        title = title,
        amountLabel = amount.displayLabel(),
        recipientSummary = displayName(),
        commentSummary = comment,
        paymentCount = stats.paymentCount,
        lastPaidAtMs = stats.lastPaidAtMs
    )

    private fun PaymentShortcut.displayName(): String =
        contacts.firstOrNull { it.id == contactId }?.displayName ?: address.username

    private fun ShortcutAmount.displayLabel(): String {
        val info = CurrencyCatalog.infoFor(normalizedCurrencyCode)
        val unit = if (info.code == CurrencyCatalog.DEFAULT_CODE) "sats" else info.code
        return "${minor.formatMinorAmount(info.fractionDigits)} $unit"
    }
}

internal data class PaymentContactContext(
    val address: LightningAddress,
    val shortcutId: String?,
    val displayName: String,
    val allowSavePrompt: Boolean,
    val comment: String? = null
)

private data class PendingSaveContact(
    val address: LightningAddress,
    val amountMsats: Long,
    val comment: String?,
    val paidAtMs: Long
)

private fun Set<ContactRole>.toggle(role: ContactRole?): Set<ContactRole> = when (role) {
    null -> emptySet()
    else -> if (role in this) this - role else this + role
}

private fun LightningAddress.sameAddressAs(other: LightningAddress): Boolean =
    full.equals(other.full, ignoreCase = true)

private fun Long.formatMinorAmount(fractionDigits: Int): String {
    if (fractionDigits <= 0) return toString()
    var factor = 1L
    repeat(fractionDigits) { factor *= 10L }
    val whole = this / factor
    val fraction = (this % factor).toString().padStart(fractionDigits, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}
