package xyz.lilsus.raylsuite.feature.paymentui

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
import xyz.lilsus.raylsuite.feature.paymentui.PaymentTestTags
import xyz.lilsus.raylsuite.feature.paymentui.components.BottomLayout
import xyz.lilsus.raylsuite.feature.paymentui.components.ConfirmationBottomSheet
import xyz.lilsus.raylsuite.feature.paymentui.components.ManualAmountBottomSheet
import xyz.lilsus.raylsuite.feature.paymentui.components.PaymentHero
import xyz.lilsus.raylsuite.feature.paymentui.components.ResultLayout
import xyz.lilsus.raylsuite.feature.paymentui.components.SessionTransactionsIconButton
import xyz.lilsus.raylsuite.feature.paymentui.components.SettingsIconButton
import xyz.lilsus.raylsuite.feature.paymentui.contacts.PaymentContactsBottomSheet
import xyz.lilsus.raylsuite.feature.paymentui.contacts.PaymentContactsUiState
import xyz.lilsus.raylsuite.feature.paymentui.contacts.SaveContactBottomSheet
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.open_shortcuts_contacts
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.point_camera_message_subtitle
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.resolving_payment_subtitle
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.resolving_payment_title
import xyz.lilsus.raylsuite.feature.paymentui.tapToDismiss

@Composable
fun PaymentScreen(
    appTitle: String,
    onNavigateSettings: () -> Unit,
    uiState: PaymentScreenState,
    sessionTransactions: List<PaymentSessionReference>,
    snackbarHostState: SnackbarHostState,
    estimatedFeeHint: String? = null,
    newSessionTransactionCount: Int = 0,
    contactsState: PaymentContactsUiState = PaymentContactsUiState(),
    onIntent: (PaymentIntent) -> Unit = {},
    onOpenTransactions: () -> Unit = {},
    onCreateShortcut: () -> Unit = {},
    onCreateContact: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showResolvingContent by remember { mutableStateOf(false) }
    val isResolvingLoading = uiState is PaymentScreenState.Loading &&
        uiState.kind == PaymentLoadingKind.Resolving
    LaunchedEffect(uiState) {
        showResolvingContent = false
        if (isResolvingLoading) {
            delay(RESOLVING_PAYMENT_INDICATOR_DELAY_MS)
            showResolvingContent = true
        }
    }

    val isDismissable = uiState is PaymentScreenState.Success ||
        uiState is PaymentScreenState.Error
    val contentState = if (isResolvingLoading && !showResolvingContent) {
        PaymentScreenState.Active
    } else {
        uiState
    }
    val showBottomActions = contentState !is PaymentScreenState.Success &&
        contentState !is PaymentScreenState.Error
    val receiptPreimage = (contentState as? PaymentScreenState.Success)
        ?.preimage
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    var showReceipt by remember { mutableStateOf(false) }
    LaunchedEffect(receiptPreimage) {
        showReceipt = false
    }
    val showTransactionAction = showBottomActions &&
        sessionTransactions.isNotEmpty() &&
        contentState == PaymentScreenState.Active
    val transactionAttentionKey = sessionTransactions.fold(sessionTransactions.size) { acc, item ->
        31 * acc + item.id.hashCode() + item.statusKey.hashCode()
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
        if (contentState == PaymentScreenState.Active) {
            Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(openContactsLabel) {
                        onIntent(PaymentIntent.OpenContacts)
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
                    onDismiss = { onIntent(PaymentIntent.DismissResult) }
                )
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PaymentHero(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                phase = uiState.toHeroPhase(),
                receiptPreimage = receiptPreimage.takeIf { showReceipt }
            )
            Crossfade(targetState = contentState) { state ->
                when {
                    state is PaymentScreenState.Success ||
                        state is PaymentScreenState.Error -> ResultLayout(
                        modifier = Modifier.fillMaxSize(),
                        result = state.toResultPresentation(),
                        receiptVisible = showReceipt,
                        estimatedFeeHint = estimatedFeeHint,
                        onViewReceipt = { showReceipt = true }
                    )

                    state is PaymentScreenState.Loading &&
                        state.kind == PaymentLoadingKind.Resolving -> {
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

    if (uiState is PaymentScreenState.EnterAmount) {
        ManualAmountBottomSheet(
            state = uiState.entry,
            lnurlPayDisplay = uiState.lnurlPayDisplay,
            onKeyPress = { onIntent(PaymentIntent.ManualAmountKeyPress(it)) },
            onRangeClick = { onIntent(PaymentIntent.ManualAmountPreset(it)) },
            onSubmit = { onIntent(PaymentIntent.ManualAmountSubmit) },
            onDismiss = { onIntent(PaymentIntent.ManualAmountDismiss) }
        )
    }

    if (uiState is PaymentScreenState.Confirm) {
        ConfirmationBottomSheet(
            confirmAmount = uiState.amount,
            lnurlPayDisplay = uiState.lnurlPayDisplay,
            onPay = { onIntent(PaymentIntent.ConfirmPaymentSubmit) },
            onDismiss = { onIntent(PaymentIntent.ConfirmPaymentDismiss) }
        )
    }

    if (uiState is PaymentScreenState.PendingRetry) {
        val retryTransaction = sessionTransactions.firstOrNull { transaction ->
            transaction.id == uiState.transactionId
        }
        RepeatPaymentClarificationBottomSheet(
            clarification =
                RepeatPaymentClarification(
                    retryTransaction?.previousPaymentSituation
                        ?: PreviousPaymentSituation.InProgress
                ),
            onDecision = { decision ->
                when (decision) {
                    RepeatPaymentDecision.RetryPreviousInvoice ->
                        onIntent(PaymentIntent.PendingRetryRetryPrevious)

                    RepeatPaymentDecision.CreateAdditionalPayment ->
                        onIntent(PaymentIntent.PendingRetryCreateNewInvoice)

                    RepeatPaymentDecision.ViewPreviousPayment ->
                        onIntent(PaymentIntent.PendingRetryViewPending)

                    RepeatPaymentDecision.Dismiss ->
                        onIntent(PaymentIntent.PendingRetryDismiss)
                }
            }
        )
    }

    if (contactsState.isOpen) {
        PaymentContactsBottomSheet(
            state = contactsState,
            onDismiss = { onIntent(PaymentIntent.DismissContacts) },
            onTabSelected = { onIntent(PaymentIntent.PaymentSheetTabSelected(it)) },
            onRoleSelected = { onIntent(PaymentIntent.ContactRoleSelected(it)) },
            onContactSelected = { onIntent(PaymentIntent.SelectContact(it)) },
            onShortcutSelected = { onIntent(PaymentIntent.SelectShortcut(it)) },
            onCreateShortcut = onCreateShortcut,
            onCreateContact = onCreateContact
        )
    }

    contactsState.savePrompt?.let { prompt ->
        SaveContactBottomSheet(
            state = prompt,
            onAliasChange = { onIntent(PaymentIntent.SaveContactPromptAliasChanged(it)) },
            onRoleSelected = { onIntent(PaymentIntent.SaveContactPromptRoleSelected(it)) },
            onSave = { onIntent(PaymentIntent.SaveContactPromptSave) },
            onDismiss = { onIntent(PaymentIntent.SaveContactPromptDismiss) }
        )
    }
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
