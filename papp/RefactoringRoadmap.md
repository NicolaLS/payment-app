<!-- DO NOT WRITE DOCUMENTATION HERE -->

### PAPP KMP/CMP — Refactoring Roadmap (by layer, prioritized)

This document focuses exclusively on the PAPP Kotlin/Compose Multiplatform app (not the `nwc-kmp` library). It
summarizes what the app does, outlines the current architecture, highlights issues and opportunities, and proposes a
prioritized, incremental refactoring plan separated into presentation, data, and domain layers. The goal is to first
refactor presentation and data (without changing domain APIs), and then refactor the domain layer last.

---

### What the app does (high‑level)

- Enables scan‑to‑pay and manual payment flows for Lightning:
    - Reads invoices from QR code or manual input.
    - Parses input as BOLT11, LNURL, or Lightning Address.
    - For LNURL/Lightning Address, fetches pay params and requests invoices.
    - Sends payment via Nostr Wallet Connect (NWC) to a configured wallet.
- Settings:
    - Manage wallet connection(s) and active wallet.
    - Currency selection and exchange rate display.
    - Language selection/override.
    - Payment confirmation mode and thresholds.
- Cross‑platform foundation:
    - Compose Multiplatform UI (common code), platform specific scanning (`AndroidQrScanner`, `IosQrScanner`).
    - Koin for DI (common module: `nwcModule`).
    - Ktor for networking (LNURL, exchange rates).
    - Shared domain use cases and models.

Key areas (illustrative files)

- Presentation: `MainScreen.kt`, `MainViewModel.kt` (~560 lines), `MainUiState.kt`, `PayNavigation.kt`,
  `SettingsNavigation.kt`, settings screens/view models, scanner controllers and permission helpers.
- Domain: `domain/use_cases/*`, `domain/model/*`, `LightningInputParser.kt`, `Bolt11InvoiceParser.kt`, etc.
- Data: `NwcWalletRepositoryImpl.kt`, `WalletDiscoveryRepositoryImpl.kt`, `LnurlRepositoryImpl.kt`, settings
  repositories, `CoinGeckoExchangeRateRepository`.
- DI: `di/NwcModule.kt` wires everything via Koin.

---

### Architecture snapshot (current)

- Presentation
    - UI state via `sealed class MainUiState` with ephemeral events via `MainEvent` (`SharedFlow`).
    - `MainViewModel` orchestrates most of the main flow: parsing inputs, confirming, paying, exchange rate, and manual
      amount.
    - Navigation uses typed routes with `kotlinx.serialization` objects, pulled view models via Koin inside
      composables (`KoinPlatformTools.defaultContext().get()` + `remember { koin.get<...>() }`).
    - Platform scanning controllers in `presentation.main.scan` (Android/iOS implementations) controlled from
      `PayNavigation.kt`/`MainScreen`.
- Domain
    - Use cases per responsibility, repository interfaces, error modeling via `AppError`/`AppErrorException`.
    - Utilities for parsing inputs: LNURL / Lightning Address / BOLT11; currency catalog; amount and display models.
- Data
    - Repositories implementing domain interfaces (NWC wallet operations, LNURL HTTP, exchange rates,
      preferences/storage, language/platform repos).
    - `NwcWalletRepositoryImpl` manages an `NwcClient` with caching and mutex.
    - `LnurlRepositoryImpl` builds a `HttpClient` internally by default; parsing JSON via `kotlinx.serialization`.
- DI
    - Single Koin module registering repositories (as `single`), use cases (as `factory`), and view models (as
      `factory`). Also supplies a `CoroutineDispatcher` and an application `CoroutineScope`.

---

### Refactoring Guidelines (General, all layers)

Priorities P1 (quick wins) → P2 (intermediate) → P3 (advanced). Keep domain APIs stable until the final phase to allow
independent refactoring of presentation and data first.

