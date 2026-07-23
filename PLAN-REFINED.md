# RAYL suite plan

Status: high-level product and repository direction

Audience: leads and implementers working at any stage of the extraction

Source plan: [PLAN.md](PLAN.md)

Supporting research: [RAYL-SDK-RESEARCH.md](RAYL-SDK-RESEARCH.md)

Last refined: 2026-07-23

## 1. Purpose of this document

This document defines the durable direction for turning the current `papp`
application into the RAYL suite of focused payment apps.

It is intentionally not:

- a greenfield architecture specification;
- a file-by-file migration checklist;
- a detailed implementation sequence;
- a complete feature specification for every provider;
- a commitment to preserve the current application's internal architecture.

A separate implementation plan will define the concrete extraction and rewrite
work. The repository-specific
[ARCHITECTURE_REWRITE_REVIEW.md](ARCHITECTURE_REWRITE_REVIEW.md) must inform
that work, particularly the greenfield Lasr and Blip implementations, but its
detailed recommendations are not duplicated here.

This plan should remain useful before, during, and after the migration. Its job
is to answer:

- What is RAYL?
- Which apps belong to it?
- What is shared, and why?
- What must remain app-specific?
- How is the repository organized?
- What is the first milestone?
- Which decisions are settled, and which remain explicitly deferred?

## 2. Product direction

### 2.1 RAYL is the suite

RAYL is:

- the user-facing suite brand;
- the repository umbrella;
- the shared design and engineering context for the apps.

RAYL is not an installable application. There is no RAYL launcher or routing
app.

The planned applications are:

| App | Provider or protocol | Initial status |
| --- | --- | --- |
| Lasr | Nostr Wallet Connect (NWC) | First extraction |
| Blip | Blink API key | First extraction |
| Flint | Spark via Breez SDK | First implementation after Lasr and Blip |
| Quark | Ark using the Second/Bark ecosystem | Stub; product details deferred |
| Nutrino | Cashu | Stub; product details deferred |
| Femto | Fedimint | Stub; product details deferred |

There is no card application and no card support in the RAYL plan.

`Nutrino` is the intentional product spelling and includes the desired reference
to Cashu NUTs.

### 2.2 One product idea, several focused apps

The product is a fast checkout/payment tool. The wallet provider is an
implementation detail from the user's perspective.

The apps should therefore have:

- nearly identical primary user journeys;
- nearly identical interaction patterns;
- a coherent RAYL visual identity;
- the same standard of speed, clarity, and payment feedback;
- separate provider setup and provider-specific behavior.

The apps may diverge where the provider genuinely requires it. They must not
carry provider switches, generic provider orchestration, or artificial
abstractions merely to keep their implementations textually similar.

Initially, launcher names are simply `Lasr`, `Blip`, `Flint`, and so on. Store
titles are expected to use forms such as `Lasr by RAYL`.

Different app color themes may be introduced later. The initial priority is a
coherent, almost identical look and feel.

### 2.3 Focused means single-provider

Each app implements one provider or protocol approach:

- Lasr knows NWC.
- Blip knows Blink.
- Flint knows the selected Spark integration.
- Quark knows the selected Ark implementation.
- Nutrino knows Cashu.
- Femto knows Fedimint.

An app must not acquire a second provider merely because another RAYL app has
similar UI or business operations.

The extraction exists specifically to eliminate the current need to coordinate
NWC and Blink through shared provider-switching logic. Recreating that pattern
at suite level would defeat the purpose of the split.

### 2.4 Payment tools, not general-purpose wallets

Quark, Nutrino, and Femto are intended to import or connect an existing wallet
or existing provider value and then act as focused payment tools. Their primary
purpose is paying Lightning or another instant-settlement request.

They are not planned as full-featured Ark, Cashu, or Fedimint wallets. Exact
import formats, retained balances/change, supported request types, recovery
surfaces, and provider selection remain deferred.

The narrow UI scope does not permit unsafe handling of funds. Each app must
still persist and reconcile any provider state, change, or pending payment
created by the operations it supports.

## 3. Repository reality

The original high-level plan was written without access to this codebase. The
following facts materially change its recommendations.

### 3.1 Current topology

The current Gradle root is the `app` directory inside the Git repository. It
contains:

- `:shared`, the Compose Multiplatform/KMP application module;
- `:androidApp`, the Android shell;
- `iosApp`, the iOS/Xcode shell.

