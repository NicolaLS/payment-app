@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package xyz.lilsus.rayl.blip.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import xyz.lilsus.rayl.blip.data.UnsupportedInput
import xyz.lilsus.rayl.blip.domain.ConfirmationMode
import xyz.lilsus.rayl.blip.domain.ConnectBlinkOutcome
import xyz.lilsus.rayl.blip.domain.ConnectionStatus
import xyz.lilsus.rayl.blip.domain.CurrencyCode
import xyz.lilsus.rayl.blip.domain.PaymentAttempt
import xyz.lilsus.rayl.blip.domain.PaymentAttemptState
import xyz.lilsus.rayl.blip.domain.PaymentFailure
import xyz.lilsus.rayl.blip.domain.PaymentOrigin
import xyz.lilsus.rayl.blip.platform.AppThemePreference
import xyz.lilsus.rayl.blip.platform.BlipRuntime
import xyz.lilsus.rayl.blip.platform.ScannerViewport

private sealed interface AppRoute {
    data object Home : AppRoute
    data object Settings : AppRoute
    data object Wallet : AppRoute
    data object Contacts : AppRoute
    data object PaymentSettings : AppRoute
    data object Currency : AppRoute
    data object Theme : AppRoute
    data object Language : AppRoute
    data object Transactions : AppRoute
    data class Transaction(val id: String) : AppRoute
}

