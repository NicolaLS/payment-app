package xyz.lilsus.blip.feature.payment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.payment.amount.ManualAmountKey
import xyz.lilsus.blip.feature.payment.components.BottomLayout
import xyz.lilsus.blip.feature.payment.components.ConfirmationBottomSheet
import xyz.lilsus.blip.feature.payment.components.ManualAmountBottomSheet
import xyz.lilsus.blip.feature.payment.components.PaymentHero
import xyz.lilsus.blip.feature.payment.components.ResultLayout
import xyz.lilsus.blip.feature.payment.components.SessionTransactionsIconButton
import xyz.lilsus.blip.feature.payment.components.SettingsIconButton
import xyz.lilsus.blip.feature.payment.contacts.PaymentContactsBottomSheet
import xyz.lilsus.blip.feature.payment.contacts.PaymentContactsUiState
import xyz.lilsus.blip.feature.payment.contacts.PaymentSheetTab
import xyz.lilsus.blip.feature.payment.contacts.SaveContactBottomSheet
import xyz.lilsus.blip.feature.payment.generated.resources.Res
import xyz.lilsus.blip.feature.payment.generated.resources.open_shortcuts_contacts
import xyz.lilsus.blip.feature.payment.generated.resources.point_camera_message_subtitle
import xyz.lilsus.blip.feature.payment.generated.resources.resolving_payment_subtitle
import xyz.lilsus.blip.feature.payment.generated.resources.resolving_payment_title
import xyz.lilsus.raylsuite.core.camera.QrScannerMode
import xyz.lilsus.raylsuite.core.model.ContactRole
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.feature.paymentintent.PreviousPaymentSituation
import xyz.lilsus.raylsuite.feature.paymentintent.RepeatPaymentClarification
import xyz.lilsus.raylsuite.feature.paymentintent.RepeatPaymentClarificationBottomSheet
import xyz.lilsus.raylsuite.feature.paymentintent.RepeatPaymentDecision

