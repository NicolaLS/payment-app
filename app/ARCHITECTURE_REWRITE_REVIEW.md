# Lasr Architecture and Greenfield Rewrite Review

**Document status:** Static architecture audit and rewrite decision record

**Last updated:** 2026-07-12

**Repository scope:** Kotlin Multiplatform app in `app/`

**Companion document:** [`PRD.md`](PRD.md) describes the product behavior this architecture must preserve

## 1. Purpose

This report reviews the current Lasr implementation as input to a greenfield rewrite. It is intentionally more critical than a normal code review: the goal is to retain decisions that encode valuable product or operational knowledge while replacing mechanisms that accumulated coupling, unsafe edge cases, and maintenance cost.

The central conclusion is:

> Lasr's payment-flow reasoning is materially stronger than its architectural mechanics.

The app has already solved several difficult product problems well: an attempt stays bound to the wallet selected when it began; fixed and dynamic payment requests receive different duplicate handling; uncertain payment outcomes are recognized as distinct from definitive failure; NWC connections are reused and tied to lifecycle; and the real E2E environment exercises an actual payment path. Those invariants should become first-class specifications for the rewrite.

The current mechanisms around those ideas are much weaker. Weak internal ownership boundaries, a 1,891-line payment ViewModel, loosely implemented MVI, session-only attempt tracking, secret-bearing navigation arguments, incomplete protocol validation, JSON blobs used as a database, inconsistent error channels, custom lifecycle/scoping infrastructure, and stale CI create avoidable correctness and security risks.

This is a static review, not a penetration test or production telemetry analysis. It examined application, platform, build, test, and E2E code and ran the Android-host shared test suite. Runtime behavior that depends on live providers, OS restoration, device cameras, or release infrastructure still requires focused validation.

## 2. Review method and rating scale

The review covered:

- Module boundaries, dependency direction, domain purity, and feature ownership.
- Payment parsing, confirmation, execution, duplicate detection, pending resolution, and wallet selection.
- Domain types, the custom `Result`/`AppError` model, repositories, and use cases.
- NWC, Blink, LNURL, exchange-rate, persistence, and credential-storage implementations.
- MVI/state ownership, Compose UI contracts, navigation, deep links, accessibility, and adaptive design.
- Koin composition, coroutine ownership, dispatchers, expect/actual abstractions, and native camera code.
- Gradle configuration, release automation, CI, unit tests, and the Maestro/regtest harness.

Ratings use this scale:

| Rating | Meaning | Rewrite action |
|---:|---|---|
| 5 | Strong decision and implementation | Preserve deliberately |
| 4 | Sound foundation with bounded issues | Keep and refine |
| 3 | Mixed; useful idea, costly implementation | Preserve intent, redesign mechanism |
| 2 | Debt or unreliable abstraction | Replace in the rewrite |
| 1 | Critical correctness, security, or operability risk | Fix immediately and do not reproduce |

Ratings are not a measure of developer effort. A low rating often marks a locally reasonable solution that outgrew its original scope.

## 3. Executive scorecard

| Area or decision | Rating | Verdict |
|---|---:|---|
| Kotlin Multiplatform for shared product logic | 4 | Good fit for the product and small native shells; retain if the team can support both native ecosystems |
| Compose Multiplatform UI | 4 | High reuse and consistent visual identity; improve adaptive and accessibility behavior |
| `expect`/`actual` platform boundaries | 4 | Correct direction; inject implementations instead of relying on platform globals |
| Conventional `:shared` KMP module | 4 | Standard and appropriate; the problem is internal ownership/dependency discipline, not the module itself |
| `domain`/`data`/`presentation` folder layering | 3 | Import direction is mostly disciplined, but directories cannot enforce boundaries and domain is not fully pure |
| Repository-port pattern | 3 | Useful at real infrastructure boundaries; overused for trivial preference CRUD and inconsistently named |
| One use case per repository method | 2 | Mostly pass-through ceremony that inflates DI and constructors without adding policy |
| Custom `Result<T>` with `Loading` | 1 | Conflates UI async state with finite operations, conflicts with Kotlin's type, and coexists with several other error channels |
| Global `AppError` hierarchy | 2 | Captures useful categories but mixes domain, provider, diagnostics, and user-facing concerns |
| Money, rate, ID, hash, and credential types | 2 | Raw `Long`, `Double`, and `String` allow unit, precision, identity, and disclosure mistakes |
| Explicit wallet snapshot per payment attempt | 5 | Essential safety invariant; make it a first-class attempt field |
| Provider-neutral payment routing | 3 | Correct capability boundary; mutable active-wallet cache and `runBlocking` weaken it |
| Central Lightning input parser | 4 | Clear typed classification with useful tests; consolidate all parsing and distinguish unsupported variants |
| Custom BOLT11 decoding | 1 | Insufficient for deciding whether to send irreversible funds |
| LNURL flow and validation | 2 | Functional happy path, but protocol binding, URL trust, and callback validation are incomplete |
| NWC connection reuse and lifecycle | 4 | One of the strongest infrastructure decisions; make ownership and cancellation policy explicit |
| Generated Blink GraphQL operations | 4 | Retain generated protocol models; replace error-string heuristics and unmanaged client configuration |
| Settings JSON blobs for wallets/contacts/shortcuts | 2 | Acceptable prototype storage, not a transactional or migratable application database |
| Platform secure-storage intent | 4 | Correct principle and useful native work; narrow it to credentials and define backup/reinstall behavior |
| Current payment MVI/state model | 1 | State is fragmented across flows and hidden mutable fields; contradictory projections already create a frozen-screen path |
| Duplicate and uncertain-payment semantics | 5 | Valuable domain insight; preserve as explicit durable state transitions |
| Pending tracker implementation | 2 | Useful extraction, but in-memory, presentation-coupled, and can turn unknown truth into failure |
| Koin as KMP DI container | 4 | Reasonable choice; the graph and resolution style need redesign |
| Current DI graph and service-locator use | 2 | A 442-line module, hidden lookups, circular callbacks, and unqualified scopes/dispatchers obscure ownership |
| Typed serializable navigation routes | 4 | Retain type safety and nested graph intent; routes must contain identifiers, never credentials |
| Global channel-based navigation/deep-link buses | 1 | Lossy, timing-dependent hidden state with onboarding and restoration hazards |
| Design system, resources, themes, and previews | 4 | Solid reusable foundation and recognizable visual language |
| Adaptive UI and accessibility | 2 | Fixed dimensions, orientation workarounds, incomplete labels, and animation policy need systematic redesign |
| Cross-platform scanner abstraction | 3 | Valuable capability boundary, but native implementations and lifecycle protocol are oversized and weakly stateful |
| Shared unit-test suite | 4 | Strong regression coverage around core payment behavior; important gaps remain at boundaries and policies |
| Local real-payment E2E harness | 5 | Expensive operational knowledge worth preserving and expanding |
| Test breadth across repositories/platforms | 2 | Coverage is concentrated around the god ViewModel; protocol, persistence, DI, and restoration risks are under-tested |
| CI configuration | 1 | Workflows reference a removed `:composeApp` module and therefore do not provide the intended gate |
| Release automation | 3 | Captures real signing/packaging needs, but is complex, machine-specific, and split across inconsistent version sources |
| Dependency reproducibility | 2 | A production snapshot dependency and absent locking/verification weaken reproducibility |
| Privacy restraint | 4 | Little analytics and secure-storage intent are positive; secret-bearing state and raw error details undermine it |
| Operability and observability | 1 | No coherent redacted diagnostics, attempt correlation, or actionable production health signal |

## 4. Decisions to preserve as requirements

These are not merely implementation details. They are hard-won product invariants that the rewrite should turn into documented architecture decisions and executable tests.

### 4.1 A payment attempt owns its wallet target

The current app snapshots a concrete `WalletPaymentTarget` before submitting a payment. Changing the globally active wallet while an attempt is pending therefore does not silently redirect that attempt. This is excellent and must survive.

In the rewrite, persist `walletId` and provider identity on the attempt before any external request. Every provider call and lookup must receive that identity explicitly. Never read a mutable “current wallet” inside the execution path.

### 4.2 Unknown is not failed

The code recognizes that timeouts and inconclusive lookups do not prove a Lightning payment failed. That distinction protects users from duplicate payments. Preserve `Unknown` or `Unconfirmed` as a first-class terminal-or-reconcilable state rather than modeling it as an exception message.

The implementation does not consistently uphold the principle today: after a lookup window, the pending tracker can store an inconclusive outcome as failure. The rewrite should preserve the semantic insight and replace the state mechanism.

### 4.3 Fixed and dynamic requests have different duplicate semantics

A fixed BOLT11 invoice can settle only once, while a Lightning address or LNURL-pay request can intentionally generate a new invoice for another payment. The current flow makes this distinction and provides retry/view choices. This is sophisticated product reasoning and belongs in the domain state machine and E2E suite.

### 4.4 Input normalization is centralized

`LightningInputParser` gives the application a typed interpretation of BOLT11, LNURL, Lightning addresses, Bitcoin URIs with a Lightning parameter, NWC URIs, BOLT12, and unsupported on-chain input. Keep one normalization boundary and one set of conformance fixtures. Do not allow app links, QR scanning, and pasted text to grow independent parsers.

### 4.5 NWC clients are reused and lifecycle-aware

`NwcConnectionManager` caches connections, serializes creation with a mutex, proactively connects, and evicts resources on lifecycle/removal events. Reusing relay connections is both a latency and resource-management win. Preserve the concept, key it by opaque `WalletId`, and specify what happens to in-flight work when the app backgrounds.

### 4.6 Native security, camera, and lifecycle remain platform responsibilities

The `expect`/`actual` direction is appropriate. Compose and shared application logic should not branch on platform. Native implementations should remain replaceable and testable through narrow ports, and it is acceptable for complex iOS platform code to live in Swift when that reduces Kotlin/Native interop complexity.

### 4.7 Generated provider schemas beat handwritten wire models

Blink uses Apollo-generated GraphQL types and checked-in operations. Retain schema generation and compile-time wire compatibility. Improve transport construction and provider-error mapping around it.

### 4.8 The real regtest E2E environment is an architectural asset

The Maestro flows and local NWC/Lightning environment test more than UI rendering: they protect integration knowledge across the app, wallet protocol, relay, and payment backend. Move this harness forward early, even before feature parity, so the rewrite can validate vertical slices.

