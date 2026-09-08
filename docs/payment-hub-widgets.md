# Payment Hub widgets and backend contract

The Payment Hub combines locally owned payment widgets with a catalogue of
backend-defined widgets rendered by native code. Android uses Compose and iOS
uses SwiftUI/UIKit. Descriptors supply values for supported controls and action
handlers; they do not download code, define arbitrary navigation, or invoke
native APIs.

## Implemented backend and client

- `core:hub-api` contains serializable Kotlin wire types for Android, iOS, and JVM.
- `integration:hub` contains the Ktor HTTP client and bounded response parsing.
- `backend:hub` is a runnable JVM Ktor server. Without supplier credentials its catalogue is empty.
- `GET /hub/v1/widgets` discovers compatible definitions.
- `POST /hub/v1/widgets/{widgetId}/content` resolves the selected native variant.
- `PUT /hub/v1/orders/{orderId}` prepares one idempotent service order;
  `GET` on the same path resumes its payment and delivery state.

The client supports `metric/v1` and `service/v1`. A variant chooses a compiled
native template and one size: `small`, `wide`, or `large`. Different finished
compositions have different variant IDs.

The `feat/hub` experiment connects Claro El Salvador airtime and packages through
the Bitrefill Personal API. Supplier catalog values are discovered at runtime;
fixtures are never loaded into the live catalogue. See the
[Claro experiment guide](claro-service-experiment.md) for setup, boundaries, and
verification. Price and SilentLink integrations are not implemented.

## Local development

Use JDK 21 and start the server from the repository root:

```sh
./gradlew :backend:hub:run
```

The default listener is `127.0.0.1:8080`. `HUB_HOST` and `HUB_PORT` configure the
listener. No public deployment is assumed or created.

```sh
curl http://127.0.0.1:8080/hub/v1/widgets \
  -H 'X-Rayl-App: com.nicolasusca.rayl' \
  -H 'X-Rayl-Version: 1.0.0' \
  -H 'X-Rayl-Build: 1' \
  -H 'X-Rayl-Platform: ios' \
  -H 'X-Rayl-Hub-Contracts: metric/v1,service/v1' \
  -H 'Accept-Language: en'
```

Mobile builds accept `-Prayl.hub.baseUrl=https://your-configured-host` or the
`RAYL_HUB_BASE_URL` environment variable. The Gradle property takes precedence.
No value means that the remote catalogue is unavailable; local Hub features
continue to work. The URL is build configuration, not a user account setting.

The HTTP client accepts HTTPS, plus HTTP loopback development hosts `localhost`,
`127.0.0.1`, `::1`, and Android emulator host alias `10.0.2.2`. Android and Apple
transport policies still apply; use an HTTPS development endpoint unless the
relevant Debug configuration explicitly permits local HTTP. Never weaken a
Release transport policy to test this endpoint. Rayl Android Debug explicitly
allows the loopback/emulator hosts; `scripts/build-hub-ios-debug.sh` supplies a
temporary Debug-only plist for local iOS simulator verification.

## Request metadata

Every request includes the same metadata, including content refreshes:

| Header | Meaning |
| --- | --- |
| `X-Rayl-App` | Full native package/bundle identifier, such as `com.nicolasusca.rayl` |
| `X-Rayl-Version` | Native user-facing app version |
| `X-Rayl-Build` | Native build identifier |
| `X-Rayl-Platform` | `android` or `ios` |
| `X-Rayl-Hub-Contracts` | Comma-separated contracts actually supported by the client |
| `Accept-Language` | Requested locale; native local widgets retain their bundled localization |

The server rejects missing metadata with `400` and
`{"code":"client_metadata_required"}`. An empty supported-contract list is
allowed. Metadata is not authentication and contains no device identifier.
The client advertises the intersection of the supplied capabilities and its
implemented contracts: `metric/v1` and `service/v1`. Unknown capabilities are
never advertised; service order requests require service support.

## Four separate data boundaries

### Catalogue definition

