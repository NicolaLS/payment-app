# Lasr

Lasr is the Nostr Wallet Connect-only Rayl Suite application. It connects to
one existing NWC wallet and contains no Blink integration or provider-selection
UI.

- Android package: `xyz.lilsus.lasr`
- Debug package: `xyz.lilsus.lasr.dev`
- E2E package: `xyz.lilsus.lasr.e2e`
- iOS bundle: `xyz.lilsus.lasr`

Build from the repository root:

```shell
./gradlew :lasr:androidApp:assembleDebug
./gradlew :lasr:androidApp:assembleE2e
./gradlew :lasr:shared:linkReleaseFrameworkIosSimulatorArm64
```

Lasr accepts `nostr+walletconnect:`, `lightning:`, `bitcoin:`, and `lnurl:`
links. Credentials are stored in app-scoped encrypted storage and are not
imported from older apps.
