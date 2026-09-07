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

There is currently no supported provider-neutral replacement harness. The build
commands above produce E2E apps; they do not run an automated end-to-end suite.

Use private test credentials supplied outside the repository. Never commit Blink
credentials, Flint recovery phrases or Breez API keys, or NWC URIs, and never
expose them to pull-request workflows.
