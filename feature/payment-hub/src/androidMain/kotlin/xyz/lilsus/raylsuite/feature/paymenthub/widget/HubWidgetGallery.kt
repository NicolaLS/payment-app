package xyz.lilsus.raylsuite.feature.paymenthub.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import xyz.lilsus.raylsuite.feature.paymenthub.HubWidgetDefinition
import xyz.lilsus.raylsuite.feature.paymenthub.HubWidgetKind
import xyz.lilsus.raylsuite.feature.paymenthub.HubWidgetTile
import xyz.lilsus.raylsuite.feature.paymenthub.R
import xyz.lilsus.raylsuite.feature.paymenthub.WidgetHubState
import xyz.lilsus.raylsuite.feature.paymenthub.WidgetHubViewModel

@Composable
internal fun HubWidgetGallery(
    state: WidgetHubState,
    viewModel: WidgetHubViewModel,
    modifier: Modifier = Modifier
) {
    Box(contentAlignment = Alignment.TopCenter, modifier = modifier) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()
        ) {
            item {
                Text(
                    stringResource(R.string.hub_widget_gallery_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            items(state.gallery, key = HubWidgetDefinition::id) { definition ->
                GalleryEntry(definition, onClick = { viewModel.selectDefinition(definition.id) })
            }
            if (state.catalogLoading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
            if (state.catalogUnavailable) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.hub_widget_catalog_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::refreshCatalog) {
                            Text(stringResource(R.string.hub_widget_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryEntry(definition: HubWidgetDefinition, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(20.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (definition.kind) {
                            HubWidgetKind.Contacts -> Icons.Filled.People
                            HubWidgetKind.Shortcut -> Icons.Filled.Bolt
                            HubWidgetKind.Favorites -> Icons.Filled.Star
                            HubWidgetKind.Recents -> Icons.Filled.AccessTime
                            HubWidgetKind.Metric -> Icons.Filled.Insights
                            HubWidgetKind.Service -> Icons.Filled.PhoneAndroid
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(definition.label(), style = MaterialTheme.typography.titleMedium)
                val body = definition.body()
                if (body.isNotBlank()) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun HubWidgetVariants(
    state: WidgetHubState,
    viewModel: WidgetHubViewModel,
    modifier: Modifier = Modifier
) {
    val definition = state.selectedDefinition ?: return
    val variants = definition.variants
    val pager = rememberPagerState(
        initialPage = variants.indexOfFirst {
            it.id ==
                state.editor?.variantId
        }.coerceAtLeast(0)
    ) {
        variants.size
    }
    LaunchedEffect(pager, definition.id) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            variants.getOrNull(page)?.let { viewModel.selectVariant(it.id) }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(
                1f
            ).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 20.dp)
        ) {
            Text(
                definition.label(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Text(
                definition.body(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 480.dp).padding(horizontal = 24.dp)
            )
            HorizontalPager(
                state = pager,
                pageSpacing = 16.dp,
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val variant = variants[page]
                val description =
                    stringResource(R.string.hub_widget_preview_page, page + 1, variants.size)
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = description
                    }
                ) {
                    val previewWidth = maxWidth.coerceAtMost(400.dp)
                    val unit = (previewWidth - 12.dp) / 2
                    Box(
                        Modifier.fillMaxWidth().height(previewWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        HubWidgetFace(
                            tile = HubWidgetTile(
                                "preview",
                                definition.id,
                                definition.kind,
                                variant,
                                definition.title
                            ),
                            onPay = {},
                            preview = true,
                            interactive = false,
                            modifier = Modifier.width(
                                unit * variant.columns + 12.dp * (variant.columns - 1)
                            )
                                .height(unit * variant.rows + 12.dp * (variant.rows - 1))
                        )
                    }
                }
            }
            if (variants.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    variants.indices.forEach { page ->
                        Box(
                            Modifier.size(7.dp).background(
                                if (page ==
                                    pager.currentPage
                                ) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                CircleShape
                            )
                        )
                    }
                }
            }
            variants.getOrNull(pager.currentPage)?.let { variant ->
                Text(variant.title ?: variant.label(), style = MaterialTheme.typography.titleMedium)
                if (definition.kind != HubWidgetKind.Metric) {
                    Text(
                        variant.body(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Button(
            onClick = {
                variants.getOrNull(pager.currentPage)?.let { viewModel.selectVariant(it.id) }
                viewModel.configureSelected()
            },
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(16.dp)
        ) {
            Text(stringResource(R.string.hub_widget_continue))
        }
    }
}
