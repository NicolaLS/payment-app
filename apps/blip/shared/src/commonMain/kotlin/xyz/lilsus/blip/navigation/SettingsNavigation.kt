package xyz.lilsus.blip.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatformTools
import rayl_suite.blip.shared.generated.resources.Res
import rayl_suite.blip.shared.generated.resources.settings_currency_subtitle_format
import rayl_suite.blip.shared.generated.resources.settings_language_system_default
import rayl_suite.blip.shared.generated.resources.settings_theme_dark
import rayl_suite.blip.shared.generated.resources.settings_theme_light
import rayl_suite.blip.shared.generated.resources.settings_theme_system_default
import xyz.lilsus.blip.domain.model.CurrencyCatalog
import xyz.lilsus.blip.domain.model.LanguageCatalog
import xyz.lilsus.blip.domain.model.LanguagePreference
import xyz.lilsus.blip.domain.model.ThemePreference
import xyz.lilsus.blip.domain.model.WalletConnection
import xyz.lilsus.blip.domain.repository.OnboardingRepository
import xyz.lilsus.blip.domain.usecases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.blip.domain.usecases.ObserveLanguagePreferenceUseCase
import xyz.lilsus.blip.domain.usecases.ObserveSecondaryCurrencyPreferenceUseCase
import xyz.lilsus.blip.domain.usecases.ObserveThemePreferenceUseCase
import xyz.lilsus.blip.domain.usecases.ObserveWalletConnectionUseCase
import xyz.lilsus.blip.navigation.DonationNavigation.donationAddress
import xyz.lilsus.blip.navigation.DonationNavigation.emit
import xyz.lilsus.blip.navigation.DonationRequest
import xyz.lilsus.blip.navigation.Pay
import xyz.lilsus.blip.platform.readPlainText
import xyz.lilsus.blip.presentation.common.rememberRetainedInstance
import xyz.lilsus.blip.presentation.main.scan.rememberCameraPermissionState
import xyz.lilsus.blip.presentation.main.scan.rememberQrScannerController
import xyz.lilsus.blip.presentation.settings.ChooseWalletTypeScreen
import xyz.lilsus.blip.presentation.settings.ContactSettingsEditorScreen
import xyz.lilsus.blip.presentation.settings.ContactsSettingsEvent
import xyz.lilsus.blip.presentation.settings.ContactsSettingsScreen
import xyz.lilsus.blip.presentation.settings.ContactsSettingsViewModel
import xyz.lilsus.blip.presentation.settings.CurrencyOption
import xyz.lilsus.blip.presentation.settings.CurrencySettingsScreen
import xyz.lilsus.blip.presentation.settings.CurrencySettingsViewModel
import xyz.lilsus.blip.presentation.settings.LanguageSettingsScreen
import xyz.lilsus.blip.presentation.settings.LanguageSettingsViewModel
import xyz.lilsus.blip.presentation.settings.ManageWalletScreen
import xyz.lilsus.blip.presentation.settings.PaymentsSettingsEvent
import xyz.lilsus.blip.presentation.settings.PaymentsSettingsScreen
import xyz.lilsus.blip.presentation.settings.PaymentsSettingsViewModel
import xyz.lilsus.blip.presentation.settings.SettingsScreen
import xyz.lilsus.blip.presentation.settings.ShortcutContactPickerScreen
import xyz.lilsus.blip.presentation.settings.ShortcutContactPickerViewModel
import xyz.lilsus.blip.presentation.settings.ShortcutCurrencyPickerScreen
import xyz.lilsus.blip.presentation.settings.ShortcutSettingsEditorScreen
import xyz.lilsus.blip.presentation.settings.ThemeSettingsScreen
import xyz.lilsus.blip.presentation.settings.ThemeSettingsViewModel
import xyz.lilsus.blip.presentation.settings.addblink.AddBlinkWalletEvent
import xyz.lilsus.blip.presentation.settings.addblink.AddBlinkWalletScreen
import xyz.lilsus.blip.presentation.settings.addblink.AddBlinkWalletViewModel
import xyz.lilsus.blip.presentation.settings.addwallet.AddWalletEvent
import xyz.lilsus.blip.presentation.settings.addwallet.AddWalletScreen
import xyz.lilsus.blip.presentation.settings.addwallet.AddWalletViewModel
import xyz.lilsus.blip.presentation.settings.wallet.BlinkContactsImportEvent
import xyz.lilsus.blip.presentation.settings.wallet.BlinkContactsImportScreen
import xyz.lilsus.blip.presentation.settings.wallet.BlinkContactsImportViewModel
import xyz.lilsus.blip.presentation.settings.wallet.WalletDetailsScreen
import xyz.lilsus.blip.presentation.settings.wallet.WalletDetailsViewModel
import xyz.lilsus.blip.presentation.settings.wallet.WalletSettingsViewModel

