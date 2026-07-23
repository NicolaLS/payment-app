# Recommendation

Your idea is sound. The important architectural correction is this:

> NWC, Blink, Spark, and card-funded payments are not interchangeable implementations of one wallet abstraction. They are distinct payment systems with different state machines, failure modes, security models, dependencies, and product behavior.

Use:

* **One source-control repository**
* **One Gradle root initially**
* **Separate application modules and binaries**
* **Small, provider-neutral shared library modules**
* **Separate provider-specific domain models rather than `WalletProvider`**
* **Purposeful duplication whenever behavior is merely similar rather than identical**

A monorepo does not require a shared application architecture. It can simply provide a common workspace, toolchain, CI system, and carefully selected leaf-level libraries.

## The mental model

Keep these boundaries separate:

1. **Repository boundary:** all projects live together.
2. **Build boundary:** initially, one Gradle multi-project build.
3. **Product boundary:** each payment technology produces its own Android and iOS application.
4. **Sharing boundary:** only stable, semantically identical, provider-neutral code crosses product boundaries.

That gives you centralized DX without centralizing product logic.

---

# One Gradle root or several?

## Start with one Gradle root

I recommend one `settings.gradle.kts`, one Gradle wrapper, one version catalog, and separate modules for every application.

A Gradle multi-project build already lets every subproject define its own plugins, source sets, dependencies, and build logic. Being in the same Gradle build does **not** cause every application to receive every dependency. ([Gradle Docs][1])

Separate Gradle roots connected through a composite build are technically possible. Composite builds are specifically intended for independent builds in monorepos, but included builds do not share repositories, plugins, properties, or other configuration automatically. Each included build is configured independently. ([Gradle Docs][2])

That independence is useful later, but it introduces exactly the kind of Gradle complexity you do not currently need.

### One root gives you

* One Gradle wrapper and compatible toolchain
* One dependency catalog
* One set of convention plugins
* Direct `project(...)` dependencies
* Easy IDE import
* Easy cross-project refactoring
* One command to test everything
* Scoped commands to build only one app
* No need to publish shared libraries locally

### It does not require

* One application module
* One dependency graph
* One release version
* One package/bundle ID
* One payment abstraction
* One architecture inside every app
* Building every app for every normal development command

For example, you can run only:

```bash
./gradlew :apps:spark:androidApp:assembleDebug
./gradlew :apps:spark:shared:allTests
./gradlew :apps:nwc:shared:embedAndSignAppleFrameworkForXcode
```

The exact KMP task names depend on your plugin configuration, but the important point is that Gradle tasks remain addressable by module.

---

# Suggested repository structure

A reasonable target structure would be:

```text
lasr-suite/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
│
├── gradle/
│   ├── libs.versions.toml
│   ├── verification-metadata.xml
│   └── wrapper/
│
├── build-logic/
│   └── convention/
│       ├── build.gradle.kts
│       └── src/main/kotlin/
│           ├── lasr.kmp-library.gradle.kts
│           ├── lasr.compose-library.gradle.kts
│           ├── lasr.android-application.gradle.kts
│           ├── lasr.quality.gradle.kts
│           └── lasr.testing.gradle.kts
│
├── shared/
│   ├── ui-foundation/
│   ├── payment-primitives/
│   ├── testkit/
│   └── platform-testing/
│
└── apps/
    ├── nwc/
    │   ├── shared/
    │   ├── androidApp/
    │   └── iosApp/
    │
    ├── blink/
    │   ├── shared/
    │   ├── androidApp/
    │   └── iosApp/
    │
    ├── spark/
    │   ├── shared/
    │   ├── androidApp/
    │   └── iosApp/
    │
    └── card/
        ├── shared/
        ├── androidApp/
        ├── iosApp/
        └── backend/
```

Here, each `apps/<product>/shared` directory is a KMP module containing that application's shared Compose UI and application implementation.

For example:

```text
apps/spark/shared/
├── build.gradle.kts
└── src/
    ├── commonMain/
    │   └── kotlin/
    │       ├── onboarding/
    │       ├── wallet/
    │       ├── payment/
    │       ├── scanner/
    │       └── navigation/
    ├── androidMain/
    └── iosMain/
```

The word `shared` here means “shared between Android and iOS for this product.” It does **not** mean shared between all four products.

That distinction is important.

---

# The KMP and iOS arrangement

Each application should have one application-specific KMP umbrella module:

```text
:spark-shared
:nwc-shared
:blink-shared
:card-shared
```

