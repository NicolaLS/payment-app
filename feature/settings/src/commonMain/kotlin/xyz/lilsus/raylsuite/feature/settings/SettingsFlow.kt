package xyz.lilsus.raylsuite.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.LanguageCatalog
import xyz.lilsus.raylsuite.core.model.LanguagePreference
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.feature.contacts.ContactEditorScreen
import xyz.lilsus.raylsuite.feature.contacts.ContactsEvent
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository
import xyz.lilsus.raylsuite.feature.contacts.ContactsScreen
import xyz.lilsus.raylsuite.feature.contacts.ContactsViewModel
import xyz.lilsus.raylsuite.feature.contacts.rememberContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencySettingsScreen
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencySettingsViewModel
import xyz.lilsus.raylsuite.feature.currencysettings.rememberCurrencyPreferences
import xyz.lilsus.raylsuite.feature.languagesettings.LanguageRepository
import xyz.lilsus.raylsuite.feature.languagesettings.LanguageSettingsScreen
import xyz.lilsus.raylsuite.feature.languagesettings.LanguageSettingsViewModel
import xyz.lilsus.raylsuite.feature.languagesettings.rememberLanguageRepository
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentSettingsScreen
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentSettingsViewModel
import xyz.lilsus.raylsuite.feature.paymentsettings.rememberPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.paymentshortcuts.PaymentShortcutContactPickerScreen
import xyz.lilsus.raylsuite.feature.paymentshortcuts.PaymentShortcutCurrencyPickerScreen
import xyz.lilsus.raylsuite.feature.paymentshortcuts.PaymentShortcutEditorScreen
import xyz.lilsus.raylsuite.feature.paymentshortcuts.PaymentShortcutsEvent
import xyz.lilsus.raylsuite.feature.paymentshortcuts.PaymentShortcutsViewModel
import xyz.lilsus.raylsuite.feature.settings.generated.resources.Res
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_currency_subtitle_format
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_language_english
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_language_german
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_language_spanish
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_language_system_default
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_theme_dark
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_theme_light
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_theme_system_default
import xyz.lilsus.raylsuite.feature.themesettings.ThemePreferences
import xyz.lilsus.raylsuite.feature.themesettings.ThemeSettingsScreen
import xyz.lilsus.raylsuite.feature.themesettings.ThemeSettingsViewModel

