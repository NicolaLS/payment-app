package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.settings_currency
import papp.composeapp.generated.resources.settings_currency_subtitle
import papp.composeapp.generated.resources.settings_footer_privacy
import papp.composeapp.generated.resources.settings_footer_repo
import papp.composeapp.generated.resources.settings_footer_version
import papp.composeapp.generated.resources.settings_language
import papp.composeapp.generated.resources.settings_language_subtitle
import papp.composeapp.generated.resources.settings_manage_wallets
import papp.composeapp.generated.resources.settings_manage_wallets_subtitle
import papp.composeapp.generated.resources.settings_payments
import papp.composeapp.generated.resources.settings_theme
import papp.composeapp.generated.resources.settings_theme_subtitle
import papp.composeapp.generated.resources.settings_title
import xyz.lilsus.papp.appVersionName
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.model.LanguageCatalog
import xyz.lilsus.papp.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageWallets: () -> Unit,
    onPayments: () -> Unit,
    onCurrency: () -> Unit,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    onDonate: (Long) -> Unit,
    walletSubtitle: String? = null,
    currencySubtitle: String? = null,
    languageSubtitle: String? = null,
    themeSubtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val entries = listOf(
        SettingsEntry(
            title = stringResource(Res.string.settings_manage_wallets),
            subtitle = walletSubtitle ?: stringResource(
                Res.string.settings_manage_wallets_subtitle
            ),
            onClick = onManageWallets
        ),
        SettingsEntry(
            title = stringResource(Res.string.settings_payments),
            onClick = onPayments
        ),
        SettingsEntry(
            title = stringResource(Res.string.settings_currency),
            subtitle = currencySubtitle ?: stringResource(Res.string.settings_currency_subtitle),
            onClick = onCurrency
        ),
        SettingsEntry(
            title = stringResource(Res.string.settings_language),
            subtitle = languageSubtitle ?: stringResource(Res.string.settings_language_subtitle),
            onClick = onLanguage
        ),
        SettingsEntry(
            title = stringResource(Res.string.settings_theme),
            subtitle = themeSubtitle ?: stringResource(Res.string.settings_theme_subtitle),
            onClick = onTheme
        )
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(entries) { entry ->
                SettingsListItem(entry)
            }
            item {
                DonationCard(
                    onDonate1k = { onDonate(1_000) },
                    onDonate5k = { onDonate(5_000) },
                    onDonate10k = { onDonate(10_000) }
                )
            }
            item {
                SettingsFooter()
            }
        }
    }
}

@Immutable
private data class SettingsEntry(
    val title: String,
    val subtitle: String? = null,
    val onClick: () -> Unit
)

@Composable
private fun SettingsListItem(entry: SettingsEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = Modifier
                .clickable(onClick = entry.onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(end = 32.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                entry.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun SettingsFooter() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(Res.string.settings_footer_version, appVersionName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(Res.string.settings_footer_privacy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    uriHandler.openUri(PRIVACY_POLICY_URL)
                }
            )
            Text(
                text = stringResource(Res.string.settings_footer_repo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    uriHandler.openUri(REPO_URL)
                }
            )
        }
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    AppTheme {
        SettingsScreen(
            onBack = {},
            onManageWallets = {},
            onPayments = {},
            onCurrency = {},
            onLanguage = {},
            onTheme = {},
            onDonate = {},
            currencySubtitle = stringResource(CurrencyCatalog.infoFor("USD").nameRes),
            languageSubtitle = LanguageCatalog.displayName("en"),
            themeSubtitle = stringResource(Res.string.settings_theme_subtitle)
        )
    }
}

private const val PRIVACY_POLICY_URL =
    "https://github.com/NicolaLS/payment-app/blob/main/privacy-policy.md"
private const val REPO_URL = "https://github.com/NicolaLS/payment-app"