private const val SHORTCUT_CONTACT_RESULT_KEY = "shortcut_contact_result"
private const val SHORTCUT_CURRENCY_RESULT_KEY = "shortcut_currency_result"

@Serializable
internal object Settings

@Serializable
internal object SettingsSubNav

@Serializable
internal object SettingsPayments

@Serializable
internal object SettingsContacts

@Serializable
internal object SettingsAddContact

@Serializable
internal data class SettingsEditContact(val contactId: String)

@Serializable
internal object SettingsShortcutCreateContactPicker

@Serializable
internal data class SettingsShortcutCreate(val contactId: String)

@Serializable
internal data class SettingsShortcutEdit(val shortcutId: String)

@Serializable
internal data class SettingsShortcutContactPicker(val selectedContactId: String? = null)

@Serializable
internal data class SettingsShortcutCurrencyPicker(val selectedCode: String)

@Serializable
internal object SettingsCurrency

@Serializable
internal object SettingsLanguage

@Serializable
internal object SettingsTheme

@Serializable
internal object SettingsManageWallet

@Serializable
internal object SettingsAddWallet

@Serializable
internal object SettingsChooseWalletType

@Serializable
internal data class SettingsAddBlinkWallet(val completeOnboarding: Boolean = false)

@Serializable
internal object SettingsWalletDetails

@Serializable
internal object SettingsImportBlinkContacts