@Composable
fun SettingsFlow(
    storageName: String,
    themePreferences: ThemePreferences,
    bitcoinPriceProvider: BitcoinPriceProvider,
    legalLinks: SettingsLegalLinks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    startDestination: SettingsStartDestination = SettingsStartDestination.Overview,
    currencyPreferences: CurrencyPreferences? = null,
    contactsRepository: ContactsRepository? = null,
    paymentPreferences: PaymentPreferencesRepository? = null,
    leadingEntries: List<SettingsEntry> = emptyList(),
    trailingEntries: List<SettingsEntry> = emptyList(),
    donationAppName: String? = null,
    onDonate: ((Long) -> Unit)? = null,
    additionalContactActions: @Composable ColumnScope.() -> Unit = {}
) {
    var destination by remember(startDestination) {
        mutableStateOf(startDestination.toInternalDestination())
    }
    var shortcutReturnDestination by remember {
        mutableStateOf<SettingsDestination?>(SettingsDestination.Payments)
    }
    var shortcutCurrencySearch by remember { mutableStateOf("") }

    val storedCurrencyPreferences = rememberCurrencyPreferences(storageName)
    val languageRepository = rememberLanguageRepository()
    val storedContactsRepository = rememberContactsRepository(storageName)
    val storedPaymentPreferences = rememberPaymentPreferencesRepository(storageName)
    val resolvedCurrencyPreferences = currencyPreferences ?: storedCurrencyPreferences
    val resolvedContactsRepository = contactsRepository ?: storedContactsRepository
    val resolvedPaymentPreferences = paymentPreferences ?: storedPaymentPreferences
    val primaryCurrencyState =
        resolvedCurrencyPreferences.primaryCode.collectAsState(CurrencyCatalog.DEFAULT_CODE)

    val contactsViewModel =
        remember(resolvedContactsRepository) {
            ContactsViewModel(resolvedContactsRepository)
        }
    val shortcutsViewModel =
        remember(resolvedContactsRepository) {
            PaymentShortcutsViewModel(
                repository = resolvedContactsRepository,
                preferredCurrencyCode = { primaryCurrencyState.value }
            )
        }
    val paymentSettingsViewModel =
        remember(
            resolvedPaymentPreferences,
            resolvedCurrencyPreferences,
            resolvedContactsRepository,
            bitcoinPriceProvider
        ) {
            PaymentSettingsViewModel(
                paymentPreferences = resolvedPaymentPreferences,
                currencyPreferences = resolvedCurrencyPreferences,
                contactsRepository = resolvedContactsRepository,
                bitcoinPriceProvider = bitcoinPriceProvider
            )
        }
    val contactsState by contactsViewModel.uiState.collectAsState()
    val shortcutsState by shortcutsViewModel.uiState.collectAsState()
    val paymentSettingsState by paymentSettingsViewModel.uiState.collectAsState()

    LaunchedEffect(startDestination, shortcutsViewModel) {
        if (startDestination == SettingsStartDestination.ShortcutCreate) {
            shortcutsViewModel.startAdd()
            shortcutReturnDestination = null
        }
    }
    DisposableEffect(contactsViewModel, shortcutsViewModel, paymentSettingsViewModel) {
        onDispose {
            contactsViewModel.clear()
            shortcutsViewModel.clear()
            paymentSettingsViewModel.clear()
        }
    }
    LaunchedEffect(contactsViewModel) {
        contactsViewModel.events.collectLatest { event ->
            when (event) {
                ContactsEvent.CloseEditor -> {
                    destination = SettingsDestination.Contacts
                }

                is ContactsEvent.CreateShortcut -> {
                    shortcutsViewModel.startAdd(event.contactId)
                    shortcutReturnDestination = SettingsDestination.ContactEditor
                    destination = SettingsDestination.ShortcutEditor
                }
            }
        }
    }
    LaunchedEffect(shortcutsViewModel) {
        shortcutsViewModel.events.collectLatest { event ->
            when (event) {
                PaymentShortcutsEvent.CloseEditor -> {
                    shortcutReturnDestination?.let { destination = it } ?: onBack()
                }
            }
        }
    }

    when (destination) {
        SettingsDestination.Overview -> {
            SettingsOverview(
                currencyPreferences = resolvedCurrencyPreferences,
                languageRepository = languageRepository,
                themePreferences = themePreferences,
                legalLinks = legalLinks,
                onBack = onBack,
                onPayments = { destination = SettingsDestination.Payments },
                onContacts = { destination = SettingsDestination.Contacts },
                onCurrency = { destination = SettingsDestination.Currency },
                onLanguage = { destination = SettingsDestination.Language },
                onTheme = { destination = SettingsDestination.Theme },
                modifier = modifier,
                leadingEntries = leadingEntries,
                trailingEntries = trailingEntries,
                donationAppName = donationAppName,
                onDonate = onDonate
            )
        }

        SettingsDestination.Currency -> {
            val viewModel =
                remember(resolvedCurrencyPreferences) {
                    CurrencySettingsViewModel(resolvedCurrencyPreferences)
                }
            val state by viewModel.uiState.collectAsState()
            ClearOnDispose(viewModel, viewModel::clear)
            CurrencySettingsScreen(
                state = state,
                onQueryChange = viewModel::updateSearch,
                onPreferenceSelected = viewModel::selectPreference,
                onCurrencySelected = viewModel::selectCurrency,
                onBack = { destination = SettingsDestination.Overview },
                modifier = modifier
            )
        }

        SettingsDestination.Language -> {
            val viewModel =
                remember(languageRepository) {
                    LanguageSettingsViewModel(languageRepository)
                }
            val state by viewModel.uiState.collectAsState()
            ClearOnDispose(viewModel, viewModel::clear)
            LanguageSettingsScreen(
                state = state,
                onQueryChange = viewModel::updateSearch,
                onOptionSelected = viewModel::selectOption,
                onBack = { destination = SettingsDestination.Overview },
                modifier = modifier
            )
        }

        SettingsDestination.Theme -> {
            val viewModel =
                remember(themePreferences) {
                    ThemeSettingsViewModel(themePreferences)
                }
            val state by viewModel.uiState.collectAsState()
            ClearOnDispose(viewModel, viewModel::clear)
            ThemeSettingsScreen(
                state = state,
                onThemeSelected = viewModel::selectTheme,
                onBack = { destination = SettingsDestination.Overview },
                modifier = modifier
            )
        }

        SettingsDestination.Payments -> {
            PaymentSettingsScreen(
                state = paymentSettingsState,
                shortcuts = shortcutsState.shortcuts,
                onBack = { destination = SettingsDestination.Overview },
                onModeSelected = paymentSettingsViewModel::selectConfirmationMode,
                onThresholdChanged =
                paymentSettingsViewModel::updateConfirmationThreshold,
                onConfirmManualEntryChanged =
                paymentSettingsViewModel::setConfirmManualEntry,
                onConfirmShortcutPaymentsChanged =
                paymentSettingsViewModel::setConfirmShortcutPayments,
                onAskToSaveNewContactsChanged =
                paymentSettingsViewModel::setAskToSaveNewContacts,
                onVibrateOnScanChanged = paymentSettingsViewModel::setVibrateOnScan,
                onVibrateOnPaymentChanged =
                paymentSettingsViewModel::setVibrateOnPayment,
                onAddShortcut = {
                    shortcutsViewModel.startAdd()
                    shortcutReturnDestination = SettingsDestination.Payments
                    destination = SettingsDestination.ShortcutEditor
                },
                onEditShortcut = { shortcutId ->
                    shortcutsViewModel.startEdit(shortcutId)
                    shortcutReturnDestination = SettingsDestination.Payments
                    destination = SettingsDestination.ShortcutEditor
                },
                modifier = modifier
            )
        }

        SettingsDestination.Contacts -> {
            ContactsScreen(
                state = contactsState,
                onBack = {
                    if (startDestination == SettingsStartDestination.Contacts) {
                        onBack()
                    } else {
                        destination = SettingsDestination.Overview
                    }
                },
                onAddContact = {
                    contactsViewModel.startAddContact()
                    destination = SettingsDestination.ContactEditor
                },
                onSearchChange = contactsViewModel::updateSearch,
                onEditContact = { contactId ->
                    contactsViewModel.startEditContact(contactId)
                    destination = SettingsDestination.ContactEditor
                },
                modifier = modifier,
                additionalActions = additionalContactActions
            )
        }

        SettingsDestination.ContactEditor -> {
            ContactEditorScreen(
                state = contactsState.editor,
                onBack = contactsViewModel::dismissEditor,
                onAddressChange = contactsViewModel::updateEditorAddress,
                onAliasChange = contactsViewModel::updateEditorAlias,
                onRoleSelected = contactsViewModel::toggleEditorRole,
                onSave = contactsViewModel::saveNewContact,
                onDelete = contactsViewModel::deleteEditedContact,
                onCreateShortcut = contactsViewModel::createShortcutForEditedContact,
                modifier = modifier
            )
        }

        SettingsDestination.ShortcutEditor -> {
            PaymentShortcutEditorScreen(
                state = shortcutsState.editor,
                onBack = shortcutsViewModel::dismissEditor,
                onTitleChange = shortcutsViewModel::updateTitle,
                onContactChange = {
                    shortcutsViewModel.updateContactSearch("")
                    destination = SettingsDestination.ShortcutContactPicker
                },
                onAmountChange = shortcutsViewModel::updateAmount,
                onCurrencyChange = {
                    shortcutCurrencySearch = ""
                    destination = SettingsDestination.ShortcutCurrencyPicker
                },
                onCommentChange = shortcutsViewModel::updateComment,
                onSave = shortcutsViewModel::saveEditor,
                onDelete = shortcutsViewModel::deleteEditedShortcut,
                modifier = modifier
            )
        }

        SettingsDestination.ShortcutContactPicker -> {
            PaymentShortcutContactPickerScreen(
                state = shortcutsState,
                selectedContactId = shortcutsState.editor?.selectedContact?.id,
                onBack = {
                    if (shortcutReturnDestination == null) {
                        onBack()
                    } else {
                        destination = SettingsDestination.ShortcutEditor
                    }
                },
                onSearchChange = shortcutsViewModel::updateContactSearch,
                onContactSelected = { contactId ->
                    shortcutsViewModel.selectContact(contactId)
                    destination = SettingsDestination.ShortcutEditor
                },
                modifier = modifier
            )
        }

        SettingsDestination.ShortcutCurrencyPicker -> {
            PaymentShortcutCurrencyPickerScreen(
                selectedCode =
                shortcutsState.editor?.currencyCode
                    ?: CurrencyCatalog.DEFAULT_CODE,
                searchQuery = shortcutCurrencySearch,
                onBack = { destination = SettingsDestination.ShortcutEditor },
                onSearchChange = { shortcutCurrencySearch = it },
                onCurrencySelected = { code ->
                    shortcutsViewModel.selectCurrency(code)
                    destination = SettingsDestination.ShortcutEditor
                },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun SettingsOverview(
    currencyPreferences: CurrencyPreferences,
    languageRepository: LanguageRepository,
    themePreferences: ThemePreferences,
    legalLinks: SettingsLegalLinks,
    onBack: () -> Unit,
    onPayments: () -> Unit,
    onContacts: () -> Unit,
    onCurrency: () -> Unit,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    modifier: Modifier,
    leadingEntries: List<SettingsEntry>,
    trailingEntries: List<SettingsEntry>,
    donationAppName: String?,
    onDonate: ((Long) -> Unit)?
) {
    val primaryCode by currencyPreferences.primaryCode.collectAsState(
        CurrencyCatalog.DEFAULT_CODE
    )
    val secondaryCode by currencyPreferences.secondaryCode.collectAsState(
        CurrencyCatalog.DEFAULT_SECONDARY_CODE
    )
    val languagePreference by languageRepository.preference.collectAsState()
    val themePreference by themePreferences.preference.collectAsState(ThemePreference.System)
    val currencyLabel =
        stringResource(
            Res.string.settings_currency_subtitle_format,
            primaryCode,
            secondaryCode
        )

    SettingsScreen(
        onBack = onBack,
        onPayments = onPayments,
        onContacts = onContacts,
        onCurrency = onCurrency,
        onLanguage = onLanguage,
        onTheme = onTheme,
        legalLinks = legalLinks,
        modifier = modifier,
        currencySubtitle = currencyLabel,
        languageSubtitle = languageSubtitle(languagePreference),
        themeSubtitle = themeSubtitle(themePreference),
        leadingEntries = leadingEntries,
        trailingEntries = trailingEntries,
        donationAppName = donationAppName,
        onDonate = onDonate
    )
}

@Composable
private fun ClearOnDispose(key: Any, clear: () -> Unit) {
    DisposableEffect(key) {
        onDispose(clear)
    }
}

@Composable
private fun languageSubtitle(preference: LanguagePreference): String {
    val resolvedName = languageName(preference.resolvedTag)
    return when (preference) {
        is LanguagePreference.System ->
            stringResource(
                Res.string.settings_language_system_default,
                resolvedName
            )

        is LanguagePreference.Override -> languageName(preference.overrideTag)
    }
}

@Composable
private fun languageName(tag: String): String {
    val code =
        LanguageCatalog.infoForTag(tag)?.code
            ?: LanguageCatalog.infoForCode(tag.substringBefore('-'))?.code
            ?: LanguageCatalog.fallback.code
    return stringResource(languageNameResource(code))
}

private fun languageNameResource(code: String): StringResource = when (code) {
    "de" -> Res.string.settings_language_german
    "es" -> Res.string.settings_language_spanish
    else -> Res.string.settings_language_english
}

@Composable
private fun themeSubtitle(preference: ThemePreference): String = when (preference) {
    ThemePreference.System ->
        stringResource(
            Res.string.settings_theme_system_default,
            stringResource(
                if (isSystemInDarkTheme()) {
                    Res.string.settings_theme_dark
                } else {
                    Res.string.settings_theme_light
                }
            )
        )

    ThemePreference.Light -> stringResource(Res.string.settings_theme_light)
    ThemePreference.Dark -> stringResource(Res.string.settings_theme_dark)
}

private enum class SettingsDestination {
    Overview,
    Currency,
    Language,
    Theme,
    Payments,
    Contacts,
    ContactEditor,
    ShortcutEditor,
    ShortcutContactPicker,
    ShortcutCurrencyPicker
}

enum class SettingsStartDestination {
    Overview,
    Contacts,
    ShortcutCreate
}

private fun SettingsStartDestination.toInternalDestination(): SettingsDestination = when (this) {
    SettingsStartDestination.Overview -> SettingsDestination.Overview
    SettingsStartDestination.Contacts -> SettingsDestination.Contacts
    SettingsStartDestination.ShortcutCreate -> SettingsDestination.ShortcutContactPicker
}
