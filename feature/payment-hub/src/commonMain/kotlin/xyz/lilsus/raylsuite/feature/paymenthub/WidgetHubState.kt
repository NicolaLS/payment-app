package xyz.lilsus.raylsuite.feature.paymenthub

import xyz.lilsus.raylsuite.core.model.StoredAmount

enum class HubWidgetKind { Contacts, Shortcut, Favorites, Recents, Metric }

enum class HubWidgetScreen { Hub, Gallery, Variants, Configure }

data class HubWidgetVariant(
    val id: String,
    val columns: Int,
    val rows: Int,
    val capacity: Int,
    val title: String? = null
)

/** Local definitions use platform-localized copy; remote definitions supply localized copy. */
data class HubWidgetDefinition(
    val id: String,
    val kind: HubWidgetKind,
    val variants: List<HubWidgetVariant>,
    val title: String? = null,
    val description: String? = null,
    val fields: List<HubWidgetField> = emptyList()
)

data class HubWidgetField(
    val key: String,
    val type: String,
    val label: String,
    val required: Boolean,
    val options: List<HubWidgetChoice> = emptyList(),
    val maxLength: Int? = null
)

data class HubWidgetChoice(val id: String, val label: String)

data class HubWidgetPerson(
    val actionId: String,
    val contactId: String,
    val title: String,
    val address: String,
    val amount: StoredAmount? = null
)

data class HubWidgetMetric(
    val value: String,
    val unit: String,
    val label: String,
    val asOf: String?
)

data class HubWidgetTile(
    val id: String,
    val definitionId: String,
    val kind: HubWidgetKind,
    val variant: HubWidgetVariant,
    val title: String?,
    val people: List<HubWidgetPerson> = emptyList(),
    val metric: HubWidgetMetric? = null,
    val loading: Boolean = false,
    val unavailable: Boolean = false
)

data class HubWidgetEditor(
    val definitionId: String,
    val kind: HubWidgetKind,
    val variantId: String,
    val existingWidgetId: String? = null,
    val contactIds: List<String> = emptyList(),
    val title: String = "",
    val amountInput: String = "",
    val currencyCode: String = "USD",
    val comment: String = "",
    val configuration: Map<String, String> = emptyMap()
)

enum class HubWidgetError {
    ContactNameRequired,
    InvalidAddress,
    SelectContacts,
    TooManyContacts,
    InvalidAmount,
    RequiredConfiguration,
    Unavailable,
    SaveFailed
}

data class WidgetHubState(
    val screen: HubWidgetScreen = HubWidgetScreen.Hub,
    val widgets: List<HubWidgetTile> = emptyList(),
    val gallery: List<HubWidgetDefinition> = emptyList(),
    val contacts: List<HubContact> = emptyList(),
    val editor: HubWidgetEditor? = null,
    val query: String = "",
    val arranging: Boolean = false,
    val catalogLoading: Boolean = false,
    val catalogUnavailable: Boolean = false,
    val busy: Boolean = false,
    val contactSavedSerial: Int = 0,
    val error: HubWidgetError? = null
) {
    val selectedDefinition: HubWidgetDefinition?
        get() = gallery.firstOrNull { it.id == editor?.definitionId }

    val selectedVariant: HubWidgetVariant?
        get() = selectedDefinition?.variants?.firstOrNull { it.id == editor?.variantId }
}

object LocalHubWidgets {
    val Single = HubWidgetVariant("single", 1, 1, 1)
    val Row = HubWidgetVariant("row", 2, 1, 4)
    val Card = HubWidgetVariant("card", 2, 2, 6)
    val definitions = listOf(
        HubWidgetDefinition("local.contacts", HubWidgetKind.Contacts, listOf(Single, Row, Card)),
        HubWidgetDefinition("local.shortcut", HubWidgetKind.Shortcut, listOf(Single)),
        HubWidgetDefinition("local.favorites", HubWidgetKind.Favorites, listOf(Row, Card)),
        HubWidgetDefinition("local.recents", HubWidgetKind.Recents, listOf(Row, Card))
    )
}
