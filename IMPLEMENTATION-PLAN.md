# RAYL initial extraction implementation plan

Status: implementation-ready plan

Source direction: [PLAN-REFINED.md](PLAN-REFINED.md)

Architecture input:
[ARCHITECTURE_REWRITE_REVIEW.md](ARCHITECTURE_REWRITE_REVIEW.md)

Prepared: 2026-07-23

## 0. Implementation progress

Last updated: 2026-07-23

Status key: `DONE`, `IN PROGRESS`, `NOT STARTED`

| Work item | Status | Evidence / next action |
| --- | --- | --- |
| P0.1 ACINQ dependency baseline | DONE | ACINQ Lightning/Bitcoin/secp256k1 dependencies are pinned in commit `6e57bfd`. |
| P0.2 Adopt ACINQ in legacy app | DONE | Commit `6e57bfd` removes the handwritten BOLT11 parser and uses ACINQ payment primitives. |
| P0.3 Freeze gate | DONE | Signed tag `papp-final` and protected branch `papp-legacy` point to `6e57bfd` on GitHub. |
| Milestone 1 — RAYL skeleton | DONE | GitHub repository and Gradle root are `rayl`; all six app shapes exist; `./gradlew projects ktlintCheck` passes from the Git root. |
| Milestone 2 — Blip extraction | REVIEW CHECKPOINT | Android debug candidate is installed and launches on a physical device. The functional iOS debug app also builds. Manual product/visual review is next; release validation is explicitly deferred and must not be run during implementation review. |
| Milestone 3 — Lasr extraction | NOT STARTED | Explicitly outside the current implementation run. |

Progress is updated in this table and in task-local completion notes as work is
committed. A task is marked `DONE` only when its acceptance criteria have
current evidence.

### Completed implementation evidence

#### 2026-07-23 — Prerequisite and Milestone 1

- Froze the final combined app at signed commit `6e57bfd` and published the
  signed `papp-final` tag.
- Published `papp-legacy` and enabled required pull requests, administrator
  enforcement, deletion protection, and force-push protection.
- Renamed the GitHub repository from `NicolaLS/lasr` to `NicolaLS/rayl`.
- Moved the Gradle root to the Git root and set `rootProject.name = "rayl"`.
- Recorded the legacy capability and visual baseline in
  `docs/extraction-baseline.md`.
- Created all six `apps/<name>/{shared,androidApp,iosApp}` directory shapes,
  the empty `foundation/ui`, `docs/adr`, and the empty top-level Xcode
  workspace.
- Removed the combined app, legacy test suites, Maestro flows, NWC E2E harness,
  legacy release automation, and stale nested-root CI from active development.
  They remain recoverable from `papp-final`.
- Verified the skeleton from the Git root with
  `./gradlew projects ktlintCheck`.

#### 2026-07-23 — Blip Android implementation candidate

| Blip task | Status | Current evidence / remaining gate |
| --- | --- | --- |
| B1 Bootstrap | DONE | Independent shared, Android, and iOS shell projects exist with Blip IDs/namespaces; the Xcode workspace lists `BlipApp`; Android debug resolves ACINQ/Apollo/SQLDelight and assembles. |
| B2 Decisions and boundaries | DONE | ADRs 0001–0006 record layer, money/outcome, lifecycle, persistence/vault, protocol/privacy, and intentionally-small-test decisions. `verifyBlipArchitecture` enforces the pure-domain, NWC, and no-migrated-test boundaries. |
| B3 Visual product | IMPLEMENTED — MANUAL CHECK PENDING | App-owned Compose onboarding, scanner hero, payment states, transactions, settings, editable contacts/shortcuts, themes, typography, Android/iOS icons, and resources use the established product assets. Visual comparison remains part of the user/QA handoff. |
| B4 Core and persistence | DONE | Validated values, fixed-point currency conversion/rate snapshots, SQLDelight connection/attempt/contact/shortcut storage, bounded transitions, duplicate policy, typed preferences, and Android Keystore AES-GCM credential storage are wired. No tests were migrated or added. |
| B5 Blink provisioning | DONE | Ephemeral redacted API-key entry, stable provider-code mapping, generated account/default-wallet discovery, connection generations, refresh, contact import, disconnect, and reconnect are wired. |
| B6 Input/protocol trust | DONE | One resolver handles all origins using ACINQ BOLT11/BOLT12/Bech32/hash APIs, BIP21, strict LNURL/Lightning-address policy, amount/metadata checks, and explicit unsupported classifications. |
| B7 Payment coordinator | DONE | One serialized coordinator writes attempts before provider I/O, preserves pending/unknown, blocks repeated invoice hashes, and reconciles with the original connection and wallet generation. |
| B8 Complete product wiring | IMPLEMENTED — MANUAL CHECK PENDING | Onboarding, scan/paste/manual/app-link input, amount/confirmation/payment/result, transaction detail, editable contacts/shortcuts (including fiat-at-payment-time shortcuts), currency/rates, preferences, haptics, donation, language/theme, and wallet management are connected through typed routes and separate feature states. |
| B9 Platform boundaries | DEBUG IMPLEMENTATION DONE | Android uses CameraX/ML Kit, Keystore, private preferences, clipboard, haptics, locale and lifecycle adapters. iOS now uses AVFoundation, Keychain, SQLDelight Native, `NSUserDefaults`, clipboard/haptics, a Compose host, and queued standard payment links. Both advertise only `lightning`, `bitcoin`, and `lnurl`. |
| B10 Verify/close | REVIEW CHECKPOINT | `verifyBlipArchitecture`, formatting, and `:apps:blip:androidApp:assembleDebug` pass; the APK installs and cold-starts on a physical Android device. The iOS debug framework and application build. No tests were migrated or added. User/QA manual capability and visual review is next. Release validation is deferred and must not be run for this review checkpoint. |

This checkpoint intentionally stops at the debug handoff requested for manual
testing. It does not claim the final Milestone 2 exit gate: user/QA capability
and visual sign-off remain, and release validation is deferred to an explicitly
requested later checkpoint.

## 1. Goal

Transform the current combined `papp` application into the initial RAYL
repository in three sequential milestones:

1. New project skeleton exists.
2. Blink is extracted into `apps/blip`.
3. Lasr is extracted into `apps/lasr`.

Blip and Lasr are greenfield implementations of the existing product behavior.
They preserve the established UI and user journeys, but they do not preserve the
legacy architecture.

The extraction is complete when each new app supports all applicable behavior
from the combined application with only its own provider compiled into the app.
Provider-specific product polishing happens after these milestones.

## 2. Resolved scope decisions

These decisions refine or override broader outcomes in `PLAN-REFINED.md` for
this implementation sequence:

- Milestone 1 creates the complete RAYL directory topology.
- App directories and their `shared`, `androidApp`, and `iosApp` placeholders
  may be empty and are not required to build or launch in milestone 1.
- Blip is implemented before Lasr.
- Both extractions include all applicable legacy functionality.
- The initial UI should remain effectively identical to the legacy product.
- The provider-selection and existing onboarding experiences may remain
  temporarily. Blip must disable the NWC option; Lasr must disable the Blink
  option.
- The disabled option is presentation only. It must not cause the other
  provider's SDK, routes, credentials, repositories, or runtime dispatch to
  enter the app.
