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
