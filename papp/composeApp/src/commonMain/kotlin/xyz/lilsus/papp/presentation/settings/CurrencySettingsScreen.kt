package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf("USD") }

    val currencies = remember {
        listOf(
            "SAT" to Res.string.settings_currency_satoshi,
            "BTC" to Res.string.settings_currency_bitcoin,
            "USD" to Res.string.settings_currency_usd,
            "EUR" to Res.string.settings_currency_eur,
            "GBP" to Res.string.settings_currency_gbp,
            "CAD" to Res.string.settings_currency_cad,
            "AUD" to Res.string.settings_currency_aud,
            "CHF" to Res.string.settings_currency_chf,
            "JPY" to Res.string.settings_currency_jpy,
        )
    }
    val localized = currencies.map { (code, nameRes) -> code to stringResource(nameRes) }
    val filtered = localized.filter { (_, label) ->
        label.contains(query, ignoreCase = true)
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
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth(),
                placeholder = { Text(stringResource(Res.string.search_placeholder)) },
                leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filtered, key = { it.first }) { (code, label) ->
                    CurrencyRow(
                        title = label,
                        selected = selected == code,
                        onClick = { selected = code }
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = if (selected) 6.dp else 2.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun CurrencySettingsScreenPreview() {
    AppTheme {
        CurrencySettingsScreen(onBack = {})
    }
}