## 5. Current architecture

The root project contains `:shared` and `:androidApp`; iOS is an Xcode shell. Nearly all domain, infrastructure, presentation, navigation, resources, and composition code lives in `:shared`.

```text
androidApp / iosApp
        |
        v
shared
  navigation + presentation + Compose UI
        |
        v
  domain models + use cases + repository interfaces
        ^
        |
  data implementations + provider clients
        ^
        |
  platform expect/actual implementations

All of the above are wired by one large Koin module.
```

The conventional dependency direction is mostly respected at the package-import level: domain generally does not import data or presentation, and data generally does not import presentation. A single `:shared` module is a normal KMP structure and is not itself a defect. The actual concerns are weak feature ownership, broad dependency visibility, service-location access to the same composition root, and no automated enforcement of package boundaries.

Scale signals make the concentration visible:

- Common production Kotlin is approximately 22,861 lines; common tests are approximately 5,574 lines.
- `MainViewModel.kt` is 1,891 lines.
- `SettingsNavigation.kt` is 1,114 lines; `PayNavigation.kt` is 732 lines.
- `PaymentsSettingsScreen.kt` is 940 lines and its ViewModel is 644 lines.
- `BlinkApiClient.kt` is 561 lines; `PendingPaymentTracker.kt` is 458 lines.
- `NwcModule.kt` is 442 lines and composes nearly the whole app.
- Native scanner implementations are approximately 586 Android lines and 1,198 iOS lines.
- There are 49 use-case classes across roughly 37 files, totaling only about 531 lines, indicating that many are thin delegation wrappers.

Large files are not automatically bad. Here, they correlate with multiple responsibilities, high constructor cardinality, dead state, and changes that require knowledge of unrelated flows.

## 6. Boundary and modularity review

### What works

- The package taxonomy communicates intent: `domain`, `data`, `presentation`, `navigation`, `di`, and `platform` are easy to find.
- Repository interfaces usually point inward and keep provider SDK types out of most presentation code.
- Native behavior is normally exposed through common interfaces or expect/actual declarations.
- Private persisted DTOs are often mapped to domain objects rather than becoming the public model.

### What does not

The architecture is enforced by convention only. A new import can bypass a layer without Gradle detecting it. At the current scale, broad dependency visibility plus service location makes that easy; this is a package/ownership problem first and only becomes a Gradle modularization problem if simpler enforcement is insufficient.

The domain layer is not pure:

- `CurrencyCatalog` imports Compose resource types and generated resources.
- `AmountFormatter` exposes composable behavior.
- `SetWalletConnectionUseCase` imports the NWC SDK's connection URI type.

Those dependencies make domain tests and reuse depend on UI or provider technology. In a rewrite, core models and policies should be pure Kotlin with no Compose, Koin, Ktor, Apollo, Settings, or provider SDK dependency.

The repository abstraction is also too broad. A remote payment backend, local relational store, preference key, and provider account lookup are all described with similar “repository + one-method use case” patterns. This hides the meaningful differences in reliability and transaction semantics.

### Recommendation

Keep the conventional `:shared` module initially and make its logical boundaries explicit:

```text
:shared
  core/model       pure values and policies
  application      workflows, state machines, ports
  data             storage and provider implementations
  presentation     feature-owned UI and ViewModels
  navigation       typed routes and graph composition
  di               composition only
  androidMain / iosMain platform implementations

:androidApp / iosApp     thin platform shells
```

Add architecture tests or dependency rules so core and application packages cannot import UI or infrastructure. Split `:sharedLogic` from `:sharedUI`, or extract a provider/feature module, only when independent reuse, ownership, build performance, or compile-time enforcement provides a demonstrated benefit. Do not modularize speculatively.

## 7. Domain model review

### 7.1 The custom `Result<T>` / response design — rating 1/5

The current type is:

```kotlin
sealed class Result<out T> {
    data object Loading : Result<Nothing>()
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val error: AppError, val cause: Throwable? = null) : Result<Nothing>()
}
```

#### What it gets right

- Expected errors are made explicit instead of every call returning nullable data.
- Success and failure are exhaustively matchable.
- A cause can be retained for diagnostics.

#### Why it should not survive the rewrite

1. Repository methods are generally finite `suspend` operations. They return once, so `Loading` is not a value they emit. Every caller must nevertheless include a meaningless branch. Loading belongs to an observable UI or workflow state, not an operation result.
2. The name shadows `kotlin.Result`, increasing import and reading ambiguity.
3. It has no useful composition operations such as `map`, `flatMap`, or `fold`, so orchestration becomes repetitive branching.
4. `Throwable` crosses into the domain result even though expected domain failures and diagnostic exceptions have different responsibilities.
5. The app also throws `AppErrorException`, throws `IllegalArgumentException` via `require`, returns `Boolean`/nullable values, uses `PaymentLookupResult`, uses `PayInvoiceRequestState`, and defines Blink-specific results. Callers cannot infer the failure contract from a function category.
6. Some data implementations catch `Throwable` and convert it into this result, including coroutine cancellation. Swallowing `CancellationException` violates structured concurrency and can make cancelled screens continue as if an ordinary failure occurred.

#### Rewrite rule

- A finite operation that can fail in expected business ways returns either a domain-specific sealed outcome or a small two-case `Outcome<Value, ExpectedFailure>`.
- A finite operation with no expected failure returns its value and lets unexpected exceptions reach the application boundary.
- Cancellation is control flow and is always rethrown.
- Loading/progress belongs in `PayUiState`, `Async<T>`, or the workflow state machine, never in the repository result.
- Diagnostic exceptions are logged in redacted form at a boundary; they are not formatted directly for the user.

Do not automatically replace the custom type with `kotlin.Result`: Kotlin's type only models `Throwable` failure and is a poor fit for typed, expected payment outcomes. A small local `Outcome` is sufficient; adopting Arrow solely for `Either` would add more architecture than this app currently needs.

Example direction:

```kotlin
sealed interface SubmitPaymentOutcome {
    data class Settled(val settlement: Settlement) : SubmitPaymentOutcome
    data class AlreadyPaid(val settlement: Settlement?) : SubmitPaymentOutcome
    data class Pending(val paymentHash: PaymentHash?) : SubmitPaymentOutcome
    data class Rejected(val reason: PaymentRejection) : SubmitPaymentOutcome
}
```

### 7.2 Error taxonomy — rating 2/5

`AppError` contains network state, relay transport, authentication, malformed user input, LNURL protocol failure, payment truth, provider-specific Blink errors, and `Unexpected(message)`. Its documentation calls it “user-visible” even though several variants retain raw provider messages or codes.

This creates three coupled concerns:

- Business truth: already paid, rejected, pending, unknown.
- Recoverability/control: reconnect, retry, request new invoice, choose wallet.
- Diagnostics: HTTP status, provider error code, exception, relay URL, correlation ID.

The presentation layer then duplicates the `AppError`-to-resource mapping in composable and suspend forms. Raw messages can reach UI, and provider details are either lost or risk disclosure.

Define small error families by bounded context, for example `ConnectWalletFailure`, `ResolvePaymentRequestFailure`, `PaymentRejection`, and `RateFailure`. Map them once in presentation to a resource-backed `UiText`. Keep redacted diagnostic context separately. “Unknown payment truth” should be an attempt state, not a generic error.

### 7.3 Money and exchange rates — rating 2/5

The app uses raw `Long` for millisatoshis, satoshis, fiat minor units, timestamps, and IDs. Fiat conversion uses `Double`. A reader must infer units from names, and the compiler cannot prevent passing sats where msats are expected. Floating-point conversion can introduce rounding artifacts, and unchecked multiplication/round-up paths can overflow.

`DisplayAmount` is an improvement because it pairs a minor-unit value with a currency, but BTC and SAT still depend on implicit storage semantics. Fee absence is sometimes projected as zero, incorrectly asserting that a fee is known to be free.

Use value types and checked arithmetic:

```kotlin
@JvmInline value class MilliSatoshi(val value: Long)
@JvmInline value class Satoshi(val value: Long)
@JvmInline value class PaymentHash(val hex: String)
@JvmInline value class WalletId(val value: String)
@JvmInline value class CurrencyCode(val value: String)

data class ExchangeRate(
    val quote: CurrencyCode,
    val minorUnitsPerBtc: BigInteger,
    val observedAt: Instant,
    val source: RateSource,
)
```

Centralize conversions with explicit rounding modes, overflow checks, finite/positive rate validation, and freshness policy. Keep `fee: MilliSatoshi?` nullable all the way to UI.

### 7.4 Identity, byte, and credential modeling — rating 2/5

Wallet IDs, wallet public keys, payment hashes, NWC URIs, Blink API keys, contact IDs, and shortcut IDs are mostly strings. This permits accidental interchange and accidental `toString()` disclosure. `Bolt11Memo.HashOnly(ByteArray)` also inherits array identity equality rather than content equality.

Use opaque value objects with construction validation and safe string representations. Secret types must redact `toString()` and should ideally never enter general domain/UI models at all. Immutable byte/hex types must implement content equality.

Generate contact, shortcut, and attempt identities with an injected UUID/ULID source. Hashing normalized addresses or combining the wall clock with map size is not a robust identity strategy.

### 7.5 Wallet model — rating 2/5

`WalletConnection` is effectively a tagged union implemented as one data class with a `WalletType`, an NWC URI, a field named `walletPublicKey`, provider metadata, and defaults that permit invalid combinations. For Blink, “public key” is used for a different kind of provider identifier. `WalletPaymentTarget.Nwc` carries the complete secret URI.

Separate identity, public profile, provider configuration, and credentials:

```kotlin
data class WalletProfile(
    val id: WalletId,
    val provider: WalletProvider,
    val alias: String,
    val publicMetadata: WalletMetadata,
    val status: WalletStatus,
)

sealed interface ProviderConfiguration {
    data class Nwc(val relays: List<RelayUrl>, val walletPubkey: PublicKey) : ProviderConfiguration
    data class Blink(val accountId: BlinkAccountId) : ProviderConfiguration
}
```

The credential vault stores secret material by `WalletId`; navigation, UI state, logs, saved state, and durable public records only carry the ID. `WalletStatus.NeedsReauthentication` is safer than automatically deleting a profile on a heuristically classified authentication failure.

### 7.6 Protocol parsing and trust — rating 1/5 for payment authorization

