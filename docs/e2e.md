# E2E and Maestro Testing

Run these commands from `app/`.

## Reset Dev Install State

The debug/dev app installs separately from release as `xyz.lilsus.papp.dev`, so you can
reset it without touching a real install.

Android:

```bash
adb shell pm clear xyz.lilsus.papp.dev
```

Uninstalling the debug app also works:

```bash
adb uninstall xyz.lilsus.papp.dev
```

iOS simulator:

```bash
xcrun simctl erase booted
```

If you only want to remove the app first:

```bash
xcrun simctl uninstall booted xyz.lilsus.papp.dev
```

The full simulator erase is more reliable because wallet data can survive uninstall in
Keychain.

## Android Emulator

For faster and less flaky local Maestro runs, disable Android system animations on the
emulator before running flows:

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

Restore the defaults with:

```bash
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
```

If multiple devices are connected, add `-s <device-id>` to `adb`.

Maestro's `platform.android.disableAnimations: true` setting is cloud-only. For local
emulator runs, use the `adb shell settings put global ...` commands above.

## iOS Simulator

Maestro on iOS expects the app to already be installed on the target simulator.

Build and install the iOS debug app:

```bash
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -derivedDataPath build/ios-derived \
  build

xcrun simctl install booted build/ios-derived/Build/Products/Debug-iphonesimulator/Lasr.app
xcrun simctl launch booted xyz.lilsus.papp.dev
```

To verify the app is installed before running Maestro:

```bash
xcrun simctl listapps booted | grep xyz.lilsus.papp.dev
```

If you need to target a specific simulator, replace `booted` with its UDID in the
`simctl` commands.

## Regtest NWC Stack

The local Lightning/NWC stack lives in `app/e2e/`.

Start the stack:

```bash
e2e/bin/up
```

Run all local Maestro flows included by `flows/config.yaml`:

```bash
e2e/bin/maestro-suite
```

For stack details, helper scripts, relay URLs, and the test harness API, see
[`app/e2e/README.md`](../app/e2e/README.md).

## Maestro Environment Variables

Some Blink onboarding flows require access to:

```bash
BLINK_KEY_ALL
```

Provide it through Maestro Studio's Environment Manager for local Studio runs, or pass it
explicitly for CLI runs:

```bash
maestro test -e BLINK_KEY_ALL="$BLINK_KEY_ALL" flows/tests/new_users/onboarding_complete_blink.yaml
```

Do not commit the secret value into Flow YAML or a tracked Maestro config file.
