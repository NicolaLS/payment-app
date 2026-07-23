package xyz.lilsus.rayl.foundation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.rayl.blip.domain.ConfirmationMode
import xyz.lilsus.rayl.blip.domain.ConnectBlinkOutcome
import xyz.lilsus.rayl.blip.domain.Contact
import xyz.lilsus.rayl.blip.domain.ContactId
import xyz.lilsus.rayl.blip.domain.CurrencyCode
import xyz.lilsus.rayl.blip.domain.PaymentAttempt
import xyz.lilsus.rayl.blip.domain.PaymentAttemptState
import xyz.lilsus.rayl.blip.domain.PaymentFailure
import xyz.lilsus.rayl.blip.domain.PaymentOrigin
import xyz.lilsus.rayl.blip.domain.PaymentShortcut
import xyz.lilsus.rayl.blip.domain.ShortcutId
import xyz.lilsus.rayl.blip.platform.AppThemePreference
import xyz.lilsus.rayl.blip.platform.BlipRuntime
import xyz.lilsus.rayl.foundation.ui.domain.model.AppError
import xyz.lilsus.rayl.foundation.ui.domain.model.BlinkErrorType
import xyz.lilsus.rayl.foundation.ui.domain.model.CurrencyCatalog
import xyz.lilsus.rayl.foundation.ui.domain.model.DisplayAmount
import xyz.lilsus.rayl.foundation.ui.domain.model.DisplayCurrency
import xyz.lilsus.rayl.foundation.ui.domain.model.LanguageCatalog
import xyz.lilsus.rayl.foundation.ui.domain.model.PaymentConfirmationMode
import xyz.lilsus.rayl.foundation.ui.domain.model.ThemePreference
import xyz.lilsus.rayl.foundation.ui.domain.model.WalletType
import xyz.lilsus.rayl.foundation.ui.generated.resources.Res
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_currency_subtitle_format
import xyz.lilsus.rayl.foundation.ui.presentation.main.LoadingKind
import xyz.lilsus.rayl.foundation.ui.presentation.main.MainScreen
import xyz.lilsus.rayl.foundation.ui.presentation.main.MainUiState
import xyz.lilsus.rayl.foundation.ui.presentation.main.PendingStatus
import xyz.lilsus.rayl.foundation.ui.presentation.main.SessionTransactionItem
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.ManualAmountKey
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.ManualAmountUiState
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.RangeStatus
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.SessionTransactionDetailScreen
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.SessionTransactionsScreen
import xyz.lilsus.rayl.foundation.ui.presentation.main.contacts.ContactListItem
import xyz.lilsus.rayl.foundation.ui.presentation.main.contacts.ContactsUiState
import xyz.lilsus.rayl.foundation.ui.presentation.main.contacts.PaySheetTab
import xyz.lilsus.rayl.foundation.ui.presentation.main.contacts.ShortcutListItem
import xyz.lilsus.rayl.foundation.ui.presentation.main.scan.QrScannerMode
import xyz.lilsus.rayl.foundation.ui.presentation.main.scan.rememberCameraPermissionState
import xyz.lilsus.rayl.foundation.ui.presentation.main.scan.rememberQrScannerController
import xyz.lilsus.rayl.foundation.ui.presentation.onboarding.screens.AddWalletInstructionsScreen
import xyz.lilsus.rayl.foundation.ui.presentation.onboarding.screens.AgreementScreen
import xyz.lilsus.rayl.foundation.ui.presentation.onboarding.screens.AutoPaySettingsScreen
import xyz.lilsus.rayl.foundation.ui.presentation.onboarding.screens.FeaturesScreen
import xyz.lilsus.rayl.foundation.ui.presentation.onboarding.screens.NoWalletHelpScreen
import xyz.lilsus.rayl.foundation.ui.presentation.onboarding.screens.WalletTypeChoiceScreen
import xyz.lilsus.rayl.foundation.ui.presentation.onboarding.screens.WelcomeScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ChooseWalletTypeScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ContactSettingsEditor
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ContactSettingsEditorScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ContactSettingsItem
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ContactsSettingsScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ContactsSettingsUiState
import xyz.lilsus.rayl.foundation.ui.presentation.settings.CurrencyOption
import xyz.lilsus.rayl.foundation.ui.presentation.settings.CurrencyPreference
import xyz.lilsus.rayl.foundation.ui.presentation.settings.CurrencySettingsScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.CurrencySettingsUiState
import xyz.lilsus.rayl.foundation.ui.presentation.settings.LanguageOption
import xyz.lilsus.rayl.foundation.ui.presentation.settings.LanguageSettingsScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.LanguageSettingsUiState
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ManageWalletScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.PaymentsSettingsScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.PaymentsSettingsUiState
import xyz.lilsus.rayl.foundation.ui.presentation.settings.SettingsScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ShortcutContactOption
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ShortcutContactPickerScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ShortcutContactPickerUiState
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ShortcutCurrencyPickerScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ShortcutSettingsEditor
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ShortcutSettingsEditorScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ShortcutSettingsItem
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ThemeSettingsScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.ThemeSettingsUiState
import xyz.lilsus.rayl.foundation.ui.presentation.settings.addblink.AddBlinkWalletScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.addblink.AddBlinkWalletUiState
import xyz.lilsus.rayl.foundation.ui.presentation.settings.wallet.BlinkContactImportItem
import xyz.lilsus.rayl.foundation.ui.presentation.settings.wallet.BlinkContactsImportScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.wallet.BlinkContactsImportUiState
import xyz.lilsus.rayl.foundation.ui.presentation.settings.wallet.WalletDetailsScreen
import xyz.lilsus.rayl.foundation.ui.presentation.settings.wallet.WalletDetailsUiState
import xyz.lilsus.rayl.foundation.ui.presentation.settings.wallet.WalletDisplay
import xyz.lilsus.rayl.foundation.ui.presentation.settings.wallet.WalletSettingsUiState
import xyz.lilsus.rayl.foundation.ui.presentation.theme.AppTheme