The handwritten BOLT11 parser extracts useful display fields, but it does not establish all facts required before sending money: signature validity, network, expiry, required tags, and full description/description-hash semantics are not comprehensively enforced. Parsing a plausible amount is not equivalent to validating a payable invoice.

The LNURL flow checks that the returned invoice amount matches, which is good. However, hash-only memo validation accepts the result without computing and comparing the SHA-256 of LNURL metadata. URL and callback trust are also too permissive: arbitrary schemes/hosts and local/private destinations need an explicit policy, especially because resolving an input can otherwise become an SSRF path.

Use an audited, maintained BOLT11 implementation or rigorously conformance-test a dedicated parser module. The application must validate, before submission:

- Signature and network.
- Expiry at an injected clock.
- Required fields and exact amount semantics.
- LNURL invoice amount and `description_hash == SHA256(metadataRaw)` when applicable.
- Callback scheme/host policy, redirects, response size, status, content type, and private-network restrictions.
- Comment length/encoding and any payer-data requirements.

Classify LNURL-withdraw as explicitly unsupported rather than allowing it to fall through a pay-oriented type. Maintain malicious and boundary fixtures, not only happy-path invoices.

## 8. Application and payment-flow review

### 8.1 MVI is named but not actually the source of truth — rating 1/5

`MainViewModel` has a `MainUiState`, but complete state is spread across that flow, session transactions, a navigation target, wallet/contact flows, counters, and at least a dozen private mutable variables and maps. A process snapshot cannot reconstruct the workflow, and impossible combinations are representable.

`dispatch` launches a coroutine for each intent instead of serializing reductions. Admission booleans and maps compensate for concurrency, but suspended operations can still interleave. The constructor has more than twenty dependencies, several nullable only to make tests practical. `MainUiState.Detected` appears to be dead state.

A concrete defect demonstrates the architectural issue:

- `PayNavigation` projects `Success` and `Error` to `Active` for the displayed home state.
- It decides whether the scanner runs from the original state.
- Pre-attempt failures such as invalid input or a missing wallet can emit `Error` without navigating to a transaction screen.
- `MainScreen` therefore renders the non-dismissible active surface while the scanner remains stopped from the hidden error state.

The user can see an apparently active but frozen payment screen until restart. The defect exists because two consumers derive contradictory behavior from different projections of incomplete state.

### Rewrite design

Use one serialized command processor per payment session and make state explicit:

```text
Payment draft (ephemeral UI)
Idle -> ResolvingInput -> AwaitingAmount -> AwaitingConfirmation -> Submitting
  ^          |                 |                    |                 |
  +----------+-----------------+--------------------+------ recoverable error

Payment attempt (durable truth)
Created -> Submitted -> Pending -> Settled
                        |          AlreadyPaid
                        |          Rejected
                        +--------> Unknown -> reconciliation
```

`PayViewModel` should expose one complete `PayUiState` and accept one `PayAction` stream. An actor/channel or carefully scoped mutex serializes commands. A `PaymentCoordinator` owns attempts and provider calls; the ViewModel owns presentation state, not payment truth. Remove UI-only “state remapping” and derive scanner, dismissibility, navigation, and controls from the same state.

### 8.2 Payment attempt durability — rating 2/5

Session transactions and pending lookups are held in `MainViewModel` memory. Clearing the ViewModel cancels verification. An unconfirmed payment without a hash can wait indefinitely; an inconclusive hash lookup can be recorded as failure after a fixed window. App death, graph replacement, and OS process recreation therefore lose exactly the state that protects against duplicate payment.

Persist an attempt before the first external mutation:

```text
AttemptId
walletId + provider
normalized request fingerprint
invoice/payment hash when known
amount + currency/rate snapshot
origin and confirmation decision
created/submitted/lastChecked timestamps
provider request correlation
current state + state-transition history
```

Reconcile non-final attempts on launch and foreground, with platform background work only as a best-effort enhancement. A provider's inability to look up status must not be converted into definitive failure. Use idempotency/fingerprint rules appropriate to fixed versus dynamic requests.

The current tracker also has a replacement-job race: completion removes a verification job by ID without proving it is still the registered job. A cancelled old job can remove its replacement. Compare job identity or centralize work in one actor.

### 8.3 Payment routing and providers — rating 3/5

`PaymentService` is a useful provider-neutral seam, but it seeds mutable active-wallet state with `runBlocking` in its constructor and exposes execution paths that can use that cache. Constructor blocking is unsafe and hides I/O in object creation. Blink also retains convenience active-wallet APIs even though normal routing supplies a target, creating two sources of truth.

Replace repository/provider routing with explicit backends:

```kotlin
interface PaymentBackend {
    suspend fun submit(target: WalletId, invoice: ValidatedInvoice): SubmitPaymentOutcome
    suspend fun lookup(target: WalletId, hash: PaymentHash): LookupPaymentOutcome
}

interface PaymentBackendRegistry {
    fun backendFor(provider: WalletProvider): PaymentBackend
}
```

The application coordinator resolves the selected profile once, records it on the attempt, and passes its ID on every call. Provider implementations resolve credentials at the last responsible moment through the vault.

### 8.4 Confirmation policy — rating 3/5

Extracting `ShouldConfirmPaymentUseCase` is the right idea because confirmation is safety policy, not UI decoration. Current semantics are ambiguous:

- Manual-entry configuration can bypass every other policy, including “Always”.
- Millisatoshis are floored to sats before comparison.
- Code uses `>= threshold`, while UI language around “above” and “up to” suggests a different boundary.
- The exact-threshold case lacks a dedicated test.

Specify the policy as a decision table using the final post-rounding amount, source, deep-link trust, shortcut setting, and configured threshold. Deep links should continue to force confirmation unless an explicit future security decision changes that rule. Test one millisatoshi below, exactly at, and above every boundary; also test overflow and rate changes between preview and submission.

### 8.5 Use cases — rating 2/5 overall

Use cases are valuable when they name a workflow or isolate policy: resolve payment input, connect wallet with compensation, decide confirmation, submit attempt, reconcile attempt, import contacts, or remove wallet and credentials. Most current use cases are eight-to-ten-line wrappers around one repository method. They add constructor arguments and DI registrations without adding invariants.

Keep application services at meaningful transaction/workflow boundaries. For simple observable preferences and CRUD, inject a small typed store or cohesive feature port. Do not reproduce a class-per-method rule.

## 9. Data and infrastructure review

### 9.1 NWC integration — rating 4/5 concept, 2/5 hardening

This is a good example of an implementation whose central decision should survive. Client reuse, a mutex around creation, foreground prewarming, explicit pay/lookup timeouts, failure mapping, and background/removal cleanup all address real Nostr relay behavior.

Refinements needed:

- The cache is keyed or evicted using raw credential URI material. Key it by `WalletId`; resolve credentials only inside the session factory.
- Background `disconnectAll()` can close a client while a payment is in flight or race with a replacement created after the cache was cleared. Introduce request leases/reference counting and close only idle sessions, or explicitly mark affected attempts unknown before shutdown.
- Prewarming exposes presence to a relay and consumes battery/network. Keep it only as a documented latency/privacy trade-off, preferably based on recent wallet use.
- Capability/encryption metadata lacks a fetched timestamp and safe refresh transaction. The existing TODO correctly recognizes that a stale metadata write must never reactivate a removed wallet.
- Missing encryption metadata can fall back to legacy NIP-04. Downgrade behavior must be explicit, observable, and product-approved rather than an incidental default.
- The concrete NWC client prevents meaningful adapter tests. Introduce a narrow `NwcSession` interface and provider contract suite.
- Provider “cancelled” must become coroutine cancellation, not `Unexpected`.

The NWC adapter's verify-after-timeout behavior is excellent payment-safety reasoning. Preserve it in the attempt coordinator rather than hiding it inside an adapter-specific request handle.

Production currently uses `io.github.nicolals:nwc-kmp:0.3.2-SNAPSHOT` from a snapshot repository. Shipping against mutable coordinates makes builds non-reproducible and can change protocol behavior without a source change. Publish/pin a stable version or immutable commit artifact, enable dependency verification/locking, and run the NWC contract suite against exactly that artifact.

### 9.2 Blink integration — rating 3/5

#### Strong decisions

- Generated Apollo operations and internal generated types establish a sound API boundary.
- Credentials are attached per request, supporting concurrent use of more than one account.
- Status mapping distinguishes success, pending, already paid, and failure.
- Already-paid is returned as a successful distinct outcome rather than a generic rejection.
- Existing tests cover a useful set of response and concurrent-target cases.

#### Weak decisions

- Error mapping searches English code/message substrings. This is brittle across provider wording and localization. Prefer stable GraphQL extension codes or payload enums and version the mapper.
- HTTP errors other than selected 401/429 cases can collapse into `NetworkUnavailable`, so a provider rejection may be misclassified as transport uncertainty.
- Timeout classification recursively searches exception-message text.
- Only the first GraphQL/payload error is considered.
- Apollo client construction has no injected application timeout, retry, telemetry, or lifecycle policy.
- Contact import deliberately uses a deprecated field and hard-codes a provider domain.
- Provisioning writes API key and remote wallet ID before the wallet profile. A later storage failure leaves orphaned credentials.
- An invalid-key heuristic can automatically delete the wallet in one execution path. Authentication trouble should normally mark `NeedsReauthentication`, preserve alias/configuration, and let the user explicitly remove it.
- Cached default wallet identity lacks freshness/version semantics.
- Suspend and manual-request execution paths perform different validation.

Create one transactional connect-wallet workflow with compensation:

1. Validate the supplied credential and required scopes.
2. Resolve the provider account and capabilities.
3. Create a new `WalletId` and public profile transaction.
4. Store the credential under that ID.
5. Commit availability/active selection only after both stores succeed.
6. Compensate or leave a resumable provisioning record if any step fails.

The required Blink scopes should be based on actual pay, lookup, default-wallet, and optional contact-import behavior—not only a broad “write” check.

### 9.3 LNURL and HTTP trust boundary — rating 2/5

The Ktor-based separation and timeout/retry starting point are useful, and exact amount comparison after invoice retrieval is correct. The complete protocol workflow is nevertheless split across the repository and ViewModel, so neither layer guarantees that a returned invoice is safe to submit.