fun NavGraphBuilder.settingsScreen(navController: NavController, onBack: () -> Unit = {}) {
    navigation<SettingsSubNav>(startDestination = Settings) {
        composable<Settings> {
            SettingsOverviewEntry(navController = navController, onBack = onBack)
        }
        composable<SettingsPayments> {
            PaymentsSettingsEntry(
                navController = navController
            )
        }
        composable<SettingsContacts> {
            ContactsSettingsEntry(
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }
        composable<SettingsAddContact> {
            ContactSettingsEditorEntry(
                navController = navController,
                contactId = null
            )
        }
        composable<SettingsEditContact> { backStackEntry ->
            val route = backStackEntry.toRoute<SettingsEditContact>()
            ContactSettingsEditorEntry(
                navController = navController,
                contactId = route.contactId
            )
        }
        composable<SettingsShortcutCreateContactPicker> {
            ShortcutContactPickerEntry(
                navController = navController,
                selectedContactId = null,
                onContactSelected = { contactId ->
                    navController.navigateToSettingsShortcutCreate(contactId = contactId) {
                        popUpTo<SettingsShortcutCreateContactPicker> {
                            inclusive = true
                        }
                    }
                }
            )
        }
        composable<SettingsShortcutCreate> { backStackEntry ->
            val route = backStackEntry.toRoute<SettingsShortcutCreate>()
            ShortcutSettingsEditorEntry(
                navController = navController,
                backStackEntry = backStackEntry,
                shortcutId = null,
                contactId = route.contactId
            )
        }
        composable<SettingsShortcutEdit> { backStackEntry ->
            val route = backStackEntry.toRoute<SettingsShortcutEdit>()
            ShortcutSettingsEditorEntry(
                navController = navController,
                backStackEntry = backStackEntry,
                shortcutId = route.shortcutId,
                contactId = null
            )
        }
        composable<SettingsShortcutContactPicker> { backStackEntry ->
            val route = backStackEntry.toRoute<SettingsShortcutContactPicker>()
            ShortcutContactPickerEntry(
                navController = navController,
                selectedContactId = route.selectedContactId,
                onContactSelected = { contactId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(SHORTCUT_CONTACT_RESULT_KEY, contactId)
                    navController.popBackStack()
                }
            )
        }
        composable<SettingsShortcutCurrencyPicker> { backStackEntry ->
            val route = backStackEntry.toRoute<SettingsShortcutCurrencyPicker>()
            ShortcutCurrencyPickerEntry(
                navController = navController,
                selectedCode = route.selectedCode
            )
        }
        composable<SettingsCurrency> {
            CurrencySettingsEntry(onBack = { navController.popBackStack() })
        }
        composable<SettingsLanguage> {
            LanguageSettingsEntry(onBack = { navController.popBackStack() })
        }
        composable<SettingsTheme> {
            ThemeSettingsEntry(onBack = { navController.popBackStack() })
        }
        composable<SettingsManageWallet> {
            WalletSettingsEntry(navController = navController)
        }
        composable<SettingsAddWallet> {
            AddWalletEntry(navController = navController)
        }
        composable<SettingsChooseWalletType> {
            ChooseWalletTypeEntry(navController = navController)
        }
        composable<SettingsAddBlinkWallet> { backStackEntry ->
            val route = backStackEntry.toRoute<SettingsAddBlinkWallet>()
            AddBlinkWalletEntry(
                navController = navController,
                completeOnboarding = route.completeOnboarding
            )
        }
        composable<SettingsWalletDetails> {
            WalletDetailsEntry(navController = navController)
        }
        composable<SettingsImportBlinkContacts> {
            BlinkContactsImportEntry(navController = navController)
        }
    }
}

fun NavController.navigateToSettings() {
    navigate(route = Settings) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsPayments() {
    navigate(route = SettingsPayments) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsContacts() {
    navigate(route = SettingsContacts) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsAddContact() {
    navigate(route = SettingsAddContact) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsEditContact(contactId: String) {
    navigate(route = SettingsEditContact(contactId = contactId)) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsShortcutCreateContactPicker() {
    navigate(route = SettingsShortcutCreateContactPicker) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsShortcutCreate(
    contactId: String,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    navigate(route = SettingsShortcutCreate(contactId = contactId)) {
        launchSingleTop = true
        builder()
    }
}

fun NavController.navigateToSettingsShortcutEdit(
    shortcutId: String,
    builder: NavOptionsBuilder.() -> Unit = {}
) {
    navigate(route = SettingsShortcutEdit(shortcutId = shortcutId)) {
        launchSingleTop = true
        builder()
    }
}

fun NavController.navigateToSettingsShortcutContactPicker(selectedContactId: String?) {
    navigate(route = SettingsShortcutContactPicker(selectedContactId = selectedContactId)) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsShortcutCurrencyPicker(selectedCode: String) {
    navigate(route = SettingsShortcutCurrencyPicker(selectedCode = selectedCode)) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsCurrency() {
    navigate(route = SettingsCurrency) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsLanguage() {
    navigate(route = SettingsLanguage) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsTheme() {
    navigate(route = SettingsTheme) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsManageWallet() {
    navigate(route = SettingsManageWallet) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsWalletDetails() {
    navigate(route = SettingsWalletDetails) {
        launchSingleTop = true
    }
}

fun NavController.navigateToBlinkContactsImport() {
    navigate(route = SettingsImportBlinkContacts) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsAddWallet() {
    navigate(route = SettingsAddWallet) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsChooseWalletType() {
    navigate(route = SettingsChooseWalletType) {
        launchSingleTop = true
    }
}

fun NavController.navigateToSettingsAddBlinkWallet() {
    navigate(route = SettingsAddBlinkWallet()) {
        launchSingleTop = true
    }
}

// Public navigation functions for access from outside settings (e.g., onboarding)
fun NavController.navigateToAddWallet() {
    navigate(route = SettingsAddWallet) {
        launchSingleTop = true
    }
}

fun NavController.navigateToAddBlinkWallet(completeOnboarding: Boolean = false) {
    navigate(route = SettingsAddBlinkWallet(completeOnboarding = completeOnboarding)) {
        launchSingleTop = true
    }
}

@Composable
private fun WalletSettingsEntry(navController: NavController) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<WalletSettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val uiState by viewModel.uiState.collectAsState()

    ManageWalletScreen(
        state = uiState,
        onBack = { navController.popBackStack() },
        onAddWallet = { navController.navigateToSettingsChooseWalletType() },
        onRemoveWallet = viewModel::disconnectWallet,
        onWalletDetails = navController::navigateToSettingsWalletDetails
    )
}

@Composable
private fun WalletDetailsEntry(navController: NavController) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = {
            WalletDetailsViewModel(
                walletSettingsRepository = koin.get(),
                getBlinkDefaultWalletId = koin.get(),
                refreshBlinkDefaultWalletId = koin.get(),
                dispatcher = koin.get()
            )
        },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    WalletDetailsScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onRefreshBlinkDefaultWallet = viewModel::refreshDefaultWalletId,
        onImportBlinkContacts = {
            navController.navigateToBlinkContactsImport()
        }
    )
}

@Composable
private fun BlinkContactsImportEntry(navController: NavController) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = {
            koin.get<BlinkContactsImportViewModel>()
        },
        onDispose = { it.clear() }
    )

    LaunchedEffect(viewModel) {
        viewModel.loadBlinkContacts()
    }

    val state by viewModel.uiState.collectAsState()

    BlinkContactsImportScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onToggleContact = viewModel::toggleBlinkContact,
        onToggleAll = viewModel::toggleAllBlinkContacts,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onImport = viewModel::importSelectedBlinkContacts,
        onSkip = null
    )
}

@Composable
private fun AddWalletEntry(navController: NavController) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<AddWalletViewModel>() },
        onDispose = { it.clear() }
    )

    val clipboard = LocalClipboard.current
    val state by viewModel.uiState.collectAsState()
    val scannerController = rememberQrScannerController()
    val cameraPermission = rememberCameraPermissionState()
    var scannerStarted by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }

    DisposableEffect(scannerController) {
        onDispose { scannerController.stop() }
    }

    fun requestCameraPermissionAfterScannerFailure() {
        scannerStarted = false
        scannerController.stop()
        permissionRequested = true
        cameraPermission.request()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddWalletEvent.NavigateToConfirm -> {
                    navController.popBackStack()
                    navController.navigateToConnectWallet(uri = event.uri)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val text = clipboard.getClipEntry()?.readPlainText()
        viewModel.prefillUriIfValid(text)
    }

    LaunchedEffect(cameraPermission.hasPermission) {
        if (!cameraPermission.hasPermission) {
            if (scannerStarted) {
                scannerController.stop()
                scannerStarted = false
            }
            if (!permissionRequested) {
                permissionRequested = true
                cameraPermission.request()
            }
            return@LaunchedEffect
        }

        permissionRequested = false
        if (!scannerStarted) {
            scannerStarted = scannerController.start(
                onQrCodeScanned = { value ->
                    viewModel.handleScannedValue(value)
                },
                onCameraPermissionMissing = ::requestCameraPermissionAfterScannerFailure
            )
        } else {
            scannerController.resume()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AddWalletScreen(
            state = state,
            onBack = { navController.popBackStack() },
            onUriChange = viewModel::updateUri,
            onSubmit = viewModel::submit,
            controller = scannerController,
            isCameraPermissionGranted = cameraPermission.hasPermission
        )
    }
}

@Composable
private fun CurrencySettingsEntry(onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<CurrencySettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    CurrencySettingsScreen(
        state = state,
        onQueryChange = { viewModel.updateSearch(it) },
        onPreferenceSelected = { viewModel.selectPreference(it) },
        onCurrencySelected = { viewModel.selectCurrency(it) },
        onBack = onBack
    )
}

@Composable
private fun LanguageSettingsEntry(onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<LanguageSettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    LanguageSettingsScreen(
        state = state,
        onQueryChange = { viewModel.updateSearch(it) },
        onOptionSelected = { viewModel.selectOption(it) },
        onBack = onBack
    )
}

@Composable
private fun ThemeSettingsEntry(onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<ThemeSettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    ThemeSettingsScreen(
        state = state,
        onThemeSelected = { viewModel.selectTheme(it) },
        onBack = onBack
    )
}

@Composable
private fun PaymentsSettingsEntry(navController: NavController) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<PaymentsSettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    PaymentsSettingsScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onModeSelected = { viewModel.selectMode(it) },
        onThresholdChanged = { threshold -> viewModel.updateThreshold(threshold) },
        onConfirmManualEntryChanged = { enabled -> viewModel.setConfirmManualEntry(enabled) },
        onConfirmShortcutPaymentsChanged = { enabled ->
            viewModel.setConfirmShortcutPayments(enabled)
        },
        onAskToSaveNewContactsChanged = { enabled ->
            viewModel.setAskToSaveNewContacts(enabled)
        },
        onVibrateOnScanChanged = { enabled -> viewModel.setVibrateOnScan(enabled) },
        onVibrateOnPaymentChanged = { enabled -> viewModel.setVibrateOnPayment(enabled) },
        onAddShortcut = { navController.navigateToSettingsShortcutCreateContactPicker() },
        onEditShortcut = { shortcutId ->
            navController.navigateToSettingsShortcutEdit(shortcutId = shortcutId)
        }
    )
}

@Composable
private fun ContactsSettingsEntry(navController: NavController, onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<ContactsSettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ContactsSettingsEvent.OpenBlinkContactsImport -> {
                    navController.navigateToBlinkContactsImport()
                }

                is ContactsSettingsEvent.CreateShortcutForContact -> {
                    navController.navigateToSettingsShortcutCreate(
                        contactId = event.contactId
                    )
                }

                ContactsSettingsEvent.CloseContactEditor -> Unit
            }
        }
    }

    ContactsSettingsScreen(
        state = state,
        onBack = onBack,
        onAddContact = { navController.navigateToSettingsAddContact() },
        onImportBlinkContacts = viewModel::startBlinkContactsImport,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onEditContact = { contactId ->
            navController.navigateToSettingsEditContact(contactId)
        }
    )
}

@Composable
private fun ContactSettingsEditorEntry(navController: NavController, contactId: String?) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<ContactsSettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(contactId) {
        if (contactId == null) {
            viewModel.startAddContact()
        } else {
            viewModel.startEditContact(contactId)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ContactsSettingsEvent.OpenBlinkContactsImport -> {
                    navController.navigateToBlinkContactsImport()
                }

                is ContactsSettingsEvent.CreateShortcutForContact -> {
                    navController.navigateToSettingsShortcutCreate(
                        contactId = event.contactId
                    )
                }

                ContactsSettingsEvent.CloseContactEditor -> {
                    navController.popBackStack()
                }
            }
        }
    }

    ContactSettingsEditorScreen(
        state = state.contactEditor,
        onBack = { navController.popBackStack() },
        onAddressChange = viewModel::updateContactEditorAddress,
        onAliasChange = viewModel::updateContactEditorAlias,
        onRoleSelected = viewModel::updateContactEditorRole,
        onSave = viewModel::saveContactEditor,
        onDelete = viewModel::deleteContactEditor,
        onCreateShortcut = viewModel::createShortcutForCurrentContact
    )
}

