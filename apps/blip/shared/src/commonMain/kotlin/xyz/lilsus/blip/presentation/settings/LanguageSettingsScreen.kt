package xyz.lilsus.blip.presentation.settings

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import rayl_suite.blip.shared.generated.resources.Res
import rayl_suite.blip.shared.generated.resources.search_placeholder
import rayl_suite.blip.shared.generated.resources.settings_language
import xyz.lilsus.blip.domain.format.rememberAppLocale
import xyz.lilsus.blip.domain.model.LanguageCatalog
import xyz.lilsus.blip.presentation.common.AppListDefaults
import xyz.lilsus.blip.presentation.common.AppListScaffold
import xyz.lilsus.blip.presentation.common.AppSelectableListRow
import xyz.lilsus.blip.presentation.common.BackIconButton
import xyz.lilsus.blip.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    state: LanguageSettingsUiState,
    onQueryChange: (String) -> Unit,
    onOptionSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val localeTag = rememberAppLocale().languageTag
    val filtered = state.options.filter { option ->
        option.title.contains(state.searchQuery, ignoreCase = true)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            key(localeTag) {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(Res.string.settings_language)) },
                    navigationIcon = {
                        BackIconButton(onClick = onBack)
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { padding ->
        AppListScaffold(
            isEmpty = filtered.isEmpty(),
            emptyMessage = null,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(AppListDefaults.ScreenPadding),
            showSearchBar = true,
            searchQuery = state.searchQuery,
            onSearchQueryChange = onQueryChange,
            searchPlaceholder = stringResource(Res.string.search_placeholder)
        ) {
            items(filtered, key = { it.id }) { option ->
                AppSelectableListRow(
                    title = option.title,
                    selected = state.selectedCode == option.id,
                    onClick = { onOptionSelected(option.id) }
                )
            }
        }
    }
}

@Preview
@Composable
private fun LanguageSettingsScreenPreview() {
    AppTheme {
        LanguageSettingsScreen(
            state = LanguageSettingsUiState(
                selectedCode = "de",
                deviceCode = "de",
                options = listOf(
                    LanguageOption("en", LanguageCatalog.displayName("en"), "en"),
                    LanguageOption("de", LanguageCatalog.displayName("de"), "de"),
                    LanguageOption("es", LanguageCatalog.displayName("es"), "es")
                )
            ),
            onQueryChange = {},
            onOptionSelected = {},
            onBack = {}
        )
    }
}