Build a single application-level `LnurlPayCoordinator` that owns endpoint policy, parameter parsing, amount/comment validation, invoice request, audited BOLT11 decoding, and metadata-hash binding. It must return a fully validated command, never a raw invoice that still needs presentation-layer checks.

Networking rules should include:

- Explicitly check HTTP status and content type.
- Bound response and metadata sizes.
- Parse protocol integers as exact integers; never accept a JSON floating value and truncate it to `Long`.
- Reject userinfo, unsupported schemes, malformed redirects, and production loopback/private/link-local callbacks.
- Define whether redirects may change host and scheme.
- Support `.onion` only through a real Tor-capable transport; otherwise reject it honestly. Converting onion URLs to cleartext HTTP without such transport is not support.
- Do not automatically retry non-idempotent mutations. Treat an LNURL callback that creates an invoice according to a documented idempotency policy.
- Inject and own HTTP clients at the composition root; do not let repositories silently allocate unclosed defaults.

Network-connectivity preflight is a user-experience hint, not proof. VPNs, captive portals, and monitor initialization can cause false signals. Always let the real request establish the authoritative transport outcome.

### 9.4 Exchange-rate architecture — rating 2/5

There are multiple overlapping freshness/caching decisions: the repository cache, `CurrencyManager`, and secondary-preview code can each fetch or decide staleness. This risks inconsistent displayed and submitted amounts.

One rate service should own:

- Source, observation time, monotonic cache age, and a clear freshness/stale-use policy.
- Single-flight refresh so concurrent misses do not duplicate calls.
- Bounded stale-while-revalidate behavior for display, with a stricter explicit policy for a payment-affecting conversion.
- Positive/finite/exact parsing and typed provider failures.
- A rate snapshot recorded on every fiat-denominated draft/attempt.

Do not silently change an unavailable configured fiat currency to SAT while leaving the stored preference unchanged. Show an unavailable state and let the user choose a fallback.

### 9.5 Persistence model — rating 2/5

Private stored DTOs mapped to domain objects are a good compatibility seam. JSON is configured to tolerate unknown keys, and some legacy fields have defaults. Preserve explicit persistence models and mapping, but replace the storage engine and migration strategy.

Wallets, contacts, and shortcuts are currently whole JSON documents in `Settings`. This has several failure modes:

- Read-transform-write operations have no mutex/transaction; concurrent updates can overwrite one another.
- Wallet list and active wallet are separate writes, allowing torn state after crash.
- Corrupt JSON silently becomes an empty aggregate and can orphan credentials.
- There is no explicit schema version or migration ledger.
- Unknown future wallet types can be reinterpreted unsafely during downgrade rather than quarantined.
- Cleanup and persistence are not one transaction; failures can leave removed profiles with secrets or stored profiles without credentials.
- Bulk JSON prevents indexed lookup, per-record recovery, foreign keys, and durable payment attempts.
- Contact and shortcut IDs are based on hash/time constructions with avoidable collision risk.
- Shortcut records duplicate contact data and require manual cascade/normalization.
- Localized presentation text such as `"Pay <name>"` is constructed and persisted in the data layer.

Use a KMP relational database such as SQLDelight for public wallet profiles, contacts, shortcuts, and attempts. Use database transactions, foreign keys, migration tests, per-record validation/quarantine, and injected `Clock`/`IdGenerator`. Preferences remain a small typed store, preferably versioned per feature.

### 9.6 Credential storage — rating 4/5 intent, 2/5 boundary

Android's AES-GCM/Keystore approach is sound at the primitive level, and iOS Keychain is the correct native facility for wallet secrets. The current abstraction presents an entire general-purpose `Settings` instance as secure, however:

- Android overrides secure behavior only for strings; primitive settings can silently use ordinary storage.
- The same instance backs credentials, wallet state, contacts, shortcuts, and preferences.
- On iOS, this puts growing contacts JSON and ordinary preferences in a Keychain item, which has inappropriate size, access, backup, and uninstall semantics.
- Android ciphertext is not bound to its setting key as associated data, so values are not cryptographically tied to their logical name.
- Android allows backup without explicit extraction rules; restored ciphertext may not have its Keystore key, while other state may restore independently.
- iOS Keychain can survive uninstall, so reinstall behavior differs from Android and may unexpectedly retain personal data.
- There is no explicit key rotation, invalidation recovery, reset-all, or revoke-all policy.

Expose a narrow `CredentialVault` that only stores provider secrets by `WalletId`. Store public application data elsewhere. Bind ciphertext to the credential key/record identity, handle invalidated keys explicitly, define iOS accessibility class, and document backup, uninstall, migration, reset, and rotation behavior. Prefer an audited platform/library wrapper over expanding custom general-purpose cryptography.

## 10. Dependency injection, lifecycle, and concurrency

### 10.1 Koin choice — rating 4/5; current composition — rating 2/5

Koin is a reasonable pragmatic DI choice for a Compose Multiplatform application of this size. Replacing it with another container will not fix hidden dependencies by itself.

The current 442-line `nwcModule` wires far more than NWC. It registers storage, network, all providers, domain services, use cases, presentation controllers, and ViewModels. Direct calls to `KoinPlatformTools.defaultContext().get()` throughout app/navigation/composable code turn the container into a service locator. A wallet-removal callback resolves `NwcConnectionManager` through Koin, forming a hidden circular dependency with wallet settings.

Use Koin only at composition boundaries:

- Separate Koin definition groups such as `platformModule`, `storageModule`, `networkModule`, `walletModule`, `paymentModule`, and feature modules; these do not need to be separate Gradle modules.
- Constructor injection everywhere below root/route entry points.
- Explicit feature factories and Koin Compose/ViewModel integration.
- A graph-verification test that creates every production component for Android and iOS variants where feasible.
- No container access from domain/application classes and no callback that resolves a dependency later.

Replace the circular removal callback with a `WalletRegistry` application workflow that transactionally coordinates profile, credential, connection-session, active-selection, and attempt invariants.

### 10.2 Coroutine ownership and dispatchers — rating 2/5

The graph creates an application `CoroutineScope(SupervisorJob() + Dispatchers.Default)` and a separate main-scoped `CurrencyManager`; neither has an explicit close owner. Presentation objects manually create/clear scopes, and one unqualified `CoroutineDispatcher` is injected into both CPU work and UI state owners. This obscures thread assumptions and can cause multithreaded mutation races.

Use:

```kotlin
data class AppDispatchers(
    val main: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val io: CoroutineDispatcher,
)
```

Provide one application scope owned and closed by the application lifecycle, real common AndroidX `ViewModel`/`viewModelScope` where supported, and injected scopes only for genuinely app-lifetime actors. Suspending APIs should normally inherit the caller's context; adapters switch context only for blocking native work.

Every broad `catch` and `runCatching` around suspension must be audited for cancellation. Catch and rethrow `CancellationException` first. Add cancellation propagation to provider contract tests.

### 10.3 Custom retained instances — rating 2/5

`rememberRetainedInstance` uses platform-specific holders to make plain classes behave like ViewModels. The idea addresses a real KMP lifecycle gap, but duplicates lifecycle machinery and encourages manual `clear()`/scope ownership. Prefer common AndroidX lifecycle ViewModels with Koin integration and `SavedStateHandle` for non-sensitive restoration, or an explicit presenter lifecycle abstraction if common ViewModels cannot meet an iOS constraint. Do not invent two overlapping lifecycles.

## 11. Navigation, deep links, and feature ownership

### 11.1 Typed routes — rating 4/5

Serializable typed routes and nested graphs are a strong foundation. The Pay graph deliberately shares a payment owner across transaction detail and home, which encodes a useful session-continuity requirement.

Routes should contain only stable non-sensitive identifiers and small display-independent options. In particular, `ConnectWallet` currently carries a complete NWC URI or `secretHex` and reconstructs the credential from route state. NWC URIs contain client secrets; route arguments can be serialized into back-stack/saved state, printed through generated data-class methods, or captured in diagnostics. This is a P0 issue.

Use an ephemeral `SensitiveDraftStore`: the app-link/scanner boundary stores the parsed credential in memory under a random one-time token, and the route carries only that token. Consume and wipe it on entry. For restoration after process death, require secure re-entry rather than persisting the secret in navigation state.

### 11.2 Navigation owns business side effects — rating 2/5

The connect-wallet navigation code infers where the flow originated by attempting to pop a settings route. This can misclassify an NWC link/QR opened from Pay or externally as onboarding, mark onboarding complete, and create another Pay graph. A fresh-user NWC deep link can also enter connection without passing the intended onboarding/risk agreement.

Make origin an explicit safe enum/route field and let a connect-wallet application workflow decide completion. The root `AppCoordinator` owns onboarding eligibility and securely queues a pending app link until required consent is complete. Navigation renders the resulting destination; it does not write business completion state.

### 11.3 Global event channels — rating 1/5

Singleton channels for deep links, payment deep links, and donation navigation use bounded/drop-oldest semantics and depend on collector timing. They duplicate the app-to-payment handoff, hide ownership, drop bursts, and are difficult to restore or test.

Replace them with an injected `AppLinkInbox` and root coordinator. The coordinator parses once, applies onboarding/security policy, and delivers a typed command to the active feature. Ordinary in-app actions such as donation should call the feature action directly, not travel through a global bus.

### 11.4 Navigation/file concentration — rating 2/5

`SettingsNavigation.kt` owns roughly fifteen destinations plus state resolution and DI in more than 1,100 lines. `PayNavigation.kt` combines graph ownership, camera lifecycle, gesture behavior, payment state projection, and destination rendering. Split feature-owned graph builders from route entry adapters and screens. A route entry may acquire a ViewModel and translate lifecycle/navigation events; it should not reimplement domain state.

## 12. Presentation and product design implementation

### 12.1 What works well

- Compose Multiplatform fits a branded, shared-flow app with thin native shells.
- Material 3 theming, typed resources, bundled fonts, light/dark support, and English/German/Spanish resources form a solid design-system base.
- Screens are often state-hoisted and include previews.
- Stable Compose test tags support Maestro automation.
- The scanner-first visual identity and animated hero differentiate the product.
- Dedicated amount input/controllers isolate some complex formatting behavior from composables.

### 12.2 UI contracts — rating 2/5

`MainScreen` receives roughly 39 parameters. This makes it easy for a callback or state projection to be unused or inconsistent and hard to add variants without changing every caller. Some contact editor/intents and navigation callbacks appear dead in the actual bottom-sheet flow.