Each umbrella module depends only on the shared libraries and provider-specific libraries that the application needs.

For Spark:

```text
:spark-shared
    ├── :shared-ui-foundation
    ├── :shared-payment-primitives
    └── Breez Spark SDK
```

For NWC:

```text
:nwc-shared
    ├── :shared-ui-foundation
    ├── :shared-payment-primitives
    ├── Nostr SDK
    └── NWC implementation
```

For Blink:

```text
:blink-shared
    ├── :shared-ui-foundation
    ├── :shared-payment-primitives
    └── GraphQL client
```

This matters especially on iOS. JetBrains recommends exposing one umbrella framework when an iOS application consumes several KMP modules. Multiple independent Kotlin frameworks can duplicate shared dependencies and create binary and type-compatibility problems. ([Kotlin][3])

Therefore:

* `SparkShared.framework` includes Spark and the small shared modules it uses.
* `NwcShared.framework` includes NWC and its dependencies.
* Do **not** create a global `LasrSuite.framework` containing every provider.
* Do **not** link separate `UiFoundation.framework`, `PaymentPrimitives.framework`, and `Spark.framework` into the same iOS app.

The Spark umbrella KMP module should transitively contain the shared UI library and produce the single framework consumed by the Spark iOS application.

---

# Dependency isolation

## A version catalog does not add dependencies

This:

```toml
[libraries]
nostr-sdk = { module = "..." }
apollo-runtime = { module = "..." }
breez-spark = { module = "..." }
```

only makes dependency aliases available. It does not make those libraries part of every module.

A module receives a dependency only when:

1. It declares the dependency directly, or
2. It depends on another module that brings it transitively.

Version catalogs centralize dependency names and requested versions, but modules still choose which entries they consume. ([Gradle Docs][4])

For example:

```kotlin
// apps/nwc/shared/build.gradle.kts

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:ui-foundation"))
            implementation(project(":shared:payment-primitives"))

            implementation(libs.nostr.sdk)
            implementation(libs.nwc.sdk)
        }
    }
}
```

```kotlin
// apps/blink/shared/build.gradle.kts

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:ui-foundation"))
            implementation(project(":shared:payment-primitives"))

            implementation(libs.apollo.runtime)
        }
    }
}
```

```kotlin
// apps/spark/shared/build.gradle.kts

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:ui-foundation"))
            implementation(project(":shared:payment-primitives"))

            implementation(libs.breez.spark)
        }
    }
}
```

KMP supports declaring project and external dependencies at the precise source-set level, including `commonMain`, `androidMain`, and `iosMain`. ([Kotlin][5])

## Important nuance about `implementation`

Prefer `implementation` over `api`. It prevents the dependency's API from being exposed unnecessarily to compilation consumers and reduces coupling. Official Android modularization guidance recommends exposing as little as possible and preferring `implementation`. ([Android Developers][6])

But:

> `implementation` does not prevent a dependency from being packaged into an application when the implementation needs it.

If `shared-ui-foundation` depends on Apollo GraphQL, then every application using `shared-ui-foundation` will transitively receive Apollo at runtime, even though Apollo was declared with `implementation`.

Therefore dependency isolation comes primarily from the **module graph**, not from `api` versus `implementation`.

The central rule should be:

> No provider library may appear anywhere below `shared/`.

That means no Nostr, NWC, Blink, GraphQL, Breez Spark, seed handling, card SDK, or provider-specific error model in a cross-application module.

---

# What should be shared?

## Good candidates

### Pure UI primitives

Examples:

* Numeric amount keypad
* Amount typography
* Loading indicator
* Confirmation button
* Generic sheets and dialogs
* QR framing overlay
* Spacing and typography tokens
* Accessibility helpers
* Generic payment-request display components

These components should receive plain values and callbacks:

```kotlin
@Composable
fun PaymentAmountPad(
    amount: Sats?,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onContinue: () -> Unit,
)
```

They should not receive:

```kotlin
WalletProvider
PaymentRepository
NwcConnection
BlinkWallet
SparkWallet
```

Compose Multiplatform resources can also live in separate modules, so generic icons, strings, and other common assets can live with the shared UI module while app branding remains app-local. ([Kotlin][7])

### True protocol primitives

Potential examples:

```kotlin
@JvmInline
value class Sats(val value: Long)

data class Bolt11Invoice(
    val encoded: String,
    val amount: Sats?,
    val description: String?,
    val expiry: Instant,
)
```

Only share these when their meaning is genuinely dictated by the Bitcoin or Lightning protocol.

