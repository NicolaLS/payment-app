# Rayl architecture and behavior

Rayl is the suite's default Android/iOS product. It connects to exactly one Blink
or Nostr Wallet Connect wallet at a time. Blip, Lasr, and Flint remain independent
single-provider products. Spark is not a Rayl dependency.

All apps are prerelease. They do not import another app's credentials, preferences,
databases, or installation state.

## Provider ownership

| Modules | Behavior | Consumers |
| --- | --- | --- |
| `providers/blink` | Blink integration, features, localized UI, native experience | Blip and Rayl |
| `providers/nwc` | NWC integration, features, native experience | Lasr and Rayl |
| `providers/spark` | Spark application contracts, integration, features | Flint |
| `apps/*` | Identity, storage scopes, legal links, entry points, composition | Owning app |
| Root `core/*`, `feature/*`, `integration/*` | Provider-neutral values and reusable implementation | Applicable consumers |

Each provider owns payment validation, authorization, errors, retry decisions,
uncertain outcomes, and recovery. Rayl selects a concrete native experience; it
does not combine providers into one payment state machine or universal wallet API.
Apps do not depend on one another, providers do not depend on other providers or
apps, and neutral modules do not depend on either. `verifyModuleDependencies`
enforces these boundaries.

## Onboarding and navigation

First use is welcome → wallet choice → provider setup → Scan. A connection is
required before showing the main tabs. A connected app opens directly into its
provider experience:

- Blink exposes Scan, Hub, and Settings, with session payments opened from Scan.
  It retains funding-wallet selection, contact import, and fee presentation.
- NWC exposes Scan, Recent, Hub, and Settings, with its own discovery, connection
  details, and reconciliation behavior.

App education and preference setup happen once per installation. Removing a
wallet returns Rayl to wallet choice; subsequent setup uses the selected
provider's add/confirm controls without repeating general onboarding. Cancelling
setup returns to wallet choice. Blip and Lasr return directly to their own
provider setup. Saved payment defaults remain visible in Settings.

Payment Hub shortcuts, groups, and layout are local app features. Its service
catalogue is a placeholder; the app does not implement service purchasing or
fulfillment.

## Connection removal and input isolation

Remove the current connection before changing wallets. Removal is blocked during
active payment submission. Pending or unknown outcomes require explicit
acknowledgment that local payment records will be erased and the original wallet
must be checked before retrying. Removal does not cancel or reverse a payment.

Keep imported contacts, onboarding completion, general preferences, and local Hub
shortcuts/groups/layout. Imported Blink contacts remain available across
connection changes. Erase credentials, connection-specific settings, payment
session records, and queued input. Close provider resources and invalidate
outstanding work before mounting another connection.

Interrupted cleanup resumes after restart. Incomplete cleanup presents a blocking
retry screen; a new connection/payment flow cannot start until cleanup finishes.
Even another connection to the same provider cannot restore the previous wallet's
payment attempts. Blink and NWC reuse their guards in Blip and Lasr; Spark retains
its own removal semantics.

Connection links may preselect NWC but cannot silently replace an existing wallet.
Payment input routes only to the connected experience. An unconfigured user must
complete setup and reopen the request. Never replay queued input across connection
replacement.

## Native UI and storage

Shared Kotlin holds state and presentation snapshots. Android renders Compose
from `androidMain`; iOS renders SwiftUI/UIKit from `iosMain/swift`. Each consuming
Xcode target registers shared renderer directories once, with explicit resource
catalog references. See [the native app shell](native-shell.md).

App roots assemble dependencies and navigation. Provider experiences consume
shared presentation directly; provider decisions stay with their provider.
Do not add forwarding wrappers, a shared payment coordinator, or provider flags
to disguise different behavior.

Keep app-specific storage and separate named provider connection stores. Blink
and NWC both use `payments.pendingAttempts.v1`, so they must never receive the
same connection-settings instance. On iOS, general preferences use standard
app-specific UserDefaults; erasable provider data uses the explicit named
connection-settings factory.

## Identity and resources

- Android application ID and iOS bundle ID: `com.nicolasusca.rayl`.
- Kotlin/Android namespace: `xyz.lilsus.rayl`; Gradle prefix: `:rayl`.
- Android variants add `.dev` and `.e2e`; iOS E2E adds `.e2e`.
- Maintain English, German, and Spanish resources together.
- iOS/iPadOS deployment target is at least 18.5 for normal and E2E targets.