@Composable
fun BlipApp(
    runtime: BlipRuntime,
    incomingPaymentUri: String?,
    onIncomingPaymentUriConsumed: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val onboardingStore = remember(runtime, scope) { OnboardingStore(runtime, scope) }
    val payStore = remember(runtime, scope) { PayStore(runtime, scope) }
    val settingsStore = remember(runtime, scope) { SettingsStore(runtime, scope) }
    val preferences by runtime.preferences.values.collectAsState()
    val payState by payStore.state.collectAsState()
    val settingsState by settingsStore.state.collectAsState()
    var route by remember { mutableStateOf<AppRoute>(AppRoute.Home) }
    var pendingInput by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(incomingPaymentUri) {
        if (!incomingPaymentUri.isNullOrBlank()) {
            pendingInput = incomingPaymentUri
            onIncomingPaymentUriConsumed()
        }
    }
    LaunchedEffect(preferences.onboardingComplete, pendingInput) {
        val input = pendingInput
        if (preferences.onboardingComplete && input != null) {
            route = AppRoute.Home
            payStore.dispatch(PayAction.Resolve(input, PaymentOrigin.AppLink))
            pendingInput = null
        }
    }
    LaunchedEffect(preferences.onboardingComplete) {
        if (preferences.onboardingComplete) {
            payStore.dispatch(PayAction.Reconcile)
            settingsStore.dispatch(SettingsAction.Refresh)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && preferences.onboardingComplete) {
                payStore.dispatch(PayAction.Reconcile)
                settingsStore.dispatch(SettingsAction.Refresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BlipTheme(preference = preferences.theme) {
        if (!preferences.onboardingComplete) {
            OnboardingScreen(
                store = onboardingStore,
                modifier = Modifier.fillMaxSize()
            )
            return@BlipTheme
        }

        when (val current = route) {
            AppRoute.Home -> PaymentScreen(
                runtime = runtime,
                state = payState,
                store = payStore,
                onSettings = { route = AppRoute.Settings },
                onTransactions = { route = AppRoute.Transactions },
                onContacts = {
                    settingsStore.dispatch(SettingsAction.Refresh)
                    route = AppRoute.Contacts
                }
            )

            AppRoute.Settings -> SettingsScreen(
                state = settingsState,
                onBack = { route = AppRoute.Home },
                onWallet = { route = AppRoute.Wallet },
                onContacts = { route = AppRoute.Contacts },
                onPayments = { route = AppRoute.PaymentSettings },
                onCurrency = { route = AppRoute.Currency },
                onTheme = { route = AppRoute.Theme },
                onLanguage = { route = AppRoute.Language },
                onDonate = { sats ->
                    route = AppRoute.Home
                    payStore.dispatch(
                        PayAction.Resolve(
                            input = "lilsus@blink.sv",
                            origin = PaymentOrigin.Shortcut,
                            suggestedSats = sats
                        )
                    )
                }
            )

            AppRoute.Wallet -> WalletScreen(
                runtime = runtime,
                state = settingsState,
                store = settingsStore,
                onBack = { route = AppRoute.Settings }
            )

            AppRoute.Contacts -> ContactsScreen(
                state = settingsState,
                store = settingsStore,
                onBack = { route = AppRoute.Settings },
                onPay = { address, sats ->
                    route = AppRoute.Home
                    payStore.dispatch(
                        PayAction.Resolve(
                            input = address,
                            origin = PaymentOrigin.Shortcut,
                            suggestedSats = sats
                        )
                    )
                }
            )

            AppRoute.PaymentSettings -> PaymentSettingsScreen(
                runtime = runtime,
                onBack = { route = AppRoute.Settings }
            )

            AppRoute.Currency -> CurrencyScreen(
                runtime = runtime,
                onBack = { route = AppRoute.Settings }
            )

            AppRoute.Theme -> ThemeScreen(
                runtime = runtime,
                onBack = { route = AppRoute.Settings }
            )

            AppRoute.Language -> LanguageScreen(
                runtime = runtime,
                onBack = { route = AppRoute.Settings }
            )

            AppRoute.Transactions -> TransactionsScreen(
                attempts = payState.attempts,
                onBack = { route = AppRoute.Home },
                onSelect = { route = AppRoute.Transaction(it.id.value) }
            )

            is AppRoute.Transaction -> TransactionScreen(
                attempt = payState.attempts.firstOrNull { it.id.value == current.id },
                onBack = { route = AppRoute.Transactions }
            )
        }
    }
}

@Composable
private fun OnboardingScreen(store: OnboardingStore, modifier: Modifier = Modifier) {
    val state by store.state.collectAsState()
    var apiKey by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("My Blink Wallet") }

    OnboardingFrame(
        modifier = modifier,
        showBack = state.step != OnboardingStep.Welcome,
        onBack = { store.dispatch(OnboardingAction.Back) }
    ) {
        when (state.step) {
            OnboardingStep.Welcome -> {
                HeroMark(modifier = Modifier.size(180.dp), state = HeroState.Active)
                Spacer(Modifier.height(32.dp))
                Text(
                    text = "Welcome to Blip",
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "by Bitcoin Coast",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "The quickest way to pay with the wallet you already use.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = { store.dispatch(OnboardingAction.Continue) }) {
                    Text("Get Started")
                }
            }

            OnboardingStep.Features -> {
                Icon(
                    Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                Text("Quiet scanner, quick results", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Blip scans continuously so checkout stays smooth. Aim at the code, wait a beat, and you’re good.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Your wallet stays your wallet. Blip only submits payments you ask it to make.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                Button(onClick = { store.dispatch(OnboardingAction.Continue) }) {
                    Text("Continue")
                }
            }

            OnboardingStep.Agreement -> {
                Text("Before you continue", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(20.dp))
                Text(
                    "Blip is a payment interface, not a wallet. Payments may be irreversible. " +
                        "You remain responsible for verifying the recipient, amount, and your wallet permissions.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        store.dispatch(
                            OnboardingAction.SetAgreement(!state.agreementAccepted)
                        )
                    }
                ) {
                    Checkbox(
                        checked = state.agreementAccepted,
                        onCheckedChange = {
                            store.dispatch(OnboardingAction.SetAgreement(it))
                        }
                    )
                    Text("I understand and accept these risks.")
                }
                Spacer(Modifier.height(28.dp))
                Button(
                    enabled = state.agreementAccepted,
                    onClick = { store.dispatch(OnboardingAction.Continue) }
                ) {
                    Text("Continue")
                }
            }

            OnboardingStep.Provider -> {
                Text(
                    "What wallet will you connect?",
                    style = MaterialTheme.typography.headlineLarge
                )
                Spacer(Modifier.height(24.dp))
                ProviderCard(
                    title = "Blink",
                    subtitle = "Connect using an API key from your Blink account.",
                    enabled = true,
                    onClick = { store.dispatch(OnboardingAction.ChooseBlink) }
                )
                Spacer(Modifier.height(12.dp))
                ProviderCard(
                    title = "NWC Wallet",
                    subtitle = "Unavailable in Blip. Use Lasr for Nostr Wallet Connect.",
                    enabled = false,
                    onClick = {}
                )
            }

            OnboardingStep.Credentials -> {
                Text("Add Blink Wallet", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create an API key at dashboard.blink.sv with permission to send payments.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Wallet name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ConnectionFailure(state.failure)
                Spacer(Modifier.height(20.dp))
                Button(
                    enabled = apiKey.isNotBlank() && alias.isNotBlank() && !state.connecting,
                    onClick = {
                        store.dispatch(OnboardingAction.Connect(apiKey, alias))
                        apiKey = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingFrame(
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
    ) {
        if (showBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

@Composable
private fun ProviderCard(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Wallet, contentDescription = null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!enabled) Text("Unavailable", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ConnectionFailure(failure: ConnectBlinkOutcome?) {
    val message = when (failure) {
        null, is ConnectBlinkOutcome.Connected -> return

        ConnectBlinkOutcome.InvalidInput -> "Enter a wallet name and API key."

        ConnectBlinkOutcome.InvalidApiKey -> "Authentication failed. Check your API key."

        ConnectBlinkOutcome.PermissionDenied ->
            "This API key cannot send payments. Enable all required permissions."

        ConnectBlinkOutcome.RateLimited -> "Too many requests. Wait a moment and try again."

        ConnectBlinkOutcome.NetworkUnavailable -> "Could not reach Blink. Check your connection."

        ConnectBlinkOutcome.Unexpected -> "Blink returned an unexpected response."
    }
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 12.dp)
    )
}

@Composable
private fun PaymentScreen(
    runtime: BlipRuntime,
    state: PayUiState,
    store: PayStore,
    onSettings: () -> Unit,
    onTransactions: () -> Unit,
    onContacts: () -> Unit
) {
    var showInput by rememberSaveable { mutableStateOf(false) }
    var manualInput by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val active = state.mode is PayMode.Active

    LaunchedEffect(state.permissionDenied) {
        if (state.permissionDenied) {
            snackbar.showSnackbar("Camera access is required to scan QR codes.")
        }
    }

    Scaffold(
        modifier = Modifier.testTag("payment_screen"),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.attempts.isNotEmpty()) {
                    IconButton(onClick = onTransactions) {
                        Icon(Icons.Rounded.History, contentDescription = "Transactions")
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            ) {
                ScannerViewport(
                    active = active,
                    onQrCode = { value ->
                        if (runtime.preferences.values.value.payments.vibrateOnScan) {
                            runtime.platform.haptic()
                        }
                        store.dispatch(PayAction.Resolve(value, PaymentOrigin.Scan))
                    },
                    onPermissionDenied = {
                        store.dispatch(PayAction.CameraPermissionDenied)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f))
                )
                HeroMark(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(230.dp),
                    state = state.mode.toHeroState()
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Blip",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    when (state.mode) {
                        PayMode.Active -> "Point the camera at an invoice."
                        PayMode.Resolving -> "Preparing payment. Nothing has been sent yet."
                        is PayMode.EnterAmount -> "Enter the amount to pay."
                        is PayMode.Confirm -> "Review this payment."
                        is PayMode.Paying -> "Waiting for Blink…"
                        is PayMode.Result -> state.mode.attempt.state.displayName()
                        is PayMode.Duplicate -> "This invoice was already submitted."
                        is PayMode.Error -> state.mode.displayMessage()
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                if (active) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledIconButton(
                            onClick = {
                                scope.launch {
                                    runtime.platform.readClipboard()?.let {
                                        store.dispatch(PayAction.Resolve(it, PaymentOrigin.Paste))
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste")
                        }
                        FilledIconButton(onClick = { showInput = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Enter request")
                        }
                        FilledIconButton(onClick = onContacts) {
                            Icon(Icons.Rounded.People, contentDescription = "Contacts")
                        }
                    }
                } else if (
                    state.mode is PayMode.Result ||
                    state.mode is PayMode.Error ||
                    state.mode is PayMode.Duplicate
                ) {
                    TextButton(onClick = { store.dispatch(PayAction.Dismiss) }) {
                        Text("Tap to continue")
                    }
                }
            }
        }
    }

    if (showInput) {
        AlertDialog(
            onDismissRequest = { showInput = false },
            title = { Text("Enter payment request") },
            text = {
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    label = { Text("Invoice, address, or LNURL") },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    enabled = manualInput.isNotBlank(),
                    onClick = {
                        val value = manualInput
                        manualInput = ""
                        showInput = false
                        store.dispatch(PayAction.Resolve(value, PaymentOrigin.Manual))
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInput = false }) { Text("Cancel") }
            }
        )
    }

    when (val mode = state.mode) {
        is PayMode.EnterAmount -> AmountSheet(mode, store, runtime)
        is PayMode.Confirm -> ConfirmationDialog(mode, store, runtime)
        is PayMode.Result -> PaymentResultDialog(mode.attempt, store)
        is PayMode.Duplicate -> DuplicateDialog(mode.attempt, store)
        is PayMode.Error -> ErrorDialog(mode, store)
        else -> Unit
    }
}

@Composable
private fun AmountSheet(mode: PayMode.EnterAmount, store: PayStore, runtime: BlipRuntime) {
    val preferences by runtime.preferences.values.collectAsState()
    val currency = CurrencyCode.parse(preferences.primaryCurrency) ?: CurrencyCode.Sat
    var amount by remember(mode) {
        mutableStateOf(
            if (currency == CurrencyCode.Sat) mode.suggestedSats?.toString().orEmpty() else ""
        )
    }
    var comment by remember(mode) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { store.dispatch(PayAction.Dismiss) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(24.dp)
        ) {
            Text("Enter amount", style = MaterialTheme.typography.headlineMedium)
            val range = listOfNotNull(
                mode.minSats?.let { "Min $it sats" },
                mode.maxSats?.let { "Max $it sats" }
            ).joinToString(" • ")
            if (range.isNotEmpty()) {
                Text(range, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = {
                    if (it.count { character -> character == '.' } <= 1 &&
                        it.all { character -> character.isDigit() || character == '.' }
                    ) {
                        amount = it
                    }
                },
                label = { Text(currency.value) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (mode.pending is PendingAmount.Lnurl &&
                mode.pending.value.commentAllowed > 0
            ) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = {
                        comment = it.take(mode.pending.value.commentAllowed)
                    },
                    label = { Text("Comment (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (amount.isNotBlank()) {
                        store.dispatch(
                            PayAction.SubmitAmount(
                                value = amount,
                                currency = currency,
                                comment = comment
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConfirmationDialog(mode: PayMode.Confirm, store: PayStore, runtime: BlipRuntime) {
    val preferences by runtime.preferences.values.collectAsState()
    val primary = CurrencyCode.parse(preferences.primaryCurrency) ?: CurrencyCode.Sat
    val secondary = CurrencyCode.parse(preferences.secondaryCurrency)
    val primaryText = runtime.exchangeRates.format(
        amount = mode.draft.amount,
        currency = primary,
        snapshot = mode.draft.rateSnapshot
    ) ?: "${mode.draft.amount.msat.roundUpToSats()} SAT"
    val secondaryText = secondary?.let {
        runtime.exchangeRates.format(mode.draft.amount, it, mode.draft.rateSnapshot)
    }
    AlertDialog(
        onDismissRequest = { store.dispatch(PayAction.Dismiss) },
        icon = { Icon(Icons.Rounded.Wallet, contentDescription = null) },
        title = { Text("Confirm Payment") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    primaryText,
                    style = MaterialTheme.typography.headlineLarge
                )
                secondaryText?.takeIf { it != primaryText }?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                mode.draft.memo?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = { store.dispatch(PayAction.Confirm) }) {
                Text("Pay")
            }
        },
        dismissButton = {
            TextButton(onClick = { store.dispatch(PayAction.Dismiss) }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PaymentResultDialog(attempt: PaymentAttempt, store: PayStore) {
    val success = attempt.state in setOf(
        PaymentAttemptState.Settled,
        PaymentAttemptState.AlreadyPaid
    )
    AlertDialog(
        onDismissRequest = { store.dispatch(PayAction.Dismiss) },
        icon = {
            Icon(
                if (success) Icons.Rounded.Check else Icons.Rounded.Refresh,
                contentDescription = null,
                tint = if (success) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        },
        title = { Text(attempt.state.displayName()) },
        text = {
            Column {
                Text("${attempt.amount.msat.roundUpToSats()} sats")
                if (attempt.state in setOf(
                        PaymentAttemptState.Pending,
                        PaymentAttemptState.Unknown
                    )
                ) {
                    Text(
                        "The result is not final. Blip will keep checking in the background.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                attempt.failure?.let { Text(it.displayMessage()) }
            }
        },
        confirmButton = {
            TextButton(onClick = { store.dispatch(PayAction.Dismiss) }) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun DuplicateDialog(attempt: PaymentAttempt, store: PayStore) {
    AlertDialog(
        onDismissRequest = { store.dispatch(PayAction.Dismiss) },
        title = { Text("Invoice already submitted") },
        text = {
            Text(
                "The existing payment is ${attempt.state.displayName().lowercase()}. " +
                    "Blip did not submit it again."
            )
        },
        confirmButton = {
            TextButton(onClick = { store.dispatch(PayAction.Dismiss) }) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun ErrorDialog(mode: PayMode.Error, store: PayStore) {
    AlertDialog(
        onDismissRequest = { store.dispatch(PayAction.Dismiss) },
        icon = {
            Icon(
                Icons.Rounded.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Couldn’t prepare payment") },
        text = { Text(mode.displayMessage()) },
        confirmButton = {
            TextButton(onClick = { store.dispatch(PayAction.Dismiss) }) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun HeroMark(modifier: Modifier, state: HeroState) {
    val color = when (state) {
        HeroState.Active -> MaterialTheme.colorScheme.onSurfaceVariant
        HeroState.Working -> MaterialTheme.colorScheme.primary
        HeroState.Success -> MaterialTheme.colorScheme.tertiary
        HeroState.Error -> MaterialTheme.colorScheme.error
    }
    Canvas(modifier) {
        val side = size.minDimension
        val stroke = Stroke(width = side * 0.025f, cap = StrokeCap.Round)
        val corner = side * 0.18f
        listOf(
            Triple(0f, 0f, 180f),
            Triple(side - corner, 0f, 270f),
            Triple(0f, side - corner, 90f),
            Triple(side - corner, side - corner, 0f)
        ).forEach { (x, y, angle) ->
            drawArc(
                color = color,
                startAngle = angle,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(x, y),
                size = Size(corner, corner),
                style = stroke
            )
        }
        val finderSize = side * 0.24f
        val gap = side * 0.12f
        listOf(
            Offset(gap, gap),
            Offset(side - gap - finderSize, gap),
            Offset(gap, side - gap - finderSize)
        ).forEach { topLeft ->
            drawRoundRect(
                color = color,
                topLeft = topLeft,
                size = Size(finderSize, finderSize),
                cornerRadius = CornerRadius(side * 0.015f),
                style = Stroke(width = side * 0.025f)
            )
            drawRoundRect(
                color = color,
                topLeft = topLeft + Offset(finderSize * 0.3f, finderSize * 0.3f),
                size = Size(finderSize * 0.4f, finderSize * 0.4f),
                cornerRadius = CornerRadius(side * 0.01f)
            )
        }
        val bolt = Path().apply {
            moveTo(side * 0.55f, side * 0.28f)
            lineTo(side * 0.38f, side * 0.56f)
            lineTo(side * 0.49f, side * 0.56f)
            lineTo(side * 0.44f, side * 0.75f)
            lineTo(side * 0.67f, side * 0.45f)
            lineTo(side * 0.55f, side * 0.45f)
            close()
        }
        drawPath(bolt, color)
    }
}

private enum class HeroState {
    Active,
    Working,
    Success,
    Error
}

private fun PayMode.toHeroState(): HeroState = when (this) {
    PayMode.Active -> HeroState.Active

    PayMode.Resolving,
    is PayMode.EnterAmount,
    is PayMode.Confirm,
    is PayMode.Paying,
    is PayMode.Duplicate
    -> HeroState.Working

    is PayMode.Result ->
        if (attempt.state in setOf(
                PaymentAttemptState.Settled,
                PaymentAttemptState.AlreadyPaid
            )
        ) {
            HeroState.Success
        } else if (attempt.state == PaymentAttemptState.Rejected) {
            HeroState.Error
        } else {
            HeroState.Working
        }

    is PayMode.Error -> HeroState.Error
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onWallet: () -> Unit,
    onContacts: () -> Unit,
    onPayments: () -> Unit,
    onCurrency: () -> Unit,
    onTheme: () -> Unit,
    onLanguage: () -> Unit,
    onDonate: (Long) -> Unit
) {
    StandardScaffold(title = "Settings", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                SettingsRow(
                    icon = Icons.Rounded.AccountBalanceWallet,
                    title = "Wallet",
                    subtitle = state.connection?.alias ?: "Not connected",
                    onClick = onWallet
                )
                SettingsRow(
                    icon = Icons.Rounded.People,
                    title = "Contacts",
                    subtitle = "${state.contacts.size} saved • ${state.shortcuts.size} shortcuts",
                    onClick = onContacts
                )
                SettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = "Payments",
                    subtitle = "Confirmations and vibrations",
                    onClick = onPayments
                )
                SettingsRow(
                    icon = Icons.Rounded.Wallet,
                    title = "Currency",
                    subtitle = "Primary ${state.preferences.primaryCurrency} • " +
                        "Secondary ${state.preferences.secondaryCurrency}",
                    onClick = onCurrency
                )
                SettingsRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "Theme",
                    subtitle = state.preferences.theme.name,
                    onClick = onTheme
                )
                SettingsRow(
                    icon = Icons.Rounded.Language,
                    title = "Language",
                    subtitle = state.preferences.language,
                    onClick = onLanguage
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Column(Modifier.padding(20.dp)) {
                    Text("Enjoying Blip?", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Help us grow and improve by supporting development.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        AssistChip(onClick = { onDonate(1_000L) }, label = { Text("1k 🎁") })
                        AssistChip(onClick = { onDonate(5_000L) }, label = { Text("5k 🚀") })
                        AssistChip(onClick = { onDonate(10_000L) }, label = { Text("10k ✨") })
                    }
                }
                Text(
                    "Blip 1.0.0",
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun WalletScreen(
    runtime: BlipRuntime,
    state: SettingsUiState,
    store: SettingsStore,
    onBack: () -> Unit
) {
    var confirmDisconnect by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("My Blink Wallet") }
    var connecting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ConnectBlinkOutcome?>(null) }
    val scope = rememberCoroutineScope()

    StandardScaffold(title = "Wallet", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(20.dp)
        ) {
            val connection = state.connection
            if (connection == null) {
                Text("Connect Blink", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Your API key is encrypted by the device credential vault and never stored in the database.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Wallet name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                ConnectionFailure(failure)
                Spacer(Modifier.height(20.dp))
                Button(
                    enabled = !connecting && apiKey.isNotBlank(),
                    onClick = {
                        val key = apiKey
                        apiKey = ""
                        connecting = true
                        scope.launch {
                            failure = runtime.gateway.connect(key, alias)
                            connecting = false
                            store.dispatch(SettingsAction.Refresh)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (connecting) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                    } else {
                        Text("Connect")
                    }
                }
            } else {
                Text(connection.alias, style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Blink • ${connection.status.displayName()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                DetailRow("Connection ID", connection.id.value)
                DetailRow("Account", connection.accountId.value)
                DetailRow("Default wallet", connection.walletId.value)
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { store.dispatch(SettingsAction.RefreshWallet) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh default wallet")
                }
                OutlinedButton(
                    onClick = { store.dispatch(SettingsAction.ImportBlinkContacts) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import Blink contacts")
                }
                state.message?.let {
                    Text(it, modifier = Modifier.padding(vertical = 12.dp))
                }
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { confirmDisconnect = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove Wallet", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Remove wallet?") },
            text = {
                Text(
                    "The API key will be erased. Historical payment attempts stay available " +
                        "and unresolved attempts are not deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDisconnect = false
                        store.dispatch(SettingsAction.Disconnect)
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Text(
        value,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun ContactsScreen(
    state: SettingsUiState,
    store: SettingsStore,
    onBack: () -> Unit,
    onPay: (String, Long?) -> Unit
) {
    var showContactForm by remember { mutableStateOf(false) }
    var showShortcutForm by remember { mutableStateOf(false) }
    StandardScaffold(title = "Contacts & Shortcuts", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showContactForm = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Add contact") }
                    OutlinedButton(
                        onClick = { showShortcutForm = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Add shortcut") }
                }
                TextButton(
                    onClick = { store.dispatch(SettingsAction.ImportBlinkContacts) },
                    enabled = !state.busy,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text("Import from Blink")
                }
                state.message?.let {
                    Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                SectionLabel("Shortcuts")
            }
            items(state.shortcuts, key = { "shortcut-${it.id.value}" }) { shortcut ->
                ListItem(
                    headlineContent = { Text(shortcut.label) },
                    supportingContent = {
                        Text(
                            buildString {
                                shortcut.amount?.let {
                                    append("${it.roundUpToSats()} sats • ")
                                }
                                append(shortcut.lightningAddress)
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        onPay(shortcut.lightningAddress, shortcut.amount?.roundUpToSats())
                    },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                store.dispatch(SettingsAction.DeleteShortcut(shortcut.id))
                            }
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete shortcut")
                        }
                    }
                )
            }
            item { SectionLabel("Contacts") }
            items(state.contacts, key = { "contact-${it.id.value}" }) { contact ->
                ListItem(
                    headlineContent = { Text(contact.name) },
                    supportingContent = { Text(contact.lightningAddress) },
                    modifier = Modifier.clickable { onPay(contact.lightningAddress, null) },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                store.dispatch(SettingsAction.DeleteContact(contact.id))
                            }
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete contact")
                        }
                    }
                )
            }
        }
    }
    if (showContactForm) {
        ContactForm(
            onDismiss = { showContactForm = false },
            onSave = { name, address ->
                store.dispatch(SettingsAction.AddContact(name, address))
                showContactForm = false
            }
        )
    }
    if (showShortcutForm) {
        ShortcutForm(
            onDismiss = { showShortcutForm = false },
            onSave = { label, address, sats ->
                store.dispatch(SettingsAction.AddShortcut(label, address, sats))
                showShortcutForm = false
            }
        )
    }
}

@Composable
private fun SectionLabel(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun ContactForm(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add contact") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Alias") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Lightning address") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, address) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ShortcutForm(onDismiss: () -> Unit, onSave: (String, String, Long?) -> Unit) {
    var label by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add shortcut") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Shortcut name") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Lightning address") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all(Char::isDigit)) amount = it },
                    label = { Text("Satoshis (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label, address, amount.toLongOrNull()) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PaymentSettingsScreen(runtime: BlipRuntime, onBack: () -> Unit) {
    val preferences by runtime.preferences.values.collectAsState()
    val payments = preferences.payments
    var threshold by remember(payments.thresholdSats) {
        mutableStateOf(payments.thresholdSats.toString())
    }
    StandardScaffold(title = "Payments", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(20.dp)
        ) {
            Text("Confirm Payment", style = MaterialTheme.typography.titleLarge)
            ChoiceRow(
                title = "Always",
                selected = payments.confirmationMode == ConfirmationMode.Always,
                onClick = {
                    runtime.preferences.setConfirmationMode(ConfirmationMode.Always)
                }
            )
            ChoiceRow(
                title = "Above threshold",
                selected = payments.confirmationMode == ConfirmationMode.AboveThreshold,
                onClick = {
                    runtime.preferences.setConfirmationMode(ConfirmationMode.AboveThreshold)
                }
            )
            OutlinedTextField(
                value = threshold,
                onValueChange = {
                    if (it.all(Char::isDigit)) {
                        threshold = it
                        it.toLongOrNull()?.let(runtime.preferences::setConfirmationThreshold)
                    }
                },
                label = { Text("Threshold in sats") },
                enabled = payments.confirmationMode == ConfirmationMode.AboveThreshold,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            ToggleRow(
                "Confirm amounts I enter manually",
                payments.confirmManualEntry,
                runtime.preferences::setConfirmManualEntry
            )
            ToggleRow(
                "Always confirm shortcut payments",
                payments.confirmShortcutPayments,
                runtime.preferences::setConfirmShortcuts
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Vibrations", style = MaterialTheme.typography.titleLarge)
            ToggleRow(
                "Vibrate when a code is recognized",
                payments.vibrateOnScan,
                runtime.preferences::setVibrateOnScan
            )
            ToggleRow(
                "Vibrate on successful payment",
                payments.vibrateOnPayment,
                runtime.preferences::setVibrateOnPayment
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title)
    }
}

@Composable
private fun CurrencyScreen(runtime: BlipRuntime, onBack: () -> Unit) {
    val preferences by runtime.preferences.values.collectAsState()
    val currencies = listOf("SAT", "BTC", "USD", "EUR", "GBP", "CAD", "AUD", "CHF", "JPY")
    StandardScaffold(title = "Currency", onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionLabel("Primary") }
            items(currencies) { currency ->
                ChoiceRow(
                    title = currency,
                    selected = preferences.primaryCurrency == currency,
                    onClick = { runtime.preferences.setPrimaryCurrency(currency) }
                )
            }
            item { SectionLabel("Secondary") }
            items(currencies) { currency ->
                ChoiceRow(
                    title = currency,
                    selected = preferences.secondaryCurrency == currency,
                    onClick = { runtime.preferences.setSecondaryCurrency(currency) }
                )
            }
        }
    }
}

@Composable
private fun ThemeScreen(runtime: BlipRuntime, onBack: () -> Unit) {
    val preferences by runtime.preferences.values.collectAsState()
    StandardScaffold(title = "Theme", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            AppThemePreference.entries.forEach { theme ->
                ChoiceRow(
                    title = theme.name,
                    selected = preferences.theme == theme,
                    onClick = { runtime.preferences.setTheme(theme) }
                )
            }
        }
    }
}

@Composable
private fun LanguageScreen(runtime: BlipRuntime, onBack: () -> Unit) {
    val preferences by runtime.preferences.values.collectAsState()
    val languages = listOf(
        "system" to "Use device language",
        "en" to "English",
        "de" to "Deutsch",
        "es" to "Español"
    )
    StandardScaffold(title = "Language", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
            languages.forEach { (code, label) ->
                ChoiceRow(
                    title = label,
                    selected = preferences.language == code,
                    onClick = { runtime.preferences.setLanguage(code) }
                )
            }
        }
    }
}

@Composable
private fun TransactionsScreen(
    attempts: List<PaymentAttempt>,
    onBack: () -> Unit,
    onSelect: (PaymentAttempt) -> Unit
) {
    StandardScaffold(title = "Transactions", onBack = onBack) { padding ->
        if (attempts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No transactions yet.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(attempts, key = { it.id.value }) { attempt ->
                    ListItem(
                        headlineContent = {
                            Text("${attempt.amount.msat.roundUpToSats()} sats")
                        },
                        supportingContent = {
                            Text(attempt.state.displayName())
                        },
                        trailingContent = {
                            Text(
                                attempt.origin.name,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        modifier = Modifier.clickable { onSelect(attempt) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionScreen(attempt: PaymentAttempt?, onBack: () -> Unit) {
    StandardScaffold(title = "Transaction", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(20.dp)
        ) {
            if (attempt == null) {
                Text("Transaction not found.")
                return@Column
            }
            Text(
                "${attempt.amount.msat.roundUpToSats()} sats",
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                attempt.state.displayName(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            DetailRow("Attempt ID", attempt.id.value)
            DetailRow("Connection generation", attempt.connectionId.value)
            DetailRow("Payment hash", attempt.paymentHash.hex)
            DetailRow("Origin", attempt.origin.name)
            attempt.feesPaid?.let { DetailRow("Fee", "${it.msat} msat") }
            attempt.preimage?.let { DetailRow("Preimage", it.toHex()) }
            attempt.failure?.let { DetailRow("Failure", it.displayMessage()) }
        }
    }
}

@Composable
private fun StandardScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        content = content
    )
}

private fun PayMode.Error.displayMessage(): String = unsupported?.let {
    when (it) {
        UnsupportedInput.Bolt12 -> "BOLT12 offers are not supported yet."
        UnsupportedInput.OnChain -> "This is an on-chain Bitcoin request without Lightning."
        UnsupportedInput.LnurlWithdraw -> "LNURL-withdraw is not a payment request."
        UnsupportedInput.Nwc -> "NWC links are not supported in Blip. Use Lasr instead."
    }
} ?: failure?.displayMessage() ?: "Something went wrong."

private fun PaymentFailure.displayMessage(): String = when (this) {
    PaymentFailure.InvalidRequest -> "This payment request is invalid."
    PaymentFailure.ExpiredInvoice -> "This invoice has expired."
    PaymentFailure.WrongNetwork -> "This invoice is for a different Bitcoin network."
    PaymentFailure.MissingConnection -> "Connect a Blink wallet to continue."
    PaymentFailure.AuthenticationRequired -> "Your Blink API key must be replaced."
    PaymentFailure.PermissionDenied -> "Your API key cannot send payments."
    PaymentFailure.InsufficientBalance -> "Your Blink wallet has insufficient balance."
    PaymentFailure.RouteNotFound -> "No route to the recipient was found."
    PaymentFailure.RateLimited -> "Too many requests. Try again shortly."
    PaymentFailure.NetworkUnavailable -> "Could not reach the payment service."
    PaymentFailure.TimedOut -> "The request timed out."
    PaymentFailure.DuplicateInvoice -> "This invoice was already submitted."
    is PaymentFailure.ProviderRejected -> "Blink rejected the payment."
    is PaymentFailure.Unsupported -> "This payment type is unsupported."
    PaymentFailure.Unexpected -> "The payment result was unexpected."
}

private fun PaymentAttemptState.displayName(): String = when (this) {
    PaymentAttemptState.Created -> "Created"
    PaymentAttemptState.Submitted -> "Submitted"
    PaymentAttemptState.Pending -> "Pending"
    PaymentAttemptState.Settled -> "Paid"
    PaymentAttemptState.AlreadyPaid -> "Already paid"
    PaymentAttemptState.Rejected -> "Failed"
    PaymentAttemptState.Unknown -> "Status unknown"
}

private fun ConnectionStatus.displayName(): String = when (this) {
    ConnectionStatus.Connected -> "Connected"
    ConnectionStatus.NeedsReauthentication -> "Needs a new API key"
    ConnectionStatus.Disconnected -> "Disconnected"
}

private fun Long.roundUpToSats(): Long = if (this <= 0L) 0L else ((this - 1L) / 1_000L) + 1L