Do not expand `PaymentResult` until it can represent every provider. That would recreate the original problem.

### Build and development infrastructure

This is probably your highest-value sharing category:

* Kotlin and Java toolchain configuration
* KMP target configuration
* Compose compiler configuration
* Android compile/minimum SDK configuration
* Static analysis
* Unit-test setup
* Test reports
* Compiler warnings
* Dependency verification
* CI scripts
* Code formatting
* Common emulator or simulator setup

Gradle recommends convention plugins for reusable build logic and recommends placing them in an included `build-logic` build rather than `buildSrc`. ([Gradle Docs][8])

Useful convention plugins might be:

```text
lasr.kmp-library
lasr.compose-library
lasr.android-application
lasr.testing
lasr.quality
```

Convention plugins should configure builds. They should not silently add GraphQL, Nostr, Spark, or other product dependencies.

### Testing utilities

Potentially share:

* Fake clock
* Deterministic random source
* Common screenshot-test configuration
* Compose semantic helpers
* Platform test runner configuration
* Maestro execution tooling
* Test report aggregation

Keep these app-local:

* Fake NWC relay
* Blink GraphQL fixtures
* Spark wallet fixtures
* Card authorization responses
* Provider-specific assertions
* Provider-specific payment state factories

---

# What should deliberately remain duplicated?

You should duplicate:

* Payment orchestration
* Payment ViewModels
* Error mappings
* Retry logic
* Timeout behavior
* Pending-state behavior
* Onboarding
* Credential storage policy
* Recovery flows
* Provider-specific confirmation screens
* Provider-specific telemetry
* Provider-specific test fixtures

For example, Blink might have:

```kotlin
sealed interface BlinkPaymentState {
    data object Preparing : BlinkPaymentState
    data object Submitting : BlinkPaymentState
    data class Pending(val paymentId: String) : BlinkPaymentState
    data class Settled(val preimage: String?) : BlinkPaymentState
    data class Failed(val reason: BlinkFailure) : BlinkPaymentState
}
```

NWC might instead have:

```kotlin
sealed interface NwcPaymentState {
    data object Connecting : NwcPaymentState
    data object SendingRequest : NwcPaymentState
    data object AwaitingResponse : NwcPaymentState
    data class Reconnecting(val attempt: Int) : NwcPaymentState
    data class Completed(val result: NwcResult) : NwcPaymentState
    data class Failed(val reason: NwcFailure) : NwcPaymentState
}
```

Spark might need states around:

* SDK initialization
* Seed availability
* Wallet synchronization
* Spendability
* Lightning payment
* Local wallet persistence

These are different state machines. A generic abstraction would remove information and then require side channels, optional fields, or provider checks to restore it.

The correct rule is:

> Share facts and mechanics. Do not share provider policy.

---

# Internal architecture of each app

Each application can still use ports and adapters internally. The issue was not interfaces themselves; it was using one interface across systems with incompatible semantics.

For the NWC app:

```text
NwcPaymentCoordinator
    ├── NwcClient
    ├── RelayConnection
    ├── NwcRequestTracker
    └── NwcRetryPolicy
```

For Blink:

```text
BlinkPaymentCoordinator
    ├── BlinkApi
    ├── BlinkPaymentPoller
    └── BlinkErrorMapper
```

For Spark:

```text
SparkPaymentCoordinator
    ├── SparkWallet
    ├── SeedStore
    ├── WalletInitializer
    └── SparkPaymentService
```

These interfaces describe actual boundaries inside each technology. They do not pretend that all technologies have equivalent behavior.

The UI can also preserve provider-specific information rather than compressing everything into:

```kotlin
pay(invoice): Result<Unit>
```

---

# The card-funded app is a more distant product

The card application is not merely a fourth wallet adapter.

Its likely flow is closer to:

```text
Payment request
    ↓
Price/fee quote
    ↓
Card authorization
    ↓
Bitcoin acquisition or liquidity execution
    ↓
Lightning payment
    ↓
Settlement/reconciliation
```

It may require:

* A backend
* Quote expiration
* Authentication state
* Funding failures
* Payment failures after successful funding
* Refund or reconciliation handling
* Fraud and abuse controls
* Provider webhooks
* More extensive operational monitoring

Architecturally, I would put it in the same repository only for workspace convenience. Do not assume it shares a payment domain with the wallet-backed apps.

It is also the strongest candidate to eventually become a separate Gradle build or even a separate repository because it may develop an independent backend, deployment lifecycle, and security boundary.

