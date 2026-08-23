package xyz.lilsus.raylsuite.feature.paymentui

import xyz.lilsus.raylsuite.core.ui.resources.LocalizedText
import xyz.lilsus.raylsuite.core.ui.resources.localizedTextWithOptionalDetail
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.error_exchange_rate_unavailable
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.error_invalid_invoice
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.error_invalid_invoice_with_details
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.error_lnurl
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.error_lnurl_with_details
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.error_unexpected_generic
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.error_unexpected_with_details

fun invalidInvoicePaymentErrorText(reason: String?): LocalizedText =
    localizedTextWithOptionalDetail(
        detail = reason,
        generic = Res.string.error_invalid_invoice,
        withDetails = Res.string.error_invalid_invoice_with_details
    )

fun lnurlPaymentErrorText(reason: String?): LocalizedText = localizedTextWithOptionalDetail(
    detail = reason,
    generic = Res.string.error_lnurl,
    withDetails = Res.string.error_lnurl_with_details
)

fun exchangeRateUnavailablePaymentErrorText(currencyCode: String): LocalizedText =
    LocalizedText(Res.string.error_exchange_rate_unavailable, currencyCode)

fun unexpectedPaymentErrorText(detail: String?): LocalizedText = localizedTextWithOptionalDetail(
    detail = detail,
    generic = Res.string.error_unexpected_generic,
    withDetails = Res.string.error_unexpected_with_details
)
