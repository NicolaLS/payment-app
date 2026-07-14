# AGENTS.md

## Purpose

This repository is a greenfield Kotlin Multiplatform mobile app. It may reuse Lasr's visual language and selected Compose components, but it must not reproduce Lasr's architecture or business-layer technical debt.

The product has one wallet/provider integration. Do not introduce NWC, multi-wallet selection, provider registries, or abstractions designed for hypothetical future providers unless requirements change.

Optimize for payment correctness, clear ownership, testability, and a small understandable architecture. Prefer the simplest design that enforces the required invariants.

## Technology decisions

- Use Kotlin Multiplatform for shared models, application logic, data adapters, and Compose UI.
- Use Compose Multiplatform and Material 3 for shared UI.
- Keep Android and iOS shells thin. Native camera, security, lifecycle, and platform integration stay behind narrow interfaces.
- Use Navigation Compose with type-safe `@Serializable` routes.
- Use Koin for dependency injection, but only at composition roots and route/ViewModel entry points.
- Use coroutines, `StateFlow`, and `SharedFlow` with structured concurrency.
- Use a transactional KMP database such as SQLDelight for durable application data.
- Use typed preferences only for small settings such as theme, language, currency, and haptics.
- Use Android Keystore and iOS Keychain through a narrow credential-vault interface for secrets only.
- Use Ktor for HTTP unless the selected provider SDK supplies a better maintained client.
- Use generated API models where a schema is available. Do not hand-build GraphQL payloads or duplicate generated wire models.
- Pin dependencies to stable immutable versions. Do not use `SNAPSHOT` dependencies in production.

## Recommended project structure

Use the conventional single `:shared` KMP module initially. This is intentional and follows normal KMP project structure; a shared module is not an architecture smell.

```text
:shared
  src/commonMain
    core/model        Pure values and domain rules
    application       Workflows, policies, and ports
    data              Database/network/provider implementations
    presentation      Feature UI and ViewModels
    navigation        Typed routes and graph composition
    di                Koin definitions
  src/androidMain     Android implementations
  src/iosMain         iOS implementations

:androidApp           Thin Android application shell
iosApp                Thin Swift/Xcode shell
```

Keep logical dependency direction inside `:shared`:

```text
presentation -> application -> core/model
data -----------------------> application/core/model
di -------------------------> all implementations
```

The issue to avoid is not `:shared`; it is allowing every package to depend on every other package. `core/model` and `application` must not import Compose, Koin, Ktor, SQLDelight, Settings, Apollo, Android/iOS APIs, or provider SDK types.

Start with package boundaries and enforce them through review or architecture tests. Add Gradle modules only when there is evidence that compile-time dependency enforcement, independent ownership, reuse, or build performance needs them. If a split becomes useful, the first likely boundary is `:sharedLogic` versus `:sharedUI`; do not preemptively create core, application, infrastructure, and per-feature modules.

Organize presentation code by feature, not by a global collection of screens and ViewModels:

```text
feature/pay
feature/onboarding
feature/settings
feature/contacts       only if the product includes contacts
designsystem
navigation
```

## Presentation architecture

Use MVVM with unidirectional data flow. Adopt the useful parts of MVI without forcing MVI ceremony onto trivial screens.

Each non-trivial screen should expose:

```kotlin
data class FeatureUiState(...)

sealed interface FeatureAction

sealed interface FeatureEffect
```

- `UiState` is the complete immutable render state for that screen.
- `Action` represents user or lifecycle input.
- `Effect` is only for one-shot presentation work such as navigation, permission requests, or transient messages.
- Durable business truth is state, not an effect.
- Expose state through `StateFlow` and accept actions through one `onAction` entry point.
- Simple settings screens may use explicit methods instead of inventing an intent hierarchy for each toggle.
- Use standard lifecycle ViewModels and `viewModelScope`. Do not create custom retained-controller/ViewModel infrastructure.
- Scope ViewModels explicitly to a route or navigation graph.
- Use `SavedStateHandle` only for non-sensitive, reconstructible UI state.
- Collect flows with lifecycle awareness.

When action ordering matters, serialize it with an actor, channel, or narrowly scoped mutex. Do not launch an unrelated coroutine for every action and then coordinate it with mutable Boolean flags.

Keep ViewModels focused on presentation. Protocol validation, persistence transactions, payment submission, reconciliation, currency conversion, and contact import belong in application workflows.

Avoid god ViewModels and giant screen contracts. Pass a cohesive `UiState` and action callback instead of dozens of state/callback parameters.

## Application and domain rules

Use application services/use cases only when they enforce policy, coordinate multiple dependencies, or define a meaningful workflow. Examples include:

- Resolve and validate a payment request.
- Decide whether confirmation is required.
- Submit and reconcile a payment attempt.
- Provision or revoke the single wallet/provider connection.
- Import records transactionally.
- Run a storage migration.

Do not create one-line use-case wrappers around every repository getter, setter, or preference toggle.

Use interfaces at real external seams: payment provider, database, credential vault, clock, ID generator, network client, and platform services. Do not add marker interfaces or abstractions with only one implementation and no meaningful test seam.

### Result and error policy

Do not create a universal custom `Result`, `Response`, `Resource`, or `AsyncResult` type containing `Loading`, `Success`, and `Error`.

- Loading belongs in `UiState` or another observable workflow state, not in the return value of a one-shot suspend function.
- Use operation-specific sealed outcomes for important expected results.
- If many simple operations genuinely share the same pattern, a two-case `Outcome<Value, Failure>` is acceptable. Do not add a functional-programming library only for this.
- Use `kotlin.Result` only when failure is naturally exception-based; it is not a replacement for typed business outcomes.
- Unexpected defects may throw to a controlled logging boundary.
- Always rethrow `CancellationException`. Cancellation is control flow, not an application error.
- Never expose raw `Throwable.message`, provider text, or HTTP details directly to users.
- Map typed failures once in presentation to resource-backed `UiText`; keep redacted diagnostics separate.

### Strong types

Do not represent every financial value, identity, hash, or credential as `Long`, `Double`, or `String`.

Use validated value types for concepts such as:

- `MilliSatoshi`, `Satoshi`, and fiat minor units.
- `PaymentHash`, `PaymentAttemptId`, and provider account ID.
- `CurrencyCode`, `ExchangeRate`, and timestamped rate snapshots.
- Credential references and secret/redacted values.

Use checked integer or fixed-point/decimal conversion with explicit rounding. Do not use binary `Double` where conversion changes the amount submitted for payment. Keep unknown fee values nullable; missing is not zero.

## Payment correctness

The product has a single wallet/provider. Model that directly with one `PaymentBackend`; do not build a multi-provider registry or active-wallet state machine.

If the app submits an irreversible payment:

1. Fully validate and normalize the request.
2. Calculate the final amount and record any exchange-rate snapshot.
3. Evaluate confirmation policy against the final rounded amount.
4. Persist a payment attempt before calling the provider.
5. Submit exactly once unless an explicit idempotency rule permits otherwise.
6. Persist the returned state transactionally.
7. Reconcile non-final attempts after foregrounding or process restart.

Use an explicit durable state model such as:

```text
Created -> Submitting -> Pending -> Settled
                         |          AlreadyPaid
                         |          Rejected
                         +--------> Unknown -> reconciliation
```

- A timeout or lost response is `Unknown`, not `Failed`.
- Never automatically retry a payment mutation after an ambiguous transport failure.
- Keep draft UI state separate from the durable attempt record.
- Use an injected clock and UUID/ULID generator.
- Use an audited, maintained protocol library. Do not write a minimal invoice parser and treat extracted display fields as payment validation.
- Put end-to-end protocol validation in one application workflow, not partly in a repository and partly in a ViewModel.
- Test confirmation values immediately below, exactly at, and immediately above every threshold.

## Data and security

- Use the database for durable entities and payment attempts.
- Use preferences only for small independent settings.
- Do not store growing JSON aggregates in Settings, SharedPreferences, UserDefaults, or Keychain.
- All multi-record updates and provisioning flows must be transactional or have explicit compensation.
- Add schema versions and migration tests from the first release.
- Treat corruption as a diagnosable recovery state. Do not silently replace all user data with an empty collection.
- Store only credentials in the platform vault, addressed by an opaque ID.
- Never place credentials, API keys, invoices, preimages, or secret-bearing URIs in navigation routes, `SavedStateHandle`, logs, crash reports, analytics, test tags, or general data-class `toString()` output.
- Define Android backup and iOS Keychain reinstall behavior deliberately.
- Provide an explicit reset/delete-credentials path.
- Request clipboard access only after an explicit Paste action.

## Networking

- Inject and centrally own network clients; close them with the application lifecycle.
- Give query and mutation traffic separate retry policies.
- Never automatically retry an irreversible mutation.
- Connectivity monitors are UX hints, not authoritative gates. Attempt the request and classify its actual result.
- Validate HTTP status, content type, redirects, body size, and exact numeric fields.
- Treat remote callbacks and user-supplied URLs as untrusted. Define scheme, host, redirect, and private-network policy.
- Preserve stable provider error codes and redacted diagnostics. Do not classify errors by searching English message substrings.
- Send only the minimum required data and maintain a data-flow inventory for the privacy policy.

## Navigation and external inputs