---

# When separate Gradle roots become justified

Move from one multi-project build to composite independent builds when one or more of these become real problems:

1. **Toolchain incompatibility**
   One SDK requires an incompatible Kotlin, Compose, Gradle, AGP, or Kotlin/Native version.

2. **Independent buildability**
   You need to clone or open one product without configuring the others.

3. **Large IDE sync cost**
   Configuring every application becomes materially slow.

4. **Independent shared-library releases**
   Shared modules need explicit versions and publication.

5. **Separate ownership or access control**
   Different teams or contractors should not access every product.

6. **Different release infrastructure**
   One application requires substantially different signing, CI, or deployment tooling.

A future composite layout could look like:

```text
lasr-suite/
├── settings.gradle.kts       # Aggregator only
├── build-logic/
├── shared-libs/
│   ├── settings.gradle.kts
│   └── ...
└── apps/
    ├── nwc/
    │   ├── settings.gradle.kts
    │   └── ...
    ├── blink/
    │   ├── settings.gradle.kts
    │   └── ...
    └── spark/
        ├── settings.gradle.kts
        └── ...
```

```kotlin
// Aggregator settings.gradle.kts

rootProject.name = "lasr-suite"

includeBuild("build-logic")
includeBuild("shared-libs")
includeBuild("apps/nwc")
includeBuild("apps/blink")
includeBuild("apps/spark")
```

Gradle supports using a composite root to knit together independently usable builds. ([Gradle Docs][2])

But remember that each included build must explicitly configure its repositories, plugins, properties, and catalog imports. A shared TOML catalog can be imported into multiple builds from a common file, but that configuration must be added to each build. ([Gradle Docs][4])

I would not start there.

---

# Main challenges

## Accidental coupling

A developer can add:

```kotlin
implementation(project(":apps:nwc:shared"))
```

to the Spark application because everything is visible in the same build.

Prevent this through:

* Clear module naming
* Documented dependency rules
* Build checks for forbidden dependencies
* No cross-app project dependencies
* Reviewing dependency reports in CI

Gradle's `dependencies` and `dependencyInsight` reports let you inspect both the complete tree and which dependency introduced a particular artifact. ([Gradle Docs][9])

A useful acceptance check would be:

```bash
./gradlew :apps:spark:shared:dependencies
```

and verify that it contains no Nostr, NWC, Apollo, or Blink artifacts.

## Shared-code blast radius

A small change in `ui-foundation` can affect four released applications.

Therefore shared modules need:

* Narrow APIs
* Strong tests
* Stable behavior
* No application policy
* No provider-specific configuration switches

Do not create:

```kotlin
PaymentScreen(
    isNwc: Boolean,
    supportsPending: Boolean,
    needsRetry: Boolean,
    usesLocalSeed: Boolean,
)
```

That is one application hidden inside a shared component.

## Toolchain lockstep

One Gradle root strongly encourages all projects to use the same Kotlin, Compose, Gradle, and Android plugin versions.

This is currently a benefit. It becomes a reason to split only when a real SDK incompatibility appears.

## Excessive modularization

Do not make every composable or utility a module. Every module adds build configuration and cognitive overhead; official Android guidance explicitly warns against overly fine-grained modularization. ([Android Developers][10])

Begin with perhaps:

```text
shared-ui-foundation
shared-payment-primitives
shared-testkit
```

Not twenty `shared-*` modules.

---

# Security and reproducibility measures

Because these applications handle NWC secrets, Blink API keys, Spark seeds, and potentially card transactions, repository-level supply-chain controls are worthwhile.

Consider enabling:

* Gradle dependency verification
* Dependency locking
* Gradle wrapper checksum verification
* Separate CI secrets per application
* Separate signing identities
* No production credentials in shared Gradle configuration
* Provider-specific storage implementations

Gradle dependency verification can record checksums and signatures for plugins and dependencies, while dependency locking records the exact resolved versions used by the build. ([Gradle Docs][11])

This is more valuable than elaborate Clean Architecture layering.

---

# Concrete migration plan

## Phase 1: Prove the build topology

Do not immediately extract four production applications.

Create the monorepo skeleton with:

```text
apps/lasr-legacy
apps/snapfire
shared/ui-foundation
shared/testkit
build-logic
```

Keep the existing Lasr implementation largely intact as the NWC/Blink reference. Build Snapfire as the clean Spark application, as already planned.

The initial architecture experiment should prove only:

