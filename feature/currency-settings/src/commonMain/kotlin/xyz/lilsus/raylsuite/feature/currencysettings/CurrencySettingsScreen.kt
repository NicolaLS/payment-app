package xyz.lilsus.raylsuite.feature.currencysettings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.ui.components.AppListDefaults
import xyz.lilsus.raylsuite.core.ui.components.AppListScaffold
import xyz.lilsus.raylsuite.core.ui.components.AppSelectableListRow
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.Res
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.search_placeholder
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_aud
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_bitcoin
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_cad
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_chf
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_eur
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_gbp
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_jpy
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_primary
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_satoshi
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_secondary
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_usd

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySettingsScreen(
    state: CurrencySettingsUiState,
    onQueryChange: (String) -> Unit,
    onPreferenceSelected: (CurrencyPreference) -> Unit,
    onCurrencySelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_currency)) },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
        ) {
            PrimaryTabRow(selectedTabIndex = state.activePreference.ordinal) {
                CurrencyPreference.entries.forEach { preference ->
                    Tab(
                        selected = state.activePreference == preference,
                        onClick = { onPreferenceSelected(preference) },
                        text = {
                            Text(
                                stringResource(
                                    when (preference) {
                                        CurrencyPreference.Primary ->
                                            Res.string.settings_currency_primary

                                        CurrencyPreference.Secondary ->
                                            Res.string.settings_currency_secondary
                                    }
                                )
                            )
                        }
                    )
                }
            }

            CurrencyPicker(
                selectedCode = state.selectedCode,
                searchQuery = state.searchQuery,
                onQueryChange = onQueryChange,
                onCurrencySelected = onCurrencySelected,
                modifier =
                Modifier
                    .weight(1f)
                    .padding(AppListDefaults.ScreenPadding)
            )
        }
    }
}

@Composable
fun CurrencyPicker(
    selectedCode: String,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options =
        CurrencyCatalog.supported.map { info ->
            CurrencyOption(
                code = info.code,
                label = stringResource(currencyNameResource(info.code))
            )
        }
    val filtered =
        options.filter { option ->
            option.label.contains(searchQuery, ignoreCase = true)
        }

    AppListScaffold(
        isEmpty = filtered.isEmpty(),
        emptyMessage = null,
        modifier = modifier,
        showSearchBar = true,
        searchQuery = searchQuery,
        onSearchQueryChange = onQueryChange,
        searchPlaceholder = stringResource(Res.string.search_placeholder)
    ) {
        items(filtered, key = CurrencyOption::code) { option ->
            AppSelectableListRow(
                title = option.label,
                selected = selectedCode == option.code,
                onClick = { onCurrencySelected(option.code) }
            )
        }
    }
}

private data class CurrencyOption(val code: String, val label: String)

private fun currencyNameResource(code: String): StringResource = when (code) {
    "SAT" -> Res.string.settings_currency_satoshi
    "BTC" -> Res.string.settings_currency_bitcoin
    "USD" -> Res.string.settings_currency_usd
    "EUR" -> Res.string.settings_currency_eur
    "GBP" -> Res.string.settings_currency_gbp
    "CAD" -> Res.string.settings_currency_cad
    "AUD" -> Res.string.settings_currency_aud
    "CHF" -> Res.string.settings_currency_chf
    "JPY" -> Res.string.settings_currency_jpy
    else -> Res.string.settings_currency_satoshi
}

@Preview
@Composable
private fun CurrencySettingsScreenPreview() {
    RaylSuiteTheme {
        CurrencySettingsScreen(
            state = CurrencySettingsUiState(),
            onQueryChange = {},
            onPreferenceSelected = {},
            onCurrencySelected = {},
            onBack = {}
        )
    }
}
