package xyz.lilsus.raylsuite.integration.hub

import xyz.lilsus.raylsuite.core.hubapi.HubMetricContent
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetDescriptor

sealed interface HubWidgetCatalogResult {
    data class Available(val widgets: List<HubWidgetDescriptor>, val skippedCount: Int = 0) :
        HubWidgetCatalogResult

    data class Unavailable(val reason: HubWidgetUnavailableReason) : HubWidgetCatalogResult
}

sealed interface HubWidgetContentResult {
    data class Available(val content: HubMetricContent) : HubWidgetContentResult

    data class Unavailable(val reason: HubWidgetUnavailableReason) : HubWidgetContentResult
}

enum class HubWidgetUnavailableReason {
    NotConfigured,
    Offline,
    HttpError,
    NotFound,
    InvalidResponse,
    UnsupportedProtocol
}
