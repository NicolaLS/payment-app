# Release process

Blip, Flint, and Lasr start at version `1.0.0`/build code `1`. Use app-qualified
tags such as `blip-v1.0.0`, `flint-v1.0.0`, and `lasr-v1.0.0`; use `-rc.N`
while validating candidates.

## Signing model

Blip, Flint, and Lasr share one locally managed app-signing key and one locally
managed, resettable Play upload key. A copy of the app-signing key is
transferred to Play App Signing during enrollment, so Play can sign store
deliveries with the same identity that signs artifacts built here.

Two identities are used, and they are not interchangeable:

| Artifact | Key | Variables |
| --- | --- | --- |
| App Bundle uploaded to Play | Upload | `RAYL_UPLOAD_*` |
| Universal APK for GitHub, Zapstore, direct install | App signing | `RAYL_APP_SIGNING_*` |

Signing the universal APK locally with the app-signing key is what keeps a
sideloaded install update compatible with a Play install, and it keeps the
non-Play channels working even if the Play account becomes unavailable. Never
publish an APK signed with the upload key.

Copy `.envrc.example` to an ignored local file and load the passwords from a
secret manager. Each password may be given directly, or as a path to a file
holding the password on its first line; passwords are never passed to
`bundletool` on the command line.

Check readiness without revealing secrets:

```bash
./gradlew :lasr:androidApp:printReleaseSigningConfig
```

The last line must read `Release signing ready: true`.

Flint release candidates additionally require `FLINT_BREEZ_API_KEY` to be
loaded from the maintainer's secret manager. The release script rejects a Flint
candidate when it is absent.

`distribution/app-signing-certificate.sha256` pins the SHA-256 fingerprint of
the app-signing certificate. `scripts/verify-release-apk` asserts it before any
artifact is staged, because an APK published under the wrong key permanently
strands existing sideload and Zapstore users.

## Local release install

Install the signed release build, with production signing and R8 applied, on a
connected device:

```bash
./gradlew :lasr:androidApp:installSignedRelease
```

This builds the bundle, derives a device-specific APK set with `bundletool`,
signs it with the app-signing key, and installs it. Set `ANDROID_SERIAL` first
when several devices are attached. The release package has no application ID
suffix, so it installs alongside the `.dev` and `.e2e` builds but replaces any
store install of the same app.

## Android candidate

1. Start from a clean `main` checkout.
2. Confirm the app version and existing checks.
3. Run `scripts/release-android <blip|flint|lasr> 1.0.0`.
4. Collect both staged artifacts from `dist/<app>/`.
5. Upload the `-play.aab` artifact to Play internal testing.
6. Attach the verified `-universal.apk` artifact to a draft app-qualified
   GitHub release.
7. Record the app-signing certificate and both SHA-256 values.
8. Run `zsp publish --check` with the owning app's reviewed Zapstore
   configuration.

Before the first Zapstore publication, add the same suite publisher `pubkey` to
each app config and link the app-signing certificate to that identity.

## Before the first Play enrollment

Play App Signing enrollment is irreversible: the app-signing key for a package
can never be rotated afterwards, and Play's key upgrade only affects new
installs, which does not help sideload or Zapstore users. Complete this
checklist first.

1. Generate the suite app-signing key, RSA 4096, valid for 100 years:

   ```bash
   keytool -genkeypair -v -keystore rayl-suite-app-signing.jks \
       -alias rayl-suite-app-signing -keyalg RSA -keysize 4096 \
       -validity 36500 -dname "CN=Rayl Suite app signing key, O=..., C=..."
   ```

2. Generate the resettable upload key, RSA 2048:

   ```bash
   keytool -genkeypair -v -keystore rayl-suite-upload.jks \
       -alias rayl-suite-upload -keyalg RSA -keysize 2048 -validity 9125
   ```

3. Store both passwords in the secret manager and update the local `.envrc`.
4. Replace the fingerprint in `distribution/app-signing-certificate.sha256`
   with the new app-signing certificate:
   `keytool -list -keystore rayl-suite-app-signing.jks`.
5. Uninstall every previously signed test build from all devices; the signature
   change makes them non-upgradable.
6. Export the app-signing key with the PEPK tool offered by the Play Console,
   choose **Export and upload a key from a Java keystore** during enrollment,
   and register the upload certificate separately.
7. Register the remaining packages with Play's **use the same key as another
   app** option so Blip, Flint, and Lasr keep one shared app-signing identity.
8. After enrollment, confirm the certificate Play reports matches the pinned
   fingerprint.

Until this checklist is done, the tooling intentionally reuses the retired
`papp` keys so the release flow can be exercised. No artifact signed with them
may be published.

## iOS candidate

Archive the `iosApp` Release scheme for each app using the existing Apple team
identity. Flint additionally requires its production Breez API key through the
private Release configuration. Confirm bundle ID, version/build, privacy report,
required-reason API manifest, export-compliance answers, and symbols before
uploading to TestFlight.

## Go-live

Production Play/App Store submission, final signed tags, GitHub publication,
and Zapstore publication are owner-controlled actions. Promote the exact
artifacts that passed internal testing; do not rebuild between channels.

The NWC `0.3.3-SNAPSHOT` dependency is an intentional owner-approved exception.
For every candidate, record the resolved artifact checksum so a republished
snapshot cannot silently change the reviewed build.
