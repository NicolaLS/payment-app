package xyz.lilsus.raylsuite.feature.paymentui

import org.jetbrains.compose.resources.getString
import xyz.lilsus.raylsuite.core.camera.CameraAuthorizationState
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.ui.format.AmountFormatter
import xyz.lilsus.raylsuite.core.ui.format.currentAmountFormatter
import xyz.lilsus.raylsuite.feature.paymenthub.host.HubSavePrompt
import xyz.lilsus.raylsuite.feature.paymenthub.host.toNativePresentation
import xyz.lilsus.raylsuite.feature.paymentui.amount.ManualAmountUiState
import xyz.lilsus.raylsuite.feature.paymentui.amount.RangeStatus
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.camera_permission_denied_body
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.camera_permission_denied_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.camera_permission_open_settings
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.camera_permission_restricted_body
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.camera_permission_restricted_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.completed_body
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.completed_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.confirm_payment_approximate_amount
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.confirm_payment_exact_amount
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.confirm_payment_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.create_additional_payment
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.dismiss_button
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.enter_amount_range_max
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.enter_amount_range_min
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.enter_amount_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.in_progress_body
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.in_progress_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.lnurl_payment_recipient
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.outcome_unknown_body
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.outcome_unknown_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.pay_button
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.point_camera_message_subtitle
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.resolving_payment_subtitle
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.resolving_payment_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_already_paid_message
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_already_paid_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_error_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_paid_fee
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_paid_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_receipt_body_middle
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_receipt_body_only
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_receipt_body_prefix
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_receipt_body_preimage
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_receipt_body_suffix
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_receipt_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.result_view_receipt
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.retry_previous_invoice
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.tap_continue
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.view_previous_payment
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.view_session_transactions

data class NativePaymentScanSnapshot(
    val heroPhase: String,
    val receiptPreimage: String?,
    val content: NativePaymentScanContent,
    val sheet: NativePaymentScanSheet?,
    val recent: NativePaymentScanRecentEntry?,
    val cameraPermission: NativeCameraPermissionContent?
)

data class NativeCameraPermissionContent(
    val title: String,
    val body: String,
    val openSettingsTitle: String?
)

/**
 * The Scan screen's way into this session's payments. Only a product without a Recent tab supplies
 * it, and only once the session has a payment to look back at.
 */
data class NativePaymentScanRecentEntry(val title: String, val newTransactionCount: Int)

data class NativePaymentScanContent(
    val kind: String,
    val title: String,
    val subtitle: String?,
    val primaryAmount: String?,
    val secondaryText: String?,
    val feeHint: String?,
    val actionTitle: String?,
    val tapToContinue: String?
)

data class NativePaymentScanSheet(
    val kind: String,
    val title: String,
    val body: String?,
    val amount: String?,
    val exactAmount: String?,
    val recipientTitle: String?,
    val recipientDescription: String?,
    val recipientImageBase64: String?,
    val currencyLabel: String?,
    val minimumTitle: String?,
    val maximumTitle: String?,
    val rangeMessage: String?,
    val allowsDecimal: Boolean,
    val canSubmit: Boolean,
    val primaryAction: String,
    val primaryActionTitle: String,
    val secondaryActionTitle: String?,
    val tertiaryActionTitle: String?,
    val textFieldLabel: String?,
    val textFieldValue: String?
)

/**
 * Converts provider-neutral payment presentation into values consumed by SwiftUI.
 * Provider state and errors must be projected before crossing this boundary.
 */
suspend fun nativePaymentScanSnapshot(
    payment: PaymentScreenState,
    appTitle: String,
    estimatedFeeHint: String?,
    previousPaymentSituation: PreviousPaymentSituation?,
    savePrompt: HubSavePrompt?,
    receiptVisible: Boolean,
    canOpenPreviousPayment: Boolean = true,
    showResolvingContent: Boolean = true,
    recentCount: Int = 0,
    newRecentCount: Int = 0,
    offersRecentEntryPoint: Boolean = false,
    cameraAuthorization: CameraAuthorizationState = CameraAuthorizationState.AUTHORIZED,
    formatter: AmountFormatter = currentAmountFormatter()
): NativePaymentScanSnapshot {
    val receiptPreimage =
        (payment as? PaymentScreenState.Success)
            ?.preimage
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    return NativePaymentScanSnapshot(
        heroPhase = payment.toNativeHeroPhaseValue(),
        receiptPreimage = receiptPreimage.takeIf { receiptVisible },
        content =
            payment.toNativeContent(
                appTitle = appTitle,
                estimatedFeeHint = estimatedFeeHint,
                receiptVisible = receiptVisible && receiptPreimage != null,
                showResolvingContent = showResolvingContent,
                formatter = formatter
            ),
        sheet =
            savePrompt?.toNativeSheet()
                ?: payment.toNativeSheet(
                    previousPaymentSituation,
                    canOpenPreviousPayment,
                    formatter
                ),
        recent =
            if (offersRecentEntryPoint && recentCount > 0) {
                NativePaymentScanRecentEntry(
                    title = getString(Res.string.view_session_transactions),
                    newTransactionCount = newRecentCount
                )
            } else {
                null
            },
        cameraPermission =
            if (payment == PaymentScreenState.Active) {
                cameraAuthorization.toNativePermissionContent()
            } else {
                null
            }
    )
}

