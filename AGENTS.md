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