private sealed interface BlipRoute {
    data object Home : BlipRoute
    data object Transactions : BlipRoute
    data class Transaction(val id: String) : BlipRoute
    data object Settings : BlipRoute
    data object Wallet : BlipRoute
    data object ChooseWallet : BlipRoute
    data object AddBlinkWallet : BlipRoute
    data object WalletDetails : BlipRoute
    data object ImportBlinkContacts : BlipRoute
    data object Contacts : BlipRoute
    data object ContactEditor : BlipRoute
    data object Payments : BlipRoute
    data object ShortcutContactPicker : BlipRoute
    data object ShortcutEditor : BlipRoute
    data object ShortcutCurrencyPicker : BlipRoute
    data object Currency : BlipRoute
    data object Language : BlipRoute
    data object Theme : BlipRoute
}

private enum class BlipOnboardingPage {
    Welcome,
    Features,
    AutoPay,
    WalletChoice,
    NoWallet,
    Agreement,
    Instructions,
    Credentials
}

@Composable
fun BlipApp(
    runtime: BlipRuntime,
    incomingPaymentUri: String?,
    onIncomingPaymentUriConsumed: () -> Unit,
    appVersionName: String = "?"
) {
    val scope = rememberCoroutineScope()
    val payStore = remember(runtime, scope) { PayStore(runtime, scope) }
    val settingsStore = remember(runtime, scope) { SettingsStore(runtime, scope) }
    val preferences by runtime.preferences.values.collectAsState()
    val payState by payStore.state.collectAsState()
    val settingsState by settingsStore.state.collectAsState()
    var route by remember { mutableStateOf<BlipRoute>(BlipRoute.Home) }
    var contactEditor by remember { mutableStateOf<ContactSettingsEditor?>(null) }
    var shortcutEditor by remember { mutableStateOf<ShortcutSettingsEditor?>(null) }
    var shortcutPickerQuery by remember { mutableStateOf("") }
    var shortcutCurrencyQuery by remember { mutableStateOf("") }
    var contactsQuery by remember { mutableStateOf("") }

    LaunchedEffect(incomingPaymentUri, preferences.onboardingComplete) {
        val input = incomingPaymentUri
        if (!input.isNullOrBlank() && preferences.onboardingComplete) {
            route = BlipRoute.Home
            payStore.dispatch(PayAction.Resolve(input, PaymentOrigin.AppLink))
            onIncomingPaymentUriConsumed()
        }
    }
    LaunchedEffect(preferences.onboardingComplete) {
        if (preferences.onboardingComplete) {
            payStore.dispatch(PayAction.Reconcile)
            settingsStore.dispatch(SettingsAction.Refresh)
        }
    }

    AppTheme(themePreference = preferences.theme.toFoundationTheme()) {
        if (!preferences.onboardingComplete) {
            BlipOnboarding(
                runtime = runtime,
                onConnected = { route = BlipRoute.Home }
            )
            return@AppTheme
        }

        when (val currentRoute = route) {
            BlipRoute.Home -> BlipPaymentHome(
                runtime = runtime,
                payStore = payStore,
                payState = payState,
                settingsState = settingsState,
                onSettings = { route = BlipRoute.Settings },
                onTransactions = { route = BlipRoute.Transactions },
                onManageContacts = { route = BlipRoute.Contacts },
                onCreateShortcut = {
                    shortcutPickerQuery = ""
                    route = BlipRoute.ShortcutContactPicker
                }
            )

            BlipRoute.Transactions -> SessionTransactionsScreen(
                transactions = payState.attempts.map(PaymentAttempt::toSessionItem),
                onBack = { route = BlipRoute.Home },
                onTransactionSelected = { route = BlipRoute.Transaction(it) }
            )

            is BlipRoute.Transaction -> {
                val transaction = payState.attempts
                    .firstOrNull { it.id.value == currentRoute.id }
                    ?.toSessionItem()
                if (transaction == null) {
                    route = BlipRoute.Transactions
                } else {
                    SessionTransactionDetailScreen(
                        transaction = transaction,
                        onDismiss = { route = BlipRoute.Transactions }
                    )
                }
            }

            BlipRoute.Settings -> {
                val walletSubtitle = settingsState.connection?.alias
                SettingsScreen(
                    onBack = { route = BlipRoute.Home },
                    onManageWallet = { route = BlipRoute.Wallet },
                    onPayments = { route = BlipRoute.Payments },
                    onContacts = { route = BlipRoute.Contacts },
                    onCurrency = { route = BlipRoute.Currency },
                    onLanguage = { route = BlipRoute.Language },
                    onTheme = { route = BlipRoute.Theme },
                    onDonate = { amount ->
                        route = BlipRoute.Home
                        payStore.dispatch(
                            PayAction.Resolve(
                                input = DONATION_ADDRESS,
                                origin = PaymentOrigin.Shortcut,
                                suggestedValue = amount.toString()
                            )
                        )
                    },
                    walletSubtitle = walletSubtitle,
                    currencySubtitle = stringResource(
                        Res.string.settings_currency_subtitle_format,
                        preferences.primaryCurrency,
                        preferences.secondaryCurrency
                    ),
                    languageSubtitle = LanguageCatalog.displayName(
                        preferences.language.takeUnless { it == "system" } ?: "en"
                    ),
                    appVersionName = appVersionName
                )
            }

            BlipRoute.Wallet -> ManageWalletScreen(
                state = WalletSettingsUiState(
                    wallet = settingsState.connection?.let {
                        WalletDisplay(
                            connectionId = it.id.value,
                            relay = null,
                            lud16 = null,
                            alias = it.alias,
                            type = WalletType.BLINK
                        )
                    }
                ),
                onBack = { route = BlipRoute.Settings },
                onAddWallet = { route = BlipRoute.ChooseWallet },
                onRemoveWallet = {
                    settingsStore.dispatch(SettingsAction.Disconnect)
                },
                onWalletDetails = { route = BlipRoute.WalletDetails }
            )

            BlipRoute.ChooseWallet -> ChooseWalletTypeScreen(
                onBack = { route = BlipRoute.Wallet },
                onNwcSelected = {},
                onBlinkSelected = { route = BlipRoute.AddBlinkWallet },
                nwcEnabled = false
            )

            BlipRoute.AddBlinkWallet -> BlipCredentialsScreen(
                runtime = runtime,
                onBack = { route = BlipRoute.ChooseWallet },
                onConnected = {
                    settingsStore.dispatch(SettingsAction.Refresh)
                    route = BlipRoute.Wallet
                }
            )

            BlipRoute.WalletDetails -> {
                val connection = settingsState.connection
                WalletDetailsScreen(
                    state = WalletDetailsUiState(
                        connectionId = connection?.id?.value.orEmpty(),
                        alias = connection?.alias,
                        walletType = WalletType.BLINK,
                        blinkDefaultWalletId = connection?.walletId?.value,
                        isMissing = connection == null
                    ),
                    onBack = { route = BlipRoute.Wallet },
                    onRefreshBlinkDefaultWallet = {
                        settingsStore.dispatch(SettingsAction.RefreshWallet)
                    },
                    onImportBlinkContacts = {
                        route = BlipRoute.ImportBlinkContacts
                    }
                )
            }

            BlipRoute.ImportBlinkContacts -> BlipContactsImport(
                runtime = runtime,
                onBack = { route = BlipRoute.WalletDetails },
                onImported = {
                    settingsStore.dispatch(SettingsAction.Refresh)
                    route = BlipRoute.Contacts
                }
            )

            BlipRoute.Contacts -> ContactsSettingsScreen(
                state = settingsState.toContactsSettingsState(contactsQuery),
                onBack = { route = BlipRoute.Settings },
                onAddContact = {
                    contactEditor = ContactSettingsEditor(
                        contactId = null,
                        address = "",
                        alias = "",
                        roles = emptySet(),
                        addressEditable = true
                    )
                    route = BlipRoute.ContactEditor
                },
                onImportBlinkContacts = {
                    route = BlipRoute.ImportBlinkContacts
                },
                onSearchQueryChange = { contactsQuery = it },
                onEditContact = { id ->
                    val contact = settingsState.contacts.firstOrNull { it.id.value == id }
                    if (contact != null) {
                        contactEditor = ContactSettingsEditor(
                            contactId = contact.id.value,
                            address = contact.lightningAddress,
                            alias = contact.name,
                            roles = emptySet(),
                            addressEditable = false
                        )
                        route = BlipRoute.ContactEditor
                    }
                }
            )

            BlipRoute.ContactEditor -> ContactSettingsEditorScreen(
                state = contactEditor,
                onBack = {
                    contactEditor?.persistExistingContact(runtime)
                    settingsStore.dispatch(SettingsAction.Refresh)
                    route = BlipRoute.Contacts
                },
                onAddressChange = {
                    contactEditor = contactEditor?.copy(address = it, error = null)
                },
                onAliasChange = {
                    contactEditor = contactEditor?.copy(alias = it, error = null)
                    contactEditor?.persistExistingContact(runtime)
                    settingsStore.dispatch(SettingsAction.Refresh)
                },
                onRoleSelected = { role ->
                    contactEditor = contactEditor?.let { editor ->
                        editor.copy(
                            roles = when (role) {
                                null -> emptySet()
                                in editor.roles -> editor.roles - role
                                else -> editor.roles + role
                            }
                        )
                    }
                },
                onSave = {
                    val editor = contactEditor ?: return@ContactSettingsEditorScreen
                    val displayName = editor.alias.ifBlank {
                        editor.address.substringBefore('@')
                    }
                    val saved = editor.contactId?.let(ContactId::parse)?.let { id ->
                        runtime.addressBook.updateContact(id, displayName, editor.address)
                    } ?: runtime.addressBook.addContact(displayName, editor.address)
                    if (saved != null) {
                        settingsStore.dispatch(SettingsAction.Refresh)
                        contactEditor = null
                        route = BlipRoute.Contacts
                    }
                },
                onDelete = {
                    contactEditor?.contactId?.let(ContactId::parse)?.let {
                        runtime.addressBook.deleteContact(it)
                        settingsStore.dispatch(SettingsAction.Refresh)
                    }
                    contactEditor = null
                    route = BlipRoute.Contacts
                },
                onCreateShortcut = {
                    val editor = contactEditor
                    val contactId = editor?.contactId
                    val contact = settingsState.contacts.firstOrNull {
                        it.id.value == contactId
                    }
                    if (contact != null) {
                        shortcutEditor = contact.newShortcutEditor()
                        route = BlipRoute.ShortcutEditor
                    }
                }
            )

            BlipRoute.Payments -> PaymentsSettingsScreen(
                state = preferences.toPaymentsSettingsState(settingsState),
                onBack = { route = BlipRoute.Settings },
                onModeSelected = {
                    runtime.preferences.setConfirmationMode(it.toBlipMode())
                },
                onThresholdChanged = runtime.preferences::setConfirmationThreshold,
                onConfirmManualEntryChanged = runtime.preferences::setConfirmManualEntry,
                onConfirmShortcutPaymentsChanged = runtime.preferences::setConfirmShortcuts,
                onAskToSaveNewContactsChanged =
                    runtime.preferences::setAskToSaveNewContacts,
                onVibrateOnScanChanged = runtime.preferences::setVibrateOnScan,
                onVibrateOnPaymentChanged = runtime.preferences::setVibrateOnPayment,
                onAddShortcut = {
                    shortcutPickerQuery = ""
                    route = BlipRoute.ShortcutContactPicker
                },
                onEditShortcut = { id ->
                    val shortcut = settingsState.shortcuts.firstOrNull { it.id.value == id }
                    if (shortcut != null) {
                        shortcutEditor = shortcut.toEditor(settingsState.contacts)
                        route = BlipRoute.ShortcutEditor
                    }
                }
            )

            BlipRoute.ShortcutContactPicker -> {
                val options = settingsState.contacts.toShortcutContactOptions(shortcutPickerQuery)
                ShortcutContactPickerScreen(
                    state = ShortcutContactPickerUiState(
                        query = shortcutPickerQuery,
                        options = options
                    ),
                    selectedContactId = shortcutEditor?.selectedContactId,
                    onBack = {
                        route = if (shortcutEditor == null) {
                            BlipRoute.Payments
                        } else {
                            BlipRoute.ShortcutEditor
                        }
                    },
                    onQueryChange = { shortcutPickerQuery = it },
                    onContactSelected = { id ->
                        val contact = settingsState.contacts.firstOrNull {
                            it.id.value == id
                        } ?: return@ShortcutContactPickerScreen
                        shortcutEditor = (shortcutEditor ?: contact.newShortcutEditor()).copy(
                            selectedContactId = contact.id.value,
                            selectedContact = contact.toShortcutContactOption()
                        )
                        shortcutEditor?.persistExistingShortcut(runtime, settingsState.contacts)
                        settingsStore.dispatch(SettingsAction.Refresh)
                        route = BlipRoute.ShortcutEditor
                    }
                )
            }

            BlipRoute.ShortcutEditor -> ShortcutSettingsEditorScreen(
                state = shortcutEditor,
                onBack = {
                    shortcutEditor = null
                    route = BlipRoute.Payments
                },
                onTitleChange = {
                    shortcutEditor = shortcutEditor?.copy(title = it)
                    shortcutEditor?.persistExistingShortcut(runtime, settingsState.contacts)
                    settingsStore.dispatch(SettingsAction.Refresh)
                },
                onContactChange = {
                    shortcutPickerQuery = ""
                    route = BlipRoute.ShortcutContactPicker
                },
                onAmountChange = {
                    shortcutEditor = shortcutEditor?.copy(amount = it)
                    shortcutEditor?.persistExistingShortcut(runtime, settingsState.contacts)
                    settingsStore.dispatch(SettingsAction.Refresh)
                },
                onCurrencyChange = {
                    shortcutCurrencyQuery = ""
                    route = BlipRoute.ShortcutCurrencyPicker
                },
                onCommentChange = {
                    shortcutEditor = shortcutEditor?.copy(comment = it)
                },
                onSave = {
                    val editor = shortcutEditor ?: return@ShortcutSettingsEditorScreen
                    val contact = settingsState.contacts.firstOrNull {
                        it.id.value == editor.selectedContactId
                    } ?: return@ShortcutSettingsEditorScreen
                    val currency = CurrencyCode.parse(editor.currencyCode)
                    val saved = runtime.addressBook.addShortcut(
                        label = editor.title,
                        lightningAddress = contact.lightningAddress,
                        amountValue = editor.amount,
                        currency = currency,
                        contactId = contact.id
                    )
                    if (saved != null) {
                        settingsStore.dispatch(SettingsAction.Refresh)
                        shortcutEditor = null
                        route = BlipRoute.Payments
                    }
                },
                onDelete = { id ->
                    ShortcutId.parse(id)?.let(runtime.addressBook::deleteShortcut)
                    settingsStore.dispatch(SettingsAction.Refresh)
                    shortcutEditor = null
                    route = BlipRoute.Payments
                }
            )

            BlipRoute.ShortcutCurrencyPicker -> {
                val options = CurrencyCatalog.supportedCodes.map {
                    val info = CurrencyCatalog.infoFor(it)
                    CurrencyOption(info.code, stringResource(info.nameRes))
                }
                ShortcutCurrencyPickerScreen(
                    selectedCode = shortcutEditor?.currencyCode
                        ?: CurrencyCatalog.DEFAULT_CODE,
                    searchQuery = shortcutCurrencyQuery,
                    options = options,
                    onBack = { route = BlipRoute.ShortcutEditor },
                    onQueryChange = { shortcutCurrencyQuery = it },
                    onCurrencySelected = {
                        shortcutEditor = shortcutEditor?.copy(currencyCode = it)
                        shortcutEditor?.persistExistingShortcut(runtime, settingsState.contacts)
                        settingsStore.dispatch(SettingsAction.Refresh)
                        route = BlipRoute.ShortcutEditor
                    }
                )
            }

            BlipRoute.Currency -> BlipCurrencySettings(
                runtime = runtime,
                onBack = { route = BlipRoute.Settings }
            )

            BlipRoute.Language -> BlipLanguageSettings(
                runtime = runtime,
                onBack = { route = BlipRoute.Settings }
            )

            BlipRoute.Theme -> ThemeSettingsScreen(
                state = ThemeSettingsUiState(preferences.theme.toFoundationTheme()),
                onThemeSelected = {
                    runtime.preferences.setTheme(it.toBlipTheme())
                },
                onBack = { route = BlipRoute.Settings }
            )
        }
    }
}

