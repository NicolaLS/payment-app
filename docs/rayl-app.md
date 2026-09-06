# Introduce the unified Rayl app

Owner-approved implementation handoff · 5 September 2026 · MOB-39

## Product decision

Rayl is a fourth, independent Android/iOS product and the suite’s default
recommendation. Blip (Blink), Lasr (NWC), and Flint (Spark) remain available as
purpose-built apps. All are prerelease: no migration, backward compatibility,
credential transfer, cross-app synchronization, or old-storage readers belong
in this work.

Rayl v1 supports Blink and Nostr Wallet Connect. It stores exactly one wallet
connection. Supporting multiple saved connections is a possible future feature,
not a reason to add account registries or universal wallet interfaces now.
Spark ownership is restructured now, but its SDK and features are excluded from
Rayl’s dependencies. Cashu, Ark, and other integrations are out of scope.

## Existing features and onboarding

Preserve each supported provider’s existing payment features, validation,
authorization, errors, retry decisions, uncertain outcomes, and recovery.
Blink retains funding-wallet selection, contact import, fee presentation, Scan,
Hub, and Settings, with session payments opened from Scan. NWC retains its
connection details, discovery checks, reconciliation, and Scan, Recent, Hub,
Settings navigation. Do not combine their state machines to make their modules
look alike.

First use is Rayl welcome → wallet choice → provider-specific setup → Scan.
Require a connection before showing the main tabs. Once connected, open directly
into that experience. Other providers should not appear in ordinary payment
use. Onboarding copy receives the product name; native platform renderers remain
shared within each provider instead of being copied into Rayl.

App education and preference setup happen once per installation. After removing
a wallet, Rayl returns to wallet choice, then opens only the selected provider's
add/confirm wallet screens. Blip and Lasr open their own add-wallet screen directly.
These connection flows reuse the native setup controls without onboarding progress,
introductory steps, preference setup, or mandatory contact import. Cancelling in
Rayl returns to wallet choice. Saved payment defaults remain visible in Settings.

The existing Payment Hub provides local shortcuts, groups, layout, and payment
interactions. Preserve it. Its service catalogue is a placeholder. Backend APIs,
service purchases, supplier invoices, fulfilment, order history, and purchase
handoff contracts are not part of introducing Rayl.

## Removal, replacement, and continuity

Removing the current connection is the first step to changing wallets. Block
removal during active payment submission. Pending or unknown outcomes do not
block removal forever: require explicit acknowledgment that local payment
records will be erased and the user must check the original wallet before
trying again. Removal does not cancel or reverse a submitted payment.

Keep imported contacts, app onboarding completion, all general preferences
(including auto-pay, theme, currency, and language), and local Hub shortcuts,
groups, and layout. Imported contacts remain available to the Blink experience
across connection changes. Erase wallet credentials, connection-specific settings,
payment-session records, and queued inputs. Close provider resources and invalidate outstanding
work before another connection can be mounted. Interrupted cleanup must finish
after restart. A new connection must never restore the previous wallet’s
payment attempts, even when the provider is the same.

Incomplete cleanup presents a blocking retry screen. No new connection or payment
flow may start until erasure has completed and a fresh experience can be created.

Apply the active-submission guard and explicit warning to the reused Blink and
NWC experiences in Blip and Lasr as well. Preserve Spark’s own removal semantics.

Cancelling setup before connection returns to wallet choice. Connection links
may preselect NWC but cannot silently replace a configured wallet. Payment
requests route only to the connected experience; an unconfigured user must
finish setup and reopen the payment request. Do not replay inputs across wallet
replacement.

## Ownership and native boundaries

- `providers/blink`: Blink features, integration, UI, and reusable experience;
  consumed directly by Blip and Rayl.
- `providers/nwc`: NWC features, integration, and reusable experience; consumed
  directly by Lasr and Rayl.
- `providers/spark`: Spark application contracts, integration, and features;
  consumed by Flint only in v1.
- `apps/*`: product identity, storage names, legal links, entry points, and
  top-level navigation/dependency composition.
- Root `core/*`, `feature/*`, `integration/*`: provider-neutral values,
  utilities, presentation, and features.

Preserve useful module subdivisions. A complete wallet experience is an
ownership boundary, not a requirement to create one monolithic feature module.
Existing Kotlin namespaces may remain after moves. Do not leave forwarding
wrappers or type aliases to preserve old module APIs.

Apps must not depend on one another. Providers must not depend on apps or other
providers. Neutral modules must not depend on providers. Enforce each product’s
allowed providers in `verifyModuleDependencies`.

There is no universal wallet API, shared payment coordinator, provider error
union, capability registry, or generic recovery policy. The shell chooses a
concrete experience; it does not execute its payment state machine. Preserve
existing shared render projections and optional presentation sections.

Kotlin common code holds state and snapshots. Android uses Compose in
`androidMain`. iOS uses SwiftUI/UIKit in `iosMain/swift`, registered once per
consuming Xcode target, with explicit resource catalog references. No Compose
renderer on iOS and no duplicated shared Swift files.

Use app-specific storage and distinct provider connection stores. Both Blink
and NWC currently use `payments.pendingAttempts.v1`; never give them the same
connection settings instance. Named connection stores must honor their storage name.
On iOS, keep general app preferences in standard UserDefaults, their original
app-specific store. Use an explicit named connection-settings factory only for
erasable provider data. No relocation of existing app preferences, migration,
fallback reader, or cross-app sharing is required.

## Identity, delivery, and verification

Rayl’s distribution identity is `com.nicolasusca.rayl` on both platforms.
Its Kotlin/Android namespace is `xyz.lilsus.rayl`; the Gradle prefix is `:rayl`.
Android adds `.dev` and `.e2e`, iOS E2E adds `.e2e`. Keep existing app identifiers
and the `rayl-suite` root name. Maintain English, German, Spanish and iOS/iPadOS
18.5 minimum deployment targets.

Move provider ownership first, add native Rayl composition and selection next,
then register native resources, app identity, CI, and distribution tooling.
Public publishing, release builds, release tags, signing-certificate changes,
and changes to the approved NWC dependency are separate owner-authorized work.

Use affected-module ktlint, dependency-boundary verification, localization
verification, and relevant Debug builds. Add only narrowly targeted unit tests
for selection, removal guards, and isolation. Do not run broad suites, device
tests, E2E flows, or configuration matrices.

Owner QA covers both complete setup/payment journeys, same-provider replacement,
active and uncertain payments, interrupted cleanup, link routing, retained Hub
preferences, and independent operation of all four apps. Success is one Rayl
installation with two purpose-built experiences and independently understandable
provider implementations—not a universal payment engine.