The catalogue describes a widget that may be added. A definition has a stable
ID, an opaque revision, a contract, localized title/description, finished native
variants, configuration fields, and bounded actions. It does not contain a
user's saved configuration or position.

The current metric contract accepts text, phone, and single-choice fields, and
only the `refresh` action kind. Refresh uses the built-in native controls; the
current native presentation does not render descriptor action IDs or titles as
custom buttons. Omitting `actions` does not disable native refresh.

A choice stores its stable option ID, not its label. Every option ID must fit the
field's maximum length, otherwise the definition is skipped. The maximum is 256
characters when unspecified. Native editing checks required values, choice
membership, and maximum input lengths. `phone` selects a phone keyboard; it does
not imply an implemented phone-number normalization or validation standard.

The HTTP client validates transport bounds (at most 16 configuration keys, valid
key identifiers, and at most 256 characters per value). It does not retain a
catalogue or revalidate a saved configuration against the newest field schema.
Content handlers validate transport inputs, and order preparation validates the
current service revision, offer, recipient, and exact requested denomination.
The initial service contract covers phone top-ups and packages; arbitrary
supplier navigation, executable code, and other product families are not part of it.

See [the metric catalogue fixture](api/payment-hub/catalog-metric.example.json)
and [its schema](api/payment-hub/metric-catalog.schema.json). Identifiers use
letters, digits, dots, underscores, and hyphens, with a maximum length of 128.
The `local.` definition prefix is reserved for the app.

### Local widget instance

An instance has its own stable local identity, a definition ID, and selected
variant. The app-owned `HubRecord` stores contacts, reusable payment actions,
and an ordered `widgets` array in one atomic document. Array order determines
canvas arrangement; there is no separate remote layout document.

A stored widget contains `id`, `definitionId`, `kind`, `variantId`, `columns`,
`rows`, `capacity`, native `template`, and optional `title`. Contacts widgets keep ordered
`contactIds`; a Shortcut references one saved payment recipe through `actionId`;
remote metric and service instances keep field values in `configuration`. The shape
is illustrated in [the local instance fixture](api/payment-hub/local-instance.example.json).
This is local persistence, not a server API or synchronization format.

| Local definition | Variants | Contents |
| --- | --- | --- |
| `local.contacts` | Single, Row, Card | Up to 1, 4, or 6 selected contacts, each asking for an amount |
| `local.shortcut` | Single | One contact with a configured amount and optional comment |
| `local.favorites` | Row, Card | Successful payment actions ranked by use count |
| `local.recents` | Row, Card | Successful payment actions ranked by most recent use |

Local definitions share the gallery with remote definitions. Their contacts,
configuration, payment history, and arrangement do not need HTTP. Multiple
widgets can reference the same contact payment action, so placing or removing a
widget does not duplicate or erase that action's history. Shortcut recipes with
the same contact, amount, and comment reuse their payment action.

Removing a widget needs no confirmation and preserves contacts, payment actions,
and history. Deleting a contact asks for confirmation, removes its payment
actions and Shortcuts, and prunes Contacts memberships; an empty Contacts widget
is removed. The post-payment save prompt creates a contact and a single Contacts
widget together. Blink import populates only the contact book.

Service widgets do not reference contacts. Their saved phone belongs to the
widget configuration. Orders survive widget removal and are recovered through a
separate app-scoped encrypted journal. Favorites and Recents currently rank
contact payment actions; service order history is not included in this experiment.

### Refreshed content

A metric instance sends its selected variant and configuration in a JSON POST
body, keeping configuration values out of query strings:

```json
{"variantId":"usd-wide","configuration":{}}
```

The response identifies the requested widget/variant and contains a metric with
an exact decimal string, unit, label, ISO-8601 `asOf` timestamp, and optional
`refreshAfterSeconds`. Values never use JSON floating-point numbers: `"65000.00"`
with unit `"USD"` is a display value of 65,000 US dollars, not 65,000 cents. The
client preserves the decimal text without conversion or rounding. A metric is
informational content, not an authorized payment amount or exchange-rate quote.

