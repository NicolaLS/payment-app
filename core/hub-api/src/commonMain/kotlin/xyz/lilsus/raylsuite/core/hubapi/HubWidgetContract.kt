package xyz.lilsus.raylsuite.core.hubapi

import kotlinx.serialization.Serializable

/** Bounded native widget vocabulary. A contract is supported only with its native handlers. */
object HubWidgetProtocol {
    const val VERSION = 1
    const val METRIC_CONTRACT = "metric/v1"
    const val CATALOG_PATH = "/hub/v1/widgets"
    val supportedContracts: Set<String> = setOf(METRIC_CONTRACT)
}

object HubRequestHeaders {
    const val APP = "X-Rayl-App"
    const val VERSION = "X-Rayl-Version"
    const val BUILD = "X-Rayl-Build"
    const val PLATFORM = "X-Rayl-Platform"
    const val CONTRACTS = "X-Rayl-Hub-Contracts"
}

/** App metadata accompanies every request; it is not an account or device identity. */
data class HubClientMetadata(
    val app: String,
    val version: String,
    val build: String,
    val platform: String,
    val supportedContracts: Set<String> = HubWidgetProtocol.supportedContracts
)

@Serializable
data class HubWidgetCatalog(
    val protocolVersion: Int = HubWidgetProtocol.VERSION,
    val widgets: List<HubWidgetDescriptor> = emptyList()
)

/** A catalogue definition, never a user's saved widget or its arrangement. */
@Serializable
data class HubWidgetDescriptor(
    val id: String,
    val revision: String,
    val contract: String,
    val title: String,
    val description: String? = null,
    val variants: List<HubWidgetVariant>,
    val fields: List<HubWidgetField> = emptyList(),
    val actions: List<HubWidgetAction> = emptyList()
)

@Serializable
data class HubWidgetVariant(
    val id: String,
    val title: String,
    val template: String,
    val size: String = "small"
)

/** Field types are data for compiled native controls, not executable validation rules. */
@Serializable
data class HubWidgetField(
    val id: String,
    val label: String,
    val type: String,
    val required: Boolean = false,
    val maxLength: Int? = null,
    val options: List<HubWidgetChoice> = emptyList()
)

@Serializable
data class HubWidgetChoice(val id: String, val label: String)

@Serializable
data class HubWidgetAction(val id: String, val title: String, val kind: String)

@Serializable
data class HubWidgetContentRequest(
    val variantId: String,
    val configuration: Map<String, String> = emptyMap()
)

/** Exact decimal display text; informational content, never a payment amount or quote. */
@Serializable
data class HubMetricContent(
    val value: String,
    val unit: String,
    val label: String,
    val asOf: String,
    val refreshAfterSeconds: Int? = null
)

@Serializable
data class HubWidgetContent(
    val protocolVersion: Int = HubWidgetProtocol.VERSION,
    val widgetId: String,
    val variantId: String,
    val contract: String,
    val metric: HubMetricContent
)

@Serializable
data class HubApiError(val code: String)
