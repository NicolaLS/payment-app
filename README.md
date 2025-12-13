# papp

A simple cross-platform Lightning payment app using [Nostr Wallet Connect (NWC)](https://nwc.dev/).

Scan QR codes, pay invoices. That's it.

## Supported Payment Types

- BOLT11 invoices (with or without amount)
- LNURL-pay
- Lightning Addresses (LUD16)

## NWC Deep Links

Wallet connections can be added via `nostr+walletconnect://…` links.

- Android scheme registration: `papp/composeApp/src/androidMain/AndroidManifest.xml`
- iOS scheme registration: `papp/iosApp/iosApp/Info.plist` (`CFBundleURLTypes`)

## Download

### From CI (Android)

Download the latest debug APK from the GitHub Actions **CI** workflow (`.github/workflows/ci.yml`): open the [Actions tab](https://github.com/NicolaLS/payment-app/actions), pick the most recent successful **CI** run, and download the `debug-apk` artifact.

To install:
1. Transfer the APK to your Android phone
2. Open it and tap "Install" (you may need to enable "Install from unknown sources" in settings)

### From Source

See below.

## Building from Source

### Prerequisites

**Android:**
- JDK 21 or higher ([Temurin](https://adoptium.net/) recommended)
- Android SDK with platform 36 (install via Android Studio or `sdkmanager`)
- `ANDROID_HOME` environment variable set to your SDK path

**iOS:**
- macOS with Xcode 15+ (command-line example below targets the iPhone 15 simulator included with Xcode 15)
- Xcode Command Line Tools (`xcode-select --install`)

### Clone

```bash
git clone https://github.com/NicolaLS/payment-app.git
cd payment-app/papp
```

### Build Android

```bash
# Debug APK
./gradlew :composeApp:assembleDebug

# APK will be at: composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

### Build iOS

```bash
# Build the Kotlin framework
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# Then open in Xcode and run
open iosApp/iosApp.xcodeproj
```

Or build from command line:
```bash
cd iosApp
xcodebuild build \
  -scheme iosApp \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 15,OS=latest' \
  CODE_SIGNING_ALLOWED=NO
```

### Install on Device

**Android:**
```bash
# With a device connected via USB (enable USB debugging first)
adb install composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

**iOS:**
Open the project in Xcode, select your device, and hit Run. You'll need an Apple Developer account for physical devices.

## Contributing

### Code Style

The project uses [ktlint](https://pinterest.github.io/ktlint/) for Kotlin formatting.

```bash
# Check formatting
./gradlew ktlintCheck

# Auto-fix formatting issues
./gradlew ktlintFormat
```

### Git Hooks

Install the pre-commit hook to catch formatting issues before committing:

```bash
./gradlew installGitHooks
```

This runs automatically when you sync the project in Android Studio/Fleet, but you can run it manually if needed.

### Running Tests

```bash
./gradlew :composeApp:check
```

If you add or modify strings, fonts, or drawables, regenerate Compose resources with:
```bash
./gradlew :composeApp:packComposeResources
```
Android Studio/Fleet runs this automatically on sync; run it manually when building outside the IDE.

### CI

Pull requests run:
- ktlint (code style) on shared sources
- Android Lint
- Android debug build + unit tests
- iOS framework build (simulator)
- iOS app build via `xcodebuild` (simulator)

## License

MIT
