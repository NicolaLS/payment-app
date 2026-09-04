package xyz.lilsus.raylsuite.feature.paymenthub.create

import androidx.compose.runtime.Immutable

/**
 * A payable service a user could put on the hub. The catalogue is a placeholder today: the entries
 * exist so the picker has its real shape, and choosing one only says the service is on its way.
 */
@Immutable
data class HubService(
    val id: String,
    val name: String,
    val mark: String,
    val kind: HubServiceKind,
    /** How many packages the service offers, shown as part of its subtitle. */
    val optionCount: Int
)

/** Service categories. Names are proper nouns; only the kind is translated. */
enum class HubServiceKind {
    Mobile,
    EsimData,
    Other
}

object HubServiceCatalog {
    val services: List<HubService> =
        listOf(
            HubService(
                id = "claro",
                name = "Claro",
                mark = "CL",
                kind = HubServiceKind.Mobile,
                optionCount = 4
            ),
            HubService(
                id = "tigo",
                name = "Tigo",
                mark = "TG",
                kind = HubServiceKind.Mobile,
                optionCount = 3
            ),
            HubService(
                id = "silent-link",
                name = "Silent Link",
                mark = "SL",
                kind = HubServiceKind.EsimData,
                optionCount = 2
            ),
            HubService(
                id = "bills",
                name = "Bills",
                mark = "BI",
                kind = HubServiceKind.Other,
                optionCount = 1
            ),
            HubService(
                id = "dummy-1",
                name = "Dummy 1",
                mark = "D1",
                kind = HubServiceKind.Other,
                optionCount = 1
            ),
            HubService(
                id = "dummy-2",
                name = "Dummy 2",
                mark = "D2",
                kind = HubServiceKind.Other,
                optionCount = 1
            )
        )

    fun service(id: String): HubService? = services.firstOrNull { it.id == id }
}