- Duplication between Blip and Lasr is acceptable.
- No legacy unit, ViewModel, UI, screenshot, Maestro, integration, or E2E test
  is migrated.
- The new apps receive only small, newly written tests for app-owned, pure,
  deterministic logic. Tests that require mocks, stubs, fakes, test DI graphs,
  servers, databases, relays, or provider clients are out of scope.
- Automated integration, instrumented UI, screenshot, and E2E tests are
  explicitly deferred to the QA phase.
- There is no legacy data or credential migration. Users reconnect from
  scratch.
- ACINQ's Kotlin Multiplatform protocol stack is adopted by the legacy app
  before repository extraction starts. RAYL uses ACINQ's `lightning-kmp`,
  `bitcoin-kmp`, and required `secp256k1-kmp` artifacts instead of implementing
  or wrapping its own BOLT11, Bitcoin, or cryptographic primitives.
- Provider-specific onboarding, settings pruning, visual differentiation, and
  broader UX polish begin only after both extractions are verified.

## 3. Execution rules

### 3.1 Greenfield means behavioral preservation, not code migration

Legacy code may be:

- inspected for product behavior;
- used to build a capability inventory;
- used as a visual reference;
- copied when it is purely visual Compose code or a resource;
- consulted for provider protocol knowledge and edge cases.

Legacy architecture must not be copied merely to accelerate the extraction.
In particular, do not reproduce:

- `WalletType`-driven payment routing;
- a provider registry or provider switch;
- the large `MainViewModel`;
- fragmented UI and payment state;
- the custom `Result<T>` with `Loading`;
- global navigation/deep-link channels;
- secret-bearing routes or saved state;
- JSON blobs used as an application database;
- pass-through use cases for every repository method;
- Koin service location from UI/navigation;
- hidden coroutine scopes or custom retained-instance infrastructure;
- session-only payment truth;
- mutable-current-connection lookup for old attempts.

### 3.2 Preserve behavior through specifications

Before deleting or replacing a legacy area, record its observable behavior in
the capability matrix in section 8. Verification is against that matrix, not
against file parity or test parity.

### 3.3 Keep app ownership explicit

Until both apps exist, business behavior remains app-owned. `apps/blip` and
`apps/lasr` may contain deliberately duplicated:

- value types;
- payment workflows;
- state machines;
- input coordination;
- confirmation policies;
- persistence contracts;
- settings and contacts logic;
- presentation state and reducers.

After Lasr works, compare the two implementations. Promote code only when the
promotion rule in `PLAN-REFINED.md` is satisfied. The initial expected
RAYL-owned shared surface is only provider-neutral Compose design tokens and
visual components in `foundation/ui`.

Bitcoin and Lightning protocol reuse comes directly from the ACINQ KMP
libraries. Do not create a RAYL foundation wrapper merely to hide their types.
Use a thin app-local adapter only where ACINQ types must be translated into
app-owned payment models or expected outcomes.

There is no initial `foundation/core`, generic wallet layer, or shared provider
orchestration module.

### 3.4 One task should produce one reviewable change

The numbered tasks below are intended to be separate pull requests where
practical. A task is complete only when its acceptance criteria pass. Avoid
large provider-wide branches that become verifiable only at the end.

### 3.5 Testing is intentionally limited

This rule applies to every prerequisite, milestone, task, pull request, and
acceptance criterion in this plan:

- Do not migrate, copy, port, repair, or adapt any legacy test.
- Add only basic unit tests for RAYL-owned pure logic where the test can invoke
  the subject directly with ordinary values and assert the result.
- Appropriate subjects include small value objects, deterministic conversion,
  redaction, fingerprints, and pure decision/transition rules.
- Do not test ACINQ parsing, cryptography, protocol conformance, or other
  behavior already owned by a battle-tested dependency.
- Do not write a unit test if it requires a mock, stub, fake repository, fake
  store, fake provider client, fake session, test DI graph, virtual server,
  database harness, relay, filesystem, or elaborate coroutine/lifecycle
  orchestration.
- Do not add integration tests, instrumented/device tests, UI tests, screenshot
  tests, contract tests, Maestro flows, E2E tests, or a replacement E2E
  harness.
- When a small unit test genuinely needs Android APIs, prefer a focused
  Robolectric host test so it remains fast. Do not turn that test into a broad
  application or lifecycle simulation.
- Provider workflows, persistence implementations, networking, app links,
  lifecycle behavior, and cross-component wiring are verified through the
  Android debug app during implementation and by the QA team, not by building
  elaborate developer-owned test infrastructure.

If a later task says to “test” or “verify” behavior, it must be read within
these limits. An acceptance criterion must never be satisfied by introducing a
prohibited test category. Record the scenario for QA instead.

### 3.6 Use the fastest implementation feedback loop

During normal task and pull-request implementation, run only what is needed to
keep feedback fast:

- formatting or narrowly relevant static checks;
- the directly relevant basic Android/JVM host unit tests allowed by section
  3.5;
- Android debug compilation, assembly, installation, or manual launch.

Android debug is the standard proof that a task is ready to move forward. Do
not routinely run release builds, iOS builds, Kotlin/Native links, every-target
test tasks, full project checks that transitively build all targets, or the
complete validation matrix for each task or commit.

Cross-target or iOS debug validation happens only when explicitly requested at
a milestone or when Android cannot exercise platform-specific implementation.
Release builds are separate release-readiness work: never run an Android or iOS
release build merely because an implementation milestone is closing. They
require an explicit user request. None of these tasks belongs in the inner
development loop or individual task acceptance criteria.

## 4. Prerequisite gate: adopt the ACINQ KMP stack

This is a dependency of the three milestones, not a fourth milestone.

