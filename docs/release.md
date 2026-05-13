# Release Builds

Run these commands from `app/`.

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

The `app/.envrc` file exports the required Gradle signing environment variables and
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
./gradlew :androidApp:printReleaseSigningConfig
```

It should end with:

```text
Release signing ready: true
```

## Play Bundle

Build the signed release Android App Bundle:

```bash
./gradlew :androidApp:buildSignedReleaseBundle
```

Output:

```text
androidApp/build/outputs/bundle/release/androidApp-release.aab
```

This bundle is signed with the publish/upload keystore, which is the artifact to upload
to Google Play.

## Local Release Install

Install the signed release on a connected Android device:

```bash
./gradlew :androidApp:installSignedReleaseApk
```

This task builds the signed `.aab`, runs `bundletool build-apks --connected-device`,
signs the generated APK set with the app-signing keystore, and installs it with
`bundletool install-apks`.

If multiple devices are connected, set the target before running the task:

```bash
export ANDROID_SERIAL=<device-id>
./gradlew :androidApp:installSignedReleaseApk
```

Intermediate APK set output:

```text
androidApp/build/outputs/apks/release/androidApp-release.apks
```