@Composable
private fun BlipOnboarding(runtime: BlipRuntime, onConnected: () -> Unit) {
    var page by remember { mutableStateOf(BlipOnboardingPage.Welcome) }
    var featuresPage by remember { mutableStateOf(0) }
    var hasAgreed by remember { mutableStateOf(false) }
    val preferences by runtime.preferences.values.collectAsState()
    val cameraPermission = rememberCameraPermissionState()

    when (page) {
        BlipOnboardingPage.Welcome -> WelcomeScreen(
            onGetStarted = { page = BlipOnboardingPage.Features }
        )

        BlipOnboardingPage.Features -> FeaturesScreen(
            currentPage = featuresPage,
            totalPages = 3,
            onPageChanged = { featuresPage = it },
            onContinue = { page = BlipOnboardingPage.AutoPay },
            onBack = { page = BlipOnboardingPage.Welcome },
            onRequestCameraPermission = cameraPermission::request
        )

        BlipOnboardingPage.AutoPay -> AutoPaySettingsScreen(
            confirmationMode = preferences.payments.confirmationMode.toFoundationMode(),
            thresholdSats = preferences.payments.thresholdSats,
            secondaryEquivalent = null,
            onConfirmationModeChanged = {
                runtime.preferences.setConfirmationMode(it.toBlipMode())
            },
            onThresholdChanged = runtime.preferences::setConfirmationThreshold,
            onContinue = { page = BlipOnboardingPage.WalletChoice },
            onBack = { page = BlipOnboardingPage.Features }
        )

        BlipOnboardingPage.WalletChoice -> WalletTypeChoiceScreen(
            selectedType = null,
            onSelectWalletType = {
                if (it == WalletType.BLINK) page = BlipOnboardingPage.Agreement
            },
            onSelectNoWallet = { page = BlipOnboardingPage.NoWallet },
            onBack = { page = BlipOnboardingPage.AutoPay },
            nwcEnabled = false
        )

        BlipOnboardingPage.NoWallet -> NoWalletHelpScreen(
            onHasWalletNow = { page = BlipOnboardingPage.WalletChoice },
            onStartAgain = { page = BlipOnboardingPage.Welcome },
            onBack = { page = BlipOnboardingPage.WalletChoice }
        )

        BlipOnboardingPage.Agreement -> AgreementScreen(
            hasAgreed = hasAgreed,
            onAgreementChanged = { hasAgreed = it },
            onContinue = {
                if (hasAgreed) page = BlipOnboardingPage.Instructions
            },
            onBack = { page = BlipOnboardingPage.WalletChoice }
        )

        BlipOnboardingPage.Instructions -> AddWalletInstructionsScreen(
            walletType = WalletType.BLINK,
            onConnectWallet = { page = BlipOnboardingPage.Credentials },
            onBack = { page = BlipOnboardingPage.Agreement }
        )

        BlipOnboardingPage.Credentials -> BlipCredentialsScreen(
            runtime = runtime,
            onBack = { page = BlipOnboardingPage.Instructions },
            onConnected = {
                runtime.preferences.completeOnboarding()
                onConnected()
            }
        )
    }
}

