# End-to-end verification

The supported E2E packages are:

- `xyz.lilsus.blip.e2e`
- `xyz.lilsus.lasr.e2e`

Build them from the repository root:

```shell
./gradlew :blip:androidApp:assembleE2e
./gradlew :lasr:androidApp:assembleE2e
```

The former combined application's Docker/Maestro harness is preserved in Git
history (the extraction baseline is `d17dc43`; its harness originated in
`ab2e872`). Moving its provider-neutral regtest pieces into a root `e2e/`
harness is a post-closeout QA task, not a 1.0 release blocker. The old flows
target `xyz.lilsus.papp.e2e` and mix NWC setup with multi-provider onboarding,
so copying them unchanged would create a misleading, non-working harness.

Until that harness is complete, perform a release smoke pass on physical
Android and iPhone devices for each app:

1. fresh onboarding and connection;
2. paste, camera, and deep-link payment input;
3. successful payment plus cancel/error/offline behavior;
4. wallet removal and reconnection;
5. contacts, shortcuts, settings, light/dark appearance; and
6. English, German, and Spanish UI.

Blink credentials and NWC URIs must be supplied through private maintainer or
store-review channels, never committed or exposed to pull-request workflows.
