This mono-repo contains a single gradle-root with multiple modules and
three Kotlin Multiplatform (KMP) apps for both Android and IOS. KMP shares
behavior and presentation state; the user interface is native on each platform.
Android renders with Android-owned Jetpack Compose and iOS renders with
SwiftUI/UIKit. The apps still share most UI/UX, but as one renderer per platform
rather than one Compose renderer for both. `docs/native-ui-migration.md` records
the boundary and its remaining work.

# Rayl Suite development guidelines

## Products and identity

- Blip is the Blink-only product. Its Android application ID is
  `xyz.lilsus.blip`, with `.dev` and `.e2e` suffixes. Its iOS bundle ID is
  `com.nicolasusca.blip`, with an `.e2e` suffix for the E2E target.
- Flint is the Spark-only product. Its package and bundle IDs are
  `xyz.lilsus.flint`, with `.dev` and `.e2e` Android suffixes.
- Lasr is the Nostr Wallet Connect-only product. Its package and bundle IDs are
  `xyz.lilsus.lasr`, with `.dev` and `.e2e` Android suffixes.
- The Gradle root project is `rayl-suite`.
- Reusable Kotlin namespaces start with `xyz.lilsus.raylsuite`.

Blip, Flint, and Lasr are independent, single-provider products. Do not add
provider selection or cross-provider abstractions merely to remove duplication.
Provider-specific credentials, lifecycles, errors, repositories, and payment
orchestration belong to the owning app. Move code to a root `core/*`,
`feature/*`, or `integration/*` module only when its semantics and policy are
provider-neutral and it has genuine consumers in multiple apps without
provider branches. Duplication alone is not a reason to share code.

## IMPORTANT: native UI boundary

The user interface is native first. This is an architectural invariant, not a
migration state.

- `commonMain` holds state machines, presentation snapshots, localization
  projections, and provider-neutral values. It must not declare a composable.
- Android renderers are Jetpack Compose and live in their module's
  `androidMain`.
- iOS renderers are SwiftUI/UIKit and live in their module's `src/iosMain/swift`,
  compiled directly by each app's Xcode project. A shared SwiftUI file is one
  file consumed by all three products; never copy it into an app.
- Navigation, tab bars, sheets, dialogs, alerts, lists, forms, pickers, toggles,
  keyboard handling, insets, permissions, and system pickers belong to the
  platform.

There is no Compose exception. Even the custom hero animation is native on both
platforms: Compose on Android, a SwiftUI `Canvas` on iOS. `ComposeUIViewController`
must not reappear.

A custom drawn surface is shared as *values*, not as a renderer: the phase or
state enum, the geometry constants, and the colour tokens live in `commonMain`
as plain Kotlin, and each platform draws them. On iOS, derive animated values
from the time elapsed since the state changed rather than building a second
animation state machine.

Do not reintroduce a Compose fallback, a per-tab Compose controller, an
app-facing adapter, or a transitional module to avoid finishing a native screen.
A helper that only Compose needs — `remember*`, a `Modifier` extension, a
`@Composable expect` — belongs in `androidMain`; give iOS a plain factory
instead. A Compose stability annotation such as `@Immutable` on a shared render
model is a hint rather than UI and may stay in `commonMain`.

Two SwiftUI traps cost real debugging time and are worth knowing: its `Canvas`
clips to its bounds where Compose's does not, so let the canvas fill its
container and centre the drawing inside it; and `Path.addArc`'s `clockwise` flag
is unreliable in a flipped coordinate space, so trim an inscribed ellipse
instead.

## IMPORTANT: sharing policy and accepted duplication

This policy is an architectural invariant. Consider it whenever adding a
feature, moving code, removing duplication, or reviewing a refactor. Optimize
each app for the simplest and most efficient implementation of its own product.
Local clarity, small APIs, and freedom to use the provider's native concepts
are more important than reducing line count or making the apps structurally
alike. Duplication is an accepted design choice when it avoids coordination
cost, unused concepts, or a broader abstraction.

