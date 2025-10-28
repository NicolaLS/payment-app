package xyz.lilsus.papp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import org.koin.core.parameter.parametersOf
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.papp.presentation.main.MainEvent
import xyz.lilsus.papp.presentation.main.MainIntent
import xyz.lilsus.papp.presentation.main.MainScreen
import xyz.lilsus.papp.presentation.main.MainViewModel

@Serializable
object Pay

fun NavGraphBuilder.paymentScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToConnectWallet: (String) -> Unit = {},
) {
    composable<Pay> {
        MainScreenEntry(
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToConnectWallet = onNavigateToConnectWallet,
        )
    }
}

@Composable
private fun MainScreenEntry(
    onNavigateToSettings: () -> Unit,
    onNavigateToConnectWallet: (String) -> Unit,
) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    // TODO: Inject real wallet URI when connection flow is implemented.
    val connectUri = ""
    val viewModel = remember(connectUri) {
        koin.get<MainViewModel> { parametersOf(connectUri) }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                MainEvent.OpenScanner -> Unit // Scanner integration handled later.
                is MainEvent.ShowError -> Unit // Surface via snackbar/dialog later.
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    MainScreen(
        onNavigateSettings = onNavigateToSettings,
        onNavigateConnectWallet = onNavigateToConnectWallet,
        uiState = uiState,
        onManualAmountKeyPress = { key ->
            viewModel.dispatch(MainIntent.ManualAmountKeyPress(key))
        },
        onManualAmountSubmit = { viewModel.dispatch(MainIntent.ManualAmountSubmit) },
        onManualAmountDismiss = { viewModel.dispatch(MainIntent.ManualAmountDismiss) },
        onResultDismiss = { viewModel.dispatch(MainIntent.DismissResult) },
    )
}