Almost the entire application—including UI, navigation, domain logic, data
access, NWC, Blink, dependency injection, and platform abstractions—currently
lives under:

```text
shared/src/commonMain/kotlin/xyz/lilsus/papp
```

This is not already a multi-app repository and it is not a collection of
provider-neutral libraries.

### 3.2 Current provider coupling

The current application supports both NWC and Blink in one binary. Examples of
the coupling include:

- a shared `WalletType` choice between NWC and Blink;
- payment behavior selected through the current wallet connection;
- combined onboarding and wallet-selection UI;
- one large application state holder coordinating both providers;
- navigation and settings surfaces containing provider-specific branches;
- Compose resources containing mixed NWC and Blink language;
- one manifest/Info.plist claiming NWC and generic payment URI schemes.

This coupling is a source of complexity, not an architecture that the new apps
must preserve.

### 3.3 Current identity

The legacy application uses:

```text
Android application ID: xyz.lilsus.papp
Apple bundle ID:        xyz.lilsus.papp
Kotlin package root:    xyz.lilsus.papp
Displayed product:      Lasr
```

The `papp` identity is useful because it leaves `xyz.lilsus.lasr` available for
the new NWC-only Lasr.

### 3.4 Current quality assets are references, not migration contracts

The repository contains shared unit tests, large ViewModel tests, Maestro flows,
and an NWC-oriented local E2E harness. It also contains CI configuration that
still references removed or renamed module paths.

The new apps will not port or use the legacy tests and E2E suite as migration
inputs. Greenfield implementations will write their own lightweight unit and
integration tests around their actual architecture and provider.

The copied UI—not the old test structure—is the primary protection against
accidentally losing established UX.

## 4. Corrections to the original plan

The original plan had useful strategic instincts, particularly separate
binaries, a monorepo, inward dependency direction, and caution around
over-sharing. Several of its confident choices must be corrected.

### 4.1 The first split is already decided

The original plan treated the NWC/Blink split as a later question. It is now the
first fundamental product milestone:

- new Lasr is NWC-only;
- new Blip is Blink-only.

### 4.2 This is not a legacy-preservation exercise

Lasr and Blip are greenfield provider implementations using the existing UI and
product knowledge.

They should not preserve:

- the combined NWC/Blink orchestration;
- the current provider-switching state model;
- awkward business rules caused by supporting two providers;
- large state holders merely because they already exist;
- legacy dependency boundaries that conflict with the architecture review.

The architecture review must be considered while building each app. The
high-level plan does not prescribe the replacement architecture.

### 4.3 Shared business logic cannot be designed in advance

The original plan was still too confident about provider-neutral orchestration
and shared domain modules.

RAYL adopts a stricter rule:

> Implement behavior in each app first. Extract it into `foundation` only after
> multiple implementations demonstrate that the logic and semantics are truly
> identical.

Similarity is insufficient. A shared abstraction must reduce total complexity
without introducing provider switches, leaky error models, optional behavior,
or configuration matrices.

### 4.4 SDK maturity is provider-specific

The apps cannot be planned as if every provider already offers an equally mature
KMP SDK.

The point-in-time findings are maintained in `RAYL-SDK-RESEARCH.md`:

- Flint has a real Breez Spark KMP integration.
- Quark has credible official Bark Android and Swift bindings.
- Nutrino has official CDK bindings, but its exact mobile integration remains
  to be proven.
- Femto has an official Fedimint FFI direction, but may require RAYL-owned
  native packaging.

The repository topology supports these differences; `foundation` must not hide
them.

### 4.5 Platform routing differs

Android can present a chooser when several apps handle the same payment URI.
iOS custom-scheme selection is undefined when several installed applications
register the same scheme.

The suite cannot promise identical external-link routing behavior on both
platforms.

### 4.6 Release independence does not require separate repositories

One Gradle root does not imply one version or one release train. The apps can
have independent store versions, signing, CI jobs, and release schedules while
remaining in one repository.

## 5. Target repository topology

### 5.1 Repository and build root

The existing repository will be renamed to `rayl`. The Gradle root moves to the
Git repository root and uses:

```kotlin
rootProject.name = "rayl"
```

RAYL targets:

- one Git repository;
- one Gradle root;
- one shared version catalog where useful;
- one top-level Xcode workspace;
- separate app modules and native projects.

