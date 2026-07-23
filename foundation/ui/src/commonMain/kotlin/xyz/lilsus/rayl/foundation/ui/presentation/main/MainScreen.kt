package xyz.lilsus.rayl.foundation.ui.presentation.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.rayl.foundation.ui.MaestroTags
import xyz.lilsus.rayl.foundation.ui.domain.model.ContactRole
import xyz.lilsus.rayl.foundation.ui.domain.model.DisplayAmount
import xyz.lilsus.rayl.foundation.ui.domain.model.DisplayCurrency
import xyz.lilsus.rayl.foundation.ui.generated.resources.Res
import xyz.lilsus.rayl.foundation.ui.generated.resources.app_name_long
import xyz.lilsus.rayl.foundation.ui.generated.resources.open_shortcuts_contacts
import xyz.lilsus.rayl.foundation.ui.generated.resources.point_camera_message_subtitle
import xyz.lilsus.rayl.foundation.ui.generated.resources.resolving_payment_subtitle
import xyz.lilsus.rayl.foundation.ui.generated.resources.resolving_payment_title
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.BottomLayout
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.ConfirmationBottomSheet
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.ManualAmountBottomSheet
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.ManualAmountKey
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.ManualAmountUiState
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.PendingRetryBottomSheet
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.ResultLayout
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.SessionTransactionsIconButton
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.SettingsIconButton
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.hero.Hero
import xyz.lilsus.rayl.foundation.ui.presentation.main.contacts.ContactsBottomSheet
import xyz.lilsus.rayl.foundation.ui.presentation.main.contacts.ContactsUiState
import xyz.lilsus.rayl.foundation.ui.presentation.main.contacts.PaySheetTab
import xyz.lilsus.rayl.foundation.ui.presentation.main.contacts.SaveContactBottomSheet
import xyz.lilsus.rayl.foundation.ui.presentation.main.scan.QrScannerMode
import xyz.lilsus.rayl.foundation.ui.presentation.theme.AppTheme

