package xyz.lilsus.raylsuite.feature.paymentui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.feature.paymentui.components.SessionTransactionsScreen
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.session_transactions_empty
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.session_transactions_title

@Composable
fun PaymentFlow(
    state: PaymentFlowState,
    messageEvents: Flow<String>,
    appTitle: String,
    estimatedFeeHint: String?,
    onIntent: (PaymentIntent) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateShortcutCreate: () -> Unit,
    onNavigateContacts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val currentState = rememberUpdatedState(state)
    val currentMessageEvents = rememberUpdatedState(messageEvents)
    val currentOnIntent = rememberUpdatedState(onIntent)
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }
    NavHost(
        navController = navController,
        startDestination = PAYMENT_HOME_ROUTE,
        modifier = modifier
    ) {
        composable(PAYMENT_HOME_ROUTE) {
            PaymentHome(
                state = currentState.value,
                messageEvents = currentMessageEvents.value,
                appTitle = appTitle,
                estimatedFeeHint = estimatedFeeHint,
                onIntent = currentOnIntent.value,
                onNavigateTransactions = {
                    navController.navigate(PAYMENT_TRANSACTIONS_ROUTE) {
                        launchSingleTop = true
                    }
                },
                onNavigateTransactionDetail = { id ->
                    selectedTransactionId = id
                    navController.navigate(PAYMENT_DETAIL_ROUTE)
                },
                onNavigateSettings = onNavigateSettings,
                onNavigateShortcutCreate = onNavigateShortcutCreate,
                onNavigateContacts = onNavigateContacts
            )
        }
        composable(PAYMENT_TRANSACTIONS_ROUTE) {
            LaunchedEffect(Unit) {
                currentOnIntent.value(PaymentIntent.SessionTransactionsOpened)
            }
            SessionTransactionsScreen(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(Res.string.session_transactions_title),
                emptyMessage = stringResource(Res.string.session_transactions_empty),
                transactions = currentState.value.sessionItems.map(PaymentSessionItem::transaction),
                onBack = navController::navigateUp,
                onTransactionSelected = { id ->
                    selectedTransactionId = id
                    navController.navigate(PAYMENT_DETAIL_ROUTE)
                }
            )
        }
        composable(PAYMENT_DETAIL_ROUTE) {
            PaymentTransactionDetail(
                detail = currentState.value.sessionItems.firstOrNull {
                    it.id == selectedTransactionId
                }?.detail,
                estimatedFeeHint = estimatedFeeHint,
                onIntent = currentOnIntent.value,
                onBack = navController::navigateUp
            )
        }
    }
}

private const val PAYMENT_HOME_ROUTE = "payment/home"
private const val PAYMENT_TRANSACTIONS_ROUTE = "payment/transactions"
private const val PAYMENT_DETAIL_ROUTE = "payment/detail"