Reference catalog: [ACINQ on Klibs.io](https://klibs.io/organization/ACINQ)

Primary projects:

- [ACINQ lightning-kmp](https://github.com/ACINQ/lightning-kmp)
- [ACINQ bitcoin-kmp](https://github.com/ACINQ/bitcoin-kmp)
- [ACINQ secp256k1-kmp](https://github.com/ACINQ/secp256k1-kmp)

### P0.1 Establish the ACINQ dependency baseline

Owner: separately assigned developer

Subtasks:

1. Add ACINQ's `lightning-kmp` and `bitcoin-kmp` libraries to the shared KMP
   dependency catalog.
2. Add the ACINQ `secp256k1-kmp` artifacts required by each Android, JVM test,
   and iOS target.
3. Pin mutually compatible immutable releases in the version catalog and
   dependency-verification metadata.
4. Document Android and iOS target support, native packaging requirements,
   licenses, binary-size considerations, and Android 16 KB page-size
   compatibility from the selected artifacts. Defer full artifact validation
   to the applicable milestone-close gate.
5. Inventory the ACINQ APIs RAYL will use for:
   - BOLT11 decoding and signature verification;
   - network and expiry validation;
   - exact and amountless invoice amounts;
   - payment hash and description/description-hash handling;
   - Bitcoin URI/address and Bech32 primitives where applicable;
   - cryptographic hashing and signature operations.
6. Define the rule that app code uses ACINQ as the source of protocol truth.
   RAYL may add policy checks that depend on product context, but must not
   reimplement an ACINQ protocol or cryptographic primitive.
7. Keep ACINQ types behind narrow app-local mapping functions when application
   state needs RAYL-owned value types or typed expected failures.
8. Add only basic direct-value unit tests for app-owned mapping or policy code
   when useful. Do not duplicate ACINQ's upstream conformance suite and do not
   introduce test doubles.

Acceptance criteria:

- The selected ACINQ artifacts resolve for Android debug and JVM host tests.
  The iOS coordinates and requirements are recorded for milestone-only target
  validation.
- Dependency versions and checksums are pinned centrally.
- No new custom BOLT11 parser, Bitcoin primitive, Bech32 implementation, hash
  implementation, or signature implementation exists.
- Application policy keeps invalid, wrong-network, and expired invoices from
  producing a payable draft without reimplementing or retesting ACINQ
  validation.

### P0.2 Adopt ACINQ in the legacy app

Subtasks:

1. Replace the legacy handwritten BOLT11 authorization path with the ACINQ
   Lightning/Bitcoin APIs.
2. Map ACINQ invoice data into legacy display models at one narrow boundary.
3. Keep app-specific network, clock, and payment-policy checks outside the
   library without re-parsing the invoice.
4. Remove the old parser and any redundant Bitcoin, Bech32, hashing, or
   signature code from payment authorization.
5. Confirm fixed and amountless invoice flows still render the same information.
6. Run the legacy Android debug build and any still-relevant app-owned pure
   unit tests once. Remove tests for deleted hand-rolled implementations; do
   not migrate or adapt them and do not add ACINQ behavior tests.

Acceptance criteria:

- Every legacy BOLT11 payment is decoded and cryptographically validated by
  ACINQ.
- There is no fallback from validation failure to the legacy parser.
- The final legacy Android debug state builds and is ready to freeze.

### P0.3 Freeze gate

Subtasks:

1. Confirm the ACINQ adoption changes are merged.
2. Stop accepting architecture work that exists only to support the combined
   provider binary.
3. Record the exact commit as the legacy reference.
4. Create the `papp-final` tag and `papp-legacy` branch.
5. Protect the branch from normal feature development.

Acceptance criteria:

- The tag and branch resolve to the same reviewed commit.
- Extraction work can refer to a stable legacy UI and behavior baseline.
- No uncommitted local configuration or secret is included in the freeze.

## 5. Target repository and app architecture

### 5.1 Repository topology

The Git root, currently one directory above the Gradle root, becomes the single
Gradle root:

```text
rayl/
├── apps/
│   ├── blip/
│   │   ├── shared/
│   │   ├── androidApp/
│   │   └── iosApp/
│   ├── lasr/
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
│   └── ui/
├── docs/
│   └── adr/
├── rayl.xcworkspace
├── settings.gradle.kts
├── build.gradle.kts
└── gradle/
```

Empty directories require a small placeholder file to remain in Git. They are
not included in Gradle settings until a real module exists.

### 5.2 Internal shape of an implemented app

Avoid creating additional Gradle modules until enforcement or build evidence
requires them. Each implemented app begins with one app-owned KMP `shared`
module:

```text
apps/<app>/shared/src/commonMain/kotlin/xyz/lilsus/rayl/<app>/
├── core/           # pure values and rules
├── application/    # workflows, state machines, ports
├── data/           # provider, SQL, preferences, network adapters
├── presentation/   # feature UiState/Action/Event and Compose screens
├── navigation/     # typed, ID-only routes and root coordination
├── platform/       # narrow expect declarations
└── di/             # composition only
```

Platform implementations remain in `androidMain`, `iosMain`, or the native
shell when Swift/Kotlin is clearer.

### 5.3 Required dependency direction

```text
Android/iOS shell
        ↓
presentation + app composition
        ↓
application workflows and ports
        ↓
pure app-owned core

data/provider/platform adapters → application ports
app shared module → foundation/ui + applicable ACINQ KMP libraries
```

Rules:

- `core` imports Kotlin and a small injected time/ID abstraction only.
- `application` does not import Compose, resources, Apollo, NWC, Koin, Settings,
  SQL implementations, or platform APIs.
- `presentation` observes application state but never owns payment truth.
- `data` maps provider/storage failures into typed application outcomes.
- Koin is allowed only at the composition boundary; normal code uses
  constructor injection.
- Each app defines and binds exactly one app-local payment backend.
- No code branches on provider type, build flavor, bundle ID, or application ID.

### 5.4 Payment architecture required in both apps

Each app independently implements:

```text
scan / paste / app link
        ↓
normalize and classify once
        ↓
resolve protocol and validate invoice
        ↓
ephemeral PaymentDraft
        ↓
confirmation policy
        ↓
persist PaymentAttempt before provider mutation
        ↓
single app-owned PaymentBackend
        ↓
Settled | AlreadyPaid | Rejected | Pending | Unknown
        ↓
durable reconciliation on launch/foreground
```

Required invariants:

- Every provisioning creates a new opaque `ConnectionId`.
- An attempt retains the `ConnectionId` that created it.
- Reconnect cannot redirect an old attempt to new credentials.
- Unknown payment truth never becomes failure without authoritative evidence.
- Fixed invoices and dynamic requests have different duplicate behavior.
- Draft UI state and durable attempt truth are separate.
- Commands that mutate a payment session are serialized.
- Cancellation is rethrown and never mapped to an ordinary provider error.
- Navigation carries identifiers, never credentials, invoices, API keys, or NWC
  connection URLs.

### 5.5 Storage required in both apps

Use:

- a transactional KMP SQL database for public connection profiles, contacts,
  shortcuts, payment attempts, and small transition history;
- typed preferences for theme, language, currency, haptics, confirmation
  settings, and onboarding version;
- a platform credential vault keyed by `ConnectionId`;
- an ephemeral sensitive handoff for credential-bearing scan/app-link input.

SQLDelight is the default database choice. If implementation evidence favors a
different KMP SQL library, record the decision in an ADR before app persistence
work begins. Do not store aggregate JSON documents as database rows or
preferences.

## 6. Milestone 1 — New project skeleton exists

### Outcome

The repository root represents RAYL and contains the complete target directory
shape. App placeholders do not yet build or contain product code. The shared
version catalog records the pinned ACINQ dependency baseline for the later app
implementations.

### M1.1 Capture the extraction baseline

Dependencies: P0.3

Subtasks:

1. Record the legacy tag, dependency versions, Android/iOS baselines, current
   product identities, and supported locales.
2. Build the capability matrix in section 8 by walking every reachable legacy
   screen and provider path.
3. Capture reference screenshots for compact Android and iOS in light and dark
   themes.
4. Record provider-specific UI, resources, deep links, permissions, native
   code, and build dependencies.
5. Record known broken behavior from the architecture review so it is not
   mistaken for required parity.
6. Mark each capability as:
   - required in both apps;
   - Blink-only;
   - NWC-only;
   - visual-only disabled option during extraction;
   - deferred polish.

Acceptance criteria:

- Every legacy screen and externally reachable entry path appears in the
  matrix.
- Known defects are labeled `do not reproduce`.
- Screenshots and inventory contain no credential or customer data.

### M1.2 Establish the Git and repository identity

Dependencies: M1.1

Subtasks:

1. Rename the repository to `rayl` through the repository host.
2. Update the local remote and root documentation.
3. Set `rootProject.name = "rayl"`.
4. Move the Gradle wrapper, version catalog, root build configuration, and
   shared repository configuration to the Git root.
5. Update paths in scripts, hooks, ignore rules, developer setup, and CI.
6. Do not move `local.properties`, signing files, `.env` files, or generated
   build directories.
7. Remove stale `composeApp` and old nested-root references.

Acceptance criteria:

- `./gradlew projects` runs from the Git root.
- No supported build command requires changing into the old `app` directory.
- A clean checkout does not rely on machine-specific absolute paths.
- Repository history still contains the `papp-final` tag and `papp-legacy`
  branch.

### M1.3 Move the ACINQ dependency baseline to the RAYL root

Dependencies: M1.2

Subtasks:

1. Move the pinned ACINQ version aliases and dependency-verification metadata
   into the root version catalog.
2. Document the target-specific `secp256k1-kmp` artifacts required by Android,
   JVM host tests, and iOS.
3. Record `lightning-kmp` and `bitcoin-kmp` as the required protocol libraries
   for Blip and Lasr.
4. Add a dependency-policy check that rejects unapproved alternative Bitcoin,
   Lightning, Bech32, or secp256k1 implementations.
5. Keep the catalog entries available without creating an otherwise empty
   wrapper module.

Acceptance criteria:

- ACINQ versions and checksums have one source of truth at the RAYL root.
- No `foundation/bolt11` or generic protocol wrapper exists.
- App modules can consume the pinned target-appropriate artifacts directly when
  they are implemented.

### M1.4 Create the complete directory topology

Dependencies: M1.2

Subtasks:

1. Create all six app directories and the three placeholder child directories
   under each app.
2. Track empty placeholders with a minimal `.gitkeep` or README.
3. Create `foundation/ui` as a placeholder, not yet a shared-design dumping
   ground.
4. Create `docs/adr`.
5. Create an empty `rayl.xcworkspace`; add app projects only when those projects
   become real.
6. Add a short app intent README for Flint, Quark, Nutrino, and Femto without
   adding SDK dependencies or fake architecture.
7. Record the privacy deferrals required by `PLAN-REFINED.md` in the Nutrino and
   Femto READMEs and the Second/Bark decision in the Quark README.

Acceptance criteria:

- The target topology exists exactly once at the Git root.
- No placeholder app is included as a fake Gradle module.
- No speculative provider dependency has been added.
- No provider implementation is stored in `foundation`.

### M1.5 Remove the combined app from active `main`

Dependencies: M1.1, M1.3, M1.4

Subtasks:

1. Verify that UI references, the capability inventory, and the legacy tag are
   sufficient for extraction.
2. Remove the combined app modules and obsolete generated/configuration files
   from active `main`.
3. Retain durable product/release documentation that still applies to RAYL.
4. Move or rewrite documentation whose paths assume the nested Gradle root.
5. Ensure no secret-bearing sample, local E2E state, or generated artifact is
   carried into the skeleton.

Acceptance criteria:

- Active `main` contains no buildable combined NWC/Blink application.
- Legacy source remains retrievable from `papp-final`.
- Root project discovery and the narrowly relevant skeleton checks pass without
  requiring placeholder apps to build.

### Milestone 1 exit gate

- The Git and Gradle root is the RAYL root.
- All six app topologies exist but may be empty.
- `foundation/ui` is empty or contains scaffolding only.
- The root catalog pins the ACINQ KMP protocol stack.
- The legacy application is frozen and absent from active `main`.
- The extraction capability matrix and safe screenshot baseline exist.

## 7. Shared feature definitions

### 7.1 Complete UI state

Every feature ViewModel/store exposes one complete immutable `UiState`, accepts
explicit `Action` values, and emits only genuinely one-shot UI events. Scanner
activity, dismissibility, controls, and displayed content must derive from the
same state.

### 7.2 Typed outcomes

Finite operations return values, app-specific expected failure types, or a
small two-case outcome. Loading is presentation state. Raw provider messages
and `Throwable` do not enter user-visible domain models.

### 7.3 Pure values

At minimum, introduce validated types for:

- `MilliSatoshi`;
- `Satoshi`;
- `PaymentHash`;
- `AttemptId`;
- `ConnectionId`;
- provider account/public-key identity;
- `CurrencyCode`;
- provider credential wrappers with redacted `toString()`.

Fiat calculations use integer/fixed-point arithmetic, explicit rounding,
overflow checks, and a recorded rate snapshot.

### 7.4 UI preservation strategy

Because Blip is implemented first, do not put wholesale legacy UI into
`foundation/ui` before two consumers exist:

1. Copy visual code and resources into Blip's app-owned presentation area.
2. Remove business state, provider routing, and navigation side effects while
   preserving rendered appearance.
3. Repeat for Lasr using the legacy baseline and the verified Blip visuals.
4. Once both apps render the same component, promote only provider-neutral
   themes, hero visuals, controls, and screen sections to `foundation/ui`.
5. Keep complete screens duplicated when sharing would require provider
   conditionals or distorted state.

This prevents temporary migration staging from becoming permanent foundation
debt.

## 8. Capability matrix and parity contract

M1.1 must expand this matrix with the exact legacy routes and reference
screenshots. The following is the minimum extraction scope.

| Capability | Blip | Lasr | Initial extraction expectation |
| --- | --- | --- | --- |
| Welcome/features onboarding | Yes | Yes | Preserve UI and progression |
| Provider choice UI | NWC disabled | Blink disabled | Presentation only; no provider router |
| Risk/agreement flow | Yes | Yes | Cannot be bypassed by app links |
| Provider provisioning | API key | NWC URI | App-owned secure flow |
| Reconnect/remove connection | Yes | Yes | New `ConnectionId` on reconnect |
| Blink wallet discovery/default wallet | Yes | No | Generated GraphQL models |
| Blink contact import | Yes | No | App-owned feature |
| NWC capability/wallet discovery | No | Yes | App-owned NWC behavior |
| Camera scan | Yes | Yes | Narrow platform scanner port |
| Paste/manual input | Yes | Yes | Same normalization boundary as scan |
| Standard payment app links | Yes | Yes | Best effort per platform |
| `nostr+walletconnect` app link | No | Yes | Ephemeral secret handoff |
| Fixed BOLT11 | Yes | Yes | Validated before submission |
| Amountless BOLT11 | Yes | Yes | Explicit amount and confirmation |
| LNURL-pay | Yes | Yes | Strict callback and metadata validation |
| Lightning address | Yes | Yes | Resolved through LNURL policy |
| Bitcoin URI with Lightning request | Yes | Yes | Typed classification |
| Unsupported BOLT12/on-chain input | Yes | Yes | Explicit safe UI outcome |
| Confirmation policy | Yes | Yes | Explicit pure boundary policy |
| Payment submit | Blink only | NWC only | Durable attempt first |
| Payment lookup/reconciliation | Blink only | NWC only | Original connection generation |
| Unknown/pending handling | Yes | Yes | Never silently converted to failure |
| Fixed/dynamic duplicate behavior | Yes | Yes | Separate explicit rules |
| Session transactions/detail | Yes | Yes | Backed by durable attempt records |
| Continue scanning during pending work | Yes | Yes | Draft and attempt state separated |
| Manual amount entry | Yes | Yes | Typed and overflow-safe |
| Primary/secondary currencies | Yes | Yes | Fixed-point conversion |
| Exchange-rate display | Yes | Yes | Freshness and snapshot policy |
| Payment preferences | Yes | Yes | Typed preferences |
| Haptic preferences | Yes | Yes | Platform port |
| Contacts CRUD | Yes | Yes | Transactional SQL storage |
| Shortcuts CRUD/payment | Yes | Yes | Safe confirmation/origin rules |
| Theme/language settings | Yes | Yes | Preserve supported locales |
| Donation flow | Yes | Yes | Uses normal payment workflow |
| Connection/settings screens | Yes | Yes | Current look; provider-specific behavior |
| Android/iOS lifecycle | Yes | Yes | Explicit, idempotent ownership |

“Yes” means behavioral parity, not reuse of the legacy implementation.

## 9. Milestone 2 — Blink is extracted into `apps/blip`

### Outcome

Blip is a functional Blink-only Android/iOS KMP app with the full applicable
legacy behavior, near-identical UI, greenfield internal architecture, durable
payment truth, and no linked NWC implementation.

### B1 Bootstrap the Blip application

Dependencies: Milestone 1

Subtasks:

1. Create `apps/blip/shared`, `apps/blip/androidApp`, and an independent
   `apps/blip/iosApp` Xcode project.
2. Add the app project to `rayl.xcworkspace`.
3. Use:
   - application/bundle ID `xyz.lilsus.blip`;
   - Kotlin namespace `xyz.lilsus.rayl.blip`;
   - Android min SDK 29;
   - Android ARM64 and x86_64 baseline;
   - iOS 17.0 baseline.
4. Define isolated debug/test IDs and storage namespaces.
5. Add the pinned ACINQ `lightning-kmp`, `bitcoin-kmp`, and target-specific
   `secp256k1-kmp` artifacts.
6. Add only the remaining dependencies justified by Blip.
7. Add the Apollo plugin and generated Blink operations to Blip only.
8. Add app-owned build and unit-test commands.

Acceptance criteria:

- The empty Blip Android debug shell builds and launches during implementation.
- The iOS shell and workspace are structurally configured; they are built at
  the milestone-close gate.
- Blip does not depend on the NWC SDK.
- The Blip framework, resources, and generated GraphQL package use Blip
  namespaces.

### B2 Record Blip architecture decisions and boundaries

Dependencies: B1

Subtasks:

1. Record concise ADRs for:
   - app package/dependency boundaries;
   - money/identity types;
   - expected outcome/error policy;
   - payment-attempt lifecycle;
   - persistence and credential vault;
   - DI/coroutine ownership;
   - navigation/app-link handoff;
   - protocol/network trust;
   - backup/reinstall behavior.
2. Add a dependency rule or static check preventing application/core code from
   importing Compose, Apollo, Koin, Settings, or platform APIs.
3. Add a forbidden-dependency check for NWC artifacts and packages.
4. Create a Blip data-flow/privacy note covering the API endpoint, credential
   handling, request metadata, storage, and logs.

Acceptance criteria:

- Each decision has a code or build check that can enforce it where practical.
- The ADRs describe rejected legacy mechanisms, not only the chosen library.

### B3 Recreate the visual product in Blip

Dependencies: B1

Subtasks:

1. Copy the legacy Compose resources and visual components into Blip.
2. Rename generated resource packages and remove legacy `papp` identity.
3. Preserve themes, typography, shapes, hero animation, layouts, strings, and
   stable semantics/test tags.
4. Recreate screen contracts around complete feature `UiState` values and
   callbacks.
5. Remove direct repository, Koin, navigation, provider, and persistence access
   from composables.
6. Keep the provider-selection experience temporarily, with NWC visibly
   unavailable.
7. Remove NWC-specific help, credential, and connection behavior while retaining
   any neutral visual structure needed for parity.
8. Add previews or deterministic state fixtures for important screen states.

Acceptance criteria:

- The reference screen checklist has no unexplained visual omission.
- No copied composable imports a legacy ViewModel or provider implementation.
- NWC cannot be selected or reached.

### B4 Implement Blip core values, policies, and persistence

Dependencies: B2

Subtasks:

1. Implement validated money, rate, hash, connection, account, attempt, and
   credential types.
2. Implement typed operation outcomes and bounded error families.
3. Implement the payment draft and durable attempt state machines.
4. Implement fixed-versus-dynamic request fingerprint and duplicate policies.
5. Implement the exact confirmation decision table, including input origin and
   threshold boundaries.
6. Add SQLDelight schema and transactions for:
   - public Blink connection profile;
   - payment attempts and bounded transition history;
   - contacts;
   - shortcuts.
7. Add typed preference stores for non-transactional user settings.
8. Add Android and iOS credential vault implementations for the Blink API key,
   addressed only by `ConnectionId`.
9. Define explicit backup/reinstall behavior and exclude credentials from
   unsafe backup paths.

Acceptance criteria:

- No secret is stored in SQL, preferences, routes, logs, or public model string
  output.
- An attempt is written before the backend can be invoked.
- Removing/replacing the connection does not mutate old attempt provenance.
- Basic direct-value unit tests cover selected pure transitions, duplicate
  rules, confirmation boundaries, money conversion, and redaction.
- Persistence and application workflow tests are deferred; do not create fake
  stores to test them.

### B5 Implement Blink provisioning

Dependencies: B3, B4

Subtasks:

1. Implement API-key entry and validation as an ephemeral credential draft.
2. Construct the Apollo client in Blip data code with explicit lifecycle,
   timeout, header, and redacted logging policy.
3. Use generated GraphQL operations for account and wallet discovery.
4. Map provider errors by stable GraphQL/provider codes and structures, never
   raw message matching.
5. Create a new `ConnectionId`, vault the key, and commit the public profile
   transactionally only after validation succeeds.
6. Implement default-wallet selection/refresh and reauthentication state.
7. Implement disconnect/reconnect without deleting unresolved payment truth.
8. Wire current provisioning, wallet-management, and onboarding visuals.

Acceptance criteria:

- API keys never enter navigation arguments or saved UI state.
- Invalid credentials produce a typed, localizable failure.
- Reconnect creates a new connection generation.
- Provisioning is exercised in the Android debug app and recorded for QA.
  A small pure provider-code-to-error mapping may be unit-tested directly, but
  no fake Blink client or network test is added.

### B6 Implement input resolution and protocol trust

Dependencies: B3, B4, P0.1

Subtasks:

1. Implement one parser entry point for scan, paste, manual entry, and app links.
2. Decode and validate BOLT11 invoices with ACINQ `lightning-kmp` and its
   `bitcoin-kmp`/`secp256k1-kmp` stack.
3. Classify BOLT12, on-chain-only Bitcoin, LNURL-withdraw, NWC, and malformed
   input explicitly.
4. Implement LNURL-pay and Lightning-address resolution with:
   - HTTPS and redirect policy;
   - private/local-network restrictions;
   - response size, status, and content-type checks;
   - exact amount checks;
   - `description_hash == SHA256(metadataRaw)`;
   - comment/payer-data validation.
5. Implement exchange-rate retrieval, freshness, positive/finite checks, and
   integer conversion.
6. Construct an immutable `PaymentDraft` containing the normalized source and
   rate snapshot required for confirmation.

Acceptance criteria:

- Every input source reaches the same classification path.
- NWC input is reported as unsupported and cannot provision anything.
- Basic deterministic app-owned input classification and policy examples may
  be covered by direct-value unit tests. Do not retest ACINQ protocol behavior.
- Hostile network/protocol scenarios are recorded for QA; network adapters have
  no developer-owned integration harness at this stage.

### B7 Implement the Blink payment coordinator

Dependencies: B4, B5, B6

Subtasks:

1. Define an app-local `PaymentBackend` with Blink submit and lookup outcomes.
2. Implement it with generated Apollo operations and the attempt's immutable
   `ConnectionId`.
3. Implement one serialized payment command processor.
4. Persist an attempt before calling Blink.
5. Persist provider correlation, invoice hash, submission time, and every
   authoritative outcome.
6. Preserve `Pending` and `Unknown` honestly.
7. Reconcile non-final attempts at launch and foreground using the original
   credential generation.
8. Implement fixed-invoice duplicate blocking and dynamic-request retry/new
   invoice behavior.
9. Allow a new draft/scanner session while previous attempts reconcile.
10. Rethrow coroutine cancellation through every adapter.

Acceptance criteria:

- The ViewModel/store does not own settlement truth.
- Process recreation can reconstruct non-final attempts from the store
  contract.
- Lookup cannot silently use the current replacement connection.
- Basic pure transition and duplicate-policy tests may cover representative
  direct-value cases. End-to-end coordinator, timeout, cancellation, reconnect,
  and provider-outcome scenarios are recorded for Android debug/QA validation;
  do not build fake collaborators for them.

### B8 Wire the complete Blip product

Dependencies: B3, B5, B7

Subtasks:

1. Wire onboarding and consent without allowing app links to bypass them.
2. Wire scan, paste, manual amount, confirmation, submit, result, pending retry,
   transactions, and transaction detail.
3. Wire Blink contact import.
4. Reimplement contacts and shortcut CRUD on transactional storage.
5. Wire shortcut-origin and manual-entry confirmation rules.
6. Wire currencies, exchange-rate display, payment preferences, theme,
   language, haptics, donation, and wallet settings.
7. Use typed, ID-only navigation routes.
8. Replace global event buses with an app coordinator that queues one pending
   input until consent/navigation is ready.
9. Ensure destructive actions have confirmation and never erase unresolved
   attempts.

Acceptance criteria:

- Every Blip row in the capability matrix is implemented or has an approved,
  explicit exception.
- Displayed home state and scanner activity cannot disagree.
- Navigation performs no database or onboarding side effects.

### B9 Implement Blip platform boundaries and links

Dependencies: B8

Subtasks:

1. Implement camera/scanner, clipboard, haptics, connectivity, lifecycle,
   language, and system-bar adapters behind narrow ports.
2. Keep complex native implementations in Swift/Kotlin when clearer.
3. Make scanner and lifecycle start/stop calls idempotent and observable.
4. Register only the supported standard payment schemes.
5. Do not register `nostr+walletconnect`.
6. Apply app-specific permissions, network policy, backup rules, icons, and
   display names.
7. Keep Android packaging configuration compatible with ARM64, x86_64, and
   16 KB page-size requirements. Perform the artifact-level validation at B10.

Acceptance criteria:

- Cold-start, warm-start, background, foreground, permission denial, and
  rotation do not create duplicate scanners or payment commands.
- Standard app links enter the root coordinator.
- The Blip manifest/Info.plist contains no NWC scheme or provider declaration.

### B10 Verify and close the Blip extraction

Dependencies: B1–B9

Subtasks:

1. Run formatting, the allowed basic unit tests, and the Android debug build.
   Then run the dedicated milestone-only Android release and iOS
   framework/application validation once; do not repeat it for the preceding
   tasks or commits.
2. Confirm the DI graph through Android debug startup and static dependency
   inspection without starting provider I/O; do not create a test DI graph.
3. Inspect dependency reports, manifests, generated sources, and artifacts for
   NWC leakage.
4. Manually run the full capability matrix on Android and iOS.
5. Manually verify at least:
   - API-key onboarding;
   - fixed invoice payment;
   - amountless invoice payment;
   - Lightning address/LNURL payment;
   - duplicate handling;
   - pending/unknown display and later lookup;
   - disconnect/reconnect while an attempt is unresolved;
   - app restart with an unresolved attempt;
   - contacts, shortcuts, currencies, settings, and app links.
6. Compare all reference screens and record intentional differences.
7. Run a secret-denylist scan over logs, navigation state, database files, and
   diagnostics captured during manual testing.
8. Delete unused copied legacy code and resources.
9. Record deferred product polish without blocking extraction.

Acceptance criteria:

- Blip contains no NWC SDK, implementation, permission, route, or credential
  model.
- No runtime provider selection exists.
- The complete Blip capability checklist passes manually on both platforms.
- All new automated tests satisfy the strict basic-unit-test limits in section
  3.5 and use no mocks, stubs, or fakes.
- There are no migrated integration, Maestro, or E2E tests.

### Milestone 2 exit gate

- `apps/blip` is the functional Blink-only application.
- Its UI is effectively identical to the applicable legacy UI.
- NWC is only an unavailable visual option where needed for temporary parity.
- Payment attempts are durable and use honest outcome semantics.
- Provider credentials are isolated and redacted.
- Architecture-review P0 correctness/security issues are not reproduced.
- The dedicated milestone validation confirms Blip's Android/iOS and release
  targets; its allowed basic unit tests pass.

## 10. Milestone 3 — Lasr is extracted into `apps/lasr`

### Outcome

Lasr is a functional NWC-only Android/iOS KMP app with the full applicable
legacy behavior, near-identical UI, greenfield internal architecture,
generation-aware NWC lifecycle, and no linked Blink/Apollo implementation.

### L1 Bootstrap the Lasr application

Dependencies: Milestone 2

Subtasks:

1. Create `apps/lasr/shared`, `apps/lasr/androidApp`, and an independent
   `apps/lasr/iosApp` Xcode project.
2. Add the project to `rayl.xcworkspace`.
3. Use:
   - application/bundle ID `xyz.lilsus.lasr`;
   - Kotlin namespace `xyz.lilsus.rayl.lasr`;
   - Android min SDK 29;
   - Android ARM64 and x86_64 baseline;
   - iOS 17.0 baseline.
4. Define isolated debug/test IDs and storage namespaces.
5. Add the pinned ACINQ `lightning-kmp`, `bitcoin-kmp`, and target-specific
   `secp256k1-kmp` artifacts.
6. Add the selected `nwc-kmp` dependency only to Lasr. Record its exact
   coordinate and the accepted snapshot risk; move to an immutable release when
   one is available without making that upstream event an extraction blocker.
7. Do not apply Apollo or copy Blink generated sources.

Acceptance criteria:

- The empty Lasr Android debug shell builds and launches during implementation.
- The iOS shell and workspace are structurally configured; they are built at
  the milestone-close gate.
- Lasr has no dependency on Apollo or Blink code.

### L2 Establish Lasr's independent architecture

Dependencies: L1

Subtasks:

1. Re-evaluate the Blip ADRs for Lasr rather than accepting them automatically.
2. Adopt identical decisions only where NWC semantics support them.
3. Create Lasr-owned core, application, data, presentation, navigation,
   platform, and DI packages.
4. Add forbidden-dependency checks for Apollo, Blink endpoints, generated
   operations, and Blink credential types.
5. Create the NWC provider data-flow/privacy note covering relay/operator
   metadata, connection URL secrets, lifecycle, storage, and logs.

Acceptance criteria:

- Lasr can be understood without knowing Blip's implementation.
- Shared behavior is not introduced merely to remove duplicated source.

### L3 Recreate the visual product in Lasr

Dependencies: L1

Subtasks:

1. Recreate the legacy visual baseline using the verified Blip appearance as an
   additional comparison, not as a business-logic dependency.
2. Preserve all applicable screens, themes, resources, locales, hero behavior,
   and semantic tags.
3. Keep the provider-selection experience temporarily, with Blink visibly
   unavailable.
4. Remove Blink API-key, wallet-selection, and contact-import behavior.
5. Bind screens to Lasr-owned complete state and explicit callbacks.

Acceptance criteria:

- The Lasr screen checklist has no unexplained visual omission.
- Blink cannot be selected or reached.
- No Lasr composable depends on Blip code.

### L4 Implement Lasr core values, policies, and persistence

Dependencies: L2

Subtasks:

1. Independently implement or deliberately adapt the required value types,
   typed outcomes, payment states, duplicate policy, and confirmation policy.
2. Use the same storage categories as Blip with Lasr-specific schemas and
   namespaces.
3. Store the public NWC profile separately from its secret connection material.
4. Vault the NWC secret by `ConnectionId`.
5. Store payment attempts before NWC submission and retain connection
   provenance.
6. Add only basic direct-value unit tests for selected Lasr-owned pure rules.
   Do not copy Blip tests as a contract suite or introduce test doubles.

Acceptance criteria:

- No NWC URI appears in public SQL, preferences, routes, logs, or model string
  output.
- Lasr rules can diverge without adding optional behavior to shared modules.

### L5 Implement NWC provisioning and client lifecycle

Dependencies: L3, L4

Subtasks:

1. Accept NWC connection input through scan, paste, and
   `nostr+walletconnect` app links.
2. Normalize the URI into an ephemeral secret draft.
3. Validate relay URLs, wallet public key, secret, and required fields before
   provisioning.
4. Create a fresh `ConnectionId` and commit profile/vault state safely.
5. Implement one connection manager keyed by `ConnectionId`.
6. Serialize client creation and reuse the live relay connection.
7. Make proactive connection, background behavior, foreground reconnect,
   disconnect, eviction, and cancellation explicit.
8. Prevent a cached client from surviving connection replacement.
9. Implement capability/wallet discovery and typed NWC failure mapping.
10. Wire current onboarding and connection-management visuals.

Acceptance criteria:

- A client can never be retrieved for the wrong connection generation.
- Disconnect/reconnect cannot redirect an unresolved attempt.
- The secret URI is wiped from ephemeral UI state after consumption.
- Exercise connection lifecycle in the Android debug app and record concurrency,
  reuse, eviction, lifecycle, and cancellation scenarios for QA. Do not create
  fake client/session factories or a relay test harness.

### L6 Implement Lasr input resolution and payment workflow

Dependencies: L4, L5, P0.1

Subtasks:

1. Implement the same input capability set required for Lasr through one
   Lasr-owned normalization boundary.
2. Use the pinned ACINQ `lightning-kmp`, `bitcoin-kmp`, and
   `secp256k1-kmp` stack for invoice and Bitcoin/Lightning protocol handling.
3. Implement strict LNURL/Lightning-address and exchange-rate policies without
   sharing Blink networking by default.
4. Define the Lasr-local backend using NWC pay and lookup capabilities.
5. Serialize payment commands and persist the attempt before NWC mutation.
6. Map NWC outcomes into settled, already paid, rejected, pending, and unknown
   without relying on message strings.
7. Reconcile through the attempt's original connection generation.
8. Preserve fixed/dynamic duplicate behavior and concurrent scanning.
9. Rethrow cancellation and make background policy explicit.

Acceptance criteria:

- The workflow has no `WalletType`, provider registry, or Blink branch.
- Basic direct-value tests may cover pure payment-state and duplicate-policy
  decisions. Coordinator and reconnect scenarios are validated in Android
  debug/QA without fakes.
- NWC timeouts and inconclusive lookup remain unknown.

### L7 Wire the complete Lasr product

Dependencies: L3, L5, L6

Subtasks:

1. Wire onboarding, consent, provisioning, and queued NWC app-link behavior.
2. Wire scan, paste, amount, confirmation, payment, result, transaction, and
   reconciliation UI.
3. Reimplement contacts, shortcuts, currencies, exchange rates, payment
   preferences, theme, language, haptics, donation, and connection settings.
4. Use ID-only typed navigation and a root coordinator.
5. Keep Blink contact import absent rather than introducing a no-op provider
   capability.
6. Preserve the applicable legacy appearance and interaction sequence.

Acceptance criteria:

- Every Lasr row in the capability matrix is implemented or has an approved,
  explicit exception.
- App links cannot bypass onboarding/consent.
- Presentation never becomes the owner of durable payment truth.

### L8 Implement Lasr platform boundaries and links

Dependencies: L7

Subtasks:

1. Implement the same narrow platform capability categories as Blip using
   Lasr-owned adapters where behavior differs.
2. Register standard payment schemes and `nostr+walletconnect`.
3. Route credential-bearing NWC links only through the ephemeral handoff.
4. Validate camera and NWC lifecycle behavior across foreground/background
   transitions.
5. Apply Lasr-specific permissions, network declarations, backup exclusions,
   icons, and display names.
6. Keep Android packaging configuration compatible with the required ABIs and
   16 KB native-library requirements. Perform artifact-level validation at L10.

Acceptance criteria:

- Only Lasr advertises the NWC scheme.
- Native lifecycle events cannot create duplicate NWC clients or submissions.
- No NWC credential is restorable through general saved state.

### L9 Build the demonstrated UI foundation

Dependencies: B3, L3, L7

Subtasks:

1. Compare the working Blip and Lasr themes, resources, hero, controls, and
   screen sections.
2. Promote only components with identical visual and behavioral semantics into
   `foundation/ui`.
3. Parameterize simple app name, icon, copy, and theme differences.
4. Keep onboarding/provider UI app-owned when sharing requires a provider
   condition.
5. Move provider-aware and single-app resources back into their owning app.
6. Add small UI-state/previews for shared components; do not introduce a broad
   screenshot-test framework.
7. Add dependency checks preventing foundation UI from importing either app,
   Apollo, NWC, provider credentials, app navigation, or provider states.

Acceptance criteria:

- `foundation/ui` is provider-neutral.
- Both apps are real consumers of every promoted component.
- Removing either app would not make the foundation API mention the other.
- Intentional duplication is documented rather than “fixed.”

### L10 Verify and close the Lasr extraction

Dependencies: L1–L9

Subtasks:

1. Run formatting, the allowed basic unit tests, and the Android debug build.
   Then run the dedicated milestone-only Android release and iOS
   framework/application validation once; do not repeat it for the preceding
   tasks or commits.
2. Confirm the DI graph through Android debug startup and static dependency
   inspection without opening a relay connection; do not create a test DI
   graph.
3. Inspect dependencies, manifests, generated sources, and artifacts for
   Blink/Apollo leakage.
4. Manually run the full Lasr capability matrix on Android and iOS.
5. Manually verify at least:
   - scan/paste/NWC-link provisioning;
   - NWC reconnect and lifecycle reuse;
   - fixed and amountless invoices;
   - Lightning address/LNURL payment;
   - duplicate behavior;
   - pending/unknown and later reconciliation;
   - disconnect/reconnect while unresolved;
   - app restart while unresolved;
   - contacts, shortcuts, currencies, settings, and payment app links.
6. Compare all reference screens and record intentional differences.
7. Run a secret-denylist scan over logs, routes, saved state, database files,
   and diagnostics captured during manual testing.
8. Remove unused copied legacy code/resources and complete foundation cleanup.
9. Compare Blip and Lasr business implementations and explicitly decide to keep
   duplication or schedule a later promotion. Do not refactor merely because
   the code looks similar.

Acceptance criteria:

- Lasr contains no Apollo plugin/runtime, Blink operation, endpoint, route, or
  credential model.
- No runtime provider selection exists.
- The complete Lasr capability checklist passes manually on both platforms.
- All new automated tests satisfy the strict basic-unit-test limits in section
  3.5 and use no mocks, stubs, or fakes.
- There are no migrated integration, Maestro, or E2E tests.

### Milestone 3 exit gate

- `apps/lasr` is the functional NWC-only application.
- `apps/blip` remains functional and Blink-only.
- Both apps preserve the applicable legacy UI and complete behavior.
- The disabled provider choices are presentation-only and can be removed during
  product polish.
- `foundation/ui` is clean and provider-neutral.
- ACINQ libraries are the shared Bitcoin/Lightning protocol implementation;
  RAYL has no custom protocol or cryptography foundation.
- The dedicated milestone validation confirms both apps' Android/iOS and
  release targets; all allowed basic unit tests pass.
- The architecture review's payment correctness, state ownership, security,
  persistence, DI, lifecycle, and provider-isolation problems are not
  reproduced.

## 11. Verification commands

Exact task names are finalized when the new modules are created.

### 11.1 Routine per-task feedback

Run only the commands relevant to the app and code changed. Do not run both app
blocks merely because they are listed here.

```text
./gradlew ktlintCheck
./gradlew :apps:blip:shared:testAndroidHostTest
./gradlew :apps:blip:androidApp:assembleDebug
./gradlew :apps:lasr:shared:testAndroidHostTest
./gradlew :apps:lasr:androidApp:assembleDebug
```

The host-test commands run only the basic unit tests permitted by section 3.5.
Skip them when the task has no such tests. Do not replace them with `allTests`,
`check`, a connected-device suite, or an iOS test task.

### 11.2 Milestone-only validation

At B10 and L10, run the broader target and artifact validation once. B10 runs
only the Blip commands; L10 validates Lasr and revalidates Blip as the final
two-app milestone. The exact release/iOS commands depend on the generated
projects, but the gates have root-level equivalents of:

```text
./gradlew verifyAcinqDependencies
./gradlew verifyBlipProviderIsolation
./gradlew verifyLasrProviderIsolation
./gradlew :apps:blip:androidApp:assembleRelease
./gradlew :apps:blip:shared:linkReleaseFrameworkIosArm64
./gradlew :apps:lasr:androidApp:assembleRelease
./gradlew :apps:lasr:shared:linkReleaseFrameworkIosArm64
```

The isolation tasks inspect both dependency graphs and packaged/generated
content. Source-level grep alone is insufficient. The milestone process also
builds/launches the appropriate iOS application configurations for manual QA.
These commands are deliberately absent from routine task validation and do not
authorize cross-target integration or E2E test suites.

## 12. Pull-request dependency sequence

```text
P0.1 ACINQ dependency baseline
  → P0.2 legacy ACINQ adoption
  → P0.3 freeze
  → M1 repository skeleton
  → B1–B4 Blip shell/core
  → B5–B7 Blip provider/payment
  → B8–B10 Blip complete product and verification
  → L1–L4 Lasr shell/core
  → L5–L8 Lasr provider/product
  → L9 shared UI classification
  → L10 final verification
```

Within an app, visual recreation, pure core policies, and platform adapter
preparation may proceed in parallel after the app shell and ADR boundaries
exist. Provider submission must not be wired before durable-attempt creation,
credential isolation, and validated invoice handling exist.

## 13. Debt disposition

### Must be eliminated during extraction

- runtime provider routing;
- provider code leakage between artifacts;
- incomplete BOLT11 authorization;
- permissive LNURL trust behavior;
- secret-bearing navigation/state/logging;
- raw interchangeable money/hash/credential strings;
- floating-point payment conversions;
- session-only payment attempts;
- unknown-to-failed coercion;
- mutable-current-connection reconciliation;
- fragmented payment UI state;
- oversized state holders and hidden mutable workflow state;
- global lossy event buses;
- JSON aggregate persistence;
- pass-through use-case/repository ceremony;
- Koin service location and hidden scopes;
- swallowed cancellation;
- stale module paths and non-real build checks.

### May remain visually during extraction

- the current onboarding sequence;
- provider choice screen with the unavailable provider disabled;
- settings that are candidates for later provider-specific removal;
- the current theme, layout, copy, hero animation, and screen organization.

### Explicitly deferred until post-extraction polish

- provider-specific onboarding redesign;
- removing the disabled provider choice;
- settings and feature pruning;
- per-app visual themes;
- broad adaptive-layout redesign;
- complete accessibility and reduced-motion audit;
- animation/scanner performance optimization;
- automated integration tests;
- Maestro/E2E suites and old regtest harness migration;
- analytics and crash-reporting provider selection;
- store production readiness and full release provenance;
- cross-app contacts/shortcuts;
- implementation of Flint, Quark, Nutrino, or Femto;
- promoting duplicated business logic into a new foundation module.

Deferral is not permission to reproduce unsafe state, payment, credential,
protocol, concurrency, or provider-coupling mechanisms.

## 14. Final completion checklist

- [ ] ACINQ's KMP stack validates legacy BOLT11 payments before freeze.
- [ ] `papp-final` and `papp-legacy` identify the frozen combined app.
- [ ] The RAYL Git root is the only Gradle root.
- [ ] All six app directory shapes exist.
- [ ] Blip implements every applicable legacy capability.
- [ ] Lasr implements every applicable legacy capability.
- [ ] The initial UI remains effectively identical in both apps.
- [ ] Blip cannot compile, advertise, navigate to, or execute NWC behavior.
- [ ] Lasr cannot compile, advertise, navigate to, or execute Blink behavior.
- [ ] Both apps persist attempts before submission.
- [ ] Both apps bind reconciliation to the original connection generation.
- [ ] Both apps preserve unknown payment truth.
- [ ] Credentials stay in app-specific vaults and ephemeral handoffs.
- [ ] `foundation/ui` is provider-neutral and has at least two consumers per
      promoted component.
- [ ] No legacy test suite has been migrated.
- [ ] Newly written tests are basic direct-value unit tests only, use no mocks,
      stubs, or fakes, and pass on the Android/JVM host.
- [ ] Dedicated milestone validation, rather than per-task validation, confirms
      Android/iOS and release builds for both apps.
- [ ] Manual capability and secret-leak verification is recorded.