- Tooling & conventions (P1)
    - Enforce code style/format: ktlint + Spotless (KMP‑friendly). Standardize imports, annotations, and file headers.
    - Enable Detekt (KMP ready) with a sensible baseline; configure thresholds for long methods/classes.
    - Add KDoc for public types and package‑level docs where meaningful.
    - Consistent naming conventions for functions/variables/parameters; prefer expressive, domain terms.
- Concurrency (P1)
    - Provide `AppDispatchers` abstraction (expect/actual or DI) for main/io/default to avoid hard‑coding
      `Dispatchers.Default`.
    - Standardize structured concurrency: always cancel scopes in lifecycle; avoid ad‑hoc `SupervisorJob` unless
      justified.
- Testing (P1/P2)
    - Strengthen `commonTest` coverage: LNURL parsing/requests, Lightning input, main flow edge cases, error mapping.
    - Ktor `MockEngine` for HTTP tests, fake repositories for domain tests.
- Logging & diagnostics (P2)
    - Introduce a multiplatform `Logger` interface (expect/actual or DI). Add unobtrusive debug logs around network
      calls, parsing, and payment flow transitions.
- i18n (P1/P2)
    - Audit strings for completeness and placeholders. Use localized formatting for amounts, currencies, and error
      messages.
- Accessibility (P2)
    - Add semantics for important UI components (buttons, scanner overlay feedback, results). Ensure sufficient contrast
      and talkback/VoiceOver readable labels.

---

### Presentation Layer — Refactor Plan

Scope: No domain API changes in this phase. Presentation can introduce new internal adapters that call existing use
cases.

P1 — Quick wins and cleanups

- ViewModel lifecycle & scope (KMP‑friendly)
    - Current pattern: each VM constructs its own `CoroutineScope` and exposes `clear()` for manual cancel in
      `DisposableEffect`.
    - Action: Introduce a lightweight `PresentationScope` factory (DI) that creates viewmodel scopes; standardize
      cancellation in `DisposableEffect` across all screens. Keep API unchanged, but remove ad‑hoc scope creation
      patterns.
- Koin access in Composables
    - Replace `remember { KoinPlatformTools.defaultContext().get() }` with a tiny helper (e.g., `rememberInject<T>()`)
      or a dedicated `ViewModelProvider` that respects composition lifecycle.
    - Rationale: reduces boilerplate and makes test composition simpler. Keep it internal to presentation.
- Structure and file hygiene
    - Split `MainViewModel` (563 lines) into smaller private collaborators without changing public API:
        - `PaymentFlowController` (parse/confirm/pay state machine, uses existing use cases).
        - `CurrencyStateController` (observes currency preference and manages exchange rate job).
        - `ManualAmountController` already exists — keep and ensure it is the single source of truth for manual entry.
    - Keep the current `MainViewModel` surface so domain/data are unaffected.
- Side‑effects containment
    - Move scanner start/pause/resume decisions out of `MainScreen` into `MainScreenEntry` (as they largely are now) and
      wrap them into a cohesive `ScannerUiCoordinator` that:
        - Listens to `MainUiState` and camera permission.
        - Exposes `onRequestStart/onResume/onPause` callbacks for `MainScreen` to call.
    - Result: `MainScreen` becomes purely declarative with thin side‑effect boundaries.
- UI polish & semantics
    - Introduce clear semantics descriptions on result and action components; add `contentDescription` where relevant.
    - Normalize `LaunchedEffect` keys (prefer stable, well‑scoped keys like `uiState::class` or booleans).

P2 — Intermediate architectural improvements

- MVI/MVVM tightening
    - Formalize `MainIntent` handling by moving parsing paths into collaborators:
        - `LightningInputParser` usage stays, but parsing branches (BOLT11 vs LNURL vs Address) move into
          `PaymentFlowController`.
    - Ensure every state transition is unit‑testable by injecting collaborators into `MainViewModel` constructor (
      already DI‑ready).
