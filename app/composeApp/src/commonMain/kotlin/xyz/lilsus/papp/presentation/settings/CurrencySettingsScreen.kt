package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import lasr.composeapp.generated.resources.Res
import lasr.composeapp.generated.resources.settings_currency
import lasr.composeapp.generated.resources.settings_currency_aud
import lasr.composeapp.generated.resources.settings_currency_bitcoin
import lasr.composeapp.generated.resources.settings_currency_cad
import lasr.composeapp.generated.resources.settings_currency_chf
import lasr.composeapp.generated.resources.settings_currency_eur
import lasr.composeapp.generated.resources.settings_currency_gbp
import lasr.composeapp.generated.resources.settings_currency_jpy
import lasr.composeapp.generated.resources.settings_currency_primary
import lasr.composeapp.generated.resources.settings_currency_satoshi
import lasr.composeapp.generated.resources.settings_currency_secondary
import lasr.composeapp.generated.resources.settings_currency_usd
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.presentation.common.AppListDefaults
import xyz.lilsus.papp.presentation.common.BackIconButton
import xyz.lilsus.papp.presentation.theme.AppTheme

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
        ) {
            PrimaryTabRow(
                selectedTabIndex = state.activePreference.ordinal
            ) {
                listOf(
                    CurrencyPreference.Primary,
                    CurrencyPreference.Secondary
                ).forEach { preference ->
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
            CurrencyPickerContent(
                selectedCode = state.selectedCode,
                searchQuery = state.searchQuery,
                options = state.options,
                onQueryChange = onQueryChange,
                onCurrencySelected = onCurrencySelected,
                modifier = Modifier
                    .weight(1f)
                    .padding(AppListDefaults.ScreenPadding)
            )
        }
    }
}

@Preview
@Composable
private fun CurrencySettingsScreenPreview() {
    AppTheme {
        CurrencySettingsScreen(
            state = CurrencySettingsUiState(
                selectedPrimaryCode = "SAT",
                selectedSecondaryCode = "USD",
                options = listOf(
                    CurrencyOption("SAT", stringResource(Res.string.settings_currency_satoshi)),
                    CurrencyOption("BTC", stringResource(Res.string.settings_currency_bitcoin)),
                    CurrencyOption("USD", stringResource(Res.string.settings_currency_usd)),
                    CurrencyOption("EUR", stringResource(Res.string.settings_currency_eur)),
                    CurrencyOption("GBP", stringResource(Res.string.settings_currency_gbp)),
                    CurrencyOption("CAD", stringResource(Res.string.settings_currency_cad)),
                    CurrencyOption("AUD", stringResource(Res.string.settings_currency_aud)),
                    CurrencyOption("CHF", stringResource(Res.string.settings_currency_chf)),
                    CurrencyOption("JPY", stringResource(Res.string.settings_currency_jpy))
                )
            ),
            onQueryChange = {},
            onPreferenceSelected = {},
            onCurrencySelected = {},
            onBack = {}
        )
    }
}
