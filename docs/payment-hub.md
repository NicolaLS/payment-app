# Payment Hub

Payment Hub is each app's personal collection of payable destinations. It replaces the retired
Contacts and Payment Shortcuts storage and screens. Blip's provider-owned contact import remains
available as one way to create hub targets; imported contacts do not restore the old contact model.

## Concepts

- **Direct target** (`DirectPaymentTarget`) is a stable, named Lightning Address configuration with
  either an ask-every-time or preset amount rule. Identity is its `HubItemId`, not its address.
- **Group** (`PaymentTargetGroup`) is a named, one-level collection of direct-target IDs. Opening a
  group lets the user choose one member; a group itself never pays.
- **Personalization** includes pinned order, successful-payment statistics, a bundled icon, and a
  suite accent. Statistics change only after a terminal successful wallet payment.
- **Canvas** is the user's compact/wide tile arrangement. Its layout is presentation state keyed by
  `HubItemId`; clearing the canvas does not delete hub records.

`feature:payment-hub` owns the provider-neutral values, repository, localized presentation,
controller/intents, Android renderer, and shared iOS renderer. Hub records are stored as one JSON
document under `paymentHub.document`; canvas placement is stored separately under
`paymentHub.canvas.layout`.

Android renders the Hub with Compose. iOS renders it with the shared SwiftUI source at
`feature/payment-hub/src/iosMain/swift/NativePaymentHubView.swift`. Both platforms consume the same
state and report the same user intents while retaining native navigation, forms, menus, sheets,
scrolling, accessibility, and safe-area behavior.

## Payment boundary

Selecting a target emits a `DirectTargetPaymentIntent` containing the target ID, address, amount
rule, and comment. Each app's payment coordinator owns Lightning Address resolution, fresh fiat
quoting, min/max and comment validation, confirmation policy, provider payment, retry, and errors.
The Hub never receives a wallet interface or provider failure type.

After terminal success, the app reports the target ID through `recordSuccessfulPayment`. It may
offer to save an address paid outside the Hub. Each app's coordinator always presents confirmation
for a preset amount because resolving a fiat preset can depend on a fresh exchange rate.

## Extending the Hub

A future service such as phone top-up, bills, or vouchers should own its API, credentials,
configuration, fulfillment, and recovery. It may project safe display values into Hub rendering and
route selection back through the app composition root. Do not extend `DirectPaymentTarget` with
provider/service lifecycle states or introduce a generic service engine before multiple concrete
features prove identical semantics.

Destructive target and group deletion currently has no confirmation or undo. That is tracked as a
separate Payment Hub behavior improvement, not part of the native renderer migration.
