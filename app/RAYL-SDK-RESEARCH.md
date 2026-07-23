# RAYL provider SDK research

Status: decision support

Research date: 2026-07-23

Scope: Kotlin Multiplatform and Kotlin-compatible client SDKs for Flint (Spark),
Quark (Ark, including Bark and Arkade), Nutrino (Cashu), and Femto (Fedimint)

## Executive conclusion

The SDK research does not invalidate the preferred RAYL monorepo or a single
Gradle root. Provider dependencies can be scoped to the KMP module of the app
that uses them, and one Gradle build does not require shared dependency
versions, application versions, signing, or releases.

It does invalidate a stronger assumption: the six apps cannot all be treated as
if they already have equally mature `commonMain` SDKs.

| App | Protocol | Best current basis | Decision today | Confidence |
| --- | --- | --- | --- | --- |
| Flint | Spark | Breez SDK - Spark KMP | Adopt, subject to a build spike against RAYL's toolchain | High |
| Nutrino | Cashu | Cashu Development Kit (CDK) | Adopt CDK as the protocol engine; prove the mobile binding strategy before committing app architecture | Medium |
| Quark | Ark | Second's Bark SDK and official mobile bindings | Adopt Bark, subject to an app-specific integration spike | Medium-high |
| Femto | Fedimint | Official Fedimint client/FFI work | Keep Femto a stub; evaluate a small RAYL-owned packaging layer over the official FFI | Medium |

The key distinction is:

- **A KMP SDK** exposes an API from `commonMain` and ships all required native
  artifacts for the supported Android and iOS targets.
- **Kotlin bindings** may only target Kotlin/JVM/Android. They do not
  automatically work from Kotlin/Native or `commonMain`.
- **A Rust library with UniFFI** is a promising basis, not a consumable mobile
  SDK. Someone still has to build, publish, version, link, test, and support the
  Android and iOS artifacts.

The high-level suite plan should therefore keep one Gradle root as the target,
while requiring an app-specific integration proof before a provider stub becomes
an implemented app.

### Revised product scope

Quark, Nutrino, and Femto are not intended to become general-purpose wallets.
Their initial provider scope is:

1. import or connect the minimum provider state/value needed by that protocol;
2. show an available balance;
3. quote and pay a BOLT11 invoice;
4. report the final or still-pending result safely.

This narrower scope makes a small platform adapter or RAYL-owned UniFFI packaging
layer more reasonable than it would be for a full wallet product. It does not
remove the need for durable provider state:

- a Cashu melt can return change proofs or remain pending;
- an Ark Lightning payment can remain pending while imported VTXOs expire or
  require maintenance;
- Fedimint notes must be reissued into a federation client before spending, and
  Lightning payment state is asynchronous.

RAYL does not need to expose every upstream wallet capability. It does need to
preserve and reconcile the funds and operations touched by its small capability
set.

## Evaluation criteria

This review prioritizes:

1. Android and iOS support, including physical devices and simulators.
2. A supported Kotlin Multiplatform integration rather than merely generated
   JVM bindings.
3. Upstream ownership, release cadence, and explicit production-readiness.
4. Complete wallet behavior, persistence, recovery, and protocol correctness;
   not only parsing or cryptographic primitives.
5. Native packaging implications: ABIs, XCFrameworks/static libraries, Gradle
   plugins, Swift Package Manager, and version coupling.
6. The amount of Rust/FFI release infrastructure RAYL would have to own.
7. Compatibility with independent app releases inside one repository.

Only upstream project documentation and source repositories were treated as
authoritative for SDK capabilities. Community projects are considered only to
assess whether a production Kotlin option exists, not as evidence of upstream
support.

## Flint: Spark

### Finding

The **Breez SDK - Spark KMP package** is the clear choice for Flint.

The official integration uses:

- `technology.breez.spark:breez-sdk-spark-kmp:<version>` in `commonMain`;
- the `technology.breez.spark.kmp` Gradle plugin;
- a native Swift package on iOS;
- a dynamic KMP framework on iOS.

