# Claro service experiment

The payment hub can discover Claro El Salvador airtime and packages from the
Bitrefill Personal API, prepare an unpaid Lightning invoice, and check supplier
delivery separately from wallet payment. This is an optional local experiment;
the local Contacts, Shortcut, Favorites, and Recents widgets work without it.

The backend owns the supplier connection. The mobile app receives bounded
`service/v1` data and renders compiled native controls. It never receives the
Bitrefill credential, supplier package IDs, or account balance. Payment still
uses the connected wallet's existing admission and confirmation flow.

## Run locally

Use JDK 21. Store a Personal API token in a private file outside Git, readable
only by its owner. Obtain the token from Bitrefill's
[developer settings](https://www.bitrefill.com/account/developers). From the
repository root:

```sh
BITREFILL_API_KEY_FILE=/absolute/private/path/bitrefill-key.txt ./gradlew :backend:hub:run
```

The server binds to `127.0.0.1:8080`. Its Gradle `run` task uses the repository
root as its working directory. `BITREFILL_API_KEY_FILE` and `BITREFILL_API_KEY`
are mutually exclusive. Neither the startup diagnostic nor HTTP responses print
the key. With neither set, the server starts and returns an empty catalog.
An invalid key produces an unavailable remote catalog rather than fake offers.

| Setting | Default | Meaning |
| --- | --- | --- |
| `BITREFILL_API_KEY_FILE` | absent | Private file containing the Personal API token |
| `BITREFILL_API_KEY` | absent | Alternative token source; do not set with the file setting |
| `HUB_HOST` | `127.0.0.1` | Listening address |
| `HUB_PORT` | `8080` | Listening port |
| `HUB_ORDER_DIR` | `backend/hub/.runtime/orders` | Durable order journal; relative to the process working directory |
| `BITREFILL_CLARO_TOPUP_PRODUCT_ID` | `claro-el-salvador` | Authenticated airtime product ID |
| `BITREFILL_CLARO_PACKAGES_PRODUCT_ID` | `claro-el-salvador-bundles` | Authenticated packages product ID |
| `BITREFILL_COUNTRY` | `SV` | Required supplier product country |
| `BITREFILL_CALLING_CODE` | `503` | Required destination prefix without `+` |

The product overrides let the experiment adapt to supplier catalog changes.
They do not turn the adapter into a general carrier discovery service. Keep the
country, calling code, and product IDs consistent. The code checks E.164 shape
and the configured prefix; it does not establish ownership, carrier, or whether
the number is prepaid. Supplier validation can still reject a number.

For a Rayl Android Debug build using the emulator's host alias:

```sh
./gradlew :rayl:androidApp:assembleDebug -Prayl.hub.baseUrl=http://10.0.2.2:8080
```

Rayl Debug allows cleartext HTTP only for `localhost`, `127.0.0.1`, and
`10.0.2.2`. Other domains, apps, and Release configurations retain their normal
network rules. The build also accepts `RAYL_HUB_BASE_URL` as an alternative to
the Gradle property. An absent URL leaves remote widgets unavailable.

For a Rayl iOS Debug simulator build:

```sh
scripts/build-hub-ios-debug.sh
```

The helper requires the caller's `JAVA_HOME` to point to JDK 21. It defaults to
`RAYL_HUB_BASE_URL=http://127.0.0.1:8080` and
`RAYL_HUB_IOS_DERIVED_DATA=build/hub-ios`. It creates temporary ATS exceptions
for the two exact loopback hosts, builds the arm64 simulator target, and removes
the temporary plist. It does not install or launch the app.

## Try the flow

Keep the backend running while using the app. For Android, start an emulator
and install the Debug APK built above:

```sh
adb install -r apps/rayl/androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Open Rayl Dev from the emulator. For iOS, start an iPhone simulator, then install
and launch the helper's default output:

```sh
xcrun simctl install booted build/hub-ios/Build/Products/Debug-iphonesimulator/Rayl.app
xcrun simctl launch booted com.nicolasusca.rayl
```

1. Connect a wallet and open the Payment Hub.
2. Choose **Add to hub → Claro**, select the small **Top-up** layout, enter a
   Claro El Salvador prepaid number (preferably in `+503…` format), and add it.
3. Repeat with a **Packages** layout to try the package selection card.
4. Tap the top-up widget, enter a value within the displayed range, and choose
   **Review order**. For a package, select its offer and choose **Review order**.
   This step creates a real unpaid Bitrefill invoice.
5. Check the recipient, selected service value or package, exact Bitcoin price,
   and expiry. **Continue to payment** opens the existing wallet confirmation flow.
   Completing that confirmation spends real funds and purchases the service.
6. Return to the Hub and choose **View service order**. **Check status** refreshes
   payment and delivery separately; a paid invoice alone does not mean the
   carrier has delivered the top-up or package.
7. Close and reopen the app while an order is pending to check recovery through
   **View service order**. Removing its widget should leave order recovery
   available. Keep the backend journal and app storage intact during this check.

The experiment retains the latest service order on the device. It allows a new
purchase after that order is delivered, failed, or expired; unresolved orders
must be checked first. This is not a complete purchase-history screen. A lost
supplier response during creation can require manual reconciliation as described
below. Until a real purchase is tried, successful catalog discovery and quote
validation do not establish live carrier delivery.

If Claro does not appear, check that the app was built with the local backend
URL, the backend is running, and the key can access both configured products.
The loopback URLs above are for a simulator/emulator on the development machine;
a physical device needs a reachable backend and an appropriate HTTPS setup.

## Catalog and exact values

The adapter reads `GET /v2/products/{id}` from
`https://api.bitrefill.com`, requires the requested product identity, country,
and in-stock flag, and caches normalized results for 60 seconds. Preparation
refreshes them before creating an invoice. A changed revision requires the
client to reload the selection.

The authenticated response uses `packages[].id` as the value later sent in
`package_id`; those IDs may contain spaces and symbols. Airtime can expose a
`range` with `min`, `max`, and `step`. Named packages expose a `value` label and
can also expose a numeric `amount`. The
[sanitized upstream example](api/payment-hub/bitrefill-products.sanitized.example.json)
shows the observed response shape. Its availability, denominations, and labels
are examples, not a frozen product catalog. The live API is authoritative;
the public storefront can show different ranges.

Service money is an integer decimal string in minor units with an explicit
currency and fraction digit count: USD `"1000"` with `fractionDigits: 2` means
$10 of airtime. The adapter converts exact `amount` or numeric `value` data
without rounding. A named package may have no declared denomination. It never
uses `packages[].price` or `range.price_rate` as the service value: Bitrefill
prices these in the API account's currency.

The payable Bitcoin amount comes only from the returned, signed BOLT11
invoice. The backend validates a mainnet, positive, fixed-amount BOLT11 and
derives its exact integer `amountMsat` and expiry. The mobile client validates
the invoice independently before exposing Pay. A catalog amount is not a
Bitcoin quote, and no local exchange-rate estimate determines payment.

Supplier package labels are preserved. The app does not infer GB allowances,
validity periods, or benefits from a label or price. Fixed interface labels in
the backend catalog support English, German, and Spanish through
`Accept-Language`; supplier product prose is not translated.

## HTTP contract

All requests carry `X-Rayl-App` (native package/bundle ID), `X-Rayl-Version`,
`X-Rayl-Build`, `X-Rayl-Platform`, comma-separated `X-Rayl-Hub-Contracts`, and
`Accept-Language`. These are compatibility metadata, not user identity.
Catalog responses omit services unless the client advertises `service/v1`.
Responses use `Cache-Control: no-store`.

| Method and path | Behavior |
| --- | --- |
| `GET /hub/v1/widgets` | Returns supported catalog descriptors |
| `POST /hub/v1/widgets/{widgetId}/content` | Takes `variantId` and local `configuration`; returns current service offers |
| `PUT /hub/v1/orders/{client UUID}` | Prepares or resumes exactly the same locally identified request |
| `GET /hub/v1/orders/{client UUID}` | Recovers and refreshes that order's supplier state |

Order requests require `Authorization: Bearer <64 lowercase hex characters>`.
The client generates this random 32-byte recovery token and a UUID, and saves
both with the complete request before its first PUT. There are no user accounts
or an account-wide order-list endpoint. A phone number is configuration and
order data, never an authentication credential.

Examples and shape schemas:

- [Service catalog](api/payment-hub/catalog-service.example.json) and
  [catalog schema](api/payment-hub/service-catalog.schema.json).
- [Airtime content](api/payment-hub/content-service-topup.example.json) and
  [package content](api/payment-hub/content-service-packages.example.json).
- [Preparation request](api/payment-hub/order-request.example.json),
  [awaiting-payment response](api/payment-hub/order-awaiting.example.json), and
  [content/order schema](api/payment-hub/service-purchase.schema.json).

Example IDs and revisions are illustrative; fetch real values before preparing
an order. The example phone and invoice are deliberately unusable. JSON Schema
checks shape; the implementation additionally checks monetary bounds and step,
revision, offer/variant correspondence, invoice validity, and status consistency.

The backend maps normalized phone-service content into the known
`service-topup` small template and `service-packages` wide/large templates in
`PhoneServiceCatalog`. Supplier adapters do not build native widget layouts.
`ServiceSupplier` owns catalog normalization, preparation, and status lookup.
The configured supplier list is ordered: the first supplier exposing a service
ID wins. A future routing policy can replace that initial choice; an existing
order retains the selected supplier ID and opaque invoice reference.

## Preparation, recovery, and delivery

The journal serializes operations and writes a durable `preparing` record
before the supplier POST. The adapter requests `payment_method: lightning`,
`auto_pay: false`, and `send_email: false`. It never calls the account-balance
payment endpoint. Neither Ktor nor its OkHttp transport automatically retries
or redirects this POST.

Repeating PUT with the same UUID, token, and body returns the recorded order.
A changed body returns `409 order_conflict`; a wrong token returns
`401 order_unauthorized`. A definitive rejection before supplier submission
returns `400 invalid_request`, `invalid_phone`, `invalid_amount`, or
`invalid_offer`, or `409 catalog_changed` / `offer_unavailable`. Missing service
and unavailable supplier conditions are reported separately.

`404 order_not_found` from GET means no durable record exists, so the client may
repeat the original PUT with the **same** UUID, token, and request. GET and PUT
share the journal lock, including the in-flight preparation boundary.
Once submission was attempted, an uncertain result returns a stored `unknown`
order rather than a plain validation error. It must not trigger another
supplier creation. A crash between sending the supplier request and saving its
reference can leave a permanently unknown order that needs manual supplier
reconciliation. The Personal API does not document an invoice-creation
idempotency key that would resolve that interval automatically.

Payment and fulfillment remain separate fields. A paid invoice may still be
processing or have failed delivery. An invoice marked `complete` by Bitrefill
does not itself prove delivery; the adapter requires its one expected order to
report `delivered`. Expired unpaid invoices cannot be paid from this app, but
an explicit later status refresh can discover a payment that settled after
the local expiry check. A previously confirmed payment never becomes unpaid.

A supplier order marked `refunded` retains `paymentStatus: paid` and failed
fulfillment. A credit to a Bitrefill account is not evidence that money reached
the user's wallet. This experiment does not implement automatic user refunds.

The journal requires a local POSIX filesystem supporting atomic replacement
and directory synchronization. Directories are mode `700`, records mode `600`,
and only a hash of the recovery token is stored server-side. Phone numbers,
request details, invoice references, and invoices are private journal data.
Keep the directory across restarts and protect it like other application data.
A process lock prevents two server processes from sharing it; this is not a
multi-instance database. Corrupt records fail closed rather than becoming
missing orders. The experiment does not automatically purge records: deleting
the journal loses recovery and duplicate-prevention history.

Status reads are throttled to once per order per ten seconds. Bitrefill's
invoice-read quota also spans other orders, so this is intended for a small
local experiment, not concurrent public traffic. Supplier rate limits or
network failures produce unavailable/unknown state without retrying creation.
There is no public-server abuse control or authenticated webhook receiver.

## Supplier reference and verification

Bitrefill documents Personal API access for personal or small experiments;
wallet/platform distribution uses its Business integration process. Personal
API accounts do not have the Business test products. See the official
[API overview](https://docs.bitrefill.com/docs/api-overview),
[phone top-ups](https://docs.bitrefill.com/docs/phone-topups),
[crypto payments](https://docs.bitrefill.com/docs/crypto-payments),
[invoice and order events](https://docs.bitrefill.com/docs/webhooks),
[refunds](https://docs.bitrefill.com/docs/refunds), and
[rate limits](https://docs.bitrefill.com/docs/rate-limits).

The narrowly scoped backend checks use mock supplier responses and signed test
invoices; they create no live orders:

```sh
./gradlew :backend:hub:ktlintCheck :backend:hub:test
```

Tests cover exact denomination conversion, supplier package identity, explicit
unpaid Lightning preparation, payment versus delivery status, and durable
duplicate/uncertain-order behavior. Catalog access can be checked through the
GET endpoint without preparing an invoice. A successful catalog read does not
establish that a real purchase or carrier delivery works.
