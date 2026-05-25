package xyz.lilsus.papp.presentation.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import lasr.shared.generated.resources.Res
import lasr.shared.generated.resources.app_name_long
import lasr.shared.generated.resources.point_camera_message_subtitle
import lasr.shared.generated.resources.resolving_payment_subtitle
import lasr.shared.generated.resources.resolving_payment_title
import lasr.shared.generated.resources.tap_dismiss_pending
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.domain.model.ContactRole
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.presentation.main.components.BottomLayout
import xyz.lilsus.papp.presentation.main.components.ConfirmationBottomSheet
import xyz.lilsus.papp.presentation.main.components.ManualAmountBottomSheet
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey
import xyz.lilsus.papp.presentation.main.components.ManualAmountUiState
import xyz.lilsus.papp.presentation.main.components.PendingRetryBottomSheet
import xyz.lilsus.papp.presentation.main.components.ResultLayout
import xyz.lilsus.papp.presentation.main.components.SettingsIconButton
import xyz.lilsus.papp.presentation.main.components.hero.Hero
import xyz.lilsus.papp.presentation.main.contacts.ContactsBottomSheet
import xyz.lilsus.papp.presentation.main.contacts.ContactsIconButton
import xyz.lilsus.papp.presentation.main.contacts.ContactsUiState
import xyz.lilsus.papp.presentation.main.contacts.PaySheetTab
import xyz.lilsus.papp.presentation.main.contacts.SaveContactBottomSheet
import xyz.lilsus.papp.presentation.main.scan.QrScannerMode
import xyz.lilsus.papp.presentation.theme.AppTheme

@Composable
fun MainScreen(
    onNavigateSettings: () -> Unit,
    onNavigateConnectWallet: (String) -> Unit,
    uiState: MainUiState,
    wallets: List<WalletInfo> = emptyList(),
    pendingPayments: List<PendingPaymentItem>,
    contactsState: ContactsUiState = ContactsUiState(),
    snackbarHostState: SnackbarHostState,
    onManualAmountKeyPress: (ManualAmountKey) -> Unit = {},
    onManualAmountPreset: (DisplayAmount) -> Unit = {},
    onManualAmountSubmit: () -> Unit = {},
    onManualAmountDismiss: () -> Unit = {},
    onConfirmPaymentSubmit: () -> Unit = {},
    onConfirmPaymentDismiss: () -> Unit = {},
    onPendingRetrySameInvoice: () -> Unit = {},
    onPendingRetryCreateNewInvoice: () -> Unit = {},
    onPendingRetryViewPending: () -> Unit = {},
    onPendingRetryDismiss: () -> Unit = {},
    onResultDismiss: () -> Unit = {},
    onPendingTap: (String) -> Unit = {},
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

    val isWatchingPending = uiState is MainUiState.Loading && uiState.isWatchingPending
    val isDismissable = uiState is MainUiState.Success ||
        uiState is MainUiState.Error ||
        isWatchingPending
    val contentState = if (isResolvingLoading && !showResolvingContent) {
        MainUiState.Active
    } else {
        uiState
    }
    val showActiveContent = contentState.showsActiveContent()

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
        bottomBar = {
            MainBottomActions(
                showShortcutAction = showActiveContent,
                onContactsClick = onContactsOpen,
                onNavigateSettings = onNavigateSettings
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
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
                scannerMode = scannerMode,
                showScannerModeSelector = showScannerModeSelector,
                onToggleScannerMode = onToggleScannerMode
            )
            Crossfade(targetState = contentState) { state ->
                when {
                    state is MainUiState.Success ||
                        state is MainUiState.Error -> ResultLayout(
                        modifier = Modifier.fillMaxSize(),
                        result = state
                    )

                    state is MainUiState.Loading && state.isWatchingPending -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(MaestroTags.Payment.WATCHING_PENDING)
                                .padding(top = 24.dp, start = 24.dp, end = 24.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = stringResource(Res.string.tap_dismiss_pending),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    state is MainUiState.Loading && state.kind == LoadingKind.Resolving -> {
                        ResolvingPaymentLayout(modifier = Modifier.fillMaxSize())
                    }

                    else -> BottomLayout(
                        title = stringResource(Res.string.app_name_long),
                        subtitle = stringResource(Res.string.point_camera_message_subtitle),
                        wallets = wallets,
                        pendingPayments = pendingPayments,
                        onPendingTap = onPendingTap
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
        PendingRetryBottomSheet(
            source = uiState.source,
            onRetrySameInvoice = onPendingRetrySameInvoice,
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
    showShortcutAction: Boolean,
    onContactsClick: () -> Unit,
    onNavigateSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showShortcutAction) {
                ContactsIconButton(onClick = onContactsClick)
            }
            SettingsIconButton(onNavigateSettings = onNavigateSettings)
        }
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

private fun MainUiState.showsActiveContent(): Boolean = when {
    this is MainUiState.Success || this is MainUiState.Error -> false
    this is MainUiState.Loading && isWatchingPending -> false
    this is MainUiState.Loading && kind == LoadingKind.Resolving -> false
    else -> true
}

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
            pendingPayments = emptyList(),
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
            pendingPayments = emptyList(),
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
            pendingPayments = emptyList(),
            snackbarHostState = remember { SnackbarHostState() }
        )
    }
}