@Composable
private fun ShortcutSettingsEditorEntry(
    navController: NavController,
    backStackEntry: NavBackStackEntry,
    shortcutId: String?,
    contactId: String?
) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<PaymentsSettingsViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()
    val selectedContactResult by backStackEntry.savedStateHandle
        .getStateFlow<String?>(SHORTCUT_CONTACT_RESULT_KEY, null)
        .collectAsState()
    val selectedCurrencyResult by backStackEntry.savedStateHandle
        .getStateFlow<String?>(SHORTCUT_CURRENCY_RESULT_KEY, null)
        .collectAsState()

    LaunchedEffect(shortcutId, contactId) {
        when {
            shortcutId != null -> viewModel.startEditShortcut(shortcutId)
            contactId != null -> viewModel.startAddShortcutForContact(contactId)
        }
    }

    LaunchedEffect(selectedContactResult) {
        selectedContactResult?.let { selectedContactId ->
            viewModel.updateShortcutContact(selectedContactId)
            backStackEntry.savedStateHandle.set<String?>(SHORTCUT_CONTACT_RESULT_KEY, null)
        }
    }

    LaunchedEffect(selectedCurrencyResult) {
        selectedCurrencyResult?.let { selectedCurrencyCode ->
            viewModel.updateShortcutCurrency(selectedCurrencyCode)
            backStackEntry.savedStateHandle.set<String?>(SHORTCUT_CURRENCY_RESULT_KEY, null)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PaymentsSettingsEvent.CloseShortcutEditor -> {
                    navController.popBackStack()
                }
            }
        }
    }

    ShortcutSettingsEditorScreen(
        state = state.shortcutEditor,
        onBack = { navController.popBackStack() },
        onTitleChange = viewModel::updateShortcutTitle,
        onContactChange = {
            navController.navigateToSettingsShortcutContactPicker(
                selectedContactId = state.shortcutEditor?.selectedContactId
            )
        },
        onAmountChange = viewModel::updateShortcutAmount,
        onCurrencyChange = {
            state.shortcutEditor?.currencyCode?.let {
                navController.navigateToSettingsShortcutCurrencyPicker(selectedCode = it)
            }
        },
        onCommentChange = viewModel::updateShortcutComment,
        onSave = viewModel::saveShortcutEditor,
        onDelete = viewModel::deleteShortcut
    )
}