- Navigation improvements (deferred)
    - Hold navigation changes until we decide whether to adopt a new KMP navigation library; keep existing Navigation
      Compose setup unchanged for now.
- Scanner UX separation
    - Extract the drag/zoom logic in `PayNavigation.kt` into a `ScannerOverlay` component that owns gesture detection
      and exposes normalized zoom fractions to the scanner controller.
- Error UI
    - Add a consistent error presenter (snackbar/toast/banner) wired to `MainEvent.ShowError`. Create an adapter that
      maps `AppError` → `stringResource` keys with optional parameters.

P3 — Advanced presentation enhancements

- State machine/escalation policies
    - Encode the main flow transitions as an explicit state machine (e.g., sealed interface `PaymentState` with
      transition helpers). Keep `MainUiState` as a UI projection to avoid domain changes.
- Multiplatform navigation strategy (deferred)
    - Re-evaluate navigation approach only after completing the dedicated review of KMP navigation library options.
- Alternative DI in UI (optional)
    - If Koin Compose integration is introduced, replace manual helpers. Otherwise, keep the minimal `rememberInject`
      wrapper to stay lightweight.

Deliverables

- Smaller, testable collaborators for `MainViewModel`.
- Consistent DI hooks in composables.
- Better scanner overlay component abstractions.
- Error presentation adapter.

---

### Data Layer — Refactor Plan

Scope: Keep domain repository interfaces stable. Only change implementation internals and DI bindings.

P1 — Quick wins and cleanups

- HttpClient & JSON injection
    - `LnurlRepositoryImpl` currently constructs `HttpClient`/`Json` defaults. Change to injected constructor params,
      and centralize client creation in a `NetworkModule`:
        - `single<HttpClient> { createBaseHttpClient() }` (platform‑specific tuning in `androidMain`/`iosMain` parts if
          needed).
        - `single<Json> { Json { ignoreUnknownKeys = true; explicitNulls = false } }`.
    - Benefit: shared connection pool, easier testing, consistent timeouts/interceptors.
- Dispatchers
    - Stop defaulting to `Dispatchers.Default` inside repositories; inject `AppDispatchers` (or use Koin‑named
      dispatchers) for IO/network heavy work.
- Error mapping consistency
    - Consolidate throwable → `AppError` mapping in a shared helper to avoid duplication (e.g., `Throwable.toAppError()`
      inside data layer). Currently implemented twice for IO detection.

P2 — Data source structure & caching

- Exchange rates
    - Introduce a small cache with TTL and last updated timestamp to reduce network calls.
    - Expose `flow` with `refresh()` method; keep domain interface stable by having the use case decide pull vs.
      subscribe.
- LNURL
    - Strengthen validation and content‑type checks; make `parsePayParams` and `parseInvoice` package‑private helpers
      and add unit tests (MockEngine fixtures for success/error cases). Ensure onion domain handling and scheme
      normalization remain aligned with domain parsing rules.
- NWC client lifecycle
    - `NwcWalletRepositoryImpl` caches `NwcClient` per URI. Add an optional repository‑internal observer (or method) to
      invalidate cache when the active wallet changes (without changing the public interface). Today it naturally
      refreshes on pay calls; proactive invalidation is safer.
    - Add metrics/logging around `payInvoice()` timing, failures by type (timeout/request/network).
- Settings & secure storage
    - Ensure `createSecureSettings()` encrypts on each platform and is thread‑safe. Provide a small `SettingsDataSource`
      facade if we start growing settings logic.

P3 — Advanced data improvements

- Network module
    - Create a `NetworkModule` to centralize Ktor config: timeouts, logging, JSON, user agent, retry/backoff (if
      desired), and a switchable base for Tor (.onion) policies.
- Resilience
    - Consider idempotency/backoff for LNURL invoice requests; record last request metadata to deduplicate accidental
      double submissions (guarded by `MainViewModel` state in presentation too).
- Observability
    - Add structured logs and a simple in‑memory ring buffer for recent network events (dev builds only) to ease
      debugging.