@Composable
fun PaymentScreen(
    appTitle: String,
    onNavigateSettings: () -> Unit,
    uiState: PaymentUiState,
    sessionTransactions: List<SessionTransactionItem>,
    snackbarHostState: SnackbarHostState,
    errorMessageFor: @Composable (PaymentUiError) -> String,
    estimatedFeeHint: String? = null,
    newSessionTransactionCount: Int = 0,
    contactsState: PaymentContactsUiState = PaymentContactsUiState(),
    onManualAmountKeyPress: (ManualAmountKey) -> Unit = {},
    onManualAmountPreset: (DisplayAmount) -> Unit = {},
    onManualAmountSubmit: () -> Unit = {},
    onManualAmountDismiss: () -> Unit = {},
    onConfirmPaymentSubmit: () -> Unit = {},
    onConfirmPaymentDismiss: () -> Unit = {},
    onPendingRetryCreateNewInvoice: () -> Unit = {},
    onPendingRetryRetryPrevious: () -> Unit = {},
    onPendingRetryViewPending: () -> Unit = {},
    onPendingRetryDismiss: () -> Unit = {},
    onOpenTransactions: () -> Unit = {},
    onResultDismiss: () -> Unit = {},
    onContactsOpen: () -> Unit = {},
    onContactsDismiss: () -> Unit = {},
    onPaymentSheetTabSelected: (PaymentSheetTab) -> Unit = {},
    onContactsRoleSelected: (ContactRole?) -> Unit = {},
    onShortcutSelected: (String) -> Unit = {},
    onCreateShortcut: () -> Unit = {},
    onCreateContact: () -> Unit = {},
    onContactSelected: (String) -> Unit = {},
    onSaveContactPromptAliasChange: (String) -> Unit = {},
    onSaveContactPromptRoleSelected: (ContactRole?) -> Unit = {},
    onSaveContactPromptSave: () -> Unit = {},
    onSaveContactPromptDismiss: () -> Unit = {},
    scannerMode: QrScannerMode = QrScannerMode.Near,
    showScannerModeSelector: Boolean = false,
    onToggleScannerMode: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showResolvingContent by remember { mutableStateOf(false) }
    val isResolvingLoading = uiState is PaymentUiState.Loading &&
        uiState.kind == LoadingKind.Resolving
    LaunchedEffect(uiState) {
        showResolvingContent = false
        if (isResolvingLoading) {
            delay(RESOLVING_PAYMENT_INDICATOR_DELAY_MS)
            showResolvingContent = true
        }
    }

    val isDismissable = uiState is PaymentUiState.Success ||
        uiState is PaymentUiState.Error
    val contentState = if (isResolvingLoading && !showResolvingContent) {
        PaymentUiState.Active
    } else {
        uiState
    }
    val showBottomActions = contentState !is PaymentUiState.Success &&
        contentState !is PaymentUiState.Error
    val receiptPreimage = (contentState as? PaymentUiState.Success)
        ?.preimage
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    var showReceipt by remember { mutableStateOf(false) }
    LaunchedEffect(receiptPreimage) {
        showReceipt = false
    }
    val showTransactionAction = showBottomActions &&
        sessionTransactions.isNotEmpty() &&
        contentState == PaymentUiState.Active
    val transactionAttentionKey = sessionTransactions.fold(sessionTransactions.size) { acc, item ->
        31 * acc + item.id.hashCode() + item.status.hashCode()
    }
    var revealTransactionAction by remember { mutableStateOf(false) }
    LaunchedEffect(showTransactionAction) {
        if (showTransactionAction) {
            revealTransactionAction = false
            delay(TRANSACTION_ACTION_REVEAL_DELAY_MS)
            revealTransactionAction = true
        } else {
            revealTransactionAction = false
        }
    }
    val openContactsLabel = stringResource(Res.string.open_shortcuts_contacts)
    val activeContentModifier = Modifier.then(
        if (contentState == PaymentUiState.Active) {
            Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(openContactsLabel) {
                        onContactsOpen()
                        true
                    }
                )
            }
        } else {
            Modifier
        }
    )

    Scaffold(
        modifier = modifier.testTag(PaymentTestTags.SCREEN),
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        },
        floatingActionButton = {
            if (showBottomActions) {
                PaymentBottomActions(
                    showTransactionAction = revealTransactionAction,
                    newSessionTransactionCount = newSessionTransactionCount,
                    transactionAttentionKey = transactionAttentionKey,
                    onOpenTransactions = onOpenTransactions,
                    onNavigateSettings = onNavigateSettings
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = activeContentModifier
                .tapToDismiss(
                    enabled = isDismissable,
                    onDismiss = onResultDismiss
                )
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PaymentHero(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                uiState = uiState,
                receiptPreimage = receiptPreimage.takeIf { showReceipt },
                scannerMode = scannerMode,
                showScannerModeSelector = showScannerModeSelector,
                onToggleScannerMode = onToggleScannerMode
            )
            Crossfade(targetState = contentState) { state ->
                when {
                    state is PaymentUiState.Success ||
                        state is PaymentUiState.Error -> ResultLayout(
                        modifier = Modifier.fillMaxSize(),
                        result = state,
                        receiptVisible = showReceipt,
                        estimatedFeeHint = estimatedFeeHint,
                        errorMessageFor = errorMessageFor,
                        onViewReceipt = { showReceipt = true }
                    )

                    state is PaymentUiState.Loading && state.kind == LoadingKind.Resolving -> {
                        ResolvingPaymentLayout(modifier = Modifier.fillMaxSize())
                    }

                    else -> BottomLayout(
                        title = appTitle,
                        subtitle = stringResource(Res.string.point_camera_message_subtitle)
                    )
                }
            }
        }
    }

    if (uiState is PaymentUiState.EnterAmount) {
        ManualAmountBottomSheet(
            state = uiState.entry,
            onKeyPress = onManualAmountKeyPress,
            onRangeClick = onManualAmountPreset,
            onSubmit = onManualAmountSubmit,
            onDismiss = onManualAmountDismiss
        )
    }

    if (uiState is PaymentUiState.Confirm) {
        ConfirmationBottomSheet(
            confirmAmount = uiState.amount,
            onPay = onConfirmPaymentSubmit,
            onDismiss = onConfirmPaymentDismiss
        )
    }

    if (uiState is PaymentUiState.PendingRetry) {
        val retryTransaction = sessionTransactions.firstOrNull { transaction ->
            transaction.id == uiState.id
        }
        val status = retryTransaction?.status ?: PendingStatus.Sending
        status.toPreviousPaymentSituation()?.let { situation ->
            RepeatPaymentClarificationBottomSheet(
                clarification = RepeatPaymentClarification(situation),
                onDecision = { decision ->
                    when (decision) {
                        RepeatPaymentDecision.RetryPreviousInvoice ->
                            onPendingRetryRetryPrevious()

                        RepeatPaymentDecision.CreateAdditionalPayment ->
                            onPendingRetryCreateNewInvoice()

                        RepeatPaymentDecision.ViewPreviousPayment ->
                            onPendingRetryViewPending()

                        RepeatPaymentDecision.Dismiss ->
                            onPendingRetryDismiss()
                    }
                }
            )
        }
    }

    if (contactsState.isOpen) {
        PaymentContactsBottomSheet(
            state = contactsState,
            onDismiss = onContactsDismiss,
            onTabSelected = onPaymentSheetTabSelected,
            onRoleSelected = onContactsRoleSelected,
            onContactSelected = onContactSelected,
            onShortcutSelected = onShortcutSelected,
            onCreateShortcut = onCreateShortcut,
            onCreateContact = onCreateContact
        )
    }

    contactsState.savePrompt?.let { prompt ->
        SaveContactBottomSheet(
            state = prompt,
            onAliasChange = onSaveContactPromptAliasChange,
            onRoleSelected = onSaveContactPromptRoleSelected,
            onSave = onSaveContactPromptSave,
            onDismiss = onSaveContactPromptDismiss
        )
    }
}

private fun PendingStatus.toPreviousPaymentSituation(): PreviousPaymentSituation? = when (this) {
    PendingStatus.Sending,
    PendingStatus.PendingInBlink -> PreviousPaymentSituation.InProgress

    PendingStatus.StatusUnknown -> PreviousPaymentSituation.OutcomeUnknown

    PendingStatus.Success,
    PendingStatus.AlreadyPaid -> PreviousPaymentSituation.Completed

    PendingStatus.Failure -> null
}

@Composable
private fun PaymentBottomActions(
    showTransactionAction: Boolean,
    newSessionTransactionCount: Int,
    transactionAttentionKey: Int,
    onOpenTransactions: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(
            visible = showTransactionAction,
            enter = fadeIn(
                animationSpec = tween(durationMillis = TRANSACTION_ACTION_FADE_IN_MS)
            ),
            exit = fadeOut()
        ) {
            SessionTransactionsIconButton(
                badgeCount = newSessionTransactionCount,
                attentionKey = transactionAttentionKey,
                onClick = onOpenTransactions
            )
        }
        SettingsIconButton(onNavigateSettings = onNavigateSettings)
    }
}

@Composable
private fun ResolvingPaymentLayout(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.resolving_payment_title),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(Res.string.resolving_payment_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
    }
}

private const val RESOLVING_PAYMENT_INDICATOR_DELAY_MS = 1_000L
private const val TRANSACTION_ACTION_REVEAL_DELAY_MS = 120L
private const val TRANSACTION_ACTION_FADE_IN_MS = 700
