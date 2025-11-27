package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.search_placeholder
import papp.composeapp.generated.resources.settings_currency
import papp.composeapp.generated.resources.settings_currency_aud
import papp.composeapp.generated.resources.settings_currency_bitcoin
import papp.composeapp.generated.resources.settings_currency_cad
import papp.composeapp.generated.resources.settings_currency_chf
import papp.composeapp.generated.resources.settings_currency_eur
import papp.composeapp.generated.resources.settings_currency_gbp
import papp.composeapp.generated.resources.settings_currency_jpy
import papp.composeapp.generated.resources.settings_currency_satoshi
import papp.composeapp.generated.resources.settings_currency_usd
import xyz.lilsus.papp.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencySettingsScreen(
    state: CurrencySettingsUiState,
    onQueryChange: (String) -> Unit,
    onCurrencySelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val filtered = state.options.filter { option ->
        option.label.contains(state.searchQuery, ignoreCase = true)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_currency)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(Res.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.code }) { option ->
                    CurrencyRow(
                        title = option.label,
                        selected = state.selectedCode == option.code,
                        onClick = { onCurrencySelected(option.code) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = if (selected) 6.dp else 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview
@Composable
private fun CurrencySettingsScreenPreview() {
    AppTheme {
        CurrencySettingsScreen(
            state = CurrencySettingsUiState(
                selectedCode = "USD",
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
            onCurrencySelected = {},
            onBack = {}
        )
    }
}