private suspend fun CameraAuthorizationState.toNativePermissionContent():
    NativeCameraPermissionContent? =
    when (this) {
        CameraAuthorizationState.DENIED ->
            NativeCameraPermissionContent(
                title = getString(Res.string.camera_permission_denied_title),
                body = getString(Res.string.camera_permission_denied_body),
                openSettingsTitle = getString(Res.string.camera_permission_open_settings)
            )

        CameraAuthorizationState.RESTRICTED,
        CameraAuthorizationState.UNAVAILABLE ->
            NativeCameraPermissionContent(
                title = getString(Res.string.camera_permission_restricted_title),
                body = getString(Res.string.camera_permission_restricted_body),
                openSettingsTitle = null
            )

        CameraAuthorizationState.NOT_DETERMINED,
        CameraAuthorizationState.AUTHORIZED -> null
    }

private suspend fun PaymentScreenState.toNativeContent(
    appTitle: String,
    estimatedFeeHint: String?,
    receiptVisible: Boolean,
    showResolvingContent: Boolean,
    formatter: AmountFormatter
): NativePaymentScanContent = when (this) {
    is PaymentScreenState.Loading ->
        if (kind == PaymentLoadingKind.Resolving && showResolvingContent) {
            NativePaymentScanContent(
                kind = CONTENT_RESOLVING,
                title = getString(Res.string.resolving_payment_title),
                subtitle = getString(Res.string.resolving_payment_subtitle),
                primaryAmount = null,
                secondaryText = null,
                feeHint = null,
                actionTitle = null,
                tapToContinue = null
            )
        } else {
            idleContent(appTitle)
        }

    is PaymentScreenState.Success ->
        when {
            receiptVisible -> receiptContent()
            wasAlreadyPaid -> alreadyPaidContent(preimage)
            else -> successContent(formatter, estimatedFeeHint)
        }

    is PaymentScreenState.Error ->
        NativePaymentScanContent(
            kind = CONTENT_ERROR,
            title = getString(Res.string.result_error_title),
            subtitle = message,
            primaryAmount = null,
            secondaryText = null,
            feeHint = null,
            actionTitle = null,
            tapToContinue = getString(Res.string.tap_continue)
        )

    else -> idleContent(appTitle)
}

internal suspend fun PaymentScreenState.toNativeRecentDetailContent(
    estimatedFeeHint: String?,
    receiptVisible: Boolean,
    formatter: AmountFormatter = currentAmountFormatter()
): NativePaymentScanContent = toNativeContent(
    appTitle = "",
    estimatedFeeHint = estimatedFeeHint,
    receiptVisible = receiptVisible,
    showResolvingContent = true,
    formatter = formatter
)

private suspend fun idleContent(appTitle: String) = NativePaymentScanContent(
    kind = CONTENT_IDLE,
    title = appTitle,
    subtitle = getString(Res.string.point_camera_message_subtitle),
    primaryAmount = null,
    secondaryText = null,
    feeHint = null,
    actionTitle = null,
    tapToContinue = null
)

private suspend fun PaymentScreenState.Success.successContent(
    formatter: AmountFormatter,
    estimatedFeeHint: String?
) = NativePaymentScanContent(
    kind = CONTENT_SUCCESS,
    title = getString(Res.string.result_paid_title),
    subtitle = null,
    primaryAmount = formatter.format(amountPaid),
    secondaryText = getString(Res.string.result_paid_fee, formatter.format(feePaid)),
    feeHint = estimatedFeeHint.takeIf { showEstimatedFeeHint },
    actionTitle =
        getString(Res.string.result_view_receipt)
            .takeIf { !preimage.isNullOrBlank() },
    tapToContinue = getString(Res.string.tap_continue)
)

