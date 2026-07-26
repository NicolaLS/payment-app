# Legacy extraction ledger

`apps/legacy` is a read-only reference implementation. Migration work copies or
reinterprets code into the new modules; it never changes the legacy application.

Each row is complete only when the destination has the applicable legacy
behavior, visuals, resources, unit tests, and end-to-end coverage. Provider
selection is intentionally omitted because Blip and Lasr are single-provider
applications.

| Area | Legacy source | Destination | Status |
| --- | --- | --- | --- |
| Blip shell | `apps/legacy` | `apps/blip` | Empty shell |
| Lasr shell | `apps/legacy` | `apps/lasr` | Empty shell |
| Provider-neutral values | `domain/model` | `core/model` | Extracted |
| Theme and primitives | `presentation/theme`, reusable components | `core/ui` | Pending |
| Theme settings | settings presentation/domain/data | `feature/theme-settings` | Pending |
| Language settings | settings presentation/domain/data | `feature/language-settings` | Pending |
| Currency settings | settings presentation/domain/data | `feature/currency-settings` | Pending |
| Payment settings | settings presentation/domain/data | `feature/payment-settings` | Pending |
| Generic settings hub | settings presentation | `feature/settings` | Pending |
| Shared payment contracts | payment domain | `core/payment` | Pending |
| Blink integration | Blink data/domain | `apps/blip/integration/blink` | Pending |
| NWC integration | NWC data/domain | `apps/lasr/integration/nwc` | Pending |
| Blink-only stories | provider-specific presentation | `apps/blip/feature/*` | Pending |
| NWC-only stories | provider-specific presentation | `apps/lasr/feature/*` | Pending |
| Shared payment stories | provider-neutral presentation | `feature/*` | Pending |

## Per-story completion checklist

- Public contracts do not expose provider SDK or generated-resource types.
- A feature owns its UI, state, strings, icons, and other feature-specific resources.
- Shared design primitives live in `core:ui`; shared business values live in
  `core:model`.
- App modules assemble navigation and dependency injection without provider
  branching inside shared features.
- Applicable legacy unit and end-to-end tests have equivalents.
- Android debug/e2e and iOS simulator builds pass for the consuming app.
- A visual comparison confirms no unintended behavior or look-and-feel change.

## Safety check

Before every extraction commit:

```shell
git diff --exit-code d17dc43 -- apps/legacy
```

The baseline commit is the committed legacy duplication from which extraction
started. If the legacy application is intentionally re-baselined later, update
this hash in a dedicated commit.