Share the smallest implementation whose meaning is genuinely common. All
consumers must be able to use it directly, and its behavior, dependencies,
policy, and expected evolution must belong to the suite rather than one wallet.
Design tokens, stateless components, input presentation, and provider-neutral
rendering are common examples. Similar code, package-normalized equality, or a
large reduction in line count is evidence to investigate, not justification by
itself.

### Presentation projection is encouraged

Different provider behavior does not require duplicated UI. An app may project
its rich, provider-specific state into a deliberately small and lossy shared
render model. For example, Blink, Spark, and NWC failures remain distinct in
their repositories, coordinators, retry policy, and app state, while the shared
platform renderers receive `Error(message)`, render a failed hero phase, and
invoke an ordinary user-action callback.

A shared render model is appropriate when:

- It contains only information the common UI renders.
- Every required variant is meaningful for every consumer; it is not a
  superset of provider states. A concrete optional presentation section may be
  absent instead of forcing unused states into every app.
- Provider errors are converted by the app into localized display text or
  another provider-neutral visual value before crossing the boundary.
- Callbacks report UI intent such as click, dismiss, select, or retry request;
  the app still decides what that intent means and whether it is allowed.
- The mapping from app state to render state remains app-owned.

This projection is not a backend adapter. It is the normal boundary between app
logic and reusable presentation. Do not refuse to share genuinely identical UI
merely because its upstream repositories or errors differ.

### Optional provider-aware presentation is allowed

Do not duplicate a whole screen merely because one app has an additional
provider-specific setting or visual section. Shared presentation may expose a
small, explicitly named optional model, callback group, or content slot. For
example, a shared settings screen may render an optional fee-confirmation
section that only Blip supplies.

The owning app must still own whether the capability exists, its repository and
persistence, provider interpretation, resources when appropriate, and the
mapping into display state. Apps that do not have the capability pass nothing;
they must not add placeholder settings, no-op implementations, or provider
contracts. Prefer a concrete presentation name or app-supplied slot over a
provider enum, `isBlink`/`isSpark` flag, generic capability registry, or switch
inside the shared module.

This rule also applies to payment results and settings rows: share the common
layout and let an app add a genuinely app-specific rendered detail. Keep the
entire component app-owned only when the variation contains substantial
business decisions, drives provider behavior, or cannot be expressed as a
small presentation-only input.

### Keep app behavior app-owned

Do not share code when doing so requires any of the following solely to
reconcile the apps:

- An app-facing adapter, compatibility wrapper, bridge, or type-alias layer
  whose only purpose is preserving app APIs after an extraction.
- A shared coordinator, payment engine, wallet interface, or provider contract
  that app implementations must satisfy so common code can drive them.
- Generic backend error plumbing, injected error factories, or callbacks that
  choose provider policy rather than report a UI event.
- A union state model or enum containing provider statuses, failure cases, or
  lifecycle stages that are unused or impossible in another app.
- Provider flags, conditional provider branches, no-op capabilities,
  app-specific behavior overrides, or defaults that compensate for semantic
  differences.
- Additional dependencies, indirection, or public API that an app would not
  need in its own direct implementation.

Typical anti-patterns include moving a complete payment flow into a shared
module because its screens currently look alike; parameterizing provider retry
rules; using a generic error type to make unrelated failures fit one state
machine; and leaving type aliases or thin wrappers in every app after moving
their implementation. These produce nominal sharing while making each product
harder to understand and optimize.

Before extracting code:

1. List each app's concrete behavior, dependencies, lifecycle, errors, and
   policy.
2. Separate app decisions from the provider-neutral values the UI actually
   renders.
3. Extract the smallest common leaf or render model that consumers can call
   directly.
4. Keep the projection and all provider decisions in the app.
5. Recheck that removing one provider would not leave flags, impossible states,
   no-ops, or compatibility layers in the others.

Do not measure an architecture improvement by deleted lines or module symmetry.
When the direct shared boundary remains unclear, prefer app ownership and wait
for evidence from real shared evolution.

## Module boundaries