A provider may receive an exceptional included build or prebuilt adapter only
if a real integration spike proves it necessary. Separate repositories or
independent Gradle roots are not the default escape mechanism.

### 5.2 Directory shape

Each application should look like an ordinary KMP shared-UI application:

```text
rayl/
├── apps/
│   ├── lasr/
│   │   ├── shared/
│   │   ├── androidApp/
│   │   └── iosApp/
│   ├── blip/
│   │   ├── shared/
│   │   ├── androidApp/
│   │   └── iosApp/
│   ├── flint/
│   │   ├── shared/
│   │   ├── androidApp/
│   │   └── iosApp/
│   ├── quark/
│   │   ├── shared/
│   │   ├── androidApp/
│   │   └── iosApp/
│   ├── nutrino/
│   │   ├── shared/
│   │   ├── androidApp/
│   │   └── iosApp/
│   └── femto/
│       ├── shared/
│       ├── androidApp/
│       └── iosApp/
├── foundation/
│   ├── ui/
│   └── bolt11/
├── rayl.xcworkspace
├── settings.gradle.kts
├── build.gradle.kts
└── gradle/
```

The exact internal source sets and app-specific modules belong to the later
implementation plan.

Do not create paths such as `shared/ui-foundation`. `shared` is already the
conventional KMP application module inside each app, so using it as the suite
foundation name would be ambiguous.

### 5.3 Xcode organization

`rayl.xcworkspace` contains one independent `.xcodeproj` per app.

This provides one workspace for developer convenience while keeping the
following app-local:

- bundle IDs;
- signing and entitlements;
- schemes and archives;
- Swift Package Manager dependencies;
- KMP framework linking;
- provider-specific native frameworks;
- deployment and release configuration.

Common `.xcconfig` files, scripts, and templates may centralize demonstrated
common configuration. A single six-target mega-project is not the target.

### 5.4 Convention plugins

Do not begin the suite by designing convention plugins for hypothetical common
configuration.

First:

1. fully separate Lasr and Blip;
2. observe the resulting app builds;
3. identify configuration that is genuinely identical;
4. introduce convention plugins only where they simplify those real builds.

## 6. Identity and naming

### 6.1 Public application identities

Use:

| App | Android application ID / Apple bundle ID |
| --- | --- |
| Lasr | `xyz.lilsus.lasr` |
| Blip | `xyz.lilsus.blip` |
| Flint | `xyz.lilsus.flint` |
| Quark | `xyz.lilsus.quark` |
| Nutrino | `xyz.lilsus.nutrino` |
| Femto | `xyz.lilsus.femto` |

Debug and test suffixes are implementation details to define later.

### 6.2 Source namespaces

Application IDs and Kotlin namespaces are independent. Kotlin code should make
suite ownership explicit:

```text
xyz.lilsus.rayl.lasr
xyz.lilsus.rayl.blip
xyz.lilsus.rayl.flint
xyz.lilsus.rayl.quark
xyz.lilsus.rayl.nutrino
xyz.lilsus.rayl.femto
xyz.lilsus.rayl.foundation.ui
xyz.lilsus.rayl.foundation.bolt11
```

## 7. Foundation policy

### 7.1 Dependency direction

Dependencies point inward:

```text
app shell
    ↓
app-owned KMP implementation
    ↓
small provider-neutral foundation modules
```

Foundation modules must never depend on:

- an app module;
- a provider SDK;
- provider credentials;
- provider-specific storage;
- provider-specific error or state types;
- app navigation;
- provider selection.

No foundation API should accept a `WalletType`, provider enum, or collection of
optional provider capabilities.

### 7.2 `foundation/ui`

`foundation/ui` is the shared Compose UI foundation.

It may contain:

- themes, typography, colors, shapes, spacing, and motion;
- the hero animation and shared checkout visuals;
- reusable controls and screen sections;
- provider-neutral complete screens when their behavior is genuinely shared;
- UI used by any meaningful subset of RAYL apps, not necessarily all six;
- provider-neutral resources and accessibility behavior.

It must not permanently contain:

- NWC or Blink connection UI;
- provider onboarding;
- provider credentials;
- provider-specific imports;
- provider SDK models;
- provider-specific navigation or business state;
- hidden branches that reconstruct the current combined app.

Simple differences such as app name, copy, icons, or theme colors should be
parameters or theme values. They do not justify duplicating an entire screen.

