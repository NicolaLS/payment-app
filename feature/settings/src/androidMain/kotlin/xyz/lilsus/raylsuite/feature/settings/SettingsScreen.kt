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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.lilsus.raylsuite.core.ui.components.AppFadingLazyColumn
import xyz.lilsus.raylsuite.core.ui.components.AppListDefaults
import xyz.lilsus.raylsuite.core.ui.components.AppListRow
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.core.ui.platform.appVersionName
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.settings.R

@Immutable
data class SettingsEntry(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val testTag: String? = null,
    val onClick: () -> Unit
) {
    init {
        require(id.isNotBlank()) { "Settings entry ID must not be blank" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    onPayments: () -> Unit,
    onCurrency: () -> Unit,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    legalLinks: SettingsLegalLinks,
    modifier: Modifier = Modifier,
    currencySubtitle: String? = null,
    languageSubtitle: String? = null,
    themeSubtitle: String? = null,
    overviewBottomContent: (@Composable () -> Unit)? = null,
    leadingEntries: List<SettingsEntry> = emptyList(),
    trailingEntries: List<SettingsEntry> = emptyList(),
    performanceDiagnosticsEnabled: Boolean? = null,
    onPerformanceDiagnosticsChanged: ((Boolean) -> Unit)? = null,
    donationAppName: String? = null,
    onDonate: ((Long) -> Unit)? = null
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val resolvedCurrencySubtitle =
        currencySubtitle ?: stringResource(R.string.settings_currency_subtitle)
    val resolvedLanguageSubtitle =
        languageSubtitle ?: stringResource(R.string.settings_language_subtitle)
    val resolvedThemeSubtitle =
        themeSubtitle ?: stringResource(R.string.settings_theme_subtitle)
    val sharedEntries =
        listOf(
            SettingsEntry(
                id = "payments",
                title = stringResource(R.string.settings_payments),
                subtitle = stringResource(R.string.settings_payments_subtitle),
                testTag = SettingsTestTags.PAYMENTS_ROW,
                onClick = onPayments
            ),
            SettingsEntry(
                id = "currency",
                title = stringResource(R.string.settings_currency),
                subtitle = resolvedCurrencySubtitle,
                testTag = SettingsTestTags.CURRENCY_ROW,
                onClick = onCurrency
            ),
            SettingsEntry(
                id = "language",
                title = stringResource(R.string.settings_language),
                subtitle = resolvedLanguageSubtitle,
                testTag = SettingsTestTags.LANGUAGE_ROW,
                onClick = onLanguage
            ),
            SettingsEntry(
                id = "theme",
                title = stringResource(R.string.settings_theme),
                subtitle = resolvedThemeSubtitle,
                testTag = SettingsTestTags.THEME_ROW,
                onClick = onTheme
            )
        )
    val entries = leadingEntries + sharedEntries + trailingEntries
    require(entries.distinctBy(SettingsEntry::id).size == entries.size) {
        "Settings entry IDs must be unique"
    }
    require(
        (performanceDiagnosticsEnabled == null) ==
            (onPerformanceDiagnosticsChanged == null)
    ) {
        "Performance diagnostics state and callback must be provided together"
    }

    Scaffold(
        modifier = modifier.testTag(SettingsTestTags.SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    onBack?.let { back -> BackIconButton(onClick = back) }
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
            items(entries, key = { entry -> "entry:${entry.id}" }) { entry ->
                SettingsListItem(entry)
            }
            if (
                performanceDiagnosticsEnabled != null &&
                onPerformanceDiagnosticsChanged != null
            ) {
                item(key = "performance-diagnostics") {
                    PerformanceDiagnosticsRow(
                        enabled = performanceDiagnosticsEnabled,
                        onEnabledChanged = onPerformanceDiagnosticsChanged
                    )
                }
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
            overviewBottomContent?.let { content ->
                item(key = "slot:overview-bottom-content") {
                    content()
                }
            }
            item {
                SettingsFooter(legalLinks)
            }
        }
    }
}

@Composable
private fun PerformanceDiagnosticsRow(enabled: Boolean, onEnabledChanged: (Boolean) -> Unit) {
    AppListRow(
        onClick = { onEnabledChanged(!enabled) },
        minHeight = 48.dp,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_performance_diagnostics),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_performance_diagnostics_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged
        )
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
private fun SettingsFooter(legalLinks: SettingsLegalLinks) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text =
                stringResource(
                    R.string.settings_footer_version,
                    appVersionName()
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            legalLinks.privacyPolicyUrl?.let { privacyPolicyUrl ->
                Text(
                    text = stringResource(R.string.settings_footer_privacy),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uriHandler.openUri(privacyPolicyUrl) }
                )
            }
            legalLinks.termsUrl?.let { termsUrl ->
                Text(
                    text = stringResource(R.string.settings_footer_terms),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uriHandler.openUri(termsUrl) }
                )
            }
            Text(
                text = stringResource(R.string.settings_footer_repo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.clickable {
                        uriHandler.openUri(legalLinks.sourceCodeUrl)
                    }
            )
        }
    }
}

object SettingsTestTags {
    const val SCREEN = "settings_screen"
    const val PAYMENTS_ROW = "settings_payments_row"
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
            onCurrency = {},
            onLanguage = {},
            onTheme = {},
            legalLinks =
                SettingsLegalLinks(
                    privacyPolicyUrl = "https://example.com/privacy",
                    termsUrl = "https://example.com/terms",
                    sourceCodeUrl = "https://example.com/source"
                ),
            currencySubtitle = "SAT",
            languageSubtitle = "English",
            themeSubtitle = "System default"
        )
    }
}
