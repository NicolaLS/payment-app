# End-to-end verification

The supported Android E2E packages are:

- `xyz.lilsus.blip.e2e`
- `xyz.lilsus.flint.e2e`
- `xyz.lilsus.lasr.e2e`

Build them from the repository root:

```shell
./gradlew :blip:androidApp:assembleE2e
./gradlew :flint:androidApp:assembleE2e
./gradlew :lasr:androidApp:assembleE2e
```

The former combined application's Docker/Maestro harness remains available in Git history (the
extraction baseline is `d17dc43`; its harness originated in `ab2e872`). It targets the retired
`xyz.lilsus.papp.e2e` package and combines several providers, so it must not be copied unchanged
into the independent products.

Until a provider-neutral replacement harness exists, the release smoke pass on physical Android
and iPhone devices covers each product's:

1. fresh onboarding and wallet connection;
2. paste, camera, and deep-link payment input, including denied camera permission recovery;
3. successful payment plus cancellation, provider error, and offline behavior;
4. wallet removal and reconnection;
5. visible tabs, Payment Hub targets/groups, settings, and light/dark appearance; and
6. English, German, and Spanish presentation.

Blink credentials, Flint recovery phrases and Breez API keys, and NWC URIs must come through
private maintainer or store-review channels. Never commit them or expose them to pull-request
workflows.
