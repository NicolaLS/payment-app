package xyz.lilsus.raylsuite.feature.paymenthub.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOffer
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrder
import xyz.lilsus.raylsuite.feature.paymenthub.HubServiceError
import xyz.lilsus.raylsuite.feature.paymenthub.HubServicePurchaseState
import xyz.lilsus.raylsuite.feature.paymenthub.R
import xyz.lilsus.raylsuite.feature.paymenthub.WidgetHubState
import xyz.lilsus.raylsuite.feature.paymenthub.WidgetHubViewModel

/** Keep the native sheet mounted until its exit animation finishes before opening wallet payment. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HubServicePurchaseSheet(state: WidgetHubState, viewModel: WidgetHubViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var displayedPurchase by remember { mutableStateOf<HubServicePurchaseState?>(null) }
    LaunchedEffect(state.purchase) {
        if (state.purchase != null) {
            displayedPurchase = state.purchase
        } else if (displayedPurchase != null) {
            sheetState.hide()
            displayedPurchase = null
        }
    }
    LaunchedEffect(state.servicePaymentReady, displayedPurchase == null) {
        if (state.servicePaymentReady && displayedPurchase == null) {
            withFrameNanos { }
            viewModel.completeServicePaymentHandoff()
        }
    }
    val purchase = state.purchase ?: displayedPurchase ?: return
    ModalBottomSheet(
        onDismissRequest = viewModel::closePurchase,
        sheetState = sheetState,
        sheetMaxWidth = 640.dp
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    purchase.title.ifBlank { stringResource(R.string.hub_service_title) },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                TextButton(onClick = viewModel::closePurchase) {
                    Text(stringResource(R.string.hub_canvas_done))
                }
            }
            purchase.error?.let { error ->
                Text(
                    error.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }
            if (purchase.busy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(
                        stringResource(R.string.hub_widget_loading),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
            }
            val order = purchase.order
            when {
                order != null -> {
                    ServiceOrderDetails(order)
                    if (purchase.canPay) {
                        Button(
                            onClick = viewModel::payServiceOrder,
                            enabled = !purchase.busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.hub_service_pay))
                        }
                    }
                    CheckOrderButton(purchase.busy, viewModel::refreshServiceOrder)
                }

                state.hasServiceOrder && purchase.offers.isEmpty() -> {
                    OrderFact(stringResource(R.string.hub_service_recipient), purchase.phone)
                    Text(
                        stringResource(R.string.hub_service_unknown_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    CheckOrderButton(purchase.busy, viewModel::refreshServiceOrder)
                }

                else -> ServiceOrderSelection(purchase, viewModel)
            }
        }
    }
}

@Composable
private fun CheckOrderButton(busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.hub_service_check_status))
    }
}

@Composable
private fun ServiceOrderSelection(
    purchase: HubServicePurchaseState,
    viewModel: WidgetHubViewModel
) {
    val locale = serviceLocale()
    OutlinedTextField(
        value = purchase.phone,
        onValueChange = viewModel::updateServicePhone,
        enabled = !purchase.busy,
        isError = purchase.error == HubServiceError.InvalidPhone,
        label = { Text(stringResource(R.string.hub_service_phone)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        stringResource(R.string.hub_service_choose_offer),
        style = MaterialTheme.typography.titleMedium
    )
    Column(modifier = Modifier.selectableGroup()) {
        purchase.offers.forEach { offer ->
            ServiceOfferRow(
                offer = offer,
                selected = offer.id == purchase.selectedOfferId,
                enabled = !purchase.busy,
                locale = locale,
                onClick = { viewModel.selectServiceOffer(offer.id) }
            )
        }
    }
    purchase.selectedOffer?.range?.let { range ->
        OutlinedTextField(
            value = purchase.amountInput,
            onValueChange = viewModel::updateServiceAmount,
            enabled = !purchase.busy,
            isError = purchase.error == HubServiceError.InvalidAmount,
            label = { Text(stringResource(R.string.hub_service_amount, range.currency)) },
            supportingText = {
                Text(
                    stringResource(
                        R.string.hub_service_amount_range,
                        formatServiceMinor(range.minMinor, range.fractionDigits, locale),
                        formatServiceMinor(range.maxMinor, range.fractionDigits, locale),
                        formatServiceMinor(range.stepMinor, range.fractionDigits, locale)
                    )
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (range.fractionDigits ==
                    0
                ) {
                    KeyboardType.Number
                } else {
                    KeyboardType.Decimal
                }
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
    Button(
        onClick = viewModel::prepareServiceOrder,
        enabled = !purchase.busy && purchase.phone.isNotBlank() && purchase.selectedOffer != null &&
            (purchase.selectedOffer?.range == null || purchase.amountInput.isNotBlank()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.hub_service_review))
    }
}

@Composable
private fun ServiceOfferRow(
    offer: HubServiceOffer,
    selected: Boolean,
    enabled: Boolean,
    locale: Locale,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = onClick
        ).padding(vertical = 12.dp)
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = null)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(offer.title, style = MaterialTheme.typography.titleSmall)
            offer.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            offer.amount?.let { amount ->
                Text(
                    amount.display(locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ServiceOrderDetails(order: HubServiceOrder) {
    val locale = serviceLocale()
    OrderFact(stringResource(R.string.hub_service_recipient), order.phone)
    OrderFact(stringResource(R.string.hub_service_item), order.itemTitle)
    order.requestedAmount?.let { amount ->
        OrderFact(
            stringResource(R.string.hub_service_amount, amount.currency),
            amount.display(locale)
        )
    }
    order.payment?.let { payment ->
        OrderFact(
            stringResource(R.string.hub_service_lightning_price),
            formatServiceSats(payment.amountMsat, locale)
        )
        Text(
            stringResource(
                R.string.hub_service_quote_expires,
                formatServiceExpiry(payment.expiresAt, locale)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        OrderFact(
            stringResource(R.string.hub_service_order_status),
            serviceStatusLabel(order.state)
        )
        OrderFact(
            stringResource(R.string.hub_service_payment_status),
            serviceStatusLabel(order.paymentStatus)
        )
        OrderFact(
            stringResource(R.string.hub_service_fulfillment_status),
            serviceStatusLabel(order.fulfillmentStatus)
        )
    }
    Text(
        stringResource(R.string.hub_service_payment_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (order.state == "unknown" || order.paymentStatus == "unknown" ||
        order.fulfillmentStatus == "unknown"
    ) {
        Text(
            stringResource(R.string.hub_service_unknown_hint),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    OrderFact(stringResource(R.string.hub_service_order_reference), order.orderId)
}

@Composable
private fun OrderFact(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