Deliverables

- DI‑injected `HttpClient`, `Json`, and `AppDispatchers`.
- Shared error mapping helper.
- LNURL/Exchange tests with `MockEngine`.
- Optional cache invalidation for NWC client.

---

### Domain Layer — Refactor Plan (last phase)

This phase may change domain APIs; defer it until presentation/data refactors are complete and stable.

P1 — Consolidation and clarity

- Use case naming and structure
    - Ensure every use case name is verb‑based and scoped (`PayInvoiceUseCase`, `ResolveLightningAddressUseCase`, etc.
      are already good). Group by feature package if helpful.
- Models and invariants
    - `DisplayAmount`, `DisplayCurrency`, and `Amount` types: consider inline/value classes where possible for
      zero‑overhead wrappers and clearer invariants (e.g., millisatoshis as `@JvmInline value class Msats`).
- Error taxonomy
    - `AppError` is clear; add comments linking each error to typical user guidance. Provide a stable mapping surface
      for UI (e.g., error → `ErrorKey`), keeping domain free of presentation.

P2 — Parsing split and test hardening

- `LightningInputParser` and `Bolt11InvoiceParser`
    - Extract pure parsing + validation rules and extend unit tests:
        - Bitcoin URI variants, `lightning=` query param precedence, scheme normalization, LNURL bech32 errors, onion
          address policies, and corner cases (empty/tagged addresses).
- State orchestration
    - If we introduce a domain state machine later (optional), keep it behind an adapter that the `MainViewModel` can
      consume, to avoid a second break.

P3 — Advanced: Feature boundaries

- Consider separating “payment orchestration” domain service from UI completely (e.g., a pure domain
  `PaymentOrchestrator` that takes `Input` and yields `Effect/State`), which `MainViewModel` adapts.
- Add a domain‑level `Clock` or `TimeProvider` for testable timeouts, timestamps, and cache policies.

Deliverables

- Clearer domain models and parsers with comprehensive tests.
- Optional orchestrator behind an adapter API.

---

### Cross‑cutting Improvements

- DI modules (Koin)
    - Split `nwcModule` into feature‑oriented modules: `networkModule`, `repositoryModule`, `useCaseModule`,
      `presentationModule`. Keep a single `startKoin` entry per platform.
    - Bind by interface everywhere (`single<LnurlRepository> { LnurlRepositoryImpl(get(), …) }`). Avoid implicit
      defaults inside implementations.
- Build & CI
    - Add CI to run linters, tests on JVM (and, if feasible, iOS simulator headless tests). Enable caching for
      Gradle/Kotlin.
- Feature flags
    - Prepare basic flags for risky presentation changes (scanner overlay rework) that can be toggled in dev builds.
- Analytics (optional)
    - If desired, define a minimal analytics interface in domain and inject a noop in tests.

---

### Incremental Plan (order of execution)

1) Presentation refactor (no domain API changes)

- Introduce `AppDispatchers` and `rememberInject<T>()` (or similar) helper in presentation only.
- Extract `PaymentFlowController` and `CurrencyStateController` from `MainViewModel` (keep public API identical).
- Create `ScannerOverlay` and `ScannerUiCoordinator`. Migrate gesture/zoom logic out of navigation file, keep behavior.
- Add error presenter mapping `AppError` to `stringResource`.
- Add tests for `MainViewModel` transitions using fakes for use cases.

2) Data refactor (no domain API changes)

- DI‑inject `HttpClient`, `Json`, and `AppDispatchers`; refit `LnurlRepositoryImpl`, `CoinGeckoExchangeRateRepository`.
- Centralize throwable → `AppError` mapping.
- Add `MockEngine` unit tests for LNURL and Exchange.
- Optional: proactive NWC client invalidation on active wallet change.

3) Domain refactor (final, API may change)

