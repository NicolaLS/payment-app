package xyz.lilsus.blip.presentation.settings

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import rayl_suite.blip.shared.generated.resources.Res
import rayl_suite.blip.shared.generated.resources.search_placeholder
import xyz.lilsus.blip.presentation.common.AppListScaffold
import xyz.lilsus.blip.presentation.common.AppSelectableListRow

@Composable
internal fun CurrencyPickerContent(
    selectedCode: String,
    searchQuery: String,
    options: List<CurrencyOption>,
    onQueryChange: (String) -> Unit,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = options.filter { option ->
        option.label.contains(searchQuery, ignoreCase = true)
    }

    AppListScaffold(
        isEmpty = filtered.isEmpty(),
        emptyMessage = null,
        modifier = modifier,
        showSearchBar = true,
        searchQuery = searchQuery,
        onSearchQueryChange = onQueryChange,
        searchPlaceholder = stringResource(Res.string.search_placeholder)
    ) {
        items(filtered, key = { it.code }) { option ->
            AppSelectableListRow(
                title = option.label,
                selected = selectedCode == option.code,
                onClick = { onCurrencySelected(option.code) }
            )
        }
    }
}
