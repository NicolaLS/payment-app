package xyz.lilsus.raylsuite.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.LanguageCatalog
import xyz.lilsus.raylsuite.core.model.LanguagePreference
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencySettingsScreen
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencySettingsViewModel
import xyz.lilsus.raylsuite.feature.languagesettings.LanguageRepository
import xyz.lilsus.raylsuite.feature.languagesettings.LanguageSettingsScreen
import xyz.lilsus.raylsuite.feature.languagesettings.LanguageSettingsViewModel
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubLensPreferences
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.host.rememberSelectedPaymentHubLens
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubLensDefinition
import xyz.lilsus.raylsuite.feature.paymenthub.library.PaymentHubLibraryFlow
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentSettingsScreen
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentSettingsViewModel
import xyz.lilsus.raylsuite.feature.settings.generated.resources.Res
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
    themePreferences: ThemePreferences,
    languageRepository: LanguageRepository,
    bitcoinPriceProvider: BitcoinPriceProvider,
    currencyPreferences: CurrencyPreferences,
    paymentPreferences: PaymentPreferencesRepository,
    paymentHub: PaymentHubRepository,
    lensPreferences: PaymentHubLensPreferences,
    lensDefinitions: List<PaymentHubLensDefinition>,
    legalLinks: SettingsLegalLinks,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    startDestination: SettingsStartDestination = SettingsStartDestination.Overview,
    overviewBottomContent: (@Composable () -> Unit)? = null,
    leadingEntries: List<SettingsEntry> = emptyList(),
    trailingEntries: List<SettingsEntry> = emptyList(),
    performanceDiagnostics: PerformanceDiagnostics? = null,
    donationAppName: String? = null,
    onDonate: ((Long) -> Unit)? = null,
    hubLibraryActions: @Composable ColumnScope.() -> Unit = {}
) {
    var destination by remember(startDestination) {
        mutableStateOf(startDestination.toInternalDestination())
    }

    val currencyState =
        currencyPreferences.code.collectAsState(CurrencyCatalog.DEFAULT_CODE)

    val paymentSettingsViewModel =
        remember(paymentPreferences, currencyPreferences, bitcoinPriceProvider) {
            PaymentSettingsViewModel(
                paymentPreferences = paymentPreferences,
                currencyPreferences = currencyPreferences,
                bitcoinPriceProvider = bitcoinPriceProvider
            )
        }
    val paymentSettingsState by paymentSettingsViewModel.uiState.collectAsState()
    val navigationEventState =
        rememberNavigationEventState(
            currentInfo = SettingsNavigationInfo(destination)
        )

    fun navigateBack() {
        when (destination) {
            SettingsDestination.Overview -> onBack()

            SettingsDestination.Currency,
            SettingsDestination.Language,
            SettingsDestination.Theme,
            SettingsDestination.Payments,
            SettingsDestination.HomeLayout -> destination = SettingsDestination.Overview

            SettingsDestination.PaymentHub -> {
                if (startDestination == SettingsStartDestination.PaymentHub) {
                    onBack()
                } else {
                    destination = SettingsDestination.Overview
                }
            }
        }
    }

    NavigationEventHandler(
        state = navigationEventState,
        isForwardEnabled = false,
        // The hub library owns back handling for its own internal destinations.
        isBackEnabled = destination != SettingsDestination.PaymentHub,
        onBackCompleted = ::navigateBack
    )

    DisposableEffect(paymentSettingsViewModel) {
        onDispose(paymentSettingsViewModel::clear)
    }

    when (destination) {
        SettingsDestination.Overview -> {
            SettingsOverview(
                currencyPreferences = currencyPreferences,
                languageRepository = languageRepository,
                themePreferences = themePreferences,
                lensPreferences = lensPreferences,
                lensDefinitions = lensDefinitions,
                legalLinks = legalLinks,
                onBack = onBack,
                onPayments = { destination = SettingsDestination.Payments },
                onPaymentHub = { destination = SettingsDestination.PaymentHub },
                onHomeLayout = { destination = SettingsDestination.HomeLayout },
                onCurrency = { destination = SettingsDestination.Currency },
                onLanguage = { destination = SettingsDestination.Language },
                onTheme = { destination = SettingsDestination.Theme },
                modifier = modifier,
                overviewBottomContent = overviewBottomContent,
                leadingEntries = leadingEntries,
                trailingEntries = trailingEntries,
                performanceDiagnostics = performanceDiagnostics,
                donationAppName = donationAppName,
                onDonate = onDonate
            )
        }

        SettingsDestination.Currency -> {
            val viewModel =
                remember(currencyPreferences) {
                    CurrencySettingsViewModel(currencyPreferences)
                }
            val state by viewModel.uiState.collectAsState()
            ClearOnDispose(viewModel, viewModel::clear)
            CurrencySettingsScreen(
                state = state,
                onQueryChange = viewModel::updateSearch,
                onCurrencySelected = viewModel::selectCurrency,
                onBack = ::navigateBack,
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
                onBack = ::navigateBack,
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
                onBack = ::navigateBack,
                modifier = modifier
            )
        }

        SettingsDestination.Payments -> {
            PaymentSettingsScreen(
                state = paymentSettingsState,
                onBack = ::navigateBack,
                onModeSelected = paymentSettingsViewModel::selectConfirmationMode,
                onThresholdChanged =
                    paymentSettingsViewModel::updateConfirmationThreshold,
                onConfirmManualEntryChanged =
                    paymentSettingsViewModel::setConfirmManualEntry,
                onConfirmPresetPaymentsChanged =
                    paymentSettingsViewModel::setConfirmPresetPayments,
                onShowLnurlPayDetailsChanged =
                    paymentSettingsViewModel::setShowLnurlPayDetails,
                onOfferToSaveNewTargetsChanged =
                    paymentSettingsViewModel::setOfferToSaveNewTargets,
                onVibrateOnScanChanged = paymentSettingsViewModel::setVibrateOnScan,
                onVibrateOnPaymentChanged =
                    paymentSettingsViewModel::setVibrateOnPayment,
                modifier = modifier
            )
        }

        SettingsDestination.PaymentHub -> {
            PaymentHubLibraryFlow(
                repository = paymentHub,
                preferredCurrencyCode = { currencyState.value },
                onBack = ::navigateBack,
                modifier = modifier,
                libraryActions = hubLibraryActions
            )
        }

        SettingsDestination.HomeLayout -> {
            val selectedLens = rememberSelectedPaymentHubLens(lensPreferences, lensDefinitions)
            val scope = rememberCoroutineScope()
            HomeLayoutSettingsScreen(
                definitions = lensDefinitions,
                selectedId = selectedLens?.id,
                onSelect = { id -> scope.launch { lensPreferences.select(id) } },
                onBack = ::navigateBack,
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
    lensPreferences: PaymentHubLensPreferences,
    lensDefinitions: List<PaymentHubLensDefinition>,
    legalLinks: SettingsLegalLinks,
    onBack: () -> Unit,
    onPayments: () -> Unit,
    onPaymentHub: () -> Unit,
    onHomeLayout: () -> Unit,
    onCurrency: () -> Unit,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    modifier: Modifier,
    overviewBottomContent: (@Composable () -> Unit)?,
    leadingEntries: List<SettingsEntry>,
    trailingEntries: List<SettingsEntry>,
    performanceDiagnostics: PerformanceDiagnostics?,
    donationAppName: String?,
    onDonate: ((Long) -> Unit)?
) {
    val performanceDiagnosticsEnabled =
        performanceDiagnostics?.sharingEnabled?.collectAsState()?.value
    val currencyCode by currencyPreferences.code.collectAsState(
        CurrencyCatalog.DEFAULT_CODE
    )
    val languagePreference by languageRepository.preference.collectAsState()
    val themePreference by themePreferences.preference.collectAsState(ThemePreference.System)
    val selectedLens = rememberSelectedPaymentHubLens(lensPreferences, lensDefinitions)

    SettingsScreen(
        onBack = onBack,
        onPayments = onPayments,
        onPaymentHub = onPaymentHub,
        onHomeLayout = onHomeLayout,
        onCurrency = onCurrency,
        onLanguage = onLanguage,
        onTheme = onTheme,
        legalLinks = legalLinks,
        modifier = modifier,
        homeLayoutSubtitle = selectedLens?.metadata?.name?.resolve(),
        currencySubtitle = currencyCode,
        languageSubtitle = languageSubtitle(languagePreference),
        themeSubtitle = themeSubtitle(themePreference),
        overviewBottomContent = overviewBottomContent,
        leadingEntries = leadingEntries,
        trailingEntries = trailingEntries,
        performanceDiagnosticsEnabled = performanceDiagnosticsEnabled,
        onPerformanceDiagnosticsChanged =
            performanceDiagnostics?.let { diagnostics ->
                diagnostics::setSharingEnabled
            },
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
    PaymentHub,
    HomeLayout
}

private data class SettingsNavigationInfo(val destination: SettingsDestination) :
    NavigationEventInfo()

enum class SettingsStartDestination {
    Overview,
    PaymentHub
}

private fun SettingsStartDestination.toInternalDestination(): SettingsDestination = when (this) {
    SettingsStartDestination.Overview -> SettingsDestination.Overview
    SettingsStartDestination.PaymentHub -> SettingsDestination.PaymentHub
}