- Inline/value classes for amounts (`Msats`, etc.), polish models and invariants.
- Harden parsers and tests; formalize a domain adapter if we choose a state machine/orchestrator.
- Update presentation/data adapters to new domain types only in this step.

Risk mitigation

- Keep all changes behind internal collaborators and adapters until Step 3.
- Preserve public signatures for `MainViewModel`/use cases until the domain phase.
- Add tests before moving logic to catch regressions.

---

### Detailed Checklists (per layer)

Presentation (Step 1)

- [ ] Create `AppDispatchers` (DI) and use it for any `LaunchedEffect`/VM scopes that do IO or CPU work.
- [ ] `rememberInject<T>()` or a small `ViewModelProvider` to replace manual Koin access.
- [ ] Extract collaborators from `MainViewModel`; keep `dispatch()` API and `uiState`/`events` flows.
- [ ] `ScannerOverlay`: encapsulate gesture/zoom; expose `setZoom(fraction)` to `QrScannerController`.
- [ ] Error presenter with map `AppError → stringResource` (in `presentation.common.ErrorMessages`).
- [ ] Add accessibility semantics and `contentDescription` on key UI.

Data (Step 2)

- [ ] `single<HttpClient>`, `single<Json>`, `single<AppDispatchers>` in DI.
- [ ] Update `LnurlRepositoryImpl` to accept injected `HttpClient`, `Json`, `AppDispatchers.io`.
- [ ] Extract common throwable → `AppError` mapper.
- [ ] Tests using Ktor `MockEngine` for LNURL pay params and invoice request (success/error).
- [ ] Optional: invalidate NWC client on wallet change; add light telemetry.

Domain (Step 3)

- [ ] Introduce inline/value classes for amounts and identifiers where helpful.
- [ ] Expand unit tests for `LightningInputParser` and `Bolt11InvoiceParser` (corner cases, onion policies, URI
  variants).
- [ ] Optional orchestrator with adapter to presentation.

---

### Notable Observations (from code)

- `MainViewModel` combines many responsibilities (parsing, currency, LNURL flow, payments) — prime candidate for
  internal decomposition without API changes initially.
- Navigation entries fetch view models via `KoinPlatformTools` within composables. Revisit potential helpers like
  `rememberInject<T>()` after the navigation strategy review concludes.
- `LnurlRepositoryImpl` creates its own `HttpClient`/`Json` by default; DI injection will improve resource reuse and
  testability.
- Network error detection via checking `qualifiedName == "io.ktor.utils.io.errors.IOException"` appears in multiple
  places; centralize this mapping.
- Scanner zoom/gesture logic is tightly coupled with navigation entry; a dedicated overlay component will simplify
  responsibilities and testing.

---

### Definition of Done (per step)

Step 1 (Presentation)

- All presentation tests pass; no changes in domain APIs; UI behaves identically; code coverage increased for
  `MainViewModel`.

Step 2 (Data)

- All repositories use injected `HttpClient`/`Json`/`AppDispatchers`.
- LNURL/Exchange tests cover success and error flows; no domain API changes.

Step 3 (Domain)

- New amount/value classes and parser improvements merged with adapter updates in presentation/data.
- Comprehensive domain tests green; no behavioral regressions in end‑to‑end flow.

---

### Appendix: Suggested small utilities (names illustrative)

- `AppDispatchers(io: CoroutineDispatcher, default: CoroutineDispatcher, main: CoroutineDispatcher)`
- `rememberInject<T>()` or `rememberKoin<T>()`: thin wrapper around Koin lookup for composables.
- `Navigator` facade for navigation calls, backed by `NavController` (defer until navigation review completes).
- `Throwable.toAppError()` in data layer (internal), optionally `AppErrorMapper`.
- `ScannerOverlay` composable + `ScannerUiCoordinator` controller.

This plan keeps the domain stable while we iteratively improve presentation and data. Once both are cleaned up, the
domain refactor can proceed with minimal friction and clear adapter seams for required changes.