- Use type-safe serializable Navigation Compose routes.
- Routes carry stable IDs and small non-sensitive values only.
- Do not pass domain objects, credentials, API keys, or large payloads through routes.
- Use an ephemeral one-time handoff token for sensitive input that must cross a navigation boundary.
- Keep feature graph builders near their owning feature.
- Use graph-scoped ViewModels for state intentionally shared across destinations.
- Navigation renders application decisions; it must not infer business state from back-stack shape or persist onboarding/payment side effects.
- Handle deep links and platform intents through one injected root coordinator.
- Apply bootstrap, authentication/consent, validation, and deduplication before delivery to a feature.
- Do not use global singleton Channels or drop-oldest event buses for navigation or external input.
- Every hidden gesture must have a visible and accessible alternative.

## Dependency injection and concurrency

- Koin assembles the graph; business and UI code must not call the global Koin context as a service locator.
- Prefer constructor injection.
- Split DI definitions by platform, storage, network, provider, application, and feature.
- Add a production graph-verification test.
- Make every scope owner explicit. Do not register anonymous application scopes that are never cancelled.
- Provide typed dispatchers such as `AppDispatchers(main, default, io)` rather than one unqualified dispatcher.
- Suspending code inherits caller context unless it wraps genuinely blocking work.
- Avoid hidden dependency cycles implemented through callbacks that resolve more objects from DI.

## UI and design reuse

Reuse visual components selectively, not their legacy ViewModels, navigation adapters, or business state.

- Extract semantic colors, typography, shapes, spacing, elevation, motion, and reusable components into a small design system.
- Preserve the recognizable visual identity while correcting contrast issues.
- Use adaptive layouts and window size classes; do not lock orientation to compensate for fixed layouts.
- Support large text, screen readers, logical focus order, minimum touch targets, and meaningful roles/state descriptions.
- Respect reduced-motion and disabled-animation settings. Pause infinite animations when offscreen or backgrounded.
- Provide visible controls for dismissal, scanner modes, contact panels, and other gesture shortcuts.
- Keep user-facing text in typed resources and use plural/locale-aware number and time formatting.
- Never construct localized user text in data or domain code.
- Add previews for important visual states, sizes, themes, and locales.
- Request camera permission only when the user initiates a scanner action. Model denied, permanently denied, restricted, and unavailable states explicitly.

## Testing and quality gates

Tests should follow ownership boundaries:

- Pure unit/property tests for value types, conversion, confirmation, parsing, and state transitions.
- Application tests for payment idempotency, unknown outcomes, cancellation, and process-restart reconciliation.
- Contract tests for the single provider adapter using fakes and recorded/golden responses.
- Persistence tests for transactions, concurrency, corruption, and every migration.
- Presentation tests for `UiState`/`Action` behavior without real provider or database clients.
- Compose semantics, screenshot, large-font, theme, locale, and accessibility tests.
- Platform tests for secure storage, permission/lifecycle behavior, app links, and scanner state.
- End-to-end tests for the most important real payment and failure-recovery paths.

CI must run formatting, static analysis, shared tests, Android lint/build, iOS simulator build/tests, dependency verification, and a small E2E smoke suite where practical. Test the actual module names and release-like/minified builds.

Pin actions and external tools, archive release mappings/symbols, generate checksums/SBOM, and build release artifacts from the tagged commit in a clean isolated workspace.

Before merging, run the repository's documented verification command. Keep that command current in this file and CI.

## Avoid these patterns

- A universal `Loading/Success/Error` result wrapper.
- God ViewModels, giant navigation files, or screens with dozens of callback parameters.
- One use-case class per trivial repository method.
- Business logic in composables, navigation callbacks, or data mappers.
- Raw financial units and floating-point payment conversion.
- Secrets in routes, models, logs, or saved state.
- Settings/Keychain used as a database.
- Session-only tracking of submitted irreversible operations.
- Global service location or singleton event buses.
- Unowned coroutine scopes, broad `catch(Throwable)`, or swallowed cancellation.
- String matching against provider error messages.
- Automatic mutation retries.
- Custom protocol parsers when an audited maintained implementation exists.
- Speculative multi-wallet, multi-provider, plugin, or micro-module architecture.
- Silent fallbacks that hide DI, storage, migration, or protocol failures.
- Orientation locks, fixed dimensions, or gestures used instead of adaptive accessible UI.

## Decision rule

When choosing between architectures, prefer the option that:

1. Makes invalid payment and security states unrepresentable.
2. Gives each piece of mutable state one clear owner.
3. Survives cancellation and process recreation honestly.
4. Can be tested without the real network, database, clock, or platform.
5. Adds the fewest abstractions necessary for current requirements.

Record consequential choices as short ADRs with context, decision, rejected alternatives, consequences, and enforcement tests.