Behavioral differences are different. When sharing a screen would require
provider conditionals or distorted state, duplicate the app-owned screen and
compose it from smaller shared UI pieces.

### 7.3 Temporary UI migration staging

Avoiding UI/UX loss is more important than producing the final module boundary
in one move.

During the initial mechanical extraction, all existing composables and their
resources may be copied into `foundation/ui` as a temporary staging area. This
allows Lasr and Blip to start from the current visual product without
selectively rewriting or accidentally omitting UI.

That staging state is not the target architecture.

The Lasr/Blip extraction milestone is not complete until:

- UI imported by only one app has moved into that app;
- provider-aware UI has moved into the owning app;
- foundation UI no longer knows NWC or Blink;
- the remaining shared UI has been reviewed against the architecture review;
- duplicated UI is retained where it produces simpler app behavior.

As more apps are implemented, shared UI may be promoted to or removed from the
foundation based on actual use. A component may remain in the foundation when
two of six apps share it; universal use is not required.

### 7.4 `foundation/bolt11`

Create `foundation/bolt11` as an internal RAYL placeholder when the new topology
is established.

It is intended to become a complete, reusable BOLT11 library implemented by a
separate effort. Its exact functional scope is intentionally outside this plan.

The current legacy invoice parsing code is not automatically the foundation
library and does not constrain its implementation.

### 7.5 Promotion rule

Code should move into `foundation` only when:

1. at least two real consumers exist;
2. semantics are genuinely identical;
3. the API is provider-neutral without hiding provider behavior;
4. sharing reduces total implementation and testing complexity;
5. either consumer could reasonably use the module without knowing about the
   other app.

Do not be afraid of duplication. Premature shared business logic is a larger
risk to RAYL than two small, clear implementations.

## 8. App ownership

Each app owns its:

- provider SDK and transport integration;
- credentials and secure storage;
- onboarding and connection/import flow;
- provider-specific data and repositories;
- payment orchestration and state;
- error interpretation;
- provider-specific navigation;
- app composition and dependency injection;
- platform integration;
- release configuration;
- tests.

Contacts, shortcuts, amount input, payment confirmation, and similar journeys
may look identical across apps. Their business implementations should initially
remain app-owned. Extract them only after independent implementations prove
identical semantics.

Koin, Compose Multiplatform, and Compose navigation are reasonable choices, but
each app is still a greenfield implementation and must justify its own
architecture. This plan does not force every internal library choice to match.

## 9. Provider and SDK direction

The detailed, time-sensitive evaluation lives in `RAYL-SDK-RESEARCH.md`.

### 9.1 Lasr

- NWC-only.
- Continues using the selected `nwc-kmp` dependency, including the currently
  accepted snapshot dependency decision.
- Only Lasr knows NWC connection URLs and NWC-specific capabilities.
- The current combined app's NWC logic is reference material, not code that
  must be retained.

### 9.2 Blip

- Blink API-key-only.
- Owns Blink API access, generated API code if retained, credentials, imports,
  and provider behavior.
- Does not preserve the legacy shared NWC/Blink orchestration.

### 9.3 Flint

- Third implementation after the Lasr/Blip extraction milestone.
- Uses Breez SDK - Spark KMP, subject to an app-specific integration spike.
- Owns the matching Gradle plugin and iOS Swift package requirements.

### 9.4 Quark

- Targets the Second/Bark Ark ecosystem.
- Uses the official Bark Rust engine/mobile bindings unless later research
  invalidates that choice.
- Ark server selection, import shape, and complete product flow remain deferred.
- Quark remains a stub until its implementation is intentionally scheduled,
  probably after Flint.

### 9.5 Nutrino

- Cashu payment tool.
- CDK is the leading protocol-engine choice from current research.
- Import, change handling, supported requests, and exact binding architecture
  remain deferred.
- Its networking/privacy design may differ fundamentally from Lasr, Blip, and
  Flint.

### 9.6 Femto

- Fedimint payment tool.
- The official Fedimint client/FFI is the leading basis.
- Federation import/join, ecash import, supported requests, and native packaging
  remain deferred.
- Its networking/privacy design may differ fundamentally from the rest of the
  suite.

### 9.7 Rust and FFI

Rust-backed SDKs are acceptable and expected for some apps.

The default integration shape is:

```text
commonMain: small app-owned provider contract
androidMain: adapter over Android/Kotlin bindings
iOS shell: adapter over Swift bindings
```