@Composable
private fun ShortcutContactPickerEntry(
    navController: NavController,
    selectedContactId: String?,
    onContactSelected: (String) -> Unit
) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<ShortcutContactPickerViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    ShortcutContactPickerScreen(
        state = state,
        selectedContactId = selectedContactId,
        onBack = { navController.popBackStack() },
        onQueryChange = viewModel::updateSearchQuery,
        onContactSelected = onContactSelected
    )
}

@Composable
private fun ShortcutCurrencyPickerEntry(navController: NavController, selectedCode: String) {
    var query by remember { mutableStateOf("") }
    val options = CurrencyCatalog.supportedCodes.map { code ->
        val info = CurrencyCatalog.infoFor(code)
        CurrencyOption(code = info.code, label = stringResource(info.nameRes))
    }

    ShortcutCurrencyPickerScreen(
        selectedCode = selectedCode,
        searchQuery = query,
        options = options,
        onBack = { navController.popBackStack() },
        onQueryChange = { query = it },
        onCurrencySelected = { currencyCode ->
            navController.previousBackStackEntry
                ?.savedStateHandle
                ?.set(SHORTCUT_CURRENCY_RESULT_KEY, currencyCode)
            navController.popBackStack()
        }
    )
}

@Composable
private fun SettingsOverviewEntry(navController: NavController, onBack: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val observeWalletConnection = remember { koin.get<ObserveWalletConnectionUseCase>() }
    val observeCurrencyPreference = remember { koin.get<ObserveCurrencyPreferenceUseCase>() }
    val observeSecondaryCurrencyPreference = remember {
        koin.get<ObserveSecondaryCurrencyPreferenceUseCase>()
    }
    val observeLanguagePreference = remember { koin.get<ObserveLanguagePreferenceUseCase>() }
    val observeThemePreference = remember { koin.get<ObserveThemePreferenceUseCase>() }
    val wallet by observeWalletConnection().collectAsState(initial = null)
    val subtitle = wallet?.let { formatWalletSubtitle(it) }
    val currency by observeCurrencyPreference().collectAsState(
        initial = CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE).currency
    )
    val secondaryCurrency by observeSecondaryCurrencyPreference().collectAsState(
        initial = CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_SECONDARY_CODE).currency
    )
    val currencyLabel = stringResource(
        Res.string.settings_currency_subtitle_format,
        CurrencyCatalog.infoFor(currency).code,
        CurrencyCatalog.infoFor(secondaryCurrency).code
    )
    val languagePreference by observeLanguagePreference().collectAsState(
        initial = LanguagePreference.System(LanguageCatalog.fallback.tag)
    )
    val languageLabel = formatLanguageSubtitle(languagePreference)
    val themePreference by observeThemePreference().collectAsState(initial = ThemePreference.System)
    val themeLabel = formatThemeSubtitle(themePreference)

    SettingsScreen(
        onBack = onBack,
        onManageWallet = { navController.navigateToSettingsManageWallet() },
        onPayments = { navController.navigateToSettingsPayments() },
        onContacts = { navController.navigateToSettingsContacts() },
        onCurrency = { navController.navigateToSettingsCurrency() },
        onLanguage = { navController.navigateToSettingsLanguage() },
        onTheme = { navController.navigateToSettingsTheme() },
        onDonate = { amount ->
            emit(DonationRequest(amountSats = amount, address = donationAddress))
            navController.navigate(route = Pay) {
                popUpTo(Pay) { inclusive = false }
                launchSingleTop = true
            }
        },
        walletSubtitle = subtitle,
        currencySubtitle = currencyLabel,
        languageSubtitle = languageLabel,
        themeSubtitle = themeLabel
    )
}

