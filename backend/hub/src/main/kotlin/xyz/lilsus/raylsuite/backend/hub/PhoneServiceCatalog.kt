package xyz.lilsus.raylsuite.backend.hub

import xyz.lilsus.raylsuite.core.hubapi.HubWidgetAction
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetDescriptor
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetField
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetProtocol
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetVariant

/** The app's compiled phone-service presentation vocabulary, independent of supplier wire formats. */
internal val SupplierCatalog.widget: HubWidgetDescriptor
    get() = widget("en")

internal fun SupplierCatalog.widget(locale: String): HubWidgetDescriptor {
    val labels = phoneServiceLabels(locale)
    return HubWidgetDescriptor(
        id = serviceId,
        revision = content.revision,
        contract = HubWidgetProtocol.SERVICE_CONTRACT,
        title = content.title,
        description = labels.description,
        variants = buildList {
            if (content.offers.any { it.kind == "topup" }) {
                add(HubWidgetVariant("topup", labels.topup, "service-topup", "small"))
            }
            if (content.offers.any { it.kind == "package" }) {
                add(
                    HubWidgetVariant("packages-row", labels.packagesRow, "service-packages", "wide")
                )
                add(
                    HubWidgetVariant(
                        "packages-card",
                        labels.packagesCard,
                        "service-packages",
                        "large"
                    )
                )
            }
        },
        fields = listOf(
            HubWidgetField("phone", labels.phone, "phone", required = true, maxLength = 16)
        ),
        actions = listOf(HubWidgetAction("purchase", labels.continueAction, "purchase"))
    )
}

internal fun SupplierCatalog.offerKind(variantId: String): String? = when (
    widget.variants.firstOrNull { it.id == variantId }?.template
) {
    "service-topup" -> "topup"
    "service-packages" -> "package"
    else -> null
}

private data class PhoneServiceLabels(
    val description: String,
    val topup: String,
    val packagesRow: String,
    val packagesCard: String,
    val phone: String,
    val continueAction: String
)

private fun phoneServiceLabels(locale: String): PhoneServiceLabels = when (
    locale.substringBefore(',').substringBefore(';').substringBefore('-').lowercase()
) {
    "de" -> PhoneServiceLabels(
        "Prepaid-Handyguthaben und Pakete",
        "Aufladen",
        "Pakete als Reihe",
        "Pakete als Karte",
        "Telefonnummer",
        "Weiter"
    )

    "es" -> PhoneServiceLabels(
        "Recargas y paquetes móviles de prepago",
        "Recarga",
        "Paquetes en fila",
        "Paquetes en tarjeta",
        "Número de teléfono",
        "Continuar"
    )

    else -> PhoneServiceLabels(
        "Prepaid mobile top-ups and packages",
        "Top-up",
        "Packages row",
        "Packages card",
        "Phone number",
        "Continue"
    )
}
