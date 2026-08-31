# Rayl Suite

Rayl Suite contains three independent Kotlin Multiplatform Lightning clients and
the provider-neutral modules they share:

- **Blip** connects to one Blink wallet with an API key.
- **Flint** opens one Spark wallet from its recovery phrase.
- **Lasr** connects to one wallet through Nostr Wallet Connect.

Neither app chooses providers at runtime or migrates data from the former
combined reference application.

## Project layout

- `apps/blip`: Blip composition, Blink features, and Blink integration
- `apps/flint`: Flint composition, Spark features, and Spark integration
- `apps/lasr`: Lasr composition, NWC features, and NWC integration
- `core/*`: provider-neutral models, platform services, and UI primitives
- `feature/*`: reusable provider-neutral user stories
- `integration/*`: shared external-service adapters
- `distribution/*`: app-specific store copy, legal/reviewer material, and icons

## Build

Use JDK 21 and the root Gradle wrapper.

```shell
./gradlew :blip:androidApp:assembleDebug
./gradlew :flint:androidApp:assembleDebug
./gradlew :lasr:androidApp:assembleDebug
./gradlew :blip:androidApp:assembleE2e
./gradlew :flint:androidApp:assembleE2e
./gradlew :lasr:androidApp:assembleE2e
./gradlew check
```

To exercise a production-signed, R8-processed build on a connected device:

```shell
./gradlew :lasr:androidApp:printReleaseSigningConfig
./gradlew :lasr:androidApp:installSignedRelease
```

Release signing needs the `RAYL_UPLOAD_*` and `RAYL_APP_SIGNING_*` variables
from `.envrc.example`. See [docs/release.md](docs/release.md).

For iOS, open either app's `iosApp.xcodeproj`, or validate the Kotlin Release
frameworks directly:

```shell
./gradlew :blip:shared:linkReleaseFrameworkIosArm64
./gradlew :flint:shared:linkReleaseFrameworkIosArm64
./gradlew :lasr:shared:linkReleaseFrameworkIosArm64
```

See [release](docs/release.md), [E2E](docs/e2e.md),
[performance monitoring](docs/performance-monitoring.md), and the [extraction
completion record](docs/MIGRATION_LEDGER.md).
