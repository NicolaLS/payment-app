package xyz.lilsus.raylsuite.feature.paymenthub.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import xyz.lilsus.raylsuite.core.hubapi.HubServiceMoney
import xyz.lilsus.raylsuite.feature.paymenthub.HubServiceError
import xyz.lilsus.raylsuite.feature.paymenthub.R

/** Render the quoted decimal exactly; wallet exchange rates do not price service products. */
internal fun HubServiceMoney.display(locale: Locale): String =
    "${formatServiceMinor(minor, fractionDigits, locale)} $currency"

internal fun formatServiceMinor(minor: String, fractionDigits: Int, locale: Locale): String =
    runCatching {
        val amount = BigDecimal(minor).movePointLeft(fractionDigits)
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }.format(amount)
    }.getOrDefault("—")

internal fun formatServiceSats(msat: String, locale: Locale): String = runCatching {
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 3
    }.format(BigDecimal(msat).movePointLeft(3)) + " sats"
}.getOrDefault("—")

internal fun formatServiceExpiry(timestamp: String, locale: Locale): String = runCatching {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(timestamp))
}.getOrDefault(timestamp)

@Composable
internal fun serviceLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
internal fun serviceStatusLabel(status: String): String = stringResource(
    when (status) {
        "preparing" -> R.string.hub_service_status_preparing
        "awaiting_payment" -> R.string.hub_service_status_awaiting_payment
        "processing" -> R.string.hub_service_status_processing
        "delivered" -> R.string.hub_service_status_delivered
        "expired" -> R.string.hub_service_status_expired
        "failed" -> R.string.hub_service_status_failed
        "unpaid" -> R.string.hub_service_status_unpaid
        "pending" -> R.string.hub_service_status_pending
        "paid" -> R.string.hub_service_status_paid
        else -> R.string.hub_service_status_unknown
    }
)

@Composable
internal fun HubServiceError.label(): String = stringResource(
    when (this) {
        HubServiceError.InvalidPhone -> R.string.hub_service_invalid_phone
        HubServiceError.InvalidAmount -> R.string.hub_service_invalid_amount
        HubServiceError.SelectOffer -> R.string.hub_service_select_offer
        HubServiceError.Unavailable -> R.string.hub_service_unavailable
        HubServiceError.Changed -> R.string.hub_service_changed
        HubServiceError.SaveFailed -> R.string.hub_service_save_failed
        HubServiceError.InvalidInvoice -> R.string.hub_service_invalid_invoice
    }
)