- `core:model`: provider-neutral immutable values and pure Kotlin logic; no
  Compose or generated resources.
- `core:camera`: shared camera permissions, QR scanning contracts, and platform
  implementations without provider behavior.
- `core:payment`: provider-neutral payment ports and values; no wallet SDK or
  provider lifecycle behavior.
- `core:ui`: cross-app design tokens and hero values (`RaylHeroPhase`, the ARGB
  palette) in `commonMain`; the Compose theme, hero renderer, and UI primitives
  in `androidMain`.
- `core:settings`: shared platform creation of app-scoped preference and secure
  storage.
- `core:network`: shared platform HTTP clients and transport defaults.
- `feature/*`: reusable provider-neutral user stories and their resources.
  State and snapshots live in `commonMain`, the Android Compose renderer in
  `androidMain`, and the SwiftUI renderer plus its Kotlin controller in
  `iosMain`.
- `apps/<app>/feature/*`: app-owned or provider-specific user stories.
- `apps/<app>/integration/*`: provider SDK, network, credential, database, and
  repository implementations.
- `apps/<app>/ui`, where present: reusable UI and localized error presentation
  specific to that app or provider.
- `integration/*`: external adapters reused by multiple apps without
  wallet-provider behavior.
- App `shared` modules are composition roots for navigation, dependency
  assembly, legal links, and app identity; keep business rules out of them.

No app may depend on another app's modules. Root shared modules must not depend
on app-owned modules or acquire wallet-provider behavior. Shared UI primitives
belong in `core:ui`; app- and feature-specific UI remains with its owner.

Platform source sets carry the renderers, so keep Compose UI Gradle dependencies
in `androidMain`. A module whose renderer is Android-only still applies the
Compose convention plugin, which requires `compose.runtime` on every
compilation; that one dependency stays in `commonMain`. Do not add
`compose.components.resources`: Android uses its module's generated `R` class,
iOS resolves Apple String Catalogs through Foundation, and `commonMain` carries
only semantic localization keys and already-localized presentation snapshots.

### App-internal dependency direction

App-owned features may depend directly on their own provider integration when
their implementation and public contract genuinely use provider-native types.
This is the expected direction for Blip's Blink features and Lasr's NWC
features. Do not introduce app-internal provider interfaces, adapters, or
translation models merely to hide that dependency or make app module graphs
look alike.

Use Gradle `api` when types from a dependency deliberately appear in a module's
public constructors, functions, state, or events. Use `implementation` when the
dependency is confined to implementation details. Do not hide a public
provider dependency with `implementation`, and do not expose an entire
integration module transitively when no public signature requires it. App
`shared` composition roots should declare the feature and integration modules
they use directly instead of relying on incidental transitive dependencies.

An app-specific application contract implemented by an integration module can
be appropriate when it represents a real boundary inside that app. Keep such a
contract app-owned and introduce it only when the implementations or consumers
can meaningfully vary; never replicate the pattern in other apps for symmetry.

Android `R` types and iOS native resource handles stay in their owning platform
source sets and must not leak into model or repository APIs. Blink contact
import remains Blip-only.

## Resources, persistence, and sensitive data

- Feature-specific Android strings and assets belong to that feature's
  `src/androidMain/res`; feature-specific Apple strings and assets belong to
  `src/iosMain/resources` and are referenced directly by every consuming Xcode
  target. Create only catalogs that the platform actually consumes. A shared
  Apple catalog remains one file and is never copied into an app.
- Only cross-feature design assets and generic accessibility strings belong in
  the corresponding platform resource catalog in `core:ui`.
- Native iOS renderers read localized text from the exported Kotlin snapshots
  and controllers. Kotlin iOS controllers resolve their owning String Catalog
  through Foundation. Do not keep a third copy of a translation in
  `Localizable.strings`.
- App branding, icons, URL schemes, store copy, and legal links belong to the
  owning app.