Do not force an SDK into common Kotlin merely for visual consistency in the
source tree. A KMP UniFFI generator such as Gobley may be evaluated per app if
it demonstrably produces a simpler total system.

## 10. Initial app skeletons

Flint, Quark, Nutrino, and Femto initially exist as buildable Android and iOS
skeletons.

Each skeleton includes:

- final app identity and basic RAYL branding;
- its normal `shared`, `androidApp`, and `iosApp` structure;
- the main payment screen;
- the shared hero animation and relevant foundation UI;
- navigation sufficient to launch that screen;
- stubbed or no-op business behavior;
- an app README recording its provider intent and deferred decisions.

The skeletons do not include:

- working camera scanning;
- shortcuts;
- contacts;
- provider connection/import;
- payment execution;
- speculative provider SDK dependencies;
- fake provider implementations pretending to prove the future architecture.

The Nutrino and Femto READMEs must prominently state:

- privacy has a higher priority for these provider integrations;
- their network stack may be completely different from other RAYL apps;
- Tor/proxy requirements, DNS behavior, logging, correlation, and analytics are
  deferred decisions that must be resolved before implementation.

The Quark README must record that Second/Bark is selected while server and
import details remain deferred.

## 11. First milestone

The first milestone is:

> Separate the current combined `papp` product into an NWC-only Lasr and a
> Blink-only Blip, while establishing buildable skeletons for Flint, Quark,
> Nutrino, and Femto.

The milestone does not require Lasr or Blip to be production-releasable. Later
iterations will refactor, harden, and polish them.

Lasr and Blip do not need to be implemented simultaneously. One may be
completed before the other when that produces a simpler workflow.

### 11.1 Milestone outcomes

At milestone completion:

- the legacy application is frozen;
- the repository root represents RAYL;
- all six apps have distinct identities and ordinary KMP app structures;
- Lasr contains only NWC behavior;
- Blip contains only Blink behavior;
- both use greenfield provider implementations informed by the architecture
  review;
- the established UI/UX has not been lost;
- temporary UI staging has been cleaned into a provider-neutral
  `foundation/ui`;
- the four other apps build and launch their no-op payment-screen skeletons;
- `foundation/bolt11` exists as an internal placeholder;
- app-specific tests cover the new implementations at an appropriate lightweight
  unit/integration level;
- no legacy data or credential migration exists.

Production readiness, store release, complete E2E automation, analytics, and
every technical-debt improvement are not milestone exit requirements.

## 12. High-level evolution sequence

This sequence provides orientation, not a substitute for the later concrete
implementation plan.

### Stage A: freeze the legacy product

Before transforming `main`:

- allow only bounded pre-freeze improvements that are demonstrably reusable by
  the new suite or capture provider-independent learning;
- do not spend this period improving complexity that exists only because the
  legacy binary combines NWC and Blink;
- tag the final legacy state as `papp-final`;
- create `papp-legacy` from that state;
- stop maintaining the legacy branch after the freeze;
- rename the repository to `rayl`;
- transform `main` into the RAYL suite.

The old store listing will be unpublished quickly. It is retained in Git only
as a reference.

### Stage B: establish the suite shape

- move the Gradle root to the Git root;
- create the six ordinary KMP app shapes;
- create the Xcode workspace with one project per app;
- assign final application IDs and source namespaces;
- create `foundation/ui` and the `foundation/bolt11` placeholder;
- make all skeletons build and launch.

### Stage C: preserve and classify the UI

- copy the existing composables/resources so no UI or UX is silently lost;
- use that copy from the emerging Lasr and Blip apps;
- identify provider-aware and single-app UI;
- move app-owned UI out of the foundation;
- improve the remaining UI organization with the architecture review in mind.

### Stage D: implement Lasr and Blip independently

- build NWC-only Lasr as a greenfield implementation;
- build Blink-only Blip as a greenfield implementation;
- avoid rebuilding the combined provider abstraction;
- add new lightweight tests around each app's chosen architecture.

### Stage E: consolidate only demonstrated commonality

- compare the completed implementations;
- retain intentional duplication;
- promote only exact, useful shared behavior;
- introduce convention plugins only after observing real repeated build
  configuration.

### Stage F: prepare for Flint

When Lasr and Blip are approaching production quality:

- introduce the separately planned analytics/diagnostics work;
- perform the Breez SDK integration spike;
- implement Flint as the next provider app;
- revisit shared UI based on three real apps rather than two.

### Stage G: implement later providers deliberately

Quark, Nutrino, and Femto remain stubs until their product and integration
decisions are opened intentionally.

Before implementation, update the SDK research and resolve each app's README
decision register.

## 13. Legacy deprecation and user migration

The old `xyz.lilsus.papp` application is frozen and deprecated.

It will receive:

- no further releases;
- no maintenance after the freeze;
- no data-migration work;
- no credential handoff;
- no in-app migration assistant;
- no requirement to remain compatible with the new apps.

The small existing user group will be contacted directly:

- NWC users install new Lasr.
- Blink users install Blip.
- Users connect again as if installing a new product.

The legacy and new apps may coexist because their application IDs differ. If a
platform or local device creates an unforeseen conflict, the legacy app may be
removed.

## 14. External URI handling

### 14.1 Provider-specific schemes

Only Lasr handles NWC connection links such as `nostr+walletconnect:`.

No other RAYL app should know, parse, advertise, or register NWC links.

### 14.2 Standard payment schemes

Apps capable of paying the relevant request may register standard schemes such
as:

- `lightning:`;
- `bitcoin:`;
- `lnurl:`.

On Android, multiple capable apps may register these schemes and participate in
the system's normal chooser/default behavior.

On iOS, multiple installed wallets registering the same custom scheme produce
undefined target selection. RAYL accepts this platform limitation:

- capable apps may register the standard schemes;
- direct link routing is best-effort on iOS;
- scan and paste remain deterministic entry paths;
- the suite does not claim that users can select an iOS default for these
  custom schemes.

Do not invent `rayl:`, `lasr:`, `blip:`, or similar proprietary payment URI
schemes. They do not improve interoperability with standard Bitcoin payment
requests.

Universal links, a RAYL router, and iOS share extensions are not part of the
initial plan.

## 15. Platform baseline

### 15.1 Android

Suite baseline:

```text
minSdk: 29
Production device ABI: arm64-v8a
Development/emulator ABI: x86_64
32-bit ARM: unsupported
```

The legacy app's ARM64-only filter was a local native-library workaround, not a
KMP requirement. New apps must determine their final ABI packaging from their
actual provider dependencies and verify all native libraries for Android
16 KB page-size compatibility.

Apps do not need identical ABI packaging if their provider SDKs differ, but
ARM64 production and x86_64 development support are the suite default.

### 15.2 iOS

Use one suite-wide iOS 17.0 deployment baseline unless a provider SDK requires
higher.

The legacy iOS 18.2 minimum is unnecessarily restrictive for the known
dependencies and should not be copied.

## 16. Data isolation, privacy, and analytics

### 16.1 App isolation

Initially, every app has independent:

- secure credentials;
- databases and settings;
- provider state;
- contacts;
- shortcuts;
- analytics identity, if analytics is later enabled.

Do not introduce shared keychain groups, Android shared storage, suite account
state, or automatic cross-app data synchronization in the first milestone.

Sharing contacts and shortcuts between installed RAYL apps is recorded as a
future product idea. It should be pursued only if it can be implemented
elegantly without weakening app isolation.

### 16.2 Provider data-flow records

Each implemented app must maintain a short provider-specific data-flow/privacy
record. NWC relays, Blink, Spark operators, Ark servers, Cashu mints, and
Fedimint federations/gateways expose different trust and metadata surfaces.

One suite-wide statement must not obscure those differences.

### 16.3 Networking

There is no mandatory suite-wide network client.

Provider SDKs may own their own:

- HTTP or RPC stack;
- DNS behavior;
- TLS behavior;
- connection lifecycle;
- proxy or Tor support;
- native background processing.

This is particularly important for Nutrino and Femto, where privacy requirements
may justify a different networking architecture.

### 16.4 Analytics

Analytics are not part of the initial Lasr/Blip extraction.

A separate analytics iteration is planned when Lasr and Blip are nearly
production-ready and before Flint is added. Analytics and crash diagnostics
must respect each app's provider-specific data-flow/privacy record.

## 17. Build, CI, releases, and tests

### 17.1 Independent releases

Every app owns its:

- Android version code and version name;
- Apple marketing/build version;
- store metadata;
- signing configuration;
- release task and CI job;
- provider SDK update cadence.

