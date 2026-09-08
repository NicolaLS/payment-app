# Payment Hub widget implementation tracker

This tracker was explicitly requested by the owner for the widget revamp.
Canonical issue status remains in [MOB-28](https://linear.app/nicola-susca/issue/MOB-28/finish-and-validate-the-payment-hub-for-10).

| Work slice | Status | Evidence or next step |
| --- | --- | --- |
| Shared wire DTOs and bounded native metric contract | Verified: scoped formatting and JVM compilation | `core:hub-api`; only `metric/v1` is advertised |
| Backend catalogue and unknown-content endpoint | Verified: scoped formatting and one endpoint test | `backend:hub`; catalogue is empty and content returns 404 |
| HTTP client, request metadata, per-item compatibility, unavailable states | Verified: scoped formatting and three focused client tests | `integration:hub`; absent URL leaves local features usable |
| Contract documentation and example/schema fixtures | Implemented; four API fixture/schema pairs validated | `docs/payment-hub-widgets.md`, `docs/api/payment-hub` |
| Local widget definitions, instance persistence, and projections | Implemented and verified | Contacts, Shortcut, Favorites, Recents; 10 focused Hub tests pass |
| Android native gallery, previews, configuration, and canvas | Implemented; Debug build passes | Contact selection/creation, variant previews, drag and accessible reorder, direct removal |
| iOS native gallery, previews, configuration, and canvas | Implemented; Debug simulator build passes | Shared SwiftUI renderer, native navigation/forms, drag and accessible reorder, direct removal |
| App composition and local-only 1.0 configuration | Implemented | Backend URL overrides are ignored; neither platform constructs a remote session; native local widgets remain available |
| Localization and module boundaries | Verified | English, German, Spanish resources; `verifyNativeLocalizations` and `verifyModuleDependencies` pass |
| Owner UI review | Pending | Review both native flows and interaction details before closing MOB-28 |
| Live supplier integrations and service purchasing/fulfillment | Out of scope | Future work; example JSON is not a live capability |

## Verification recorded on 2026-09-07

- Scoped Kotlin formatting passed for the changed modules.
- Rayl Android Debug assembled; Flint's Android shared composition compiled.
- Rayl Xcode Debug arm64 simulator build passed, including its Kotlin framework.
- Payment Hub's 10 focused host tests passed, covering persistence, contact
  deletion, computed widgets, prompt behavior, grid packing, and remote state.
- Blink's contact storage regression, three HTTP client tests, and one backend
  endpoint test passed.
- Four JSON fixture/schema pairs validated; all example JSON parses.

No native device QA or broad test matrix was run. The backend is runnable locally
and returns an empty catalogue. A deployed URL is not assumed; configure it using
the [documented build setting](payment-hub-widgets.md#local-development).