private suspend fun alreadyPaidContent(preimage: String?) = NativePaymentScanContent(
    kind = CONTENT_ALREADY_PAID,
    title = getString(Res.string.result_already_paid_title),
    subtitle = getString(Res.string.result_already_paid_message),
    primaryAmount = null,
    secondaryText = null,
    feeHint = null,
    actionTitle =
        getString(Res.string.result_view_receipt)
            .takeIf { !preimage.isNullOrBlank() },
    tapToContinue = getString(Res.string.tap_continue)
)

private suspend fun receiptContent() = NativePaymentScanContent(
    kind = CONTENT_RECEIPT,
    title = getString(Res.string.result_receipt_title),
    subtitle =
        getString(Res.string.result_receipt_body_prefix) +
            getString(Res.string.result_receipt_body_preimage) +
            getString(Res.string.result_receipt_body_middle) +
            getString(Res.string.result_receipt_body_only) +
            getString(Res.string.result_receipt_body_suffix),
    primaryAmount = null,
    secondaryText = null,
    feeHint = null,
    actionTitle = null,
    tapToContinue = getString(Res.string.tap_continue)
)

private suspend fun PaymentScreenState.toNativeSheet(
    previousPaymentSituation: PreviousPaymentSituation?,
    canOpenPreviousPayment: Boolean,
    formatter: AmountFormatter
): NativePaymentScanSheet? = when (this) {
    is PaymentScreenState.EnterAmount -> entry.toNativeSheet(lnurlPayDisplay, formatter)

    is PaymentScreenState.Confirm -> toNativeSheet(formatter)

    is PaymentScreenState.PendingRetry ->
        (previousPaymentSituation ?: PreviousPaymentSituation.InProgress).toNativeSheet(
            canOpenPreviousPayment
        )

    else -> null
}

private suspend fun ManualAmountUiState.toNativeSheet(
    recipient: LnurlPayDisplay?,
    formatter: AmountFormatter
): NativePaymentScanSheet = NativePaymentScanSheet(
    kind = SHEET_MANUAL_AMOUNT,
    title = getString(Res.string.enter_amount_title),
    body = null,
    amount = committedNumber(),
    exactAmount = null,
    recipientTitle = recipient?.recipientTitle(),
    recipientDescription = recipient?.description,
    recipientImageBase64 = recipient?.image?.encodedBase64(),
    currencyLabel = currency.nativeLabel(),
    minimumTitle = min?.let {
        getString(Res.string.enter_amount_range_min, formatter.format(it))
    },
    maximumTitle = max?.let {
        getString(Res.string.enter_amount_range_max, formatter.format(it))
    },
    rangeMessage = rangeStatus.message(formatter),
    allowsDecimal = allowDecimal,
    canSubmit = amount != null && amount.minor > 0L && rangeStatus == RangeStatus.InRange,
    primaryAction = "submit",
    primaryActionTitle = getString(Res.string.pay_button),
    secondaryActionTitle = getString(Res.string.dismiss_button),
    tertiaryActionTitle = null,
    textFieldLabel = null,
    textFieldValue = null
)

private suspend fun PaymentScreenState.Confirm.toNativeSheet(
    formatter: AmountFormatter
): NativePaymentScanSheet = NativePaymentScanSheet(
    kind = SHEET_CONFIRMATION,
    title = getString(Res.string.confirm_payment_title),
    body = null,
    amount =
        if (amount.primaryIsEstimate) {
            getString(
                Res.string.confirm_payment_approximate_amount,
                formatter.format(amount.primary)
            )
        } else {
            formatter.format(amount.primary)
        },
    exactAmount =
        amount.exactSats?.let {
            getString(Res.string.confirm_payment_exact_amount, formatter.format(it))
        },
    recipientTitle = lnurlPayDisplay?.recipientTitle(),
    recipientDescription = lnurlPayDisplay?.description,
    recipientImageBase64 = lnurlPayDisplay?.image?.encodedBase64(),
    currencyLabel = null,
    minimumTitle = null,
    maximumTitle = null,
    rangeMessage = null,
    allowsDecimal = false,
    canSubmit = true,
    primaryAction = "submit",
    primaryActionTitle = getString(Res.string.pay_button),
    secondaryActionTitle = getString(Res.string.dismiss_button),
    tertiaryActionTitle = null,
    textFieldLabel = null,
    textFieldValue = null
)

