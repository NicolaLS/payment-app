package xyz.lilsus.raylsuite.feature.themesettings

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.ui.components.AppListDefaults
import xyz.lilsus.raylsuite.core.ui.components.AppListScaffold
import xyz.lilsus.raylsuite.core.ui.components.AppSelectableListRow
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.themesettings.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    state: ThemeSettingsUiState,
    onThemeSelected: (ThemePreference) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val resolvedSystem =
        stringResource(
            R.string.settings_theme_system_default,
            stringResource(
                if (isSystemInDarkTheme()) {
                    R.string.settings_theme_dark
                } else {
                    R.string.settings_theme_light
                }
            )
        )
    val options =
        listOf(
            ThemePreference.System to resolvedSystem,
            ThemePreference.Light to stringResource(R.string.settings_theme_light),
            ThemePreference.Dark to stringResource(R.string.settings_theme_dark)
        )

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_theme)) },
                navigationIcon = {
                    BackIconButton(
                        onClick = onBack,
                        testTag = ThemeSettingsTestTags.BACK_BUTTON
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        AppListScaffold(
            isEmpty = false,
            emptyMessage = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .navigationBarsPadding()
                    .padding(AppListDefaults.ScreenPadding)
        ) {
            items(options, key = { it.first }) { (preference, title) ->
                AppSelectableListRow(
                    title = title,
                    selected = state.selected == preference,
                    onClick = { onThemeSelected(preference) }
                )
            }
        }
    }
}

object ThemeSettingsTestTags {
    const val BACK_BUTTON = "settings_back_button"
}

@Preview
@Composable
private fun ThemeSettingsScreenPreview() {
    RaylSuiteTheme(themePreference = ThemePreference.Dark) {
        ThemeSettingsScreen(
            state = ThemeSettingsUiState(selected = ThemePreference.Dark),
            onThemeSelected = {},
            onBack = {}
        )
    }
}