private fun formatWalletSubtitle(connection: WalletConnection): String {
    connection.alias?.takeIf { it.isNotBlank() }?.let { return it }
    val key = connection.walletPublicKey
    return if (key.length <= 12) {
        key
    } else {
        buildString {
            append(key.take(6))
            append("…")
            append(key.takeLast(4))
        }
    }
}

@Composable
private fun formatLanguageSubtitle(preference: LanguagePreference): String {
    val resolvedName = resolveLanguageName(preference.resolvedTag)
    return when (preference) {
        is LanguagePreference.System -> stringResource(
            Res.string.settings_language_system_default,
            resolvedName
        )

        is LanguagePreference.Override -> resolveLanguageName(preference.overrideTag)
    }
}

@Composable
private fun resolveLanguageName(tag: String): String {
    LanguageCatalog.infoForTag(tag)?.let { info ->
        return info.displayName
    }
    val fallbackCode = tag.substringBefore('-')
    LanguageCatalog.infoForCode(fallbackCode)?.let { info ->
        return info.displayName
    }
    return tag
}

@Composable
private fun formatThemeSubtitle(preference: ThemePreference): String = when (preference) {
    ThemePreference.System -> stringResource(
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

@Composable
private fun ChooseWalletTypeEntry(navController: NavController) {
    ChooseWalletTypeScreen(
        onBack = { navController.popBackStack() },
        onNwcSelected = {
            navController.popBackStack()
            navController.navigateToSettingsAddWallet()
        },
        onBlinkSelected = {
            navController.popBackStack()
            navController.navigateToSettingsAddBlinkWallet()
        }
    )
}

@Composable
private fun AddBlinkWalletEntry(navController: NavController, completeOnboarding: Boolean) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    var importStep by remember { mutableStateOf<AddBlinkImportStep>(AddBlinkImportStep.None) }

    when (val step = importStep) {
        AddBlinkImportStep.None -> AddBlinkWalletFormStep(
            navController = navController,
            koin = koin,
            onWalletAdded = { connection ->
                if (completeOnboarding) {
                    importStep = AddBlinkImportStep.Import
                } else {
                    val popped = navController.popBackStack(
                        route = SettingsManageWallet,
                        inclusive = false
                    )
                    if (!popped) {
                        navController.popBackStack()
                    }
                }
            }
        )

        AddBlinkImportStep.Import -> AddBlinkWalletImportStep(
            navController = navController,
            koin = koin
        )
    }
}

private sealed interface AddBlinkImportStep {
    data object None : AddBlinkImportStep
    data object Import : AddBlinkImportStep
}

@Composable
private fun AddBlinkWalletFormStep(
    navController: NavController,
    koin: org.koin.core.Koin,
    onWalletAdded: (WalletConnection) -> Unit
) {
    val viewModel = rememberRetainedInstance(
        key = "add-blink-wallet-form",
        factory = { koin.get<AddBlinkWalletViewModel>() },
        onDispose = { it.clear() }
    )

    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddBlinkWalletEvent.Success -> onWalletAdded(event.connection)
                AddBlinkWalletEvent.Cancelled -> navController.popBackStack()
            }
        }
    }

    AddBlinkWalletScreen(
        state = state,
        onBack = { navController.popBackStack() },
        onAliasChange = viewModel::updateAlias,
        onApiKeyChange = viewModel::updateApiKey,
        onSubmit = viewModel::submit
    )
}

@Composable
private fun AddBlinkWalletImportStep(navController: NavController, koin: org.koin.core.Koin) {
    val onboardingRepository = remember { koin.get<OnboardingRepository>() }
    val coroutineScope = rememberCoroutineScope()
    val finishOnboarding: () -> Unit = {
        coroutineScope.launch {
            onboardingRepository.markOnboardingCompleted()
            navController.navigateFromOnboardingToPay()
        }
    }

    val viewModel = rememberRetainedInstance(
        key = "blink-contacts-import-onboarding",
        factory = {
            koin.get<BlinkContactsImportViewModel>()
        },
        onDispose = { it.clear() }
    )

    LaunchedEffect(viewModel) {
        viewModel.loadBlinkContacts()
        viewModel.events.collect { event ->
            when (event) {
                is BlinkContactsImportEvent.Imported -> finishOnboarding()
            }
        }
    }

    val state by viewModel.uiState.collectAsState()

    BlinkContactsImportScreen(
        state = state,
        onBack = finishOnboarding,
        onToggleContact = viewModel::toggleBlinkContact,
        onToggleAll = viewModel::toggleAllBlinkContacts,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onImport = viewModel::importSelectedBlinkContacts,
        onSkip = finishOnboarding
    )
}
