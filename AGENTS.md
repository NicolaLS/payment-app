# Rayl Suite extraction guidelines

## Current objective

The active work is extracting the read-only `apps/legacy` reference application
into two independent Kotlin Multiplatform apps and reusable modules. Work in
small, reviewable vertical slices and keep the migration ledger in
`docs/MIGRATION_LEDGER.md` current.

- Blip is the Blink product. Its package and bundle ID is `xyz.lilsus.blip`,
  with `.dev` and `.e2e` suffixes for those variants.
- Lasr is the NWC product. Its package and bundle ID is `xyz.lilsus.lasr`,
  with `.dev` and `.e2e` suffixes for those variants.
- The Gradle root project is `rayl-suite`.
- Reusable Kotlin namespaces start with `xyz.lilsus.raylsuite`.

## Non-negotiable migration rules

- Never edit anything under `apps/legacy`. It is a reference to copy from and
  reinterpret, not a module to refactor. Before each extraction commit run:

  ```shell
  git diff --exit-code d17dc43 -- apps/legacy
  ```

- Blip and Lasr are entirely new, pre-release applications. Do not migrate
  legacy preferences, databases, credentials, serialized state, identifiers,
  or installation data. Do not add compatibility decoders, legacy-key
  fallbacks, schema upgrades, or automatic import behavior.
- Design new persistence schemas for the new apps. Legacy storage code may
  inform current behavior, but preserving its keys or wire format is not a
  requirement.
- Preserve applicable legacy behavior, UI, UX, accessibility, resources, and
  user stories unless the task explicitly changes them.
- Remove complexity that existed only to choose between Blink and NWC. The
  final single-provider apps do not need a provider-choice screen or runtime
  provider branching.
- Do not migrate both provider implementations into a shared feature. Blink
  implementations belong to Blip; NWC implementations belong to Lasr.
- Blink contact import is a Blip-only product capability. It must not appear in
  shared contacts code or Lasr, and it must not run as installation migration.
- Prefer many focused commits. A commit should establish one boundary, extract
  one primitive, or complete one small user-story slice.

## Module boundaries

- `core:model`: provider-neutral immutable values and pure Kotlin logic. It must
  not depend on Compose or generated resources.
- `core:ui`: shared design tokens and genuinely reusable UI primitives. It may
  own generic accessibility resources used by those primitives.
- `core:settings`: shared platform creation of app-scoped preference storage.
- `feature/*`: reusable, provider-neutral user stories. A feature owns its UI,
  state, persistence contract/implementation where appropriate, strings,
  icons, and feature-specific resources.
- `apps/blip/feature/*` and `apps/lasr/feature/*`: app-only user stories.
- `apps/blip/integration/*` and `apps/lasr/integration/*`: provider SDK,
  network, credential, and repository implementations.
- App `shared` modules are composition roots for navigation, dependency
  assembly, and app identity. Keep business rules out of them.

Presentation may depend on provider-neutral models and feature contracts; this
does not make domain code UI code. Generated resource types must stay in the
module that owns the resource and must not leak into model or repository APIs.

## Resources

- Put feature-specific strings and assets in that feature's
  `composeResources`.
- Put only cross-feature design assets and generic UI accessibility strings in
  `core:ui`.
- Keep app branding, icons, URL schemes, and app-only copy in the owning app.
- Provide the existing English, German, and Spanish translations when copying
  localized legacy UI.
- Never edit generated resource accessors.

## Verification and tests

Keep testing minimal. Port the smallest valuable legacy regression test for an
extracted behavior; do not add exhaustive matrices or several near-duplicate
tests. Favor focused module tests, compilation, lint, Android debug/e2e builds,
an arm64 iOS simulator build, and visual/behavior parity checks.

For a completed shared slice, verify both consuming apps where practical. Use a
concrete arm64 simulator destination for Xcode because the new modules do not
configure `iosX64`.

## Kotlin and Gradle style

Use four-space indentation, no wildcard imports, immutable state, and the
repository's ktlint format. Use the convention plugins in `build-logic` for new
KMP modules. Keep public APIs small and avoid pass-through use-case classes that
add no policy.
