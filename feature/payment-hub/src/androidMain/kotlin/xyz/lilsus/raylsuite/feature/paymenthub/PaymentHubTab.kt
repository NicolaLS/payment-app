package xyz.lilsus.raylsuite.feature.paymenthub

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymenthub.widget.HubServicePurchaseSheet
import xyz.lilsus.raylsuite.feature.paymenthub.widget.HubWidgetCanvas
import xyz.lilsus.raylsuite.feature.paymenthub.widget.HubWidgetEditorScreen
import xyz.lilsus.raylsuite.feature.paymenthub.widget.HubWidgetGallery
import xyz.lilsus.raylsuite.feature.paymenthub.widget.HubWidgetVariants

/** Native gallery, configuration, and canvas. Payment policy stays with the host. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHubTab(
    repository: PaymentHubRepository,
    controller: PaymentHubController,
    preferredCurrencyCode: () -> String,
    modifier: Modifier = Modifier,
    importButton: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)
    val currency by rememberUpdatedState(preferredCurrencyCode)
    val remote =
        remember(repository, controller, context.applicationContext) {
            createHubRemoteSession(context)
        }
    val viewModel = remember(repository, controller, remote) {
        WidgetHubViewModel(
            repository = repository,
            host = controller,
            defaultCurrencyCode = { currency() },
            locale = { currentContext.resources.configuration.locales[0].toLanguageTag() },
            catalog = remote?.catalog,
            orderStore = remote?.orderStore
        )
    }
    DisposableEffect(viewModel, remote) {
        onDispose {
            viewModel.clear()
            remote?.close()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(viewModel, lifecycleOwner) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { owner, _ ->
            viewModel.setActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        viewModel.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer) }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    NavigationEventHandler(
        state = rememberNavigationEventState(currentInfo = WidgetHubNavigationInfo(state.screen)),
        isForwardEnabled = false,
        isBackEnabled = state.purchase == null &&
            (state.screen != HubWidgetScreen.Hub || state.arranging),
        onBackCompleted = {
            if (state.arranging &&
                state.screen == HubWidgetScreen.Hub
            ) {
                viewModel.setArranging(false)
            } else {
                viewModel.back()
            }
        }
    )
    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.screen != HubWidgetScreen.Hub) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(
                                when (state.screen) {
                                    HubWidgetScreen.Gallery -> R.string.hub_widget_gallery_title

                                    HubWidgetScreen.Variants -> R.string.hub_widget_select_variant

                                    HubWidgetScreen.Configure ->
                                        if (state.editor?.existingWidgetId != null) {
                                            R.string.hub_widget_edit
                                        } else {
                                            R.string.hub_configure_title
                                        }

                                    HubWidgetScreen.Hub -> R.string.hub_widget_gallery_title
                                }
                            )
                        )
                    },
                    navigationIcon = { BackIconButton(onClick = { viewModel.back() }) }
                )
            }
        }
    ) { padding ->
        val content = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        when (state.screen) {
            HubWidgetScreen.Hub -> HubWidgetCanvas(state, viewModel, content)

            HubWidgetScreen.Gallery -> HubWidgetGallery(state, viewModel, content)

            HubWidgetScreen.Variants -> HubWidgetVariants(state, viewModel, content)

            HubWidgetScreen.Configure -> HubWidgetEditorScreen(
                state,
                viewModel,
                content,
                importButton
            )
        }
    }
    HubServicePurchaseSheet(state, viewModel)
}

private data class WidgetHubNavigationInfo(val screen: HubWidgetScreen) : NavigationEventInfo()