Use one immutable `PayUiState` plus `onAction(PayAction)`. Subcomponents can receive smaller derived state/action interfaces. Keep effects explicit and keyed: navigation, permission request, haptic, and transient notification are effects; payment truth and input mode are state.

### 12.3 Adaptive layout and orientation — rating 2/5

The payment hero consumes a fixed proportion, a contacts sheet uses a fixed height, and Android employs a hidden zero-size view/orientation listener to force portrait on compact layouts. These are fragile across tablets, foldables, split screen, landscape, accessibility font scale, and future desktop targets.

Use window size classes and adaptive layout breakpoints. Define scanner, amount, confirmation, and contact arrangements for compact portrait, compact landscape, medium, and expanded. Respect user rotation unless there is a narrowly justified camera constraint. Test at large font and display scaling.

### 12.4 Accessibility — rating 2/5

Some null content descriptions are correctly decorative inside labeled controls, but icon-only add/delete actions and custom animated/tap surfaces need a systematic audit. Whole-screen tap-to-dismiss behavior can be undiscoverable and can conflict with assistive input.

The rewrite needs:

- Semantic labels, roles, selected/disabled states, and state descriptions.
- Minimum touch targets and logical traversal/focus order.
- Dynamic type/large-font layouts without clipping.
- Screen-reader announcements for detected, confirming, submitting, pending, settled, failed, and unknown transitions.
- Contrast tests for contact tag colors and all theme variants.
- An explicit visible dismissal/action path even if a tap gesture remains as a shortcut.
- Reduced-motion support and behavior when system animator duration is disabled.

### 12.5 Animation and scanner surface — rating 3/5

The roughly 400-line hero animation is distinctive, but multiple infinite animations around a payment-critical screen deserve isolation and performance budgets. Keep the visual identity, move the effect behind a small composable contract, pause it offscreen/background, honor reduced motion, and benchmark recomposition, CPU/GPU, thermal, and camera coexistence on lower-end devices.

### 12.6 Localization and UI text — rating 3/5

Typed Compose resources are good. The abstraction leaks in both directions: domain models import UI resource types, while the pending tracker and data repositories construct hard-coded English strings. `ErrorMessages.kt` repeats the same large error mapping for composable and suspend contexts.

Domain/application return semantic values. Presentation performs one pure mapping to `UiText.Resource` or controlled text. Never persist a localized fallback title; persist either the user's custom title or enough semantic data to localize at render time.

Formatting also needs consolidation. Amount parsing/formatting is duplicated in several ViewModels despite a locale-aware formatter, count strings do not consistently use plural resources, keypad decimal/grouping characters are hard-coded, and both platform time formatters force `HH:mm` rather than respecting the user's 12/24-hour preference. Put number/time formatting behind locale-aware presentation services and test every supported locale plus RTL resilience even if RTL is not yet shipped.

### 12.7 Color and design tokens — rating 2/5 accessibility, 3/5 system maturity

The theme establishes brand colors, typography, and shapes, but spacing, elevation, motion, status, and component tokens remain repeated raw values. More importantly, current brand combinations are not always contrast-safe:

- Light brand orange `#F7931A` against `#F7F9FC` is approximately 2.18:1, yet the primary color is used for small text and icons.
- White on the merchant-chip orange `#EF6C00` is approximately 3.08:1 for small label text.

Both require redesign for their current semantic uses. Preserve the recognizable orange, but derive separate accessible content/action colors and test semantic tokens rather than individual screenshots by eye. Create compact design tokens for spacing, status colors, elevations, motion duration/easing, and standard surface compositions; avoid a giant theoretical token system before real components require it.

### 12.8 Onboarding — rating 2/5

The journey has sound product elements: a nested graph, explicit agreement, wallet-type education, and completion tied to a usable connection. Current coordination is fragile:

- The feature carousel's interaction condition compares an emitted value with the same current value, so auto-rotation can continue after user interaction.
- Auto-rotating content has no pause or reduced-motion policy.
- Camera permission is requested while leaving marketing content rather than when the user chooses to scan.
- Progress dots include a mutually exclusive branch, so an ordinary path visibly skips a step; the dots also lack semantics.
- Wallet type selection renders selection state but navigation immediately advances and passes no selected value, leaving the radio state effectively dead.
- Auto-pay preferences are persisted fire-and-forget immediately before navigation.
- The claimed shared onboarding model can actually be created under separate destination owners.
- Several screens do not scroll and can clip on small devices or large font scales.

Use one graph-scoped onboarding coordinator with saved non-sensitive progress, explicit branch state, and a single atomic `CompleteOnboarding` application command. Version the consent so a materially changed risk agreement can be handled intentionally. Request camera permission at the scanner action and queue external credentials securely until mandatory consent completes.

### 12.9 Settings, contacts, shortcuts, and destructive actions — rating 2/5

The settings surface is feature-rich, but ownership and write semantics are inconsistent:

- `PaymentsSettingsViewModel` combines payment preferences, exchange-rate previews, contact/shortcut observation, editor state, parsing, and persistence in more than 600 lines.
- Existing contacts and shortcuts persist field edits automatically, sometimes through app-scoped jobs, while new records use an explicit save model. There is no visible saving/error state, transaction boundary, reliable debounce, or clear cancellation rule.
- Wallet, contact, and shortcut deletion is immediate with no confirmation or undo, even though wallet deletion also affects credentials and live sessions.
- Wallet activation/removal events are collected and discarded behind a TODO.
- A shortcut editor can render a blank body when its contact is temporarily unavailable.
- Blink contact import saves sequentially and can partially succeed, then report only a general error. A failed load can also set a guard that prevents retry.
- Some import/add-flow step state is local `remember` state, so recreation can return to an earlier form after the durable side effect already occurred.
- Plaintext API-key form state is retained after success.
- NWC add reads the clipboard automatically on entry, and manual entry relies on IME Done without a visible submit action.
- Cancellation in wallet discovery/import can be converted to a normal result, allowing stale work to overwrite newer state.

Split preference, contact editor, shortcut editor, import, and wallet management owners. Choose either explicit Save/Cancel or a genuinely specified autosave contract with debounce, version/conflict handling, and visible save/error state. Use bulk transactional import and report partial duplicates/validation before commit. Destructive wallet removal should explain consequences and offer confirmation; local contact/shortcut removal should at least support undo where feasible. Clipboard access must be an explicit Paste action.

## 13. Platform implementation review

### 13.1 Platform abstractions — rating 4/5 direction, 3/5 implementation

Keep expect/actual or narrow common interfaces. Replace Android application-context globals, top-Activity weak references, and native singleton monitors with injected platform services owned by the shell/application lifecycle.

Language switching currently manipulates Android activity recreation and iOS `AppleLanguages`/UserDefaults directly. Define a supported locale policy per platform and isolate it behind one service. Avoid deprecated synchronization calls and behavior that depends on private/fragile OS preferences.

### 13.2 Camera/scanner — rating 3/5

The implementations show substantial care around camera selection, capture configuration, frame backpressure, lifecycle, zoom, and cleanup. That operational knowledge should be retained. Their size indicates several responsibilities are fused:

- Device discovery and format ranking.
- Permission and lifecycle.
- Capture-session setup and teardown.
- Frame detection and throttling.
- Preview host and Compose/native bridging.
- Zoom/near-far UX.

Split pure device/format selection so it can be unit-tested, expose an observable `ScannerState` rather than only imperative booleans/callbacks, and make lifecycle methods idempotent. Consider a Swift AVFoundation implementation behind the common port if Kotlin/Native interop is a maintenance burden; shared code is a means, not a goal.

Native diagnostic logging should be debug-only and redacted. Scanner/device configuration details should not be emitted unconditionally in release builds.

Concrete behavior to preserve includes CameraX keep-latest frame backpressure, always closing analyzed images, QR-only detection, deterministic multi-code selection, defensive cleanup, near/far selection, and zoom. Concrete behavior to replace includes Android `unbindAll()` affecting unrelated CameraX consumers, ignored zoom-future failures, and a Boolean permission model that cannot express requestable/denied-permanently/restricted/unavailable. On iOS, streaming is reported before actual output is observed, the preview frame is effectively assigned once without a dependable rotation/layout update, one mode preference is ignored, and mutable callback state crosses queues without a clear owner.

`PayNavigation` currently owns substantial preview, gesture, restart, and job state, including a pixel-based swipe threshold. Move all of that into a `ScannerHost`/`ScannerSession`; use density-independent gesture thresholds and give every gesture a visible accessible equivalent. Permission denial needs persistent rationale, retry, and Open Settings states rather than continuing to show “point camera” while scanning is impossible.

### 13.3 Platform support policy — rating 2/5

The iOS deployment target is 18.2 and the target is iPhone-only, while documentation suggests an older Xcode baseline. Android is minSdk 24 but release ABI packaging is arm64-only. These may be intentional product constraints, but they are currently closer to build accidents/FIXMEs than governed decisions.

For the rewrite, publish an explicit support matrix with device/OS/ABI rationale, analytics-independent market assumptions, test devices, and an annual review date. Unify app version and build number sources across Android and iOS.

The Android source manifest should explicitly declare `ACCESS_NETWORK_STATE` because application code calls protected connectivity APIs; relying on a transitive library's merged permission is accidental. The semantic haptic abstraction is good, but Android API 24–25 currently falls through without vibration because implemented paths require API 26; either support the declared minimum fully or raise it deliberately.

## 14. Build, dependency, release, and CI review

### 14.1 CI — rating 1/5

The Gradle settings include only `:shared` and `:androidApp`, but CI and iOS workflows still invoke `:composeApp` tasks and reference `composeApp` artifacts. The intended unit/iOS verification gates therefore cannot be trusted. This is an immediate repository fix, not something to defer to the rewrite.

After fixing task names, make the required gate explicit:

```text
format/static analysis
-> common + Android host tests
-> Android debug/release-like compile and lint
-> iOS simulator compile/tests
-> protocol/provider contract tests
-> selected Maestro regtest flows
-> artifact/signing smoke checks where appropriate
```

Ktlint, Android lint, and wrapper validation are good starting checks. Kover is configured but has no meaningful enforced thresholds. Add selective thresholds around payment policy/protocol/application state rather than chasing one global percentage.

### 14.2 Dependency policy — rating 2/5

