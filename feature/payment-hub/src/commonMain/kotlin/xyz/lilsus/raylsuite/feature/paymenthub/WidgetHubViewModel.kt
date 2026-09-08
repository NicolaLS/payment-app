package xyz.lilsus.raylsuite.feature.paymenthub

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.core.hubapi.HubServiceContent
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetDescriptor
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetProtocol
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.model.StoredAmount
import xyz.lilsus.raylsuite.feature.paymenthub.create.cleanAmountInput
import xyz.lilsus.raylsuite.feature.paymenthub.create.formatMinorAmount
import xyz.lilsus.raylsuite.feature.paymenthub.create.hasFractionForWholeCurrency
import xyz.lilsus.raylsuite.feature.paymenthub.create.parseMinorAmount
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubIntent
import xyz.lilsus.raylsuite.integration.hub.HubWidgetCatalogResult
import xyz.lilsus.raylsuite.integration.hub.HubWidgetContentResult
import xyz.lilsus.raylsuite.integration.hub.HubWidgetUnavailableReason
import xyz.lilsus.raylsuite.integration.hub.KtorHubWidgetCatalogClient

/** Shared widget presentation state. Native platforms own sheets, controls, gestures and copy. */
class WidgetHubViewModel(
    private val repository: PaymentHubRepository,
    private val host: PaymentHubController,
    private val defaultCurrencyCode: () -> String,
    private val locale: () -> String = { "en" },
    private val catalog: KtorHubWidgetCatalogClient? = null,
    orderStore: HubServiceOrderStore? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState =
        MutableStateFlow(WidgetHubState(gallery = LocalHubWidgets.definitions))
    val state = mutableState.asStateFlow()
    private var remoteDefinitions = emptyList<HubWidgetDescriptor>()
    private val remoteContent = mutableMapOf<String, RemoteContent>()
    private var catalogJob: Job? = null
    private var contentJob: Job? = null
    private var contentRefreshJob: Job? = null
    private var remoteInstances = emptyList<HubWidget>()
    private var active = true
    private val purchases = HubServicePurchaseController(catalog, orderStore, host, locale, scope)

    init {
        scope.launch {
            purchases.state.collect { session ->
                mutableState.update {
                    it.copy(
                        purchase = session.purchase,
                        hasServiceOrder = session.hasOrder,
                        servicePaymentReady = session.paymentReady
                    )
                }
            }
        }
        scope.launch {
            repository.hub.collect { hub ->
                val editor = mutableState.value.editor
                val editedWidgetRemoved = editor?.existingWidgetId?.let { hub.widget(it) == null }
                    ?: false
                mutableState.update { current ->
                    current.copy(
                        contacts = hub.contacts,
                        screen = if (editedWidgetRemoved) HubWidgetScreen.Hub else current.screen,
                        editor = if (editedWidgetRemoved) {
                            null
                        } else {
                            editor?.copy(
                                contactIds = editor.contactIds.filter {
                                    hub.contact(it) !=
                                        null
                                }
                            )
                        }
                    )
                }
                val updated = hub.widgets.filter { it.kind.isRemote }
                if (updated != remoteInstances) {
                    val previous = remoteInstances.associateBy { it.id }
                    updated.forEach { widget ->
                        val old = previous[widget.id]
                        if (old?.definitionId != widget.definitionId ||
                            old.variant.id != widget.variant.id ||
                            old.configuration != widget.configuration
                        ) {
                            remoteContent.remove(widget.id)
                        }
                    }
                    remoteInstances = updated
                    remoteContent.keys.retainAll(updated.mapTo(mutableSetOf()) { it.id })
                    refreshContent()
                }
                rebuildTiles()
            }
        }
        refreshCatalog()
    }

    fun openGallery() {
        mutableState.update {
            it.copy(
                screen = HubWidgetScreen.Gallery,
                editor = null,
                query = "",
                error = null,
                arranging = false
            )
        }
    }

    fun close() {
        mutableState.update {
            it.copy(screen = HubWidgetScreen.Hub, editor = null, query = "", error = null)
        }
    }

    fun back(): Boolean {
        when (mutableState.value.screen) {
            HubWidgetScreen.Hub -> {
                if (!mutableState.value.arranging) return false
                setArranging(false)
            }

            HubWidgetScreen.Gallery -> close()

            HubWidgetScreen.Variants -> {
                if (mutableState.value.editor?.existingWidgetId != null) close() else openGallery()
            }

            HubWidgetScreen.Configure -> mutableState.update {
                it.copy(screen = HubWidgetScreen.Variants, error = null)
            }
        }
        return true
    }

    fun selectDefinition(id: String) {
        val definition = mutableState.value.gallery.firstOrNull { it.id == id } ?: return
        mutableState.update {
            it.copy(
                screen = HubWidgetScreen.Variants,
                query = "",
                error = null,
                editor = HubWidgetEditor(
                    id,
                    definition.kind,
                    definition.variants.first().id,
                    currencyCode = defaultCurrencyCode()
                )
            )
        }
    }

    fun selectVariant(id: String) {
        val definition = mutableState.value.selectedDefinition ?: return
        if (definition.variants.none { it.id == id }) return
        edit { it.copy(variantId = id) }
    }

    fun configureSelected() {
        if (mutableState.value.editor == null) return
        mutableState.update {
            it.copy(screen = HubWidgetScreen.Configure, query = "", error = null)
        }
    }

    fun updateQuery(value: String) {
        mutableState.update { it.copy(query = value) }
    }
    fun updateTitle(value: String) = edit { it.copy(title = value.take(120)) }
    fun updateComment(value: String) = edit { it.copy(comment = value.take(500)) }

    fun toggleContact(id: String) {
        if (repository.hub.value.contact(id) == null) return
        edit { editor ->
            if (editor.kind == HubWidgetKind.Shortcut) {
                editor.copy(contactIds = listOf(id))
            } else {
                editor.copy(
                    contactIds = if (id in
                        editor.contactIds
                    ) {
                        editor.contactIds - id
                    } else {
                        editor.contactIds + id
                    }
                )
            }
        }
    }

    fun moveContact(id: String, delta: Int) {
        edit { editor ->
            val from = editor.contactIds.indexOf(id)
            if (from < 0) {
                editor
            } else {
                val reordered = editor.contactIds.toMutableList()
                reordered.removeAt(from)
                reordered.add((from + delta).coerceIn(0, reordered.size), id)
                editor.copy(contactIds = reordered)
            }
        }
    }

    fun updateAmount(value: String) = edit {
        val digits = CurrencyCatalog.infoFor(it.currencyCode).fractionDigits
        it.copy(
            amountInput = if (value.hasFractionForWholeCurrency(
                    digits
                )
            ) {
                value
            } else {
                value.cleanAmountInput(digits)
            }
        )
    }

    fun selectCurrency(code: String) {
        if (code !in CurrencyCatalog.supportedCodes) return
        edit { it.copy(currencyCode = code, amountInput = "") }
    }

    fun updateConfiguration(key: String, value: String) {
        val field =
            mutableState.value.selectedDefinition?.fields?.firstOrNull { it.key == key } ?: return
        edit {
            it.copy(
                configuration =
                    it.configuration + (key to value.take(field.maxLength ?: 256))
            )
        }
    }

    fun addContact(title: String, address: String) {
        if (mutableState.value.busy) return
        val name = title.trim()
        if (name.isEmpty()) return fail(HubWidgetError.ContactNameRequired)
        val parsed = LightningAddress.parse(address) ?: return fail(HubWidgetError.InvalidAddress)
        val existing = repository.hub.value.contacts.firstOrNull {
            it.address.isSameAddressAs(parsed)
        }
        val editor = mutableState.value.editor
        val capacity = mutableState.value.selectedVariant?.capacity ?: 1
        if (editor?.kind == HubWidgetKind.Contacts && existing?.id !in editor.contactIds &&
            editor.contactIds.size >= capacity
        ) {
            return fail(HubWidgetError.TooManyContacts)
        }
        mutate {
            val contact = existing ?: repository.saveContact(parsed, name)
            mutableState.update { current ->
                val currentEditor = current.editor
                current.copy(
                    contactSavedSerial = current.contactSavedSerial + 1,
                    editor = currentEditor?.copy(
                        contactIds = if (currentEditor.kind ==
                            HubWidgetKind.Shortcut
                        ) {
                            listOf(contact.id)
                        } else {
                            (currentEditor.contactIds + contact.id).distinct()
                        }
                    )
                )
            }
        }
    }

    fun deleteContact(id: String) = mutate { repository.deleteContact(id) }

    fun saveWidget() {
        val snapshot = mutableState.value
        val editor = snapshot.editor ?: return
        val definition = snapshot.selectedDefinition ?: return fail(HubWidgetError.Unavailable)
        val variant = snapshot.selectedVariant ?: return fail(HubWidgetError.Unavailable)
        if (editor.kind == HubWidgetKind.Contacts || editor.kind == HubWidgetKind.Shortcut) {
            if (editor.contactIds.isEmpty()) return fail(HubWidgetError.SelectContacts)
            if (editor.contactIds.size >
                variant.capacity
            ) {
                return fail(HubWidgetError.TooManyContacts)
            }
        }
        val amount = if (editor.kind == HubWidgetKind.Shortcut) {
            val digits = CurrencyCatalog.infoFor(editor.currencyCode).fractionDigits
            if (editor.amountInput.hasFractionForWholeCurrency(
                    digits
                )
            ) {
                return fail(HubWidgetError.InvalidAmount)
            }
            val minor = editor.amountInput.parseMinorAmount(digits)
            if (minor == null || minor <= 0) return fail(HubWidgetError.InvalidAmount)
            StoredAmount(minor, editor.currencyCode)
        } else {
            null
        }
        if (definition.fields.any { field ->
                val value = editor.configuration[field.key].orEmpty().trim()
                (field.required && value.isEmpty()) ||
                    (
                        value.isNotEmpty() && field.type == "choice" &&
                            field.options.none { it.id == value }
                        )
            }
        ) {
            return fail(HubWidgetError.RequiredConfiguration)
        }
        val draft = HubWidgetDraft(
            editor.definitionId,
            editor.kind,
            variant,
            editor.title,
            editor.contactIds,
            amount,
            editor.comment,
            editor.configuration.filterKeys { key -> definition.fields.any { it.key == key } }
        )
        mutate {
            if (repository.saveWidget(draft, editor.existingWidgetId) == null) {
                fail(HubWidgetError.SaveFailed)
            } else {
                close()
            }
        }
    }

    fun editWidget(id: String) {
        val hub = repository.hub.value
        val widget = hub.widget(id) ?: return
        val definition = mutableState.value.gallery.firstOrNull { it.id == widget.definitionId }
        if (definition == null) {
            fail(HubWidgetError.Unavailable)
            return
        }
        val variant = definition.variants.firstOrNull { it.id == widget.variant.id }
            ?: definition.variants.first()
        val target = widget.targetId?.let(hub::target)
        val amount = (target?.amountRule as? DirectTargetAmountRule.Preset)?.amount
        mutableState.update {
            it.copy(
                screen = HubWidgetScreen.Configure,
                query = "",
                error = null,
                editor = HubWidgetEditor(
                    widget.definitionId, widget.kind, variant.id, widget.id,
                    contactIds = target?.let { listOf(it.contactId) } ?: widget.contactIds,
                    title = widget.title.orEmpty(),
                    amountInput = amount?.minor?.formatMinorAmount(
                        CurrencyCatalog.infoFor(amount.normalizedCurrencyCode).fractionDigits
                    ).orEmpty(),
                    currencyCode = amount?.normalizedCurrencyCode ?: defaultCurrencyCode(),
                    comment = target?.comment.orEmpty(), configuration = widget.configuration
                )
            )
        }
    }

    fun removeWidget(id: String) = mutate {
        repository.deleteWidget(id)
        if (mutableState.value.editor?.existingWidgetId == id) close()
    }
    fun moveWidget(id: String, targetId: String) {
        val index = repository.hub.value.widgets.indexOfFirst { it.id == targetId }
        if (index >= 0) mutate { repository.moveWidget(id, index) }
    }
    fun moveWidgetBy(id: String, delta: Int) {
        val index = repository.hub.value.widgets.indexOfFirst { it.id == id }
        if (index >= 0) mutate { repository.moveWidget(id, index + delta) }
    }
    fun setArranging(value: Boolean) {
        mutableState.update { it.copy(arranging = value, error = null) }
    }
    fun pay(actionId: String) {
        if (!mutableState.value.arranging && actionId.isNotBlank()) {
            host.dispatch(PaymentHubIntent.SelectItem(HubItemId(actionId)))
        }
    }

    fun refreshCatalog() {
        if (!active) return
        val client = catalog ?: return
        catalogJob?.cancel()
        catalogJob = scope.launch {
            mutableState.update { it.copy(catalogLoading = true) }
            val result = client.fetchCatalog(locale())
            when (result) {
                is HubWidgetCatalogResult.Available -> {
                    remoteDefinitions = result.widgets
                    mutableState.update {
                        it.copy(
                            gallery =
                                LocalHubWidgets.definitions + result.widgets.map(::definition),
                            catalogUnavailable = false,
                            catalogLoading = false
                        )
                    }
                }

                is HubWidgetCatalogResult.Unavailable -> mutableState.update {
                    it.copy(
                        catalogLoading = false,
                        catalogUnavailable =
                            result.reason != HubWidgetUnavailableReason.NotConfigured
                    )
                }
            }
            rebuildTiles()
            refreshContent()
        }
    }

    fun refreshContent() {
        if (!active) return
        val client = catalog ?: return
        contentJob?.cancel()
        contentRefreshJob?.cancel()
        contentJob = scope.launch {
            val widgets = repository.hub.value.widgets.filter { it.kind.isRemote }
            var nextRefresh = 300
            for (widget in widgets) {
                if (remoteDefinitions.none { descriptor ->
                        descriptor.id == widget.definitionId &&
                            descriptor.variants.any { it.id == widget.variant.id }
                    }
                ) {
                    continue
                }
                remoteContent[widget.id] =
                    (remoteContent[widget.id] ?: RemoteContent()).copy(loading = true)
                rebuildTiles()
                val response = client.fetchContent(
                    widget.definitionId,
                    widget.variant.id,
                    widget.configuration,
                    locale()
                )
                if (repository.hub.value.widget(widget.id) != widget) continue
                remoteContent[widget.id] = when (response) {
                    is HubWidgetContentResult.Available -> {
                        nextRefresh =
                            minOf(nextRefresh, response.content.metric?.refreshAfterSeconds ?: 300)
                        RemoteContent(
                            metric = response.content.metric?.let { metric ->
                                HubWidgetMetric(
                                    metric.value,
                                    metric.unit,
                                    metric.label,
                                    metric.asOf
                                )
                            },
                            service = response.content.service
                        )
                    }

                    is HubWidgetContentResult.Unavailable -> RemoteContent(
                        metric = remoteContent[widget.id]?.metric,
                        service = remoteContent[widget.id]?.service,
                        unavailable = true
                    )
                }
                rebuildTiles()
            }
            if (widgets.isNotEmpty()) {
                contentRefreshJob = scope.launch {
                    delay(nextRefresh.coerceAtLeast(30) * 1000L)
                    refreshContent()
                }
            }
        }
    }

    /** Native visibility and app lifecycle own remote refresh activity. */
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        purchases.setActive(value)
        if (value) {
            refreshCatalog()
        } else {
            catalogJob?.cancel()
            contentJob?.cancel()
            contentRefreshJob?.cancel()
            remoteContent.entries.forEach { it.setValue(it.value.copy(loading = false)) }
            mutableState.update { it.copy(catalogLoading = false) }
            rebuildTiles()
        }
    }

    fun openService(widgetId: String, offerId: String? = null) {
        val widget = repository.hub.value.widget(widgetId) ?: return
        val content = remoteContent[widgetId]?.service ?: return fail(HubWidgetError.Unavailable)
        if (widget.kind != HubWidgetKind.Service) return
        purchases.open(widget, content, offerId)
    }
    fun closePurchase() = purchases.close()
    fun updateServicePhone(value: String) = purchases.updatePhone(value)
    fun selectServiceOffer(id: String) = purchases.selectOffer(id)
    fun updateServiceAmount(value: String) = purchases.updateAmount(value)
    fun prepareServiceOrder() = purchases.prepare()
    fun payServiceOrder() = purchases.pay()
    fun refreshServiceOrder() = purchases.refresh()
    fun openPendingServiceOrder() = purchases.openSaved()
    fun completeServicePaymentHandoff() = purchases.completePaymentHandoff()

    fun clear() = scope.cancel()

    private fun edit(transform: (HubWidgetEditor) -> HubWidgetEditor) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(editor = it.editor?.let(transform), error = null) }
    }

    private fun fail(error: HubWidgetError) {
        mutableState.update { it.copy(error = error) }
    }

    private fun mutate(block: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        scope.launch {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                fail(HubWidgetError.SaveFailed)
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private fun definition(remote: HubWidgetDescriptor) = HubWidgetDefinition(
        id = remote.id,
        kind = if (remote.contract ==
            HubWidgetProtocol.SERVICE_CONTRACT
        ) {
            HubWidgetKind.Service
        } else {
            HubWidgetKind.Metric
        },
        title = remote.title,
        description = remote.description,
        variants = remote.variants.map { variant ->
            val size = when (variant.size) {
                "wide" -> LocalHubWidgets.Row
                "large" -> LocalHubWidgets.Card
                else -> LocalHubWidgets.Single
            }
            size.copy(id = variant.id, title = variant.title, template = variant.template)
        },
        fields = remote.fields.map { field ->
            HubWidgetField(
                field.id,
                field.type,
                field.label,
                field.required,
                field.options.map { HubWidgetChoice(it.id, it.label) },
                field.maxLength
            )
        }
    )

    private fun rebuildTiles() {
        val hub = repository.hub.value
        val paid = hub.targets.filter { it.stats.successfulPaymentCount > 0 }
        val favorites = paid.sortedWith(
            compareByDescending<DirectPaymentTarget> {
                it.stats.successfulPaymentCount
            }
                .thenByDescending { it.stats.lastSuccessfulPaymentAtMs ?: 0 }.thenBy { it.id.value }
        )
        val recents = paid.sortedWith(
            compareByDescending<DirectPaymentTarget> {
                it.stats.lastSuccessfulPaymentAtMs
                    ?: 0
            }
                .thenBy { it.id.value }
        )
        val tiles = hub.widgets.map { widget ->
            val targets = when (widget.kind) {
                HubWidgetKind.Contacts -> widget.contactIds.mapNotNull(hub::contactTarget)
                HubWidgetKind.Shortcut -> listOfNotNull(widget.targetId?.let(hub::target))
                HubWidgetKind.Favorites -> favorites.take(widget.variant.capacity)
                HubWidgetKind.Recents -> recents.take(widget.variant.capacity)
                HubWidgetKind.Metric, HubWidgetKind.Service -> emptyList()
            }
            val remote = remoteContent[widget.id]
            val available = remoteDefinitions.any { descriptor ->
                descriptor.id ==
                    widget.definitionId &&
                    descriptor.variants.any { it.id == widget.variant.id }
            }
            HubWidgetTile(
                widget.id, widget.definitionId, widget.kind, widget.variant,
                widget.title
                    ?: mutableState.value.gallery.firstOrNull {
                        it.id == widget.definitionId
                    }?.title,
                people = targets.mapNotNull { target ->
                    hub.contact(target.contactId)?.let { contact ->
                        HubWidgetPerson(
                            target.id.value,
                            contact.id,
                            if (target.amountRule is DirectTargetAmountRule.Preset) {
                                target.title
                            } else {
                                contact.title
                            },
                            contact.address.full,
                            (target.amountRule as? DirectTargetAmountRule.Preset)?.amount
                        )
                    }
                },
                metric = remote?.metric, loading = available && remote?.loading == true,
                unavailable = widget.kind.isRemote && (remote?.unavailable == true || !available),
                service = remote?.service,
                servicePhone = widget.configuration["phone"].orEmpty()
            )
        }
        mutableState.update { it.copy(widgets = tiles) }
    }

    private data class RemoteContent(
        val metric: HubWidgetMetric? = null,
        val service: HubServiceContent? = null,
        val loading: Boolean = false,
        val unavailable: Boolean = false
    )
}
