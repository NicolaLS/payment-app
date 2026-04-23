# Lasr

Kotlin Multiplatform Lightning payment app targeting Android and iOS.

## Reset Dev Install State

The debug/dev app installs separately from release as `xyz.lilsus.papp.dev`, so you can reset it without touching a real install.

### Android

Clear the dev app data:

```bash
adb shell pm clear xyz.lilsus.papp.dev
```

Uninstalling the debug app also works:

```bash
adb uninstall xyz.lilsus.papp.dev
```

### iOS Simulator

The reliable full reset is to erase the simulator, because the dev app's wallet data is stored in Keychain and can survive uninstall.

From the Simulator app:

`Device > Erase All Content and Settings...`

From the CLI:

```bash
xcrun simctl erase booted
```

If you only want to remove the app first:

```bash
xcrun simctl uninstall booted xyz.lilsus.papp.dev
```

## Maestro on Android Emulator

For faster and less flaky local Maestro runs, disable Android system animations on the emulator before running flows:

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

Note: Maestro's `platform.android.disableAnimations: true` setting is cloud-only. For local emulator runs, use the `adb shell settings put global ...` commands above.

## Maestro on iOS Simulator

Maestro on iOS expects the app to already be installed on the target simulator. In this repo, `clearState` only works after the debug app is installed on that simulator.

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

xcrun simctl install booted build/ios-derived/Build/Products/Debug-iphonesimulator/papp.app
xcrun simctl launch booted xyz.lilsus.papp.dev
```

To verify the app is installed before running Maestro:

```bash
xcrun simctl listapps booted | grep xyz.lilsus.papp.dev
```

If you need to target a specific simulator, replace `booted` with its UDID in the `simctl` commands.

## Maestro Environment Variables

The Blink onboarding Maestro flows require access to:

```bash
BLINK_KEY_ALL
```

Provide it through Maestro Studio's Environment Manager for local Studio runs, or pass it explicitly for CLI runs:

```bash
maestro test -e BLINK_KEY_ALL="$BLINK_KEY_ALL" .maestro/onboarding_blink.yaml
```

Do not commit the secret value into Flow YAML or a tracked `.maestro/config.yaml`.