A security update to Flint must be releasable without changing or publishing
the other apps.

### 17.2 Path-based CI

CI should be path-aware:

- app-only changes validate that app;
- foundation changes validate the foundation and all consuming apps;
- root build changes validate the suite;
- provider SDK changes validate only the relevant app plus required shared
  checks.

Periodic full-suite coherence checks may be added later.

Current CI paths and module names must not be assumed correct; the existing
configuration still contains references to obsolete module topology.

### 17.3 Testing posture

Do not mechanically port the legacy unit, ViewModel, Maestro, or E2E suites.

Each new app should use:

- small unit tests for its own business rules;
- focused integration tests around its provider boundary;
- reproducible fakes or provider test environments where useful;
- only as much UI/E2E automation as later risk and release needs justify.

The existing NWC regtest harness remains in legacy history and is not migrated
into Lasr or foundation. Shared test fixtures may be created later only after
multiple new provider test suites demonstrate the need.

## 18. Open-source policy

RAYL remains fully open source under the MIT license.

Provider SDK licenses and native artifacts must be reviewed when each app moves
beyond its stub. License compatibility does not replace security, maintenance,
or privacy review.

## 19. Decision register

### 19.1 Settled decisions

| Decision | Status |
| --- | --- |
| RAYL is a suite and repository umbrella, not an app | Decided |
| Apps are Lasr, Blip, Flint, Quark, Nutrino, and Femto | Decided |
| Card support is excluded | Decided |
| Lasr is NWC-only and Blip is Blink-only | Decided |
| Lasr/Blip are greenfield provider implementations | Decided |
| Remaining four apps begin as payment-screen skeletons | Decided |
| Flint is the next implementation after Lasr/Blip | Decided |
| Quark targets Second/Bark | Decided |
| One repository and one Gradle root | Decided |
| Gradle root and repository are named `rayl` | Decided |
| One Xcode workspace with one project per app | Decided |
| Public IDs use `xyz.lilsus.<app>` | Decided |
| Kotlin namespaces use `xyz.lilsus.rayl...` | Decided |
| UI may be staged wholesale, then must be classified | Decided |
| Foundation UI is permanently provider-neutral | Decided |
| Duplication is preferred over leaky provider abstractions | Decided |
| Convention plugins follow real Lasr/Blip extraction | Decided |
| No legacy data/credential migration | Decided |
| Legacy freezes on tag and branch, with no later maintenance | Decided |
| Legacy tests/E2E are not migration requirements | Decided |
| Apps have independent release cadences | Decided |
| Android baseline is API 29, ARM64 plus x86_64 | Decided |
| iOS baseline is 17.0 | Decided |
| No cross-app data sharing initially | Decided |
| MIT open-source license | Decided |

### 19.2 Explicitly deferred decisions

| Decision | Where it must be resolved |
| --- | --- |
| Quark Ark server selection | Quark README and implementation plan |
| Quark import/connection shape | Quark README and implementation plan |
| Nutrino import and returned-change behavior | Nutrino README and implementation plan |
| Nutrino exact CDK binding architecture | Nutrino SDK spike/ADR |
| Femto federation/ecash import shape | Femto README and implementation plan |
| Femto native FFI packaging | Femto SDK spike/ADR |
| Exact supported request types beyond the core payment-tool idea | Per-app product plan |
| Tor/proxy requirements for Nutrino and Femto | Privacy/network review |
| Analytics provider and event policy | Separate analytics iteration |
| Future per-app color themes | Product/design iteration |
| Cross-app contact/shortcut synchronization | Future product investigation |
| Full `foundation/bolt11` API scope | Dedicated library plan |
| Implementation order after Flint | Future suite planning |

Deferred means consciously unresolved. Implementers must not silently answer
these questions through incidental code structure.

## 20. Guardrails for future decisions

When a new decision is proposed, prefer the option that:

1. keeps each app single-purpose;
2. preserves a coherent RAYL user experience;
3. minimizes provider-aware abstractions;
4. keeps provider SDKs and networking app-local;
5. allows independent app releases;
6. avoids losing established UI/UX;
7. shares only demonstrated commonality;
8. makes security and payment state explicit;
9. records unresolved privacy and trust assumptions;
10. can be reversed without splitting the suite unnecessarily.

If a proposal centralizes code but makes an individual app harder to understand,
it is probably the wrong abstraction for RAYL.