private suspend fun PreviousPaymentSituation.toNativeSheet(
    canOpenPreviousPayment: Boolean
): NativePaymentScanSheet {
    val canRetry = this == PreviousPaymentSituation.OutcomeUnknown
    val (title, body) =
        when (this) {
            PreviousPaymentSituation.InProgress ->
                Res.string.in_progress_title to Res.string.in_progress_body

            PreviousPaymentSituation.OutcomeUnknown ->
                Res.string.outcome_unknown_title to Res.string.outcome_unknown_body

            PreviousPaymentSituation.Completed ->
                Res.string.completed_title to Res.string.completed_body
        }
    return NativePaymentScanSheet(
        kind = SHEET_REPEAT_PAYMENT,
        title = getString(title),
        body = getString(body),
        amount = null,
        exactAmount = null,
        recipientTitle = null,
        recipientDescription = null,
        recipientImageBase64 = null,
        currencyLabel = null,
        minimumTitle = null,
        maximumTitle = null,
        rangeMessage = null,
        allowsDecimal = false,
        canSubmit = true,
        primaryAction =
            when {
                canRetry -> "retry"
                canOpenPreviousPayment -> "view"
                else -> "additional"
            },
        primaryActionTitle =
            getString(
                when {
                    canRetry -> Res.string.retry_previous_invoice
                    canOpenPreviousPayment -> Res.string.view_previous_payment
                    else -> Res.string.create_additional_payment
                }
            ),
        secondaryActionTitle =
            getString(Res.string.create_additional_payment)
                .takeIf { canRetry || canOpenPreviousPayment },
        tertiaryActionTitle =
            getString(Res.string.view_previous_payment)
                .takeIf { canRetry && canOpenPreviousPayment },
        textFieldLabel = null,
        textFieldValue = null
    )
}

private suspend fun HubSavePrompt.toNativeSheet(): NativePaymentScanSheet {
    val presentation = toNativePresentation()
    return NativePaymentScanSheet(
        kind = SHEET_SAVE_TARGET,
        title = presentation.title,
        body = presentation.body,
        amount = null,
        exactAmount = null,
        recipientTitle = null,
        recipientDescription = null,
        recipientImageBase64 = null,
        currencyLabel = null,
        minimumTitle = null,
        maximumTitle = null,
        rangeMessage = null,
        allowsDecimal = false,
        canSubmit = true,
        primaryAction = "save",
        primaryActionTitle = presentation.saveTitle,
        secondaryActionTitle = presentation.dismissTitle,
        tertiaryActionTitle = null,
        textFieldLabel = presentation.nameLabel,
        textFieldValue = presentation.targetName
    )
}

private suspend fun LnurlPayDisplay.recipientTitle(): String =
    getString(Res.string.lnurl_payment_recipient, domain)

private suspend fun RangeStatus.message(formatter: AmountFormatter): String? = when (this) {
    RangeStatus.InRange,
    RangeStatus.Unknown -> null

    is RangeStatus.BelowMin ->
        getString(Res.string.enter_amount_range_min, formatter.format(min))

    is RangeStatus.AboveMax ->
        getString(Res.string.enter_amount_range_max, formatter.format(max))
}

private fun ManualAmountUiState.committedNumber(): String {
    val normalized = rawWhole.ifEmpty { "0" }
    val whole =
        if (normalized.length <= 3) {
            normalized
        } else {
            normalized.reversed().chunked(3).joinToString(",").reversed()
        }
    if (!allowDecimal || (!hasDecimal && rawFraction.isEmpty())) return whole
    return "$whole.${rawFraction.ifEmpty { "0" }}"
}

private fun DisplayCurrency.nativeLabel(): String = when (this) {
    DisplayCurrency.Bitcoin -> "BTC"
    DisplayCurrency.Satoshi -> "sat"
    is DisplayCurrency.Fiat -> iso4217.uppercase()
}

private const val CONTENT_IDLE = "idle"
private const val CONTENT_RESOLVING = "resolving"
private const val CONTENT_SUCCESS = "success"
private const val CONTENT_ALREADY_PAID = "alreadyPaid"
private const val CONTENT_RECEIPT = "receipt"
private const val CONTENT_ERROR = "error"

private const val SHEET_MANUAL_AMOUNT = "manualAmount"
private const val SHEET_CONFIRMATION = "confirmation"
private const val SHEET_REPEAT_PAYMENT = "repeatPayment"
private const val SHEET_SAVE_TARGET = "saveTarget"