@Composable
private fun BlipCredentialsScreen(
    runtime: BlipRuntime,
    onBack: () -> Unit,
    onConnected: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(AddBlinkWalletUiState()) }

    AddBlinkWalletScreen(
        state = state,
        onBack = onBack,
        onAliasChange = { state = state.copy(alias = it, error = null) },
        onApiKeyChange = { state = state.copy(apiKey = it, error = null) },
        onSubmit = {
            if (state.isSaving) return@AddBlinkWalletScreen
            state = state.copy(isSaving = true, error = null)
            scope.launch {
                val outcome = try {
                    runtime.gateway.connect(state.apiKey, state.alias)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    ConnectBlinkOutcome.Unexpected
                }
                if (outcome is ConnectBlinkOutcome.Connected) {
                    state = state.copy(isSaving = false, apiKey = "")
                    onConnected()
                } else {
                    state = state.copy(
                        isSaving = false,
                        error = outcome.toAppError()
                    )
                }
            }
        }
    )
}

@Composable
private fun BlipContactsImport(runtime: BlipRuntime, onBack: () -> Unit, onImported: () -> Unit) {
    val scope = rememberCoroutineScope()
    var state by remember {
        mutableStateOf(BlinkContactsImportUiState(isLoading = true))
    }

    LaunchedEffect(runtime) {
        state = try {
            val candidates = runtime.addressBook.blinkContactCandidates()
            val items = candidates.map { candidate ->
                BlinkContactImportItem(
                    id = candidate.lightningAddress,
                    displayName = candidate.name,
                    address = candidate.lightningAddress,
                    alias = candidate.name,
                    transactionsCount = 0,
                    alreadyAdded = candidate.alreadyAdded
                )
            }
            state.copy(
                items = items,
                selectedIds = items.filterNot(BlinkContactImportItem::alreadyAdded)
                    .map(BlinkContactImportItem::id)
                    .toSet(),
                hasLoaded = true,
                isLoading = false
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            state.copy(
                hasLoaded = true,
                isLoading = false,
                error = AppError.Unexpected()
            )
        }
    }

    BlinkContactsImportScreen(
        state = state,
        onBack = onBack,
        onToggleContact = { id ->
            state = state.copy(
                selectedIds = if (id in state.selectedIds) {
                    state.selectedIds - id
                } else {
                    state.selectedIds + id
                }
            )
        },
        onToggleAll = {
            state = state.copy(
                selectedIds = if (state.allSelected) {
                    emptySet()
                } else {
                    state.items.filterNot(BlinkContactImportItem::alreadyAdded)
                        .map(BlinkContactImportItem::id)
                        .toSet()
                }
            )
        },
        onSearchQueryChange = { state = state.copy(searchQuery = it) },
        onImport = {
            if (state.isImporting || state.selectedIds.isEmpty()) {
                return@BlinkContactsImportScreen
            }
            state = state.copy(isImporting = true, error = null)
            scope.launch {
                try {
                    val imported = runtime.addressBook.importBlinkContacts(state.selectedIds)
                    state = state.copy(
                        isImporting = false,
                        importedCount = imported.size
                    )
                    onImported()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    state = state.copy(
                        isImporting = false,
                        error = AppError.Unexpected()
                    )
                }
            }
        },
        onSkip = null
    )
}

@Composable
private fun BlipPaymentHome(
    runtime: BlipRuntime,
    payStore: PayStore,
    payState: PayUiState,
    settingsState: SettingsUiState,
    onSettings: () -> Unit,
    onTransactions: () -> Unit,
    onManageContacts: () -> Unit,
    onCreateShortcut: () -> Unit
) {
    val scannerController = rememberQrScannerController()
    val cameraPermission = rememberCameraPermissionState()
    val snackbarHostState = remember { SnackbarHostState() }
    var scannerMode by remember { mutableStateOf(QrScannerMode.Near) }
    var scannerStarted by remember { mutableStateOf(false) }
    var contactsOpen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(PaySheetTab.Shortcuts) }
    var manualAmount by remember { mutableStateOf("") }
    val mainState = payState.toMainUiState(manualAmount)
    val scannerShouldRun = mainState == MainUiState.Active && !contactsOpen

    DisposableEffect(scannerController) {
        onDispose { scannerController.stop() }
    }
    LaunchedEffect(scannerShouldRun, cameraPermission.hasPermission, scannerMode) {
        if (!scannerShouldRun) {
            if (scannerStarted) scannerController.pause()
            return@LaunchedEffect
        }
        if (!cameraPermission.hasPermission) {
            cameraPermission.request()
            return@LaunchedEffect
        }
        scannerController.setMode(scannerMode)
        if (!scannerStarted) {
            scannerStarted = scannerController.start(
                onQrCodeScanned = {
                    if (runtime.preferences.values.value.payments.vibrateOnScan) {
                        runtime.platform.haptic()
                    }
                    payStore.dispatch(PayAction.Resolve(it, PaymentOrigin.Scan))
                },
                onCameraPermissionMissing = cameraPermission::request
            )
        } else {
            scannerController.resume()
        }
    }

    MainScreen(
        onNavigateSettings = onSettings,
        onNavigateConnectWallet = {},
        uiState = mainState,
        sessionTransactions = payState.attempts.map(PaymentAttempt::toSessionItem),
        contactsState = settingsState.toContactsUiState(
            isOpen = contactsOpen,
            selectedTab = selectedTab
        ),
        snackbarHostState = snackbarHostState,
        onManualAmountKeyPress = { key ->
            manualAmount = manualAmount.applyKey(key)
        },
        onManualAmountPreset = {
            manualAmount = it.minor.toString()
        },
        onManualAmountSubmit = {
            val entry = payState.mode as? PayMode.EnterAmount
            payStore.dispatch(
                PayAction.SubmitAmount(
                    value = manualAmount,
                    currency = entry?.suggestedCurrency ?: CurrencyCode.Sat
                )
            )
        },
        onManualAmountDismiss = {
            manualAmount = ""
            payStore.dispatch(PayAction.Dismiss)
        },
        onConfirmPaymentSubmit = { payStore.dispatch(PayAction.Confirm) },
        onConfirmPaymentDismiss = { payStore.dispatch(PayAction.Dismiss) },
        onPendingRetryCreateNewInvoice = { payStore.dispatch(PayAction.Dismiss) },
        onPendingRetryViewPending = onTransactions,
        onPendingRetryDismiss = { payStore.dispatch(PayAction.Dismiss) },
        onOpenTransactions = onTransactions,
        onResultDismiss = { payStore.dispatch(PayAction.Dismiss) },
        onContactsOpen = { contactsOpen = true },
        onContactsDismiss = { contactsOpen = false },
        onPaySheetTabSelected = { selectedTab = it },
        onContactsRoleSelected = {},
        onShortcutSelected = { id ->
            val shortcut = settingsState.shortcuts.firstOrNull { it.id.value == id }
            if (shortcut != null) {
                contactsOpen = false
                payStore.dispatch(
                    PayAction.Resolve(
                        input = shortcut.lightningAddress,
                        origin = PaymentOrigin.Shortcut,
                        suggestedValue = shortcut.amountInput(),
                        suggestedCurrency = shortcut.currency() ?: CurrencyCode.Sat
                    )
                )
            }
        },
        onCreateShortcut = {
            contactsOpen = false
            onCreateShortcut()
        },
        onCreateContact = {
            contactsOpen = false
            onManageContacts()
        },
        onContactSelected = { id ->
            val contact = settingsState.contacts.firstOrNull { it.id.value == id }
            if (contact != null) {
                contactsOpen = false
                payStore.dispatch(
                    PayAction.Resolve(contact.lightningAddress, PaymentOrigin.Manual)
                )
            }
        },
        scannerMode = scannerMode,
        showScannerModeSelector = scannerController.supportsManualModeSelection,
        onToggleScannerMode = if (scannerController.supportsManualModeSelection) {
            {
                scannerMode = if (scannerMode == QrScannerMode.Near) {
                    QrScannerMode.Far
                } else {
                    QrScannerMode.Near
                }
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun BlipCurrencySettings(runtime: BlipRuntime, onBack: () -> Unit) {
    val preferences by runtime.preferences.values.collectAsState()
    var preference by remember { mutableStateOf(CurrencyPreference.Primary) }
    var query by remember { mutableStateOf("") }
    val options = CurrencyCatalog.supportedCodes.map {
        val info = CurrencyCatalog.infoFor(it)
        CurrencyOption(info.code, stringResource(info.nameRes))
    }
    CurrencySettingsScreen(
        state = CurrencySettingsUiState(
            selectedPrimaryCode = preferences.primaryCurrency,
            selectedSecondaryCode = preferences.secondaryCurrency,
            activePreference = preference,
            searchQuery = query,
            options = options
        ),
        onQueryChange = { query = it },
        onPreferenceSelected = { preference = it },
        onCurrencySelected = {
            when (preference) {
                CurrencyPreference.Primary -> runtime.preferences.setPrimaryCurrency(it)
                CurrencyPreference.Secondary -> runtime.preferences.setSecondaryCurrency(it)
            }
        },
        onBack = onBack
    )
}

@Composable
private fun BlipLanguageSettings(runtime: BlipRuntime, onBack: () -> Unit) {
    val preferences by runtime.preferences.values.collectAsState()
    var query by remember { mutableStateOf("") }
    LanguageSettingsScreen(
        state = LanguageSettingsUiState(
            searchQuery = query,
            selectedCode = preferences.language.takeUnless { it == "system" } ?: "en",
            deviceCode = "en",
            options = LanguageCatalog.supported.map {
                LanguageOption(it.code, it.displayName, it.tag)
            }
        ),
        onQueryChange = { query = it },
        onOptionSelected = runtime.preferences::setLanguage,
        onBack = onBack
    )
}

private fun PayUiState.toMainUiState(manualAmount: String): MainUiState =
    when (val current = mode) {
        PayMode.Active -> MainUiState.Active

        PayMode.Resolving -> MainUiState.Loading(LoadingKind.Resolving)

        is PayMode.EnterAmount -> {
            val amount = manualAmount.toLongOrNull()?.takeIf { it > 0 }
            MainUiState.EnterAmount(
                ManualAmountUiState(
                    amount = amount?.let {
                        DisplayAmount(it, current.suggestedCurrency.toDisplay())
                    },
                    currency = current.suggestedCurrency.toDisplay(),
                    min = current.minSats?.let { DisplayAmount(it, DisplayCurrency.Satoshi) },
                    max = current.maxSats?.let { DisplayAmount(it, DisplayCurrency.Satoshi) },
                    allowDecimal = current.suggestedCurrency != CurrencyCode.Sat,
                    rawWhole = manualAmount.ifBlank { "0" },
                    rangeStatus = amount.rangeStatus(current.minSats, current.maxSats)
                )
            )
        }

        is PayMode.Confirm -> MainUiState.Confirm(current.draft.amount.toDisplayAmount())

        is PayMode.Paying -> MainUiState.Loading(LoadingKind.Paying)

        is PayMode.Result -> current.attempt.toMainResult()

        is PayMode.Duplicate -> MainUiState.Success(
            amountPaid = current.attempt.amount.toDisplayAmount(),
            feePaid = current.attempt.feesPaid?.toDisplayAmount()
                ?: DisplayAmount(0, DisplayCurrency.Satoshi),
            wasAlreadyPaid = true,
            preimage = current.attempt.preimage?.toHex()
        )

        is PayMode.Error -> MainUiState.Error(
            current.failure?.toAppError()
                ?: AppError.UnrecognizedInput(current.unsupported?.toString())
        )
    }

private fun Long?.rangeStatus(min: Long?, max: Long?): RangeStatus = when {
    this == null -> RangeStatus.InRange

    min != null && this < min ->
        RangeStatus.BelowMin(DisplayAmount(min, DisplayCurrency.Satoshi))

    max != null && this > max ->
        RangeStatus.AboveMax(DisplayAmount(max, DisplayCurrency.Satoshi))

    else -> RangeStatus.InRange
}

private fun String.applyKey(key: ManualAmountKey): String = when (key) {
    is ManualAmountKey.Digit -> (this + key.value).trimStart('0').take(14)
    ManualAmountKey.Backspace -> dropLast(1)
    ManualAmountKey.Decimal -> if ('.' in this) this else "$this."
}

private fun fr.acinq.lightning.MilliSatoshi.toDisplayAmount(): DisplayAmount =
    DisplayAmount((msat + 999L) / 1_000L, DisplayCurrency.Satoshi)

private fun PaymentAttempt.toMainResult(): MainUiState = when (state) {
    PaymentAttemptState.Settled,
    PaymentAttemptState.AlreadyPaid
    -> MainUiState.Success(
        amountPaid = amount.toDisplayAmount(),
        feePaid = feesPaid?.toDisplayAmount() ?: DisplayAmount(0, DisplayCurrency.Satoshi),
        wasAlreadyPaid = state == PaymentAttemptState.AlreadyPaid,
        preimage = preimage?.toHex()
    )

    PaymentAttemptState.Pending,
    PaymentAttemptState.Submitted,
    PaymentAttemptState.Unknown
    -> MainUiState.PendingRetry(id.value)

    else -> MainUiState.Error(failure?.toAppError() ?: AppError.Unexpected())
}

private fun PaymentAttempt.toSessionItem(): SessionTransactionItem = SessionTransactionItem(
    id = id.value,
    amount = amount.toDisplayAmount(),
    status = when (state) {
        PaymentAttemptState.Settled,
        PaymentAttemptState.AlreadyPaid
        -> PendingStatus.Success

        PaymentAttemptState.Rejected -> PendingStatus.Failure

        else -> PendingStatus.Waiting
    },
    createdAtMs = createdAtMillis,
    fee = feesPaid?.toDisplayAmount(),
    error = failure?.toAppError(),
    wasAlreadyPaid = state == PaymentAttemptState.AlreadyPaid,
    preimage = preimage?.toHex()
)

private fun SettingsUiState.toContactsUiState(
    isOpen: Boolean,
    selectedTab: PaySheetTab
): ContactsUiState = ContactsUiState(
    isOpen = isOpen,
    selectedTab = selectedTab,
    shortcuts = shortcuts.map(PaymentShortcut::toShortcutListItem),
    hasContacts = contacts.isNotEmpty(),
    contactCount = contacts.size,
    contacts = contacts.map(Contact::toContactListItem)
)

private fun SettingsUiState.toContactsSettingsState(query: String): ContactsSettingsUiState =
    ContactsSettingsUiState(
        contacts = contacts.filter {
            query.isBlank() ||
                it.name.contains(query, ignoreCase = true) ||
                it.lightningAddress.contains(query, ignoreCase = true)
        }.map {
            ContactSettingsItem(
                id = it.id.value,
                displayName = it.name,
                address = it.lightningAddress,
                roles = emptySet()
            )
        },
        query = query,
        hasBlinkWallet = connection != null
    )

private fun Contact.toContactListItem(): ContactListItem = ContactListItem(
    id = id.value,
    displayName = name,
    address = lightningAddress,
    roles = emptySet(),
    paymentCount = 0,
    lastPaidAtMs = null
)

private fun PaymentShortcut.toShortcutListItem(): ShortcutListItem = ShortcutListItem(
    id = id.value,
    title = label,
    amountLabel = listOfNotNull(amountInput(), currencyCode).joinToString(" "),
    recipientSummary = lightningAddress,
    commentSummary = null,
    paymentCount = 0,
    lastPaidAtMs = null
)

private fun xyz.lilsus.rayl.blip.platform.UserPreferences.toPaymentsSettingsState(
    settingsState: SettingsUiState
) = PaymentsSettingsUiState(
    confirmationMode = payments.confirmationMode.toFoundationMode(),
    thresholdSats = payments.thresholdSats,
    confirmManualEntry = payments.confirmManualEntry,
    confirmShortcutPayments = payments.confirmShortcutPayments,
    vibrateOnScan = payments.vibrateOnScan,
    vibrateOnPayment = payments.vibrateOnPayment,
    askToSaveNewContacts = askToSaveNewContacts,
    shortcuts = settingsState.shortcuts.map { shortcut ->
        ShortcutSettingsItem(
            id = shortcut.id.value,
            title = shortcut.label,
            amountText = listOfNotNull(
                shortcut.amountInput(),
                shortcut.currencyCode
            ).joinToString(" "),
            contactName = settingsState.contacts
                .firstOrNull { it.id == shortcut.contactId }
                ?.name
                ?: shortcut.lightningAddress,
            comment = null
        )
    }
)

private fun Contact.toShortcutContactOption(): ShortcutContactOption = ShortcutContactOption(
    id = id.value,
    displayName = name,
    address = lightningAddress
)

private fun List<Contact>.toShortcutContactOptions(query: String): List<ShortcutContactOption> {
    val normalizedQuery = query.trim()
    return filter {
        normalizedQuery.isBlank() ||
            it.name.contains(normalizedQuery, ignoreCase = true) ||
            it.lightningAddress.contains(normalizedQuery, ignoreCase = true)
    }.map(Contact::toShortcutContactOption)
}

private fun Contact.newShortcutEditor(): ShortcutSettingsEditor = ShortcutSettingsEditor(
    shortcutId = null,
    title = name,
    selectedContactId = id.value,
    selectedContact = toShortcutContactOption(),
    amount = "",
    currencyCode = CurrencyCatalog.DEFAULT_CODE,
    comment = ""
)

private fun PaymentShortcut.toEditor(contacts: List<Contact>): ShortcutSettingsEditor {
    val contact = contacts.firstOrNull { it.id == contactId }
    return ShortcutSettingsEditor(
        shortcutId = id.value,
        title = label,
        selectedContactId = contact?.id?.value,
        selectedContact = contact?.toShortcutContactOption(),
        amount = amountInput().orEmpty(),
        currencyCode = currencyCode ?: CurrencyCatalog.DEFAULT_CODE,
        comment = ""
    )
}

private fun ContactSettingsEditor.persistExistingContact(runtime: BlipRuntime) {
    val id = contactId?.let(ContactId::parse) ?: return
    val displayName = alias.ifBlank { address.substringBefore('@') }
    runtime.addressBook.updateContact(id, displayName, address)
}

private fun ShortcutSettingsEditor.persistExistingShortcut(
    runtime: BlipRuntime,
    contacts: List<Contact>
) {
    val id = shortcutId?.let(ShortcutId::parse) ?: return
    val contact = contacts.firstOrNull { it.id.value == selectedContactId } ?: return
    runtime.addressBook.updateShortcut(
        id = id,
        label = title,
        lightningAddress = contact.lightningAddress,
        amountValue = amount,
        currency = CurrencyCode.parse(currencyCode),
        contactId = contact.id
    )
}

private fun ConfirmationMode.toFoundationMode(): PaymentConfirmationMode = when (this) {
    ConfirmationMode.Always -> PaymentConfirmationMode.Always
    ConfirmationMode.AboveThreshold -> PaymentConfirmationMode.Above
}

private fun PaymentConfirmationMode.toBlipMode(): ConfirmationMode = when (this) {
    PaymentConfirmationMode.Always -> ConfirmationMode.Always
    PaymentConfirmationMode.Above -> ConfirmationMode.AboveThreshold
}

private fun AppThemePreference.toFoundationTheme(): ThemePreference = when (this) {
    AppThemePreference.System -> ThemePreference.System
    AppThemePreference.Light -> ThemePreference.Light
    AppThemePreference.Dark -> ThemePreference.Dark
}

private fun ThemePreference.toBlipTheme(): AppThemePreference = when (this) {
    ThemePreference.System -> AppThemePreference.System
    ThemePreference.Light -> AppThemePreference.Light
    ThemePreference.Dark -> AppThemePreference.Dark
}

private fun CurrencyCode.toDisplay(): DisplayCurrency = when (this) {
    CurrencyCode.Sat -> DisplayCurrency.Satoshi
    CurrencyCode.Btc -> DisplayCurrency.Bitcoin
    else -> DisplayCurrency.Fiat(value)
}

private fun ConnectBlinkOutcome.toAppError(): AppError = when (this) {
    ConnectBlinkOutcome.InvalidInput -> AppError.Unexpected()

    ConnectBlinkOutcome.InvalidApiKey ->
        AppError.BlinkError(BlinkErrorType.InvalidApiKey)

    ConnectBlinkOutcome.PermissionDenied ->
        AppError.BlinkError(BlinkErrorType.PermissionDenied)

    ConnectBlinkOutcome.RateLimited ->
        AppError.BlinkError(BlinkErrorType.RateLimited)

    ConnectBlinkOutcome.NetworkUnavailable -> AppError.NetworkUnavailable

    ConnectBlinkOutcome.Unexpected -> AppError.Unexpected()

    is ConnectBlinkOutcome.Connected -> AppError.Unexpected()
}

private fun PaymentFailure.toAppError(): AppError = when (this) {
    PaymentFailure.InvalidRequest -> AppError.InvalidInvoice()

    PaymentFailure.ExpiredInvoice -> AppError.BlinkError(BlinkErrorType.InvoiceExpired)

    PaymentFailure.WrongNetwork -> AppError.InvalidInvoice()

    PaymentFailure.MissingConnection -> AppError.MissingWalletConnection

    PaymentFailure.AuthenticationRequired ->
        AppError.BlinkError(BlinkErrorType.InvalidApiKeyWalletRemoved)

    PaymentFailure.PermissionDenied ->
        AppError.BlinkError(BlinkErrorType.PermissionDenied)

    PaymentFailure.InsufficientBalance ->
        AppError.BlinkError(BlinkErrorType.InsufficientBalance)

    PaymentFailure.RouteNotFound ->
        AppError.BlinkError(BlinkErrorType.RouteNotFound)

    PaymentFailure.RateLimited ->
        AppError.BlinkError(BlinkErrorType.RateLimited)

    PaymentFailure.NetworkUnavailable -> AppError.NetworkUnavailable

    PaymentFailure.TimedOut -> AppError.Timeout

    PaymentFailure.DuplicateInvoice -> AppError.PaymentRejected()

    is PaymentFailure.ProviderRejected -> AppError.PaymentRejected(code = code)

    is PaymentFailure.Unsupported -> AppError.UnrecognizedInput(kind)

    PaymentFailure.Unexpected -> AppError.Unexpected()
}

private const val DONATION_ADDRESS = "lilsus@blink.sv"