The KMP dependency and Swift package must use the exact same version. A mismatch
can cause link-time or runtime failure. These requirements are documented in
the [official KMP installation guide](https://sdk-doc-spark.breez.technology/guide/install_kotlin_multiplatform.html).
The [upstream repository](https://github.com/breez/spark-sdk) contains the Rust
core, language bindings, and a Kotlin Multiplatform example. It is MIT licensed
and actively released.

### Why it fits RAYL

- It supplies a real `commonMain` dependency rather than separate Android and
  Swift APIs.
- Its application-facing capabilities include Lightning and Spark payments,
  persistence, recovery, and synchronization.
- Its native packaging is already defined and documented by its maintainer.
- The dependency and plugin can remain confined to `apps/flint/shared`.

### Risks and constraints

- Flint's KMP framework must be dynamic unless Breez changes its integration
  contract.
- Flint's Xcode target must include the matching Breez Swift package.
- The two Breez version declarations need one Flint-owned source of truth or an
  automated consistency check.
- The upstream KMP publishing build currently uses older Kotlin/Gradle plugin
  generations than this repository. Published libraries are often compatible
  with newer consumers, but its Gradle plugin crosses a more sensitive build
  boundary. This must be tested against the RAYL Kotlin and Android Gradle
  Plugin versions rather than assumed.
- API-key provisioning, backup/recovery UX, storage, and background behavior
  remain Flint product decisions; choosing the SDK does not decide them.

### Required adoption spike

Before Flint advances beyond a stub:

1. Add the SDK and plugin only to an isolated Flint KMP module in the proposed
   root build.
2. Compile Android, iOS device, Apple Silicon simulator, and Intel simulator
   targets if Intel simulator support remains a suite requirement.
3. Launch a minimal app, initialize the SDK, persist state, restore it, and
   exercise a regtest/testnet send and receive.
4. Verify release packaging, native symbols, Android 16 KB page-size
   compatibility, and the exact-version guard between Gradle and Swift Package
   Manager.

## Nutrino: Cashu

### Finding

The **Cashu Development Kit (CDK)** is the best current protocol engine, but its
mobile language packaging is not yet a clean KMP dependency.

CDK is the upstream Rust implementation at
[cashubtc/cdk](https://github.com/cashubtc/cdk). The Cashu project's curated
[library list](https://github.com/cashubtc/awesome-cashu#libraries) points to
the official generated [CDK Kotlin bindings](https://github.com/cashubtc/cdk-kotlin)
and [CDK Swift bindings](https://github.com/cashubtc/cdk-swift).

The Kotlin repository currently publishes:

- `cdk-jvm`, generated Kotlin/JVM bindings loaded through JNA;
- `cdk-android`, an Android library bundling native `.so` files;
- JVM native artifacts;
- `cdk-ios-ios-arm64`, a JAR containing an iOS static library.

This is not equivalent to one `commonMain` API:

- the generated Kotlin API is JVM/JNA-based;
- the iOS artifact contains a static native library, not a demonstrated
  Kotlin/Native API surface;
- the published iOS target is device ARM64 only;
- no iOS simulator artifact is currently listed or built in the bindings
  repository.

The separate Swift package is more complete on iOS and advertises an
XCFramework for iOS device and ARM64/x86_64 simulators, but adopting it means
the two platforms do not directly call the same Kotlin API.

CDK's FFI surface is a particularly good match for Nutrino's reduced product
scope. It can decode an imported Cashu token, identify its mint and amount,
request a melt quote, prepare a melt directly from the encoded token, confirm
the melt, and finalize pending melts. Nutrino therefore does not need to expose
CDK's minting, sending, Nostr, or general multi-mint wallet features merely
because they exist upstream.

The reduced UI scope does not make a stateless implementation safe. Imported
proofs, fee reserve, returned change, quote state, and an interrupted melt must
be persisted until the mint gives a conclusive result.

### Decision

Choose **CDK as Nutrino's protocol engine**, but do not yet choose between these
integration shapes:

#### Option A: supported platform bindings behind a RAYL-owned boundary

- Android uses `cdk-android`.
- iOS uses `cdk-swift`.
- Nutrino defines a narrow wallet service contract and platform adapters.

Advantages:

- uses the best-packaged upstream artifact on each platform;
- avoids RAYL owning Rust compilation and UniFFI generation immediately;
- includes current iOS simulator support through the Swift package.

Costs:

- the iOS adapter lives in the Swift shell or a native framework rather than
  being a simple `commonMain` implementation;
- API parity and behavioral consistency must be tested by RAYL;
- sharing provider orchestration in common Kotlin becomes more awkward.

#### Option B: RAYL-owned KMP/native bridge over CDK

- generate or integrate Kotlin/Native-compatible bindings;
- package CDK for every required Android and iOS target;
- expose a Nutrino-local common Kotlin API.

Advantages:

- a more natural common Kotlin call site;
- one provider adapter and more shared provider tests.

Costs:

- RAYL becomes responsible for Rust toolchains, UniFFI generation, native
  publishing, linker behavior, target coverage, upgrades, and security fixes;
- this can become a substantial SDK-maintenance project;
- it duplicates packaging work that upstream may soon complete.

### Recommendation

Run an integration spike before choosing A or B. Start with Option A because it
uses published upstream artifacts and has the smaller ownership surface. Choose
Option B only if the spike demonstrates that the platform boundary materially
damages Nutrino's simplicity, and only after estimating the continuing cost of
owning the bridge.

Do not:

- select an unmaintained older pure-Kotlin Cashu client;
- implement the NUTs or blind-signature wallet engine from scratch merely to
  obtain a pure KMP codebase;
- treat the current `cdk-ios` device archive as proof of simulator support.

### Required adoption spike

Before Nutrino advances beyond a stub:

1. Compile and launch Android ARM64 and x86_64 builds with `cdk-android`.
2. Compile and launch iOS device, Apple Silicon simulator, and—if required—Intel
   simulator builds with `cdk-swift`.
3. Prove the exact boundary by implementing token decode/import, mint
   identification, melt quote/payment, change handling, balance,
   pending-operation recovery, and database reopen. Do not expand the spike
   into unrelated minting or token-send features.
4. Test compatible behavior and error mapping across both platforms.
5. Inspect all shipped native libraries for Android 16 KB alignment.
6. Confirm CDK's database location, backup model, seed/proof recovery,
   multi-mint behavior, and upgrade compatibility against Nutrino's product
   requirements.

## Quark: Ark

### Finding

Ark currently has two distinct implementation ecosystems: **Second/Bark** and
**Arkade**. The [protocol project lists both implementations](https://ark-protocol.org/).
They must not be treated as interchangeable SDKs or as two transports behind a
generic Quark interface.

#### Second/Bark

[Bark](https://gitlab.com/ark-bitcoin/bark) is Second's Rust Ark wallet. Its
official [Bark FFI](https://gitlab.com/ark-bitcoin/bark-ffi) uses UniFFI, and
the official
[language-bindings repository](https://gitlab.com/ark-bitcoin/bark-ffi-bindings)
publishes:

- an Android AAR and JVM artifact with generated Kotlin bindings;
- a Swift package with generated Swift bindings and a device/simulator
  XCFramework;
- versioned releases tied to an exact Bark version;
- official Kotlin and Swift mobile examples.

The Kotlin package is Android/JVM, not a `commonMain` KMP API. The Swift package
is therefore still a platform boundary, just like the recommended CDK option.
That is a manageable integration shape for a small Quark provider adapter.

The FFI exposes the exact initial Quark flow: open from a mnemonic/seed, import
a VTXO, synchronize, inspect balance, estimate a Lightning fee, pay a BOLT11
invoice, and inspect pending/final payment state. It also exposes maintenance,
VTXO refresh, and exit operations that Quark may keep out of normal UI but
cannot pretend do not exist while it holds user value.

At the research date, the bindings had an active release series and included
Android ARM64/x86_64 libraries plus an iOS XCFramework. The Swift README states
iOS 13 while its current `Package.swift` declares iOS 14; the actual package
manifest must be treated as authoritative and this mismatch should be raised
with upstream.

#### Arkade

Arkade's current application documentation is centered on its official
TypeScript SDK. The [official Rust SDK](https://github.com/arkade-os/rust-sdk)
is an active, modular implementation, but it does not publish documented
Kotlin, Swift, Android, or iOS bindings.

A community [Arkade Kotlin Multiplatform SDK](https://github.com/shubertm/arkade-kotlin)
exists, but its own README calls it experimental and not production-ready. The
upstream [`arkd` documentation](https://docs.arkadeos.com/arkd/what-is-arkd)
also currently describes the server as alpha, with limited mainnet testing.

### Decision

Keep Quark as a branded, buildable stub. Do not make an irreversible SDK choice
in the initial Lasr/Blip extraction milestone.

For a mobile Quark implementation starting with import/balance/pay-invoice,
**Bark is the best current candidate**. It has a materially lower integration
and maintenance risk than creating RAYL-owned Arkade bindings.

The lead selected the Second/Bark ecosystem for Quark. The intended Ark server,
import shape, and detailed payment flow remain deferred to Quark's product and
implementation planning.

### Rejected shortcuts

- Embedding the TypeScript SDK in a WebView or private JavaScript runtime would
  introduce a second application runtime, storage boundary, and debugging model
  merely to retain Compose UI. It conflicts with the suite's simplicity goal.
- Implementing directly against `arkd` gRPC/REST APIs would require RAYL to own
  protocol transactions, signing, VTXO lifecycle, recovery, and compatibility.
  The existence of an API does not make this a small networking adapter.
- Shipping the community Kotlin SDK despite its production warning transfers a
  protocol and security risk to RAYL without upstream backing.
- Combining Bark and Arkade in Quark would recreate the multi-provider
  complexity that the RAYL split is intended to remove.

### Future evaluation spike

When Quark implementation is scheduled, require:

- an explicit Second/Bark versus Arkade decision;
- Android ARM64/x86_64 and iOS device/simulator packaging inside RAYL;
- mnemonic/seed open, VTXO import, database reopen, balance, fee estimate,
  BOLT11 payment, and pending-payment reconciliation;
- a background/foreground policy for required VTXO maintenance and expiry;
- a tested recovery or exit path even if it is not part of normal checkout UI;
- compatibility testing against the intended Ark server/operator;
- a documented update path for server/protocol version changes.

## Femto: Fedimint

### Finding

Fedimint has an official Rust client that is intended to support mobile
applications, but it does not currently publish a production-ready KMP package.
The [Fedimint technical reference](https://docs.fedimint.org/) describes the
client library as usable for desktop, mobile, and WASM.

There are two relevant upstream efforts:

1. [fedimint/fedimint-sdk](https://github.com/fedimint/fedimint-sdk) publishes
   web and React Native packages. Its React Native bindings include prebuilt
   Android native libraries and an iOS XCFramework, but its public API and
   packaging target React Native/TypeScript, not Compose Multiplatform.
2. [fedimint/fedimint-sdk-ffi](https://github.com/fedimint/fedimint-sdk-ffi) is
   a small official UniFFI wrapper over the Fedimint client RPC crates. At the
   research date it has no releases and does not publish consumable Kotlin,
   Swift, Android, or iOS packages.

The FFI repository is useful evidence of upstream direction. It is not yet an
SDK distribution contract. The Android linker workaround and tightly pinned
Fedimint/Rust dependencies in its source also illustrate why consuming raw FFI
source is an ongoing maintenance responsibility rather than a one-time wrapper.

The FFI's JSON-RPC surface already covers the reduced Femto workflow:

- set or generate a mnemonic;
- parse an invite and join/open a federation client;
- parse and reissue imported out-of-band ecash notes;
- parse and pay a BOLT11 invoice through the federation's Lightning module;
- subscribe to asynchronous payment/reissue state.

Femto therefore does not need to design a Fedimint protocol client. It needs
reproducible Android/iOS packaging around an upstream Rust client that already
implements the required behavior.

### Decision

Keep Femto as a branded, buildable stub during the first milestone.

When Femto implementation begins, first ask upstream whether supported native
packages are imminent. If they are not, the reduced product scope makes a thin
RAYL-owned build and packaging layer over `fedimint-sdk-ffi` a reasonable
candidate. Keep the Rust/JSON-RPC contract upstream-shaped and expose only a
small app-local Kotlin service for import, balance, quote/pay, and payment
status.

Do not embed the React Native runtime in Femto solely to consume the current
package. Do not fork or reimplement the full Fedimint client. A local packaging
layer still means RAYL explicitly accepts ownership of:

- Rust and NDK toolchains;
- UniFFI Kotlin and Swift generation;
- Android `.so` and iOS XCFramework production;
- database/native dependency behavior;
- federation/client upgrade compatibility;
- reproducible releases and urgent security rebuilds.

### Future evaluation spike

When Femto implementation is scheduled, prove:

- supported Android ARM64/x86_64 and iOS device/simulator packages;
- federation join/open, imported-note reissue, database reopen, balance,
  Lightning gateway payment, and recovery from interrupted reissue/payment
  operations;
- federation and module-version compatibility behavior;
- application size and native-symbol/linking impact;
- Android 16 KB page-size compatibility;
- upstream's release and security-update process.

## Implications for the RAYL repository

### One Gradle root remains the recommended target

A single Gradle root can contain:

```text
apps/lasr/shared
apps/blip/shared
apps/flint/shared
apps/quark/shared
apps/nutrino/shared
apps/femto/shared
foundation/ui
foundation/bolt11
```

Provider SDKs belong only to their app:

- Breez SDK and its plugin: `apps/flint/shared`;
- CDK Android/native integration: `apps/nutrino/shared` and Nutrino's iOS shell;
- Bark Android/Swift bindings, if chosen: `apps/quark`;
- Fedimint FFI packaging and adapter, if chosen: `apps/femto`.

This does **not** imply:

- one version number for all apps;
- simultaneous releases;
- one signing identity or store pipeline;
- one provider dependency version catalog entry shared by all apps;
- rebuilding or publishing every app when one app changes.

Gradle tasks and CI jobs can be app-scoped. A Flint SDK security update can
change and release Flint alone. Path-based CI can validate the affected app and
the foundation modules it consumes.

### What a single root cannot solve

- Xcode still has to link provider-specific Swift packages, frameworks, or
  static libraries.
- Gradle plugin compatibility can affect configuration of the whole build even
  when the runtime dependency is app-scoped. Flint's Breez plugin therefore
  needs an early root-build spike.
- Native libraries must cover each app's declared ABIs and iOS targets.
- A shared version catalog is convenient but must not force provider versions
  into a suite-wide release cadence.
- A future SDK with incompatible Gradle/Kotlin requirements could require an
  included build, a prebuilt adapter artifact, or—only as a last resort—a
  separate build root. The current evidence does not require that split.

### FFI strategy

UniFFI is a sound way to consume these Rust engines, but it does not imply that
the generated API must live in `commonMain`.

Mozilla's standard UniFFI generators produce Kotlin bindings for Android/JVM
and Swift bindings for iOS. The simplest initial RAYL shape is therefore:

```text
commonMain: small app-owned provider contract and UI state
androidMain: adapter over the upstream Kotlin/Android binding
iOS shell: adapter over the upstream Swift package
```

This retains one Rust protocol engine without forcing RAYL to generate a new
cross-platform binding surface.

[Gobley](https://gobley.dev/) can generate Kotlin Multiplatform bindings for
UniFFI and build Rust through Gradle. It is a legitimate future option if a
provider's platform adapters become the dominant source of duplication.
However, its own documentation describes the binding generator as young and
potentially unstable. Do not insert it into all provider apps merely to make
their call sites look uniform. First prefer maintained upstream Android and
Swift packages; use an app-local Gobley bridge only after a focused spike shows
that it reduces total complexity.

### Privacy-sensitive networking

Nutrino and Femto have a stronger privacy requirement than the first Lasr/Blip
milestone. Their app READMEs and future implementation records must explicitly
state that networking may not use the suite's default HTTP assumptions.

Cashu mints and Fedimint federations/gateways can observe different metadata,
and the Rust engines may own DNS, TLS, connection pooling, proxy, or Tor
behavior internally. Consequently:

- do not put a mandatory suite-wide network client in `foundation`;
- keep network routing and privacy policy provider-app-owned;
- evaluate proxy/Tor support, DNS leakage, connection reuse, logs, telemetry,
  and timing metadata before implementing Nutrino or Femto;
- do not enable analytics in these apps until their privacy threat model states
  what may leave the device.

### Recommended Xcode organization

Use one repository-level `.xcworkspace` containing one `.xcodeproj` per app,
rather than one six-target mega-project.

This gives developers one workspace to open while preserving:

- app-local Swift Package Manager dependencies;
- app-local KMP framework/linker settings;
- separate bundle IDs, signing, entitlements, schemes, and archives;
- ordinary `apps/<app>/iosApp` KMP app structure;
- a smaller blast radius when a provider requires special native packaging.

Centralize repeatable configuration with checked-in `.xcconfig` files, scripts,
and templates. Do not centralize provider frameworks into a suite-wide iOS
target merely to remove a few duplicated Xcode settings.

## ABI and platform baseline

RAYL's agreed Android minimum SDK is 29. Minimum SDK and CPU ABI are separate
decisions.

The legacy app explicitly filters Android packaging to `arm64-v8a`. Its build
comment ties that restriction to native ML Kit and `acinq-secp256k1` 16 KB
alignment concerns; it is not a KMP limitation and should not be copied into
every RAYL app.

For new apps:

- require `arm64-v8a` for production Android devices;
- include `x86_64` where all native dependencies support it, especially for
  emulator-based development and CI;
- do not support `armeabi-v7a`; RAYL has explicitly chosen a 64-bit-only
  Android baseline;
- test the final APK/AAB, not only RAYL-owned native code, for 16 KB page-size
  compatibility.

Android's [16 KB guidance](https://developer.android.com/guide/practices/page-sizes)
requires compatible alignment from every prebuilt native library. Its
[64-bit guidance](https://developer.android.com/google/play/requirements/64-bit)
distinguishes ARM `arm64-v8a` and emulator/Intel `x86_64`; supporting one does
not automatically provide the other.

The SDK-specific spikes should establish each app's ABI matrix. There is no
technical or product reason to require all six binaries to ship the same native
ABIs if their provider SDKs differ.

The suite-wide iOS deployment baseline is iOS 17.0 unless a provider SDK
requires higher. An upstream SDK supporting an older minimum remains compatible
with an application that chooses this higher deployment target.

## Decision gates before implementing provider behavior

The suite can create all six app skeletons now. A provider app should move from
stub to implementation only when:

1. Its engine is upstream-supported for the intended production use.
2. Android and iOS packaging succeeds inside the actual single-root repository.
3. Device and simulator targets needed by the team are proven.
4. Persistence, restore, interruption recovery, and protocol upgrade behavior
   are understood.
5. Native size, ABI, 16 KB alignment, and linker constraints are measured.
6. RAYL's ownership boundary is explicit: what upstream supports and what the
   suite must maintain.
7. The choice is captured in a short app-specific architecture decision record.

## Research limits and review cadence

This is a point-in-time SDK assessment, not a permanent lockfile. Ark, Cashu,
Fedimint, Spark, Kotlin, Gradle, and their binding projects are moving quickly.

Review this document:

- immediately before starting Flint, Nutrino, Quark, or Femto implementation;
- when an upstream project publishes a mobile/KMP release;
- after a material protocol or security change;
- before raising the suite's Kotlin, Gradle, Android, or iOS baseline.

The decisions that should remain stable are the evaluation criteria and the
rule that each provider integration must earn its place in the shared build.
Exact SDK versions and packaging workarounds should live in app-specific
implementation records, not in the high-level suite plan.
