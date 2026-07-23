package xyz.lilsus.rayl.foundation.ui.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.rayl.foundation.ui.MaestroTags
import xyz.lilsus.rayl.foundation.ui.domain.model.CurrencyCatalog
import xyz.lilsus.rayl.foundation.ui.domain.model.LanguageCatalog
import xyz.lilsus.rayl.foundation.ui.generated.resources.Res
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_contacts
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_contacts_subtitle
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_currency
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_currency_subtitle
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_currency_subtitle_format
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_footer_privacy
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_footer_repo
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_footer_version
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_language
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_language_subtitle
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_manage_wallet
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_manage_wallet_subtitle
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_payments
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_payments_subtitle
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_theme
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_theme_subtitle
import xyz.lilsus.rayl.foundation.ui.generated.resources.settings_title
import xyz.lilsus.rayl.foundation.ui.presentation.common.AppFadingLazyColumn
import xyz.lilsus.rayl.foundation.ui.presentation.common.AppListDefaults
import xyz.lilsus.rayl.foundation.ui.presentation.common.AppListRow
import xyz.lilsus.rayl.foundation.ui.presentation.common.BackIconButton
import xyz.lilsus.rayl.foundation.ui.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageWallet: () -> Unit,
    onPayments: () -> Unit,
    onContacts: () -> Unit,
    onCurrency: () -> Unit,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    onDonate: (Long) -> Unit,
    walletSubtitle: String? = null,
    currencySubtitle: String? = null,
    languageSubtitle: String? = null,
    themeSubtitle: String? = null,
    appVersionName: String = "?",
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val entries = listOf(
        SettingsEntry(
            title = stringResource(Res.string.settings_manage_wallet),
            subtitle = walletSubtitle ?: stringResource(
                Res.string.settings_manage_wallet_subtitle
            ),
            testTag = MaestroTags.Settings.MANAGE_WALLET_ROW,
            onClick = onManageWallet
        ),
        SettingsEntry(
            title = stringResource(Res.string.settings_payments),
            subtitle = stringResource(Res.string.settings_payments_subtitle),
            testTag = MaestroTags.Settings.PAYMENTS_ROW,
            onClick = onPayments
        ),
        SettingsEntry(
            title = stringResource(Res.string.settings_contacts),
            subtitle = stringResource(Res.string.settings_contacts_subtitle),
            testTag = MaestroTags.Settings.CONTACTS_ROW,
            onClick = onContacts
        ),
        SettingsEntry(
            title = stringResource(Res.string.settings_currency),
            subtitle = currencySubtitle ?: stringResource(Res.string.settings_currency_subtitle),
            testTag = MaestroTags.Settings.CURRENCY_ROW,
            onClick = onCurrency
        ),
        SettingsEntry(
            title = stringResource(Res.string.settings_language),
            subtitle = languageSubtitle ?: stringResource(Res.string.settings_language_subtitle),
            testTag = MaestroTags.Settings.LANGUAGE_ROW,
            onClick = onLanguage
        ),
        SettingsEntry(
            title = stringResource(Res.string.settings_theme),
            subtitle = themeSubtitle ?: stringResource(Res.string.settings_theme_subtitle),
            testTag = MaestroTags.Settings.THEME_ROW,
            onClick = onTheme
        )
    )

    Scaffold(
        modifier = modifier.testTag(MaestroTags.Settings.SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        AppFadingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = AppListDefaults.ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(AppListDefaults.SectionSpacing)
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
                SettingsFooter(appVersionName)
            }
        }
    }
}

@Immutable
private data class SettingsEntry(
    val title: String,
    val subtitle: String? = null,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
private fun SettingsListItem(entry: SettingsEntry) {
    AppListRow(
        onClick = entry.onClick,
        testTag = entry.testTag,
        minHeight = 48.dp,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsFooter(appVersionName: String) {
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
            onManageWallet = {},
            onPayments = {},
            onContacts = {},
            onCurrency = {},
            onLanguage = {},
            onTheme = {},
            onDonate = {},
            currencySubtitle = stringResource(
                Res.string.settings_currency_subtitle_format,
                CurrencyCatalog.infoFor("SAT").code,
                CurrencyCatalog.infoFor("USD").code
            ),
            languageSubtitle = LanguageCatalog.displayName("en"),
            themeSubtitle = stringResource(Res.string.settings_theme_subtitle)
        )
    }
}

private const val PRIVACY_POLICY_URL =
    "https://github.com/NicolaLS/lasr/blob/main/privacy-policy.md"
private const val REPO_URL = "https://github.com/NicolaLS/lasr"
