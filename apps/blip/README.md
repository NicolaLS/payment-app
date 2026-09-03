# Blip

Blip is the Blink-only Rayl Suite application. It connects to one existing
Blink wallet using a user-provided API key. It contains no NWC integration or
provider-selection UI.

- Android package: `xyz.lilsus.blip`
- Debug package: `xyz.lilsus.blip.dev`
- E2E package: `xyz.lilsus.blip.e2e`
- iOS bundle: `com.nicolasusca.blip`
- iOS E2E bundle: `com.nicolasusca.blip.e2e`

Build from the repository root:

```shell
./gradlew :blip:androidApp:assembleDebug
./gradlew :blip:androidApp:assembleE2e
./gradlew :blip:shared:linkReleaseFrameworkIosSimulatorArm64
```

Blip accepts `lightning:`, `bitcoin:`, and `lnurl:` links. Credentials are
stored in app-scoped encrypted storage and are not imported from older apps.
