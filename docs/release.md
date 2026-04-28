# Release Builds

Run these commands from `papp/`.

## Android Signing Setup

Release signing is automated through Gradle, `bundletool`, `direnv`, and `pass`.

The local release helper expects the Android release keys under:

```text
/Users/sus/scratch/android-release-papp/publish-key/papp-publish.jks
/Users/sus/scratch/android-release-papp/release-key/papp-signing.jks
```

The keystore aliases are:

```text
publish/upload bundle key: papp-publish-key
local install APK key:   papp-signing-key
```

The `papp/.envrc` file exports the required Gradle signing environment variables and
reads the password from:

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

## Play Bundle

Build the signed release Android App Bundle:

```bash
./gradlew :composeApp:buildSignedReleaseBundle
```

Output:

```text
composeApp/build/outputs/bundle/release/composeApp-release.aab
```

This bundle is signed with the publish/upload keystore, which is the artifact to upload
to Google Play.

## Local Release Install

Install the signed release on a connected Android device:

```bash
./gradlew :composeApp:installSignedReleaseApk
```

This task builds the signed `.aab`, runs `bundletool build-apks --connected-device`,
signs the generated APK set with the app-signing keystore, and installs it with
`bundletool install-apks`.

If multiple devices are connected, set the target before running the task:

```bash
export ANDROID_SERIAL=<device-id>
./gradlew :composeApp:installSignedReleaseApk
```

Intermediate APK set output:

```text
composeApp/build/outputs/apks/release/composeApp-release.apks
```