@Composable
fun MainScreen(
    onNavigateSettings: () -> Unit,
    onNavigateConnectWallet: (String) -> Unit,
    uiState: MainUiState,
    sessionTransactions: List<SessionTransactionItem>,
    newSessionTransactionCount: Int = 0,
    contactsState: ContactsUiState = ContactsUiState(),
    snackbarHostState: SnackbarHostState,
    onManualAmountKeyPress: (ManualAmountKey) -> Unit = {},
    onManualAmountPreset: (DisplayAmount) -> Unit = {},
    onManualAmountSubmit: () -> Unit = {},
    onManualAmountDismiss: () -> Unit = {},
    onConfirmPaymentSubmit: () -> Unit = {},
    onConfirmPaymentDismiss: () -> Unit = {},
    onPendingRetryCreateNewInvoice: () -> Unit = {},
    onPendingRetryViewPending: () -> Unit = {},
    onPendingRetryDismiss: () -> Unit = {},
    onOpenTransactions: () -> Unit = {},
    onResultDismiss: () -> Unit = {},
    onContactsOpen: () -> Unit = {},
    onContactsDismiss: () -> Unit = {},
    onPaySheetTabSelected: (PaySheetTab) -> Unit = {},
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
    val isResolvingLoading = uiState is MainUiState.Loading &&
        uiState.kind == LoadingKind.Resolving
    LaunchedEffect(uiState) {
        showResolvingContent = false
        if (isResolvingLoading) {
            delay(RESOLVING_PAYMENT_INDICATOR_DELAY_MS)
            showResolvingContent = true
        }
    }

    val isDismissable = uiState is MainUiState.Success ||
        uiState is MainUiState.Error
    val contentState = if (isResolvingLoading && !showResolvingContent) {
        MainUiState.Active
    } else {
        uiState
    }
    val showBottomActions = contentState !is MainUiState.Success &&
        contentState !is MainUiState.Error
    val receiptPreimage = (contentState as? MainUiState.Success)
        ?.preimage
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    var showReceipt by remember { mutableStateOf(false) }
    LaunchedEffect(receiptPreimage) {
        showReceipt = false
    }
    val showTransactionAction = showBottomActions &&
        sessionTransactions.isNotEmpty() &&
        contentState == MainUiState.Active
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
        if (contentState == MainUiState.Active) {
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
        modifier = modifier.testTag(MaestroTags.Payment.SCREEN),
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
                MainBottomActions(
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
            Hero(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                uiState = uiState,
                receiptPreimage = receiptPreimage.takeIf { showReceipt },
                scannerMode = scannerMode,
                showScannerModeSelector = showScannerModeSelector,
                onToggleScannerMode = onToggleScannerMode
            )
            Crossfade(targetState = contentState) { state ->
                when {
                    state is MainUiState.Success ||
                        state is MainUiState.Error -> ResultLayout(
                        modifier = Modifier.fillMaxSize(),
                        result = state,
                        receiptVisible = showReceipt,
                        onViewReceipt = { showReceipt = true }
                    )

                    state is MainUiState.Loading && state.kind == LoadingKind.Resolving -> {
                        ResolvingPaymentLayout(modifier = Modifier.fillMaxSize())
                    }

                    else -> BottomLayout(
                        title = stringResource(Res.string.app_name_long),
                        subtitle = stringResource(Res.string.point_camera_message_subtitle)
                    )
                }
            }
        }
    }

    if (uiState is MainUiState.EnterAmount) {
        ManualAmountBottomSheet(
            state = uiState.entry,
            onKeyPress = onManualAmountKeyPress,
            onRangeClick = onManualAmountPreset,
            onSubmit = onManualAmountSubmit,
            onDismiss = onManualAmountDismiss
        )
    }

    if (uiState is MainUiState.Confirm) {
        ConfirmationBottomSheet(
            confirmAmount = uiState.amount,
            onPay = onConfirmPaymentSubmit,
            onDismiss = onConfirmPaymentDismiss
        )
    }

    if (uiState is MainUiState.PendingRetry) {
        val retryTransaction = sessionTransactions.firstOrNull {
            it.id == uiState.id
        }
        PendingRetryBottomSheet(
            status = retryTransaction?.status ?: PendingStatus.Waiting,
            onCreateNewInvoice = onPendingRetryCreateNewInvoice,
            onViewPending = onPendingRetryViewPending,
            onDismiss = onPendingRetryDismiss
        )
    }

    if (contactsState.isOpen) {
        ContactsBottomSheet(
            state = contactsState,
            onDismiss = onContactsDismiss,
            onTabSelected = onPaySheetTabSelected,
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

@Composable
private fun MainBottomActions(
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

fun Modifier.tapToDismiss(enabled: Boolean, onDismiss: () -> Unit) = clickable(
    enabled = enabled,
    indication = null,
    interactionSource = null
) { onDismiss() }

private const val RESOLVING_PAYMENT_INDICATOR_DELAY_MS = 1_000L
private const val TRANSACTION_ACTION_REVEAL_DELAY_MS = 120L
private const val TRANSACTION_ACTION_FADE_IN_MS = 700

@Preview
@Composable
fun MainScreenPreviewSuccess() {
    AppTheme {
        MainScreen(
            onNavigateSettings = {},
            onNavigateConnectWallet = {},
            uiState = MainUiState.Success(
                amountPaid = DisplayAmount(12345, DisplayCurrency.Satoshi),
                feePaid = DisplayAmount(69, DisplayCurrency.Satoshi)
            ),
            sessionTransactions = emptyList(),
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}

@Preview
@Composable
fun MainScreenPreviewEnterAmount() {
    AppTheme {
        MainScreen(
            onNavigateSettings = {},
            onNavigateConnectWallet = {},
            uiState = MainUiState.EnterAmount(
                entry = ManualAmountUiState(
                    amount = DisplayAmount(123, DisplayCurrency.Satoshi),
                    currency = DisplayCurrency.Satoshi,
                    min = DisplayAmount(10, DisplayCurrency.Satoshi),
                    max = DisplayAmount(1000, DisplayCurrency.Satoshi),
                    allowDecimal = false
                )
            ),
            sessionTransactions = emptyList(),
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}

@Preview
@Composable
fun MainScreenPreviewConfirm() {
    AppTheme {
        MainScreen(
            onNavigateSettings = {},
            onNavigateConnectWallet = {},
            uiState = MainUiState.Confirm(
                amount = DisplayAmount(500_000, DisplayCurrency.Satoshi)
            ),
            sessionTransactions = emptyList(),
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
