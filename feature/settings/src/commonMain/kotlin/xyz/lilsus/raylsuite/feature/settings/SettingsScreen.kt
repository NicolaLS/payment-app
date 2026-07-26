package xyz.lilsus.raylsuite.feature.settings

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
import xyz.lilsus.raylsuite.core.ui.components.AppFadingLazyColumn
import xyz.lilsus.raylsuite.core.ui.components.AppListDefaults
import xyz.lilsus.raylsuite.core.ui.components.AppListRow
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.core.ui.platform.appVersionName
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.settings.generated.resources.Res
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_contacts
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_contacts_subtitle
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_currency
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_currency_subtitle
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_footer_privacy
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_footer_repo
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_footer_version
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_language
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_language_subtitle
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_payments
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_payments_subtitle
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_theme
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_theme_subtitle
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_title

@Immutable
data class SettingsEntry(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val testTag: String? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPayments: () -> Unit,
    onContacts: () -> Unit,
    onCurrency: () -> Unit,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    modifier: Modifier = Modifier,
    currencySubtitle: String? = null,
    languageSubtitle: String? = null,
    themeSubtitle: String? = null,
    leadingEntries: List<SettingsEntry> = emptyList(),
    trailingEntries: List<SettingsEntry> = emptyList(),
    donationAppName: String? = null,
    onDonate: ((Long) -> Unit)? = null
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val resolvedCurrencySubtitle =
        currencySubtitle ?: stringResource(Res.string.settings_currency_subtitle)
    val resolvedLanguageSubtitle =
        languageSubtitle ?: stringResource(Res.string.settings_language_subtitle)
    val resolvedThemeSubtitle =
        themeSubtitle ?: stringResource(Res.string.settings_theme_subtitle)
    val sharedEntries =
        listOf(
            SettingsEntry(
                id = "payments",
                title = stringResource(Res.string.settings_payments),
                subtitle = stringResource(Res.string.settings_payments_subtitle),
                testTag = SettingsTestTags.PAYMENTS_ROW,
                onClick = onPayments
            ),
            SettingsEntry(
                id = "contacts",
                title = stringResource(Res.string.settings_contacts),
                subtitle = stringResource(Res.string.settings_contacts_subtitle),
                testTag = SettingsTestTags.CONTACTS_ROW,
                onClick = onContacts
            ),
            SettingsEntry(
                id = "currency",
                title = stringResource(Res.string.settings_currency),
                subtitle = resolvedCurrencySubtitle,
                testTag = SettingsTestTags.CURRENCY_ROW,
                onClick = onCurrency
            ),
            SettingsEntry(
                id = "language",
                title = stringResource(Res.string.settings_language),
                subtitle = resolvedLanguageSubtitle,
                testTag = SettingsTestTags.LANGUAGE_ROW,
                onClick = onLanguage
            ),
            SettingsEntry(
                id = "theme",
                title = stringResource(Res.string.settings_theme),
                subtitle = resolvedThemeSubtitle,
                testTag = SettingsTestTags.THEME_ROW,
                onClick = onTheme
            )
        )
    val entries = leadingEntries + sharedEntries + trailingEntries

    Scaffold(
        modifier = modifier.testTag(SettingsTestTags.SCREEN),
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
            modifier =
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = AppListDefaults.ScreenPadding,
            verticalArrangement = Arrangement.spacedBy(AppListDefaults.SectionSpacing)
        ) {
            items(entries, key = SettingsEntry::id) { entry ->
                SettingsListItem(entry)
            }
            if (donationAppName != null && onDonate != null) {
                item {
                    DonationCard(
                        appName = donationAppName,
                        onDonate1k = { onDonate(1_000) },
                        onDonate5k = { onDonate(5_000) },
                        onDonate10k = { onDonate(10_000) }
                    )
                }
            }
            item {
                SettingsFooter()
            }
        }
    }
}

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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            entry.subtitle?.let { subtitle ->
                Text(
                    text = subtitle,
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
private fun SettingsFooter() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text =
                stringResource(
                    Res.string.settings_footer_version,
                    appVersionName()
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = stringResource(Res.string.settings_footer_privacy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.clickable {
                        uriHandler.openUri(PRIVACY_POLICY_URL)
                    }
            )
            Text(
                text = stringResource(Res.string.settings_footer_repo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.clickable {
                        uriHandler.openUri(REPOSITORY_URL)
                    }
            )
        }
    }
}

object SettingsTestTags {
    const val SCREEN = "settings_screen"
    const val PAYMENTS_ROW = "settings_payments_row"
    const val CONTACTS_ROW = "settings_contacts_row"
    const val CURRENCY_ROW = "settings_currency_row"
    const val LANGUAGE_ROW = "settings_language_row"
    const val THEME_ROW = "settings_theme_row"
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    RaylSuiteTheme {
        SettingsScreen(
            onBack = {},
            onPayments = {},
            onContacts = {},
            onCurrency = {},
            onLanguage = {},
            onTheme = {},
            currencySubtitle = "Primary SAT • Secondary USD",
            languageSubtitle = "English",
            themeSubtitle = "System default"
        )
    }
}

private const val PRIVACY_POLICY_URL =
    "https://github.com/NicolaLS/lasr/blob/main/privacy-policy.md"
private const val REPOSITORY_URL = "https://github.com/NicolaLS/lasr"