Pin all production dependencies to immutable releases. Add Gradle dependency locking and verification metadata, automated update PRs, license/SCA scanning, and an SBOM for releases. Schema/provider upgrades should run contract and conformance tests. Remove the snapshot repository from production resolution when the pinned NWC artifact no longer requires it.

Extend the same provenance rule outside Gradle: pin GitHub Actions by commit, Docker images by digest where practical, verify downloaded release archives before executing them, and version the external `bundletool` dependency. The local CLN/NIP-47 image currently downloads and executes an archive without a checksum/signature. Supply-chain controls should cover the E2E environment because it shapes the protocol behavior accepted by the app.

### 14.3 Release automation — rating 3/5

Android release minification/resource shrinking and explicit keep rules are useful. Custom keystore and bundletool tasks encode real packaging requirements and should be documented before being simplified. Problems include personal signing-path fallbacks, operational complexity in application build scripts, Android/iOS version divergence, and broad removal of all `Log`/`PrintStream` calls in release.

Move release rules into a small convention plugin or dedicated build logic, supply signing only through CI/approved local properties, and provide a non-secret signing-readiness diagnostic. Replace broad log stripping with structured compile-time/build-variant logging that redacts secrets but retains actionable failures.

Do not wire Git-hook installation into IDE model preparation; build/model tasks should not unexpectedly mutate `.git`.

The release script has a more serious provenance path: when asked to release a commit other than `HEAD`, it can reuse whatever local AAB/APK is present, validate only the target commit's version, then tag/publish the binary. Remove that path or build in an isolated checkout and verify an embedded commit/version plus artifact hashes before signing/tagging. Archive R8 mappings, native symbols, checksums, SBOM, and attestation with each release.

Signing secrets must not be converted to command-line `pass:` arguments visible in process listings. Keep the legitimate distinction between Play upload and directly distributed APK signing keys, but remove personal absolute-path fallbacks and feed credentials through CI/local secret files or providers. Build a corresponding documented iOS archive/sign/publish workflow; current iOS release/versioning is not at Android's operational maturity.

Android R8 currently marks all `PrintStream` methods as side-effect-free. That can remove library behavior that is not logging. Replace it with an application-owned logger rather than globally changing Java semantics. On iOS, release-active `NSLog` has the opposite problem. One cross-platform logging policy should govern both.

### 14.4 E2E variants — rating 4/5

The separate Android E2E application ID and release-like/minified build are strong. The iOS test seeder manually duplicates secure persistence keys/schema in Swift, which is brittle whenever app storage evolves. Expose a test-only seed interface compiled only into the E2E target and share migration fixtures. Avoid blocking the launch main thread with `runBlocking`, even in test builds.

If the harness offers a “slow network” mode, it should actually inject latency/failure rather than only logging a policy. Network fault injection is especially important for the uncertain-payment invariant.

## 15. Test architecture review

### What exists and should be preserved

- A substantial shared test suite with strong regression value around the main payment flow.
- Blink API/repository tests for authorization, statuses, errors, and concurrent target selection.
- Parser, settings, onboarding, and ViewModel tests.
- Three real Maestro scenarios covering NWC onboarding, Blink onboarding, and a fixed BOLT11 NWC payment.
- A local Lightning/NWC regtest environment that makes a real vertical payment test possible.

### Structural weakness

Testing is concentrated around a 2,000-line `MainViewModelTest`, so it validates many behaviors only through the most coupled object. NWC repository tests explicitly cannot substitute the concrete client. LNURL, persistence concurrency/corruption, cancellation, DI graph, platform storage migration, process restoration, accessibility, and exact confirmation boundaries are sparse or absent.

The current suite also did not catch the frozen-home state because navigation projects ViewModel state outside the ViewModel. This is a classic sign that the tested unit is not the architectural unit that owns behavior.

### Rewrite test pyramid

1. **Pure model/state tests:** transition-table and property tests for input, confirmation, rounding, duplicate policy, and attempt state machine.
2. **Protocol conformance:** official/golden BOLT11 and LNURL vectors, tampered/fuzz inputs, callback trust cases, and exact metadata hashing.
3. **Port contract suites:** the same payment behavior contract for NWC and Blink, including cancellation and unknown outcomes.
4. **Persistence tests:** migrations from every legacy fixture, transactions, corruption quarantine, concurrent updates, process restart, and credential compensation.
5. **Presentation tests:** reducer/action/effect tests with no provider clients; screenshot and accessibility semantics checks at representative sizes/themes/locales.
6. **Platform tests:** secure-store invalidation/backup behavior, camera format selection, lifecycle idempotence, and app-link ingestion.
7. **E2E/regtest:** onboarding, fixed/amountless/LNURL, threshold boundary, wallet switch mid-attempt, duplicate scan, provider timeout then reconciliation, process restart, deep-link consent, revoked credential, and failure recovery.
8. **Architecture gates:** forbidden imports/module dependencies, Koin graph verification, Apollo schema drift, dependency verification, secret scan, and release configuration checks.

Tests should use injected clocks, dispatchers, rate snapshots, and ID sources. Avoid optional production dependencies merely to make one god object constructible.

## 16. Security, privacy, and observability

### Security priorities

- Never put NWC secrets, Blink keys, preimages, or credential-bearing URIs in routes, saved state, general models, `toString()`, logs, crash reports, screenshots, or analytics.
- Treat invoices, payment hashes, Lightning addresses, wallet public keys, contact addresses, and relay URLs as sensitive metadata even when not credentials.
- Validate BOLT11/LNURL fully before an irreversible call.
- Treat callbacks and redirects as untrusted network input.
- Persist and reconcile payment intent to prevent duplicate submission after uncertainty.
- Define backup, uninstall, Keychain retention, credential invalidation, reset, and migration policies.
- Require consent/onboarding policy before processing sensitive app links.
- Treat custom URL schemes as hostile because another app can claim them; prefer QR/explicit paste or an authenticated universal-link provisioning handshake for bearer credentials.
- Add an explicit sensitive-screen/capture policy for credential entry and high-value confirmation, with careful usability trade-offs.

### Observability — rating 1/5

The app has little coherent production observability. That is privacy-positive compared with indiscriminate analytics, but it makes payment and provider failures difficult to diagnose. Raw provider/error text is sometimes shown directly instead of being safely recorded.

Adopt a privacy-first diagnostic model:

- Structured events with a random opaque `AttemptId`, state transition, provider type, duration bucket, and stable error code.
- A central redactor that rejects known secret formats and never records invoice/preimage/credential/address content.
- Debug logs for local development; minimal release logs and opt-in crash reporting under a documented retention policy.
- Coarse operational metrics such as resolution stage, pending reconciliation rate, provider timeout class, and scanner readiness—not user/payment identity.
- User-facing support codes that correlate to a redacted event without exposing internals.

No analytics is a valid deliberate product decision. It should be recorded as an ADR with the resulting support/quality trade-offs rather than being an accidental absence.

### Privacy documentation and data-flow inventory — rating 1/5 current accuracy

The checked-in privacy policy says the app obtains no information, shares no data, and has no unauthorized-access risk. That is incompatible with normal operation even if Lasr itself runs no analytics service:

- CoinGecko receives the selected quote currency and network metadata such as IP address.
- Blink receives an API key plus account/payment request data.
- LNURL services receive requested amount, optional comment, and network metadata.
- User-selected Nostr relays observe encrypted traffic and timing/connection metadata.
- OS/service dependencies may have their own diagnostic behavior.

Create a real data-flow inventory before the rewrite ships. For each datum, record source, purpose, recipient, transport, device/server retention, backup behavior, user deletion, sensitivity, and whether it appears in diagnostics. Update the public policy to distinguish “Lasr has no first-party analytics/backend” from “the app sends data to services necessary to perform user-requested features.” Avoid absolute security claims.

### Backup, reinstall, and capture policy

Android currently enables backup without explicit data-extraction rules. Encrypted preferences can restore on a device whose Keystore key is unavailable; decryption then silently appears as missing, while onboarding or other preferences may restore independently. Choose one governed policy: exclude the vault and restore profiles as reconnect-required, disable backup, or design an authenticated export/recovery flow.

iOS Keychain can survive uninstall while UserDefaults may not. Choose whether reinstall recovers connections, asks to reconnect, or erases everything; use a first-install marker and explicit Keychain accessibility class. Add a user-visible “reset Lasr data and credentials” path and a supportable recovery state for key invalidation/corruption.

## 17. Recommended greenfield architecture

```text
Android shell                         iOS shell
     |                                    |
     +--------------- :shared ------------+
                         |
                 Root AppCoordinator
             onboarding + safe app links
                         |
       +-----------------+------------------+
       |                 |                  |
  Pay feature      Wallet feature    Contacts/Settings
       |                 |                  |
       +----------- application ------------+
            workflows, state machines, ports
                         |
       +-----------------+---------------------------+
       |           |          |          |           |
    NWC data   Blink data    LNURL    database   credential vault
       |           |          |          |           |
       +------------ core/model rules ---------------+
```

### 17.1 Ownership rules

- `core/model` knows only Kotlin and a small time abstraction. It has no Compose resources, Ktor, Apollo, Koin, Settings, or provider SDK.
- `application` owns workflows, transaction boundaries, state machines, policies, and ports. It does not format UI text.
- Infrastructure implements ports and maps wire/storage/native errors into typed application failures.
- Feature presentation owns `UiState`, `Action`, `Effect`, and semantic-to-resource mapping.
- Presentation/navigation inside `:shared` owns the root coordinator, typed navigation, theme, and composition. It contains no payment protocol decisions.
- Platform shells own native initialization, entitlements/permissions, secure facilities, and lifecycle adapters.

### 17.2 Core payment flow

```text
Input source
  -> normalize/classify
  -> resolve + validate protocol
  -> construct PaymentDraft
  -> calculate final amount/rate snapshot
  -> evaluate confirmation policy
  -> create durable PaymentAttempt(walletId, fingerprint)
  -> submit through explicit provider backend
  -> persist outcome
       Settled | AlreadyPaid | Rejected | Pending/Unknown
  -> reconcile non-final attempts on launch/foreground
  -> presentation observes attempt state
```

The database record is created before submission. The credential vault is accessed only inside the provider adapter. Navigation observes IDs and renders; it never transports payment secrets or owns settlement truth.

### 17.3 State model

Keep draft state and attempt truth separate. A user can dismiss a draft or leave its screen; an already-submitted attempt remains durable and observable. A new draft can coexist with pending attempts. This directly supports the current app's valuable “continue scanning while verification runs” experience without tying truth to a ViewModel lifetime.

