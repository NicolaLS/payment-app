# Payment Hub widget implementation tracker

This tracker was explicitly requested by the owner for the widget revamp.
Canonical issue status remains in
[MOB-28](https://linear.app/nicola-susca/issue/MOB-28/finish-and-validate-the-payment-hub-for-10)
for widget polish and
[MOB-47](https://linear.app/nicola-susca/issue/MOB-47/implement-bitrefill-claro-purchase-experiment-in-payment-hub)
for the Claro experiment integrated into `main`.
[MOB-48](https://linear.app/nicola-susca/issue/MOB-48/integrate-production-hub-services-for-11-and-maintain-raylblip-10)
tracks future production integration and maintenance of the local-only 1.0 branches.

| Work slice | Status | Evidence or next step |
| --- | --- | --- |
| Shared wire DTOs and bounded native contracts | Verified: scoped formatting and compilation | `core:hub-api`; `metric/v1` and `service/v1` are advertised |
| Backend catalog, content, and service orders | Verified: six focused backend tests and authenticated catalog smoke | `backend:hub`; Claro El Salvador top-ups and packages when a key is configured; empty catalog without it |
| HTTP client, request metadata, per-item compatibility, unavailable states | Verified: scoped formatting and three focused client tests | `integration:hub`; absent URL leaves local features usable |
| Contract documentation and example/schema fixtures | Implemented; all current JSON parses | `docs/payment-hub-widgets.md`, `docs/claro-service-experiment.md`, `docs/api/payment-hub`; new service schema execution not run |
| Local widget definitions, instance persistence, and projections | Implemented and verified | Contacts, Shortcut, Favorites, Recents; 11 focused Hub host tests pass, including service recovery |
| Android native gallery, previews, configuration, and canvas | Implemented; Debug build passes | Contact selection/creation, variant previews, drag and accessible reorder, direct removal |
| iOS native gallery, previews, configuration, and canvas | Implemented; Debug simulator build passes | Shared SwiftUI renderer, native navigation/forms, drag and accessible reorder, direct removal |
| App composition and configurable backend URL | Implemented | `main` permits opt-in development endpoints; no URL skips remote session and order-store creation; the 1.0 branches ignore URL overrides |
| Native Claro configuration, quote review, and payment handoff | Implemented; Android and iOS Debug builds pass | Saved phone, ranged top-up and fixed packages, signed invoice validation, existing provider payment confirmation |
| Anonymous order recovery and supplier routing | Implemented; focused recovery checks pass | Device secure storage, durable backend journal, pinned supplier reference, separate payment and delivery status |
| Localization and module boundaries | Verified | English, German, Spanish resources; `verifyNativeLocalizations` and `verifyModuleDependencies` pass |
| Owner UI review | Pending | Review both native flows and interaction details before closing MOB-28 |
| Real Claro purchase and carrier delivery | Owner validation pending | Follow `docs/claro-service-experiment.md`; no live invoice or purchase was created during agent verification |
| Public service rollout and additional suppliers | Outside this experiment | Business supplier access, production persistence/traffic controls, refunds, and complete purchase history remain future work |

## Verification recorded on 2026-09-07

- Scoped Kotlin formatting passed for the changed modules.
- Rayl Android Debug assembled; Flint's Android shared composition compiled.
- Rayl Xcode Debug arm64 simulator build passed, including its Kotlin framework.
- Payment Hub's 11 focused host tests passed, covering persistence, contact
  deletion, computed widgets, prompt behavior, grid packing, remote state, and
  resuming an interrupted service request with the same order ID and token.
- Three HTTP client tests and six backend tests passed. Backend checks cover
  supplier money/package mapping, unpaid invoice preparation, status semantics,
  and durable order recovery.
- An authenticated read-only local backend smoke returned one Claro descriptor
  with three variants, one USD airtime range, and four package offers. No order
  records were created.
- All example/schema JSON parses. New service fixture/schema validation was not
  executed because a JSON Schema validator was unavailable.

No native device QA, real purchase, or broad test matrix was run. A deployed URL
is not assumed. The [Claro experiment guide](claro-service-experiment.md) covers
local backend setup, native Debug builds, installation, and the manual flow.
