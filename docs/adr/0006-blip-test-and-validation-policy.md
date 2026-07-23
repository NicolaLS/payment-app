# ADR 0006: Blip keeps developer tests intentionally small

Status: accepted for the initial extraction

No legacy test, Maestro flow, E2E harness, integration test, screenshot test,
instrumented test, mock server, fake provider, fake store, or test DI graph is
migrated into Blip.

The initial extraction adds no automated tests. If a later change genuinely
needs one, it may add only a direct-value unit test for small Blip-owned pure
logic, without mocks, stubs, fakes, provider clients, databases, relays, or
lifecycle orchestration. ACINQ parsing and cryptography are not retested.
Robolectric is preferred only when such a small test needs an Android API.

Routine feedback is formatting, the Blip architecture check, and the Android
debug build. Release and iOS validation are milestone gates, not per-commit
work. QA owns integration, UI, and E2E coverage.

Enforcement: `:apps:blip:shared:verifyBlipArchitecture` fails if prohibited test
source sets or local flow/E2E harnesses appear under `apps/blip`.
