# Lasr App Workspace

This directory is the actual Kotlin Multiplatform app workspace. Run Gradle, Android,
iOS, and local test commands from here.

## Wallet Connection Scope

Lasr currently supports one connected wallet at a time, either Blink or NWC. Earlier
versions supported multiple wallets. Although that can be useful, it was implemented
before there was demonstrated user demand and added substantial complexity to payment
state, safeguards, testing, and the UI. We removed it to keep the payment experience
reliable and straightforward. Multi-wallet support may be added again if actual user
demand justifies that complexity.

## Layout

- `shared/`: shared Compose Multiplatform code and iOS framework module
- `androidApp/`: Android application packaging, variants, resources, and signing
- `iosApp/`: iOS host app and Xcode project
- `e2e/`: local Docker Compose regtest Lightning/NWC harness for Maestro
- `flows/`: Maestro flows and helper scripts
- `gradle/`: Gradle wrapper and version catalog

## Prerequisites

Android:

- JDK 21 or higher
- Android SDK with platform 36
- `ANDROID_HOME` set to your Android SDK path

iOS:

- macOS with Xcode 15 or newer
- Xcode Command Line Tools

## Setup

Clone the repository and enter the app workspace:

```bash
git clone https://github.com/NicolaLS/lasr.git
cd lasr/app
```

Install the pre-commit hook:

```bash
./gradlew installGitHooks
```

## Android

Build a debug APK:

```bash
./gradlew :androidApp:assembleDebug
```

The APK is written to:

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Install it on a connected device:

```bash
adb install androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

The debug app installs as `xyz.lilsus.papp.dev`, separate from the release app.

## Release

After bumping `versionCode` and `versionName`, run the Android release helper:

```bash
scripts/release-android <version>
```

For example:

```bash
scripts/release-android 2
```

The helper validates the version, runs checks, builds the signed Play `.aab` and
universal `.apk`, creates and pushes a signed `v<version>` tag, then creates the
GitHub release with both artifacts attached. Preview the exact commands without
changing anything with:

```bash
scripts/release-android <version> --dry-run
```

Upload the generated Play bundle to Google Play:

```text
androidApp/build/outputs/bundle/release/androidApp-release.aab
```

To backfill a GitHub release tag for an already-published commit, reuse existing
artifacts and point the tag at that commit:

```bash
scripts/release-android 2 --commit 1b5a9f5 --skip-checks
```

## iOS

Build the Kotlin framework for the iOS simulator:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Open the host app in Xcode:

```bash
open iosApp/iosApp.xcodeproj
```

Or build from the command line:

```bash
cd iosApp
xcodebuild build \
  -scheme iosApp \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
  CODE_SIGNING_ALLOWED=NO
```

## Checks

Run the full app verification suite:

```bash
./gradlew check :androidApp:assembleDebug
```

Run ktlint only:

```bash
./gradlew ktlintCheck
```

Auto-format Kotlin code:

```bash
./gradlew ktlintFormat
```

Run Android unit tests:

```bash
./gradlew :shared:testAndroidHostTest
```

Run all shared tests:

```bash
./gradlew :shared:allTests
```

Run a single test class:

```bash
./gradlew :shared:testAndroidHostTest --tests "xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceParserTest"
```

Regenerate Compose resources after editing strings, fonts, or drawables:

```bash
./gradlew :shared:packComposeResources
```

## CI

Pull requests run ktlint, Android lint, the Android debug build, Android unit tests, and
the iOS app build.

## More Docs

- [Release builds](../docs/release.md)
- [E2E and Maestro testing](../docs/e2e.md)
- [Local Lightning/NWC Maestro testing](e2e/README.md)
