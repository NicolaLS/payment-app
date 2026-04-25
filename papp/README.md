# Lasr

Kotlin Multiplatform Lightning payment app targeting Android and iOS.

## Android Release Builds

Release signing is automated through Gradle, `bundletool`, `direnv`, and `pass`.

### One-Time Setup

The release helper expects the Android release keys under:

```text
/Users/sus/scratch/android-release-papp/publish-key/papp-publish.jks
/Users/sus/scratch/android-release-papp/release-key/papp-signing.jks
```

The keystore aliases are:

```text
publish/upload bundle key: papp-publish-key
local install APK key:   papp-signing-key
```

The repo `.envrc` exports the required Gradle signing environment variables and reads the password from:

```bash
pass show papp-signing
```

Enable the environment after cloning or changing `.envrc`:

```bash
direnv allow
```

Check that Gradle can see the release signing setup without printing secrets:

```bash
./gradlew :composeApp:printReleaseSigningConfig
```

It should end with:

```text
Release signing ready: true
```

### Build the Play Bundle

Build the signed release Android App Bundle:

```bash
./gradlew :composeApp:buildSignedReleaseBundle
```

Output:

```text
composeApp/build/outputs/bundle/release/composeApp-release.aab
```

This bundle is signed with the publish/upload keystore, which is the artifact to upload to Play.

### Install the Signed Release Locally

Install the signed release on a connected Android device:

```bash
./gradlew :composeApp:installSignedReleaseApk
```

This task builds the signed `.aab`, runs `bundletool build-apks --connected-device`, signs the generated APK set with the app-signing keystore, and installs it with `bundletool install-apks`.

If multiple devices are connected, set the target before running the task:

```bash
export ANDROID_SERIAL=<device-id>
./gradlew :composeApp:installSignedReleaseApk
```

Intermediate APK set output:

```text
composeApp/build/outputs/apks/release/composeApp-release.apks
```

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
