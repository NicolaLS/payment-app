package xyz.lilsus.raylsuite.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.LanguageCatalog
import xyz.lilsus.raylsuite.core.model.LanguagePreference
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencySettingsScreen
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencySettingsViewModel
import xyz.lilsus.raylsuite.feature.currencysettings.rememberCurrencyPreferences
import xyz.lilsus.raylsuite.feature.languagesettings.LanguageSettingsScreen
import xyz.lilsus.raylsuite.feature.languagesettings.LanguageSettingsViewModel
import xyz.lilsus.raylsuite.feature.languagesettings.rememberLanguageRepository
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    leadingEntries: List<SettingsEntry> = emptyList(),
    trailingEntries: List<SettingsEntry> = emptyList()
) {
    var destination by remember { mutableStateOf(SettingsDestination.Overview) }
    val currencyPreferences = rememberCurrencyPreferences(storageName)
    val languageRepository = rememberLanguageRepository()

    when (destination) {
        SettingsDestination.Overview -> {
            val primaryCode by currencyPreferences.primaryCode.collectAsState(
                CurrencyCatalog.DEFAULT_CODE
            )
            val secondaryCode by currencyPreferences.secondaryCode.collectAsState(
                CurrencyCatalog.DEFAULT_SECONDARY_CODE
            )
            val languagePreference by languageRepository.preference.collectAsState()
            val themePreference by themePreferences.preference.collectAsState(
                ThemePreference.System
            )
            val currencyLabel =
                stringResource(
                    Res.string.settings_currency_subtitle_format,
                    primaryCode,
                    secondaryCode
                )

            SettingsScreen(
                onBack = onBack,
                onCurrency = { destination = SettingsDestination.Currency },
                onLanguage = { destination = SettingsDestination.Language },
                onTheme = { destination = SettingsDestination.Theme },
                modifier = modifier,
                currencySubtitle = currencyLabel,
                languageSubtitle = languageSubtitle(languagePreference),
                themeSubtitle = themeSubtitle(themePreference),
                leadingEntries = leadingEntries,
                trailingEntries = trailingEntries
            )
        }

        SettingsDestination.Currency -> {
            val viewModel = remember(currencyPreferences) {
                CurrencySettingsViewModel(currencyPreferences)
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
            val viewModel = remember(languageRepository) {
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
            val viewModel = remember(themePreferences) {
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
    }
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
    Theme
}