### 17.4 Storage model

- SQL database: public wallet profiles/capabilities, contacts, shortcuts, attempts, and migration ledger.
- Typed preferences: theme, language override, selected currency, haptics, confirmation configuration, and onboarding/consent version.
- Credential vault: NWC secret/API key only, addressed by `WalletId` and versioned credential kind.
- Ephemeral sensitive store: one-time app-link/scan credential drafts, wiped on consume/background/timeout according to policy.

### 17.5 Provider model

With only NWC and Blink, an explicit sealed provider dispatch is simpler than a dynamic plugin framework. Define one backend contract and provider-specific capabilities. If future providers genuinely become third-party/runtime extensions, revisit a registry then. Avoid speculative plugin architecture now.

## 18. Architecture decisions to record before implementation

Create concise ADRs for at least these decisions:

| ADR | Decision to make | Recommended default |
|---|---|---|
| 001 | Cross-platform strategy | Keep KMP + Compose; permit native Swift/Kotlin implementations behind ports |
| 002 | Shared-module package boundaries | Keep conventional `:shared`; enforce pure core/application dependencies and split Gradle modules only when justified |
| 003 | Money/rate/identity types | Validated value types, checked integer/fixed-point conversion, injected clock/IDs |
| 004 | Result and error policy | Operation-specific outcomes or two-case `Outcome`; no `Loading`; cancellation propagates |
| 005 | Wallet and credential model | Opaque `WalletId`; public profile separate from narrowly vaulted secret |
| 006 | Payment attempt lifecycle | Durable state machine created before submission; Unknown is first-class and reconciled |
| 007 | Protocol validation | Audited BOLT11 adapter and one end-to-end LNURL coordinator with network policy |
| 008 | Persistence and migrations | Transactional KMP DB, typed preferences, credential vault, legacy import ledger |
| 009 | DI and coroutine ownership | Koin composition-only, constructor injection, owned scopes, qualified dispatchers |
| 010 | Navigation and app links | Typed ID-only routes, root coordinator, one-time sensitive handoff, consent gate |
| 011 | Exchange-rate policy | One typed snapshot/cache/freshness policy; record rate used by payment |
| 012 | Privacy and diagnostics | Redacted structured events, secret denylist, explicit crash/analytics policy |
| 013 | Scanner/platform boundary | Observable scanner state, idempotent lifecycle, native implementation freedom |
| 014 | Platform support and release | Supported OS/ABI/device matrix and one version source |
| 015 | Quality gates | Conformance, provider contracts, migrations, architecture, accessibility, and regtest CI |
| 016 | Backup/reinstall/recovery | Explicit Android backup exclusions and iOS Keychain retention/accessibility behavior |
| 017 | External identity/release provenance | Preserve store identity and signing continuity; isolated reproducible builds with attestation |

An ADR should state context, decision, rejected options, consequences, and tests/metrics that keep it true. Avoid turning ADRs into aspirational documents with no enforcement.

## 19. Prioritized action plan

### P0: backport safety and operability fixes now

These issues affect the current app and should not wait for a rewrite:

1. Remove NWC URI/secret material from navigation and saved state; introduce an ephemeral handoff immediately.
2. Fix the frozen Pay-home error state so displayed state, scanner state, and dismissibility share one source of truth.
3. Fix CI references from removed `:composeApp` tasks/artifacts to real modules and make required checks pass.
4. Never turn inconclusive lookup into failure; preserve `Unknown`, persist enough state if feasible, and clarify UI.
5. Enforce LNURL metadata-description hash, amount, callback URL policy, and HTTP validation; use or introduce a validated BOLT11 decoder with expiry/network/signature checks.
6. Prevent deep links from bypassing onboarding/risk consent and stop navigation from inferring origin by popping routes.
7. Rethrow `CancellationException` in every suspend adapter.
8. Pin the NWC dependency to an immutable release/artifact.

### P1: establish the rewrite foundation

1. Ratify the product invariants in `PRD.md` and the ADRs above; preserve Android application ID/signing continuity, iOS bundle/team identity, deep-link compatibility, and store records unless a separately planned migration says otherwise.
2. Build core value types, typed errors/outcomes, and the payment-attempt transition model.
3. Introduce database schema, credential vault, migration ledger, and golden legacy fixtures.
4. Implement the `PaymentCoordinator` actor and reconciliation scheduler using fake backends.
5. Establish enforceable package boundaries, DI composition, dispatchers/scopes, navigation coordinator, redacted logging, and CI gates.
6. Port the regtest environment and make one fixed NWC payment vertical slice pass.

### P2: migrate product capabilities by vertical slice

1. NWC connection/provisioning and fixed BOLT11 payment.
2. Blink provisioning/payment with generated GraphQL and stable error mapping.
3. Amountless invoice, LNURL-pay, and Lightning address validation.
4. Confirmation policies, currencies, exchange-rate snapshots, and shortcuts.
5. Contacts/import/settings/onboarding with transactional migrations.
6. Transaction detail, receipt, duplicate handling, and process-restart reconciliation.

Each slice must include protocol/contract tests, reducer tests, migration impact, and one appropriate E2E scenario before the next slice expands scope.

### P3: quality and experience hardening

1. Adaptive layouts, large text, screen reader semantics, contrast, reduced motion, and rotation.
2. Scanner decomposition and device matrix performance work.
3. Broader fault-injection E2E and platform secure-store tests.
4. Release/version/signing build logic, SBOM/SCA, dependency automation, and support diagnostics.
5. Remove legacy stores only after verified migration and an agreed rollback window.

## 20. Legacy migration inventory

A greenfield codebase does not imply greenfield installed devices. Before changing application IDs, namespaces, preference names, Keychain service, or encryption, create golden import fixtures for the existing locations:

| Platform/data | Existing location or key |
|---|---|
| Android secure preferences | File `secure_wallet_settings`; AES key alias `wallet_secure_key` |
| Android encrypted string envelope | Base64 of 12-byte GCM IV followed by ciphertext/tag |
| iOS secure store | Keychain service `"<appStorageNamespace>.wallet"` |
| Wallet aggregate | `wallet.list` |
| Active wallet | `wallet.active` |
| Blink credential | `blink.apikey.<walletId>` |
| Blink default wallet | `blink.defaultWallet.<walletId>` |
| Contacts/shortcuts aggregate | `contacts.state`, including legacy `amountMsats` |
| Primary/secondary currency | `display.currency.code`, `display.secondary.currency.code` |
| Payment preferences | Existing `payment.*` keys |
| Theme | `theme.preference` |
| Android onboarding | File `onboarding_settings`, key `onboarding.completed` |
| iOS onboarding | Standard defaults |
| iOS language override | `language.override.tag` and existing `AppleLanguages` behavior |

Migration must be idempotent and transactional where stores permit it:

1. Read and validate legacy public records without deleting them.
2. Generate/remap stable new IDs and relationships in one database transaction.
3. Copy credentials without logging or exposing plaintext to navigation/UI.
4. Verify the new profile-to-credential association and decryptability.
5. Record a migration version/checksum.
6. Leave legacy data intact through the rollback window.
7. Delete old records only after product-approved success criteria and recovery support.

Corrupt or unknown records should be quarantined and surfaced as a recoverable migration issue, not silently replaced with an empty wallet/contact list.

## 21. Rewrite acceptance gates

The rewrite should not be considered safer merely because it is newer. Require these outcomes before replacing the current app:

- No credential or secret-bearing URI is serializable through navigation, saved state, logs, diagnostics, or public model `toString()`.
- BOLT11 and LNURL conformance/security suites pass, including tampered and malicious input.
- Every submitted attempt is durable before provider mutation and survives process recreation.
- Wallet switching/removal cannot redirect or erase an in-flight attempt.
- Unknown outcomes remain unknown until authoritative reconciliation; resubmission requires an explicit safe decision.
- Exact confirmation boundaries and all input origins have executable policy tests.
- Both providers pass the same core payment contract, cancellation, and lookup tests.
- Legacy Android and iOS fixtures migrate idempotently without secret loss.
- Existing store identities and signing certificates remain upgrade-compatible, or a separately tested user migration plan is approved.
- Koin graph verification and module dependency rules pass.
- Compact/expanded, light/dark, all supported locales, large text, screen-reader semantics, and reduced motion are validated.
- CI builds/tests real Gradle modules and produces reproducible artifacts from pinned dependencies.
- Release artifacts are built from the tagged commit in an isolated workspace and include hashes, mappings/symbols, SBOM, and verifiable provenance.
- A real regtest fixed payment, dynamic payment, duplicate, timeout/reconciliation, and wallet-switch flow pass.
- Release logs/crash payloads pass secret-redaction tests.

## 22. Avoided over-engineering

The rewrite is an opportunity to simplify, not to replace local debt with fashionable infrastructure.

- Do not create dozens of modules for a roughly 23K-line common codebase; keep `:shared` and enforce a small acyclic package dependency graph first.
- Do not add a full functional-programming framework only to replace `Result`.
- Do not build a dynamic provider-plugin system for two compile-time providers.
- Do not introduce event sourcing; a transactional current attempt plus a small transition audit is enough.
- Do not wrap every repository call in a one-line use-case class.
- Do not force native camera/security code into common Kotlin when a narrow Swift/Kotlin adapter is clearer.
- Do not add analytics by default; add only privacy-reviewed signals tied to an operational question.
- Do not optimize animation or build modularity before payment correctness, protocol trust, durability, and CI are reliable.

## 23. Final assessment

Lasr should not be rewritten as a feature-by-feature translation of the current folders. That would preserve the largest debts while losing the opportunity to make payment truth durable and enforce boundaries.

The successful rewrite is a behavioral preservation project built around a smaller set of explicit invariants:

- A validated request and final amount.
- A deliberate confirmation decision.
- An immutable wallet identity per attempt.
- A durable attempt created before external mutation.
- Honest settled/already-paid/rejected/unknown truth.
- Credentials confined to a narrow vault.
- One owner for each state transition, app link, lifecycle, and scope.

Keep the cross-platform strategy, scanner-first experience, typed navigation concept, provider abstraction, NWC session reuse, generated Blink API, native secret-storage intent, visual design system, and real regtest harness. Replace the mechanisms that make those decisions implicit: raw primitive/secret models, incomplete validation, fragmented state, mutable active-wallet caches, session-only tracking, settings databases, service location, global event buses, and stale quality gates.

