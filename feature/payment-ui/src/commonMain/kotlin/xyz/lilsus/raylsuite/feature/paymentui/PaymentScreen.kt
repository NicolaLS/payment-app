package xyz.lilsus.raylsuite.feature.paymentui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import xyz.lilsus.raylsuite.core.ui.hero.RaylHeroPhase
import xyz.lilsus.raylsuite.feature.paymenthub.host.HubGroupBottomSheet
import xyz.lilsus.raylsuite.feature.paymenthub.host.HubSavePromptBottomSheet
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubHostState
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubIntent
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubActions
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubLensDefinition
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubScannerSlot
import xyz.lilsus.raylsuite.feature.paymentui.components.BottomLayout
import xyz.lilsus.raylsuite.feature.paymentui.components.ConfirmationBottomSheet
import xyz.lilsus.raylsuite.feature.paymentui.components.ManualAmountBottomSheet
import xyz.lilsus.raylsuite.feature.paymentui.components.PaymentHero
import xyz.lilsus.raylsuite.feature.paymentui.components.ResultLayout
import xyz.lilsus.raylsuite.feature.paymentui.components.SessionTransactionsIconButton
import xyz.lilsus.raylsuite.feature.paymentui.components.SettingsIconButton
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.open_payment_hub
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.point_camera_message_subtitle
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.resolving_payment_subtitle
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.resolving_payment_title
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.scanner_close

/**
 * Common payment host. While the app payment state is active it renders the selected lens over
 * the shared hub render state; otherwise it renders the existing amount, confirmation, loading,
 * result, retry, and error presentations. The scanner surface moves between both without losing
 * its animation state.
 */
@Composable
fun PaymentScreen(
    appTitle: String,
    onNavigateSettings: () -> Unit,
    uiState: PaymentScreenState,
    sessionTransactions: List<PaymentSessionReference>,
    snackbarHostState: SnackbarHostState,
    hub: PaymentHubHostState,
    lens: PaymentHubLensDefinition,
    hubActions: PaymentHubActions,
    estimatedFeeHint: String? = null,
    newSessionTransactionCount: Int = 0,
    onIntent: (PaymentIntent) -> Unit = {},
    onHubIntent: (PaymentHubIntent) -> Unit = {},
    onOpenTransactions: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
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
    val showHostActions = contentState !is PaymentScreenState.Success &&
        contentState !is PaymentScreenState.Error
    val receiptPreimage = (contentState as? PaymentScreenState.Success)
        ?.preimage
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    var showReceipt by remember { mutableStateOf(false) }
    LaunchedEffect(receiptPreimage) {
        showReceipt = false
    }
    val showTransactionAction = showHostActions &&
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
    val openLibraryLabel = stringResource(Res.string.open_payment_hub)
    val activeContentModifier = Modifier.then(
        if (contentState == PaymentScreenState.Active) {
            Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(openLibraryLabel) {
                        onOpenLibrary()
                        true
                    }
                )
            }
        } else {
            Modifier
        }
    )

    val heroPhase by rememberUpdatedState(uiState.toHeroPhase())
    val heroReceipt by rememberUpdatedState(receiptPreimage.takeIf { showReceipt })
    val currentAppTitle by rememberUpdatedState(appTitle)
    val scannerSurface =
        remember {
            movableContentOf<Modifier, Boolean> { surfaceModifier, compact ->
                ScannerSurface(
                    phase = heroPhase,
                    receiptPreimage = heroReceipt,
                    appTitle = currentAppTitle,
                    compact = compact,
                    modifier = surfaceModifier
                )
            }
        }
    val scannerSlot = remember(scannerSurface) { PaymentHubScannerSlot(scannerSurface) }
    val showLens = contentState == PaymentScreenState.Active && !hub.scannerRequested

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
        }
    ) { paddingValues ->
        Column(
            modifier = activeContentModifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HostActionsRow(
                visible = showHostActions,
                showTransactionAction = revealTransactionAction,
                newSessionTransactionCount = newSessionTransactionCount,
                transactionAttentionKey = transactionAttentionKey,
                onOpenTransactions = onOpenTransactions,
                onNavigateSettings = onNavigateSettings
            )
            if (showLens) {
                lens.Content(
                    state = hub.render,
                    actions = hubActions,
                    scanner = scannerSlot,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (contentState == PaymentScreenState.Active) {
                ScannerTakeover(
                    scanner = scannerSlot,
                    onDismiss = { onHubIntent(PaymentHubIntent.DismissScanner) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier =
                        Modifier
                            .tapToDismiss(
                                enabled = isDismissable,
                                onDismiss = { onIntent(PaymentIntent.DismissResult) }
                            )
                            .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    scannerSlot.Content(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                        compact = true
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
            amount = uiState.amount,
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

    hub.groupSheet?.let { sheet ->
        HubGroupBottomSheet(
            sheet = sheet,
            onMemberSelected = { onHubIntent(PaymentHubIntent.SelectItem(it)) },
            onDismiss = { onHubIntent(PaymentHubIntent.DismissGroup) }
        )
    }

    hub.savePrompt?.let { prompt ->
        HubSavePromptBottomSheet(
            prompt = prompt,
            onTitleChange = { onHubIntent(PaymentHubIntent.SavePromptTitleChanged(it)) },
            onSave = { onHubIntent(PaymentHubIntent.SavePromptSave) },
            onDismiss = { onHubIntent(PaymentHubIntent.SavePromptDismiss) }
        )
    }
}

/** Hero glyph plus, unless compact, the app title and camera hint. */
@Composable
private fun ScannerSurface(
    phase: RaylHeroPhase,
    receiptPreimage: String?,
    appTitle: String,
    compact: Boolean,
    modifier: Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PaymentHero(
            modifier = Modifier.fillMaxWidth().weight(1f),
            phase = phase,
            receiptPreimage = receiptPreimage
        )
        if (!compact) {
            BottomLayout(
                title = appTitle,
                subtitle = stringResource(Res.string.point_camera_message_subtitle)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Host-owned prominent scanner surface a lens can request through `openScanner`. */
@Composable
private fun ScannerTakeover(
    scanner: PaymentHubScannerSlot,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        scanner.Content(modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = onDismiss,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .testTag(PaymentTestTags.SCANNER_CLOSE_BUTTON)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.scanner_close)
            )
        }
    }
}

@Composable
private fun HostActionsRow(
    visible: Boolean,
    showTransactionAction: Boolean,
    newSessionTransactionCount: Int,
    transactionAttentionKey: Int,
    onOpenTransactions: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(HOST_ACTIONS_HEIGHT)
                .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!visible) return@Row
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

private val HOST_ACTIONS_HEIGHT = 64.dp
private const val RESOLVING_PAYMENT_INDICATOR_DELAY_MS = 1_000L
private const val TRANSACTION_ACTION_REVEAL_DELAY_MS = 120L
private const val TRANSACTION_ACTION_FADE_IN_MS = 700