- Maintain English, German, and Spanish resources together. Keep keys,
  placeholders, plurals, and translated values aligned within each platform
  and across intentionally paired Android and Apple catalogs. Declare a
  platform-only resource surface in `verifyNativeLocalizations` instead of
  adding an unused mirror catalog. Run the task after changing localized
  resources.
- Every iOS and iPadOS target has a minimum deployment version of 18.5. Keep
  normal and E2E targets aligned.
- Never edit generated resource accessors or generated build output.
- Do not add fallback decoders, migration paths, or import behavior for the
  retired combined app unless the user explicitly requests a migration task.
- Never commit or log wallet credentials, NWC URIs, API keys, signing material,
  payment preimages, or other sensitive wallet data.

## Implementation style

Use JDK 21, four-space Kotlin indentation, no wildcard imports, and the
repository's ktlint format. Prefer convention plugins from `build-logic` for
new modules. Expose immutable state and confine mutation to implementation
boundaries. Keep public APIs small and avoid pass-through use-case classes that
add no policy.

Swift renderers use four-space indentation and ordinary SwiftUI idiom, and stay
free of provider policy. A Kotlin controller exported to Swift exposes an
immutable snapshot plus explicit intent methods; its observers take a callback
and return a cancel closure rather than exporting a `Flow`.

## Minimal verification

Verification must remain deliberately small. The owner performs QA and broad
validation; agents should only obtain a fast compile and formatting signal for
the code they changed.

- Run ktlint only for the affected module or smallest practical project scope.
- Compile only the affected platforms and apps. Prefer the Android Debug variant
  and the iOS simulator Debug framework, for example
  `:<app>:androidApp:assembleDebug` and
  `:<app>:shared:linkDebugFrameworkIosSimulatorArm64`.
- If Swift or Xcode project files change, use one relevant Debug simulator build
  instead of a platform or configuration matrix. A new shared Swift file must be
  added to every consuming app's Xcode project.
- After creating a new source-set directory, Gradle can report a stale
  `UP-TO-DATE` compile and leave a moved declaration unresolved. Re-run that one
  build with `--rerun-tasks` before believing the error.
- Do not run root `check`, broad test suites, integration tests, E2E tests,
  Maestro flows, device tests, screenshot tests, or test matrices.
- Do not add tests by default. Add or run only a very small, directly targeted
  unit test when non-trivial pure logic or a specific regression makes it
  clearly valuable. Leave broader behavioral and integration validation to the
  owner.
- Do not build Android or iOS Release variants unless the user explicitly asks.
  If a refactor or edit is unusually large or release-sensitive, explain why a
  Release build would be useful and ask the user for permission before running
  it.

## Performance diagnostics

- Camera performance stages are recorded as Android system-trace events and iOS
  signposts. Use and interpret them as described in
  `docs/performance-monitoring.md`.
- Keep performance marker names static and provider-neutral. Never attach QR
  contents, wallet credentials, invoices, NWC URIs, payment preimages, or other
  dynamic user data to a marker.
- Camera startup and first-frame measurements do not require a QR fixture. Treat
  QR-detection timing as diagnostic rather than a repeatable benchmark unless
  the capture setup is controlled.

## Release invariants

Release procedure details live in `docs/release.md`; the safeguards below are
non-negotiable.

- Blip, Flint, and Lasr share the suite upload identity and app-signing
  identity. Both identities are locally managed and their secrets remain
  outside Git.
- Direct Android distribution uses a locally signed universal APK built from
  the release bundle with the shared app-signing key. Its certificate is pinned
  in `distribution/app-signing-certificate.sha256` and must not change once
  Play App Signing is enrolled.
- Release tags and artifacts are app-qualified.
- The owner-approved `io.github.nicolals:nwc-kmp:0.3.3-SNAPSHOT` dependency may
  remain until its maintainer publishes a stable version. Do not replace or
  update it without owner approval, and record its resolved checksum for release
  candidates.
- Never change the pinned signing certificate, create release tags, upload
  artifacts, or publish to a public store, GitHub, or Zapstore without explicit
  owner authorization and review of the exact candidate artifacts and
  declarations.
