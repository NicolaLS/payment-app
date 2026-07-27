# Rayl Suite development guidelines

## Products and identity

- Blip is the Blink-only product. Its package and bundle ID is
  `xyz.lilsus.blip`, with `.dev` and `.e2e` Android suffixes.
- Lasr is the Nostr Wallet Connect-only product. Its package and bundle ID is
  `xyz.lilsus.lasr`, with `.dev` and `.e2e` Android suffixes.
- The Gradle root project is `rayl-suite`.
- Reusable Kotlin namespaces start with `xyz.lilsus.raylsuite`.

The applications are independent, single-provider products. Do not introduce
provider selection, Blink behavior in Lasr, NWC behavior in Blip, or migration
from the former combined reference application.

## Module boundaries

- `core:model`: provider-neutral immutable values and pure Kotlin logic; no
  Compose or generated resources.
- `core:ui`: shared design tokens and genuinely reusable UI primitives.
- `core:settings`: shared platform creation of app-scoped preference and secure
  storage.
- `core:network`: shared platform HTTP clients and transport defaults.
- `feature/*`: reusable provider-neutral user stories and their resources.
- `apps/blip/feature/*` and `apps/lasr/feature/*`: app-owned user stories.
- `apps/blip/integration/*` and `apps/lasr/integration/*`: provider SDK,
  network, credential, and repository implementations.
- `integration/*`: external adapters reused by both apps without wallet-provider
  behavior.
- App `shared` modules are composition roots for navigation, dependency
  assembly, legal links, and app identity; keep business rules out of them.

Generated resource types stay in their owning module and must not leak into
model or repository APIs. Blink contact import remains Blip-only.

## Resources and persistence

- Feature-specific strings and assets belong to that feature's
  `composeResources`.
- Only cross-feature design assets and generic accessibility strings belong in
  `core:ui`.
- App branding, icons, URL schemes, store copy, and legal links belong to the
  owning app.
- Maintain English, German, and Spanish resources together.
- Never edit generated resource accessors.
- Do not add fallback decoders or import behavior for the retired combined app.

## Verification

Keep tests focused; do not add broad matrices or near-duplicate tests without a
dedicated QA task. Existing tests must remain green. Favor module compilation,
ktlint, Android debug/E2E/Release builds, lint, arm64 iOS simulator tests, both
Kotlin/Native Release framework targets, and concrete Xcode builds.

Use JDK 21, four-space Kotlin indentation, no wildcard imports, immutable state,
the repository's ktlint format, and convention plugins from `build-logic`.
Keep public APIs small and avoid pass-through use-case classes without policy.

## Release invariants

- Blip and Lasr share the suite upload identity and app-signing identity; both
  are locally managed and their secrets remain outside Git.
- Direct Android distribution uses a locally signed universal APK built from
  the release bundle with the shared app-signing key. Its certificate is pinned
  in `distribution/app-signing-certificate.sha256` and must never change once
  Play App Signing is enrolled.
- Release tags and artifacts are app-qualified.
- The owner-approved NWC snapshot dependency may remain until its maintainer
  publishes a stable version; record its resolved checksum for candidates.
- Public store, GitHub, and Zapstore publication requires owner review of the
  exact candidate artifacts and declarations.
