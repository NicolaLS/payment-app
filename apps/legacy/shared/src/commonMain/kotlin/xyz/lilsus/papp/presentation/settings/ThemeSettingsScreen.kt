package xyz.lilsus.papp.presentation.settings

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
import androidx.compose.ui.tooling.preview.Preview
import lasr.shared.generated.resources.Res
import lasr.shared.generated.resources.settings_theme
import lasr.shared.generated.resources.settings_theme_dark
import lasr.shared.generated.resources.settings_theme_light
import lasr.shared.generated.resources.settings_theme_system_default
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.domain.model.ThemePreference
import xyz.lilsus.papp.presentation.common.AppListDefaults
import xyz.lilsus.papp.presentation.common.AppListScaffold
import xyz.lilsus.papp.presentation.common.AppSelectableListRow
import xyz.lilsus.papp.presentation.common.BackIconButton
import xyz.lilsus.papp.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    state: ThemeSettingsUiState,
    onThemeSelected: (ThemePreference) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val resolvedSystem = stringResource(
        Res.string.settings_theme_system_default,
        stringResource(
            if (isSystemInDarkTheme()) {
                Res.string.settings_theme_dark
            } else {
                Res.string.settings_theme_light
            }
        )
    )
    val options = listOf(
        ThemePreference.System to resolvedSystem,
        ThemePreference.Light to stringResource(Res.string.settings_theme_light),
        ThemePreference.Dark to stringResource(Res.string.settings_theme_dark)
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_theme)) },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        AppListScaffold(
            isEmpty = false,
            emptyMessage = null,
            modifier = Modifier
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

@Preview
@Composable
private fun ThemeSettingsScreenPreview() {
    AppTheme(themePreference = ThemePreference.Dark) {
        ThemeSettingsScreen(
            state = ThemeSettingsUiState(selected = ThemePreference.Dark),
            onThemeSelected = {},
            onBack = {}
        )
    }
}
