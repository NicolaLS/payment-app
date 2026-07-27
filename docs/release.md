# Release process

Blip and Lasr start at version `1.0.0`/build code `1`. Use app-qualified tags:
`blip-v1.0.0` and `lasr-v1.0.0`; use `-rc.N` while validating candidates.

## Signing model

Both Android packages use one Google-managed app-signing key. Register the
second package with Play's **use the same key as another app** option. Both
packages also use one locally managed, resettable Play upload key.

The same `RAYL_UPLOAD_*` environment variables sign both AABs. Copy
`.envrc.example` to an ignored local file and load the passwords from a secret
manager.

GitHub and Zapstore distribute the signed universal APK downloaded from Play's
latest releases/bundles page. Do not publish a locally signed APK: using the
Play artifact preserves update compatibility between channels.

## Android candidate

1. Start from a clean `main` checkout.
2. Confirm the app version and existing checks.
3. Run `scripts/release-android <blip|lasr> 1.0.0`.
4. Upload the resulting AAB from `dist/<app>/` to Play internal testing.
5. Download Google's signed universal APK.
6. Run `scripts/verify-play-apk <app> <apk>`.
7. Record the app-signing certificate and AAB/APK SHA-256 values.
8. Attach the verified APK to a draft app-qualified GitHub release.
9. Run `zsp publish --check` with `zapstore.yaml` for Lasr or
   `distribution/blip/zapstore.yaml` for Blip.

Before the first Zapstore publication, add the same suite publisher `pubkey` to
both configs and link the Play app-signing certificate to that identity.

## iOS candidate

Archive the `iosApp` Release scheme for each app using the existing Apple team
identity. Confirm bundle ID, version/build, privacy report, required-reason API
manifest, export-compliance answers, and symbols before uploading to TestFlight.

## Go-live

Production Play/App Store submission, final signed tags, GitHub publication,
and Zapstore publication are owner-controlled actions. Promote the exact
artifacts that passed internal testing; do not rebuild between channels.

The NWC `0.3.2-SNAPSHOT` dependency is an intentional owner-approved exception.
For every candidate, record the resolved artifact checksum so a republished
snapshot cannot silently change the reviewed build.
