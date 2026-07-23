package xyz.lilsus.rayl.foundation.ui.presentation.settings

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.rayl.foundation.ui.generated.resources.Res
import xyz.lilsus.rayl.foundation.ui.generated.resources.search_placeholder
import xyz.lilsus.rayl.foundation.ui.presentation.common.AppListScaffold
import xyz.lilsus.rayl.foundation.ui.presentation.common.AppSelectableListRow

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