That approach preserves what the current app learned while making the rewrite materially safer, simpler, more testable, and easier to evolve.

## Appendix A: Verification and evidence map

### Verification performed

- `./gradlew :shared:testAndroidHostTest` — **passed** on 2026-07-12 (`BUILD SUCCESSFUL`, 6m31s).
- Static inventory covered common production/test Kotlin, Android/iOS actuals and shells, Compose resources, Gradle/version catalog, CI workflows, release scripts, Maestro flows, and the local E2E harness.
- No live Blink, CoinGecko, LNURL, Nostr relay, or production release operation was performed.
- No Android instrumentation/device or iOS simulator/platform test suite was run for this report.

### Primary evidence

The following locations make the most consequential findings directly traceable. Line numbers describe the reviewed revision and will naturally drift as fixes land.

| Finding | Evidence |
|---|---|
| Only two Gradle modules | [`settings.gradle.kts:34`](settings.gradle.kts#L34) |
| Custom `Result` includes dead `Loading` for one-shot suspend calls | [`Result.kt:6`](shared/src/commonMain/kotlin/xyz/lilsus/papp/domain/model/Result.kt#L6), [`LnurlRepository.kt:7`](shared/src/commonMain/kotlin/xyz/lilsus/papp/domain/repository/LnurlRepository.kt#L7) |
| Global/UI-oriented error model plus exception channel | [`AppError.kt:28`](shared/src/commonMain/kotlin/xyz/lilsus/papp/domain/model/AppError.kt#L28), [`AppError.kt:110`](shared/src/commonMain/kotlin/xyz/lilsus/papp/domain/model/AppError.kt#L110) |
| NWC bearer secret in ordinary domain models | [`WalletConnection.kt:7`](shared/src/commonMain/kotlin/xyz/lilsus/papp/domain/model/WalletConnection.kt#L7), [`WalletPaymentTarget.kt:9`](shared/src/commonMain/kotlin/xyz/lilsus/papp/domain/model/WalletPaymentTarget.kt#L9) |
| NWC URI/secret in typed navigation route | [`ConnectWalletNavigation.kt:15`](shared/src/commonMain/kotlin/xyz/lilsus/papp/navigation/ConnectWalletNavigation.kt#L15), [`ConnectWalletNavigation.kt:54`](shared/src/commonMain/kotlin/xyz/lilsus/papp/navigation/ConnectWalletNavigation.kt#L54) |
| Navigation infers connection origin and owns onboarding completion | [`ConnectWalletNavigation.kt:28`](shared/src/commonMain/kotlin/xyz/lilsus/papp/navigation/ConnectWalletNavigation.kt#L28) |
| External NWC input can precede onboarding gate | [`App.kt:74`](shared/src/commonMain/kotlin/xyz/lilsus/papp/App.kt#L74) |
| Minimal BOLT11 parser discards signature words | [`Bolt11InvoiceParser.kt:18`](shared/src/commonMain/kotlin/xyz/lilsus/papp/domain/bolt11/Bolt11InvoiceParser.kt#L18), [`Bolt11InvoiceParser.kt:122`](shared/src/commonMain/kotlin/xyz/lilsus/papp/domain/bolt11/Bolt11InvoiceParser.kt#L122) |
| LNURL endpoint/callback fetch and broad error catches | [`LnurlRepositoryImpl.kt:36`](shared/src/commonMain/kotlin/xyz/lilsus/papp/data/lnurl/LnurlRepositoryImpl.kt#L36), [`LnurlRepositoryImpl.kt:80`](shared/src/commonMain/kotlin/xyz/lilsus/papp/data/lnurl/LnurlRepositoryImpl.kt#L80) |
| LNURL amount check is good, hash-only memo check is incomplete | [`MainViewModel.kt:1032`](shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/MainViewModel.kt#L1032), [`MainViewModel.kt:1063`](shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/MainViewModel.kt#L1063) |
| Payment state owner concentration | [`MainViewModel.kt:76`](shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/MainViewModel.kt#L76), [`MainViewModel.kt:255`](shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/MainViewModel.kt#L255) |
| Error/result state remapped to Active while scanner uses original state | [`PayNavigation.kt:211`](shared/src/commonMain/kotlin/xyz/lilsus/papp/navigation/PayNavigation.kt#L211), [`PayNavigation.kt:724`](shared/src/commonMain/kotlin/xyz/lilsus/papp/navigation/PayNavigation.kt#L724), [`MainScreen.kt:121`](shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/MainScreen.kt#L121) |
| Pending state is cleared with ViewModel and unknown becomes failure | [`MainViewModel.kt:1789`](shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/MainViewModel.kt#L1789), [`PendingPaymentTracker.kt:315`](shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/PendingPaymentTracker.kt#L315), [`PendingPaymentTracker.kt:365`](shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/PendingPaymentTracker.kt#L365) |
| Mutable payment-service wallet state and constructor blocking | [`PaymentService.kt:37`](shared/src/commonMain/kotlin/xyz/lilsus/papp/domain/service/PaymentService.kt#L37) |
| Confirmation threshold/manual semantics | [`ShouldConfirmPaymentUseCase.kt:14`](shared/src/commonMain/kotlin/xyz/lilsus/papp/domain/usecases/ShouldConfirmPaymentUseCase.kt#L14) |
| Strong NWC cache/lifecycle and verify-on-timeout behavior | [`NwcConnectionManager.kt:16`](shared/src/commonMain/kotlin/xyz/lilsus/papp/data/nwc/NwcConnectionManager.kt#L16), [`NwcWalletRepositoryImpl.kt:53`](shared/src/commonMain/kotlin/xyz/lilsus/papp/data/nwc/NwcWalletRepositoryImpl.kt#L53) |
| Blink string-heuristic errors and minimally configured Apollo client | [`BlinkApiClient.kt:41`](shared/src/commonMain/kotlin/xyz/lilsus/papp/data/blink/BlinkApiClient.kt#L41), [`BlinkApiClient.kt:510`](shared/src/commonMain/kotlin/xyz/lilsus/papp/data/blink/BlinkApiClient.kt#L510) |
| JSON aggregate writes and silent corruption fallback | [`WalletSettingsRepositoryImpl.kt:98`](shared/src/commonMain/kotlin/xyz/lilsus/papp/data/settings/WalletSettingsRepositoryImpl.kt#L98), [`WalletSettingsRepositoryImpl.kt:106`](shared/src/commonMain/kotlin/xyz/lilsus/papp/data/settings/WalletSettingsRepositoryImpl.kt#L106), [`ContactsRepositoryImpl.kt:203`](shared/src/commonMain/kotlin/xyz/lilsus/papp/data/settings/ContactsRepositoryImpl.kt#L203) |
| Android/iOS secure store implementations | [`SecureSettings.android.kt:29`](shared/src/androidMain/kotlin/xyz/lilsus/papp/data/settings/SecureSettings.android.kt#L29), [`SecureSettings.ios.kt:8`](shared/src/iosMain/kotlin/xyz/lilsus/papp/data/settings/SecureSettings.ios.kt#L8) |
| Whole-app DI graph, global scopes, hidden removal cycle | [`NwcModule.kt:119`](shared/src/commonMain/kotlin/xyz/lilsus/papp/di/NwcModule.kt#L119), [`NwcModule.kt:124`](shared/src/commonMain/kotlin/xyz/lilsus/papp/di/NwcModule.kt#L124), [`NwcModule.kt:289`](shared/src/commonMain/kotlin/xyz/lilsus/papp/di/NwcModule.kt#L289) |
| Service location from application/navigation | [`App.kt:45`](shared/src/commonMain/kotlin/xyz/lilsus/papp/App.kt#L45), [`SettingsNavigation.kt:409`](shared/src/commonMain/kotlin/xyz/lilsus/papp/navigation/SettingsNavigation.kt#L409) |
| Lossy singleton event channels | [`DeepLinkEvents.kt:11`](shared/src/commonMain/kotlin/xyz/lilsus/papp/navigation/DeepLinkEvents.kt#L11), [`PaymentDeepLinkEvents.kt:22`](shared/src/commonMain/kotlin/xyz/lilsus/papp/navigation/PaymentDeepLinkEvents.kt#L22), [`DonationNavigation.kt:11`](shared/src/commonMain/kotlin/xyz/lilsus/papp/navigation/DonationNavigation.kt#L11) |
| Oversized UI contract | [`MainScreen.kt:70`](shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/MainScreen.kt#L70) |
| Fixed contacts-sheet height and animated hero | [`ContactsBottomSheet.kt:392`](shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/contacts/ContactsBottomSheet.kt#L392), [`HeroAnimation.kt:257`](shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/components/hero/HeroAnimation.kt#L257) |
| Automatic clipboard access in wallet add | [`SettingsNavigation.kt:537`](shared/src/commonMain/kotlin/xyz/lilsus/papp/navigation/SettingsNavigation.kt#L537) |
| Android backup enabled without explicit rules | [`AndroidManifest.xml:11`](androidApp/src/main/AndroidManifest.xml#L11) |
| Android connectivity requires an explicit app permission | [`NetworkConnectivity.android.kt:15`](shared/src/androidMain/kotlin/xyz/lilsus/papp/platform/NetworkConnectivity.android.kt#L15) |
| Stale CI module/task references | [`ci.yml:123`](../.github/workflows/ci.yml#L123), [`ios.yml:61`](../.github/workflows/ios.yml#L61) |
| Mutable snapshot NWC dependency | [`libs.versions.toml:18`](gradle/libs.versions.toml#L18), [`settings.gradle.kts:30`](settings.gradle.kts#L30) |
| Release can reuse artifacts for a non-HEAD commit | [`release-android:113`](scripts/release-android#L113), [`release-android:185`](scripts/release-android#L185) |
| Privacy-policy claims versus runtime recipients | [`privacy-policy.md:5`](../privacy-policy.md#L5), [`CoinGeckoExchangeRateRepository.kt:71`](shared/src/commonMain/kotlin/xyz/lilsus/papp/data/exchange/CoinGeckoExchangeRateRepository.kt#L71), [`BlinkApiClient.kt:376`](shared/src/commonMain/kotlin/xyz/lilsus/papp/data/blink/BlinkApiClient.kt#L376) |
