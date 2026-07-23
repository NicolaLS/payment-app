# Blip

Blip is the Blink-only RAYL payment app. Its implementation is the first app
extraction milestone.

- Android application ID: `xyz.lilsus.blip` (`.dev` for debug)
- Kotlin namespace: `xyz.lilsus.rayl.blip`
- Shared framework: `BlipShared`
- Minimum Android SDK: 29
- Minimum iOS version: 17.0

The app directly uses ACINQ's protocol stack, generated Blink GraphQL models,
SQLDelight durable payment state, and a native credential vault. It has no NWC
SDK or runtime provider selector.

Fast implementation validation:

```sh
./gradlew :apps:blip:shared:verifyBlipArchitecture \
  :apps:blip:androidApp:assembleDebug
```

Do not migrate legacy tests or add integration/E2E infrastructure. The
repository-wide rules in `IMPLEMENTATION-PLAN.md` sections 3.5 and 3.6 apply.