* Both Android applications build
* Both iOS applications build
* Each iOS app consumes one app-specific KMP framework
* One trivial UI primitive is shared
* Spark has no Nostr or GraphQL dependencies
* Lasr does not depend on Spark
* Scoped Gradle commands work
* CI can test one app independently

This gives you real evidence before restructuring the existing application.

## Phase 2: Extract only demonstrated commonality

Migrate one small vertical slice into Snapfire:

```text
Open app
→ detect QR
→ parse BOLT11
→ show payment confirmation
```

Initially duplicate code from Lasr where necessary.

After both implementations work, compare them:

* Is the amount keypad genuinely identical?
* Is invoice presentation identical?
* Are accessibility semantics identical?
* Are callbacks and state requirements identical?
* Would a future change normally be wanted in both applications?

Only then extract the component.

This follows your desired rule: sharing is earned, not presumed.

## Phase 3: Decide whether NWC and Blink need separate products

Once Snapfire validates the monorepo architecture, extract NWC or Blink from legacy Lasr one at a time.

Do not perform both extractions simultaneously.

A sensible order is:

1. Snapfire as clean Spark app
2. Preserve Lasr as working legacy reference
3. Extract the simpler standalone Blink app
4. Extract NWC afterward
5. Keep or retire legacy Lasr
6. Treat card funding as a separate later project

## Phase 4: Add enforcement

Add checks that assert:

* Shared modules cannot depend on provider packages
* Applications cannot depend on sibling applications
* Spark's resolved dependencies contain no Nostr or Apollo artifacts
* NWC contains no Spark SDK
* Blink contains no Nostr SDK
* Shared modules use `internal` by default
* Every shared module has at least two actual consumers or a strong infrastructure justification

## Phase 5: Optimize only after measuring

Enable and validate:

```properties
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.parallel=true
```

Gradle's build cache reuses task outputs, while configuration cache can skip repeated configuration work. ([Gradle Docs][12])

Do not adopt composite builds merely because they sound more isolated. Adopt them when measurements or incompatible toolchains justify them.

---

# Final architecture rule set

Use these as the repository's initial architecture decisions:

1. Every payment technology is an independent application.
2. Applications never depend on sibling applications.
3. There is no cross-product `WalletProvider`.
4. Each application owns its payment state machine and error model.
5. Shared modules contain no provider dependencies.
6. Shared UI is stateless or provider-neutral.
7. Similar workflows remain duplicated unless their semantics are identical.
8. Each iOS application consumes one application-specific umbrella framework.
9. Dependency aliases may be centralized; dependency declarations remain module-local.
10. Convention plugins centralize build configuration, not product behavior.
11. The repository begins as one Gradle multi-project build.
12. Composite builds are a later escape hatch for toolchain or operational independence.

This gives you the slim application binaries and explicit product semantics you want without giving up the practical benefits of a monorepo.

[1]: https://docs.gradle.org/current/userguide/multi_project_builds.html "Multi-Project Builds"
[2]: https://docs.gradle.org/current/userguide/composite_builds.html "Composite Builds (Included Builds)"
[3]: https://kotlinlang.org/docs/multiplatform/multiplatform-project-configuration.html "Choosing a configuration for your Kotlin Multiplatform project | Kotlin Multiplatform Documentation"
[4]: https://docs.gradle.org/current/userguide/version_catalogs.html "Version Catalogs"
[5]: https://kotlinlang.org/docs/multiplatform/multiplatform-add-dependencies.html "Adding dependencies on multiplatform libraries | Kotlin Multiplatform Documentation"
[6]: https://developer.android.com/topic/modularization/patterns "Common modularization patterns  |  App architecture  |  Android Developers"
[7]: https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources.html?utm_source=chatgpt.com "Resources overview | Kotlin Multiplatform Documentation"
[8]: https://docs.gradle.org/current/userguide/best_practices_structuring_builds.html "Best Practices for Structuring Builds"
[9]: https://docs.gradle.org/current/dsl/org.gradle.api.tasks.diagnostics.DependencyInsightReportTask.html?utm_source=chatgpt.com "DependencyInsightReportTask - Gradle DSL Version 9.6.1"
[10]: https://developer.android.com/topic/modularization "Guide to Android app modularization  |  App architecture  |  Android Developers"
[11]: https://docs.gradle.org/current/userguide/dependency_verification.html?utm_source=chatgpt.com "Verifying Dependencies"
[12]: https://docs.gradle.org/current/userguide/performance.html?utm_source=chatgpt.com "Improve the Performance of Gradle Builds"