A suggested refresh interval must be between 30 seconds and one day; it is not
permission for unrestricted background work. The current surface refreshes all
its metric instances together, defaults to five minutes, and uses the shortest
suggested interval with a five-minute upper bound. The wire value is a scheduling
hint rather than a guarantee that an individual widget is polled at that interval.

See [the metric content fixture](api/payment-hub/content-metric.example.json)
and [its schema](api/payment-hub/metric-content.schema.json). Displaying cached
content must preserve its age; a failed refresh must not make old data appear
fresh. Credentials for a future authorized balance lookup must not be placed in
ordinary widget configuration or logged by request handlers.

### Service content and purchases

`service/v1` provides `service-topup` and `service-packages` native templates.
Content contains the country/calling code, an opaque revision, and selectable
offers. Offers have opaque IDs and supplier-provided labels/descriptions. Airtime
has an exact fixed denomination or a minimum/maximum/step range. Named packages
may have no monetary denomination; their payment cost comes from the quote.
Amounts use integer minor-unit strings plus currency and fraction digits, never
floating point. `amountMsat` is an integer count of Lightning millisatoshis.

The client generates an order UUID and a cryptographically random recovery
credential, and persists both with the requested selection before sending PUT.
The bearer credential authorizes access only to that order. It is stored using
Android Keystore-backed encryption or Apple Keychain, separately from contacts,
widgets, and wallet credentials, and scoped to the backend endpoint.

The backend validates the current selection before submission, pins the chosen
supplier, and writes a durable preparation marker before creating an unpaid
Lightning invoice. Identical requests reuse the same order; changed requests
cannot reuse its ID. An uncertain upstream creation never automatically creates
another invoice. A lost request can resume using GET and, only when the server
confirms that no order exists, repeat the identical PUT.

The app validates the signed invoice, exact amount, and expiry, then presents the
recipient, selected product, and Lightning price. Explicit Pay rechecks the order;
a changed quote requires another review. Native presentation closes the service
sheet before handing the invoice to the selected wallet's existing admission and
confirmation flow. Provider execution and retry policy remain provider-owned.

Payment and fulfillment are separate fields. The Hub checks the original order
until the supplier reports delivery or an unresolved/terminal outcome. Closing
the screen or removing the widget never cancels a purchase. No user account is
required for order recovery. The experiment retains the latest order locally;
backend records remain durable. Production order history, supplier failover,
refund operations, and multi-instance coordination need further design.

See the [experiment guide](claro-service-experiment.md) and
[wire examples](api/payment-hub/) for the implemented exchange and its limits.

## Versions and unavailable content

`protocolVersion` versions the wire envelope. `metric/v1` and `service/v1` version
compiled native behavior contracts. The definition's `revision` identifies catalogue
changes. A service identity or offer identifier is
opaque to the client; it is not a route or a Kotlin class name. No catalogue
caching revision or ETag is required by this initial server.

The client parses definitions independently. Unsupported contracts/templates,
invalid entries, duplicate IDs, and reserved local IDs are skipped without
discarding other valid entries. The available result reports the skipped count.
Malformed envelopes and unsupported protocol versions return unavailable states.
Responses are bounded to 256 KiB and 128 catalogue entries, with bounded fields,
variants, choices, and text lengths. Requests have a 15-second deadline and
coroutine cancellation remains cancellation. The caller owns the HTTP client;
transport retry policy may retry read-only content requests within that deadline.
HTTP error status is classified by this client even if the caller enabled Ktor's
`expectSuccess` option. Local definition IDs never produce content HTTP requests.

Absent configuration, transport failure, malformed responses, and content `404`
are distinct client results. An empty successful catalogue is a normal result.
Temporary remote unavailability must not delete a saved local widget instance.
Backend services can be added without a mobile release only when their data and
actions fit contracts already implemented and available for native app review.

## Focused verification

```sh
./gradlew :integration:hub:jvmTest :backend:hub:test
```

These focused checks exercise metadata, per-item compatibility, malformed/offline
results, exact values, catalog normalization, and order idempotency.
Fixture checks do not replace authenticated catalog verification or native device QA.
