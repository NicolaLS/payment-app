# Rayl Suite development guidelines

## Products and identity

- Blip is the Blink-only product. Its package and bundle IDs are
  `xyz.lilsus.blip`, with `.dev` and `.e2e` Android suffixes.
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
their repositories, coordinators, retry policy, and app state, while shared UI
may receive `Error(message)`, render a failed hero phase, and invoke an ordinary
user-action callback.

A shared render model is appropriate when:

- It contains only information the common UI renders.
- Every variant is meaningful for every consumer; it is not a superset of
  provider states.
- Provider errors are converted by the app into localized display text or
  another provider-neutral visual value before crossing the boundary.
- Callbacks report UI intent such as click, dismiss, select, or retry request;
  the app still decides what that intent means and whether it is allowed.
- The mapping from app state to render state remains app-owned.

This projection is not a backend adapter. It is the normal boundary between app
logic and reusable presentation. Do not refuse to share genuinely identical UI
merely because its upstream repositories or errors differ.

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
- `core:ui`: cross-app design tokens and genuinely reusable UI primitives.
- `core:settings`: shared platform creation of app-scoped preference and secure
  storage.
- `core:network`: shared platform HTTP clients and transport defaults.
- `feature/*`: reusable provider-neutral user stories and their resources.
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

Generated resource types stay in their owning module and must not leak into
model or repository APIs. Blink contact import remains Blip-only.

## Resources, persistence, and sensitive data

- Feature-specific strings and assets belong to that feature's
  `composeResources`.
- Only cross-feature design assets and generic accessibility strings belong in
  `core:ui`.
- App branding, icons, URL schemes, store copy, and legal links belong to the
  owning app.
- Maintain English, German, and Spanish resources together. Keep keys,
  placeholders, and plurals aligned across `values`, `values-de`, and
  `values-es`.
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
  instead of a platform or configuration matrix.
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
