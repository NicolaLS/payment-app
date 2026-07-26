# Legacy extraction ledger

`apps/legacy` is a read-only reference implementation. Migration work copies or
reinterprets code into the new modules; it never changes the legacy application.
Blip and Lasr are new applications, so this ledger tracks feature extraction,
not user-data migration. New modules must not preserve legacy storage schemas,
keys, credentials, or compatibility paths.

Each row is complete only when the destination has the applicable legacy
behavior, visuals, resources, unit tests, and end-to-end coverage. Provider
selection is intentionally omitted because Blip and Lasr are single-provider
applications.

| Area | Legacy source | Destination | Status |
| --- | --- | --- | --- |
| Blip shell | `apps/legacy` | `apps/blip` | Shared settings flow wired |
| Lasr shell | `apps/legacy` | `apps/lasr` | Shared settings flow wired |
| Provider-neutral values | `domain/model` | `core/model` | Extracted |
| Theme and primitives | `presentation/theme`, reusable components | `core/ui` | Theme extracted |
| Localized amount formatting | `domain/format` | `core/ui` | Extracted |
| Theme settings | settings presentation/domain/data | `feature/theme-settings` | Extracted |
| Language settings | settings presentation/domain/data | `feature/language-settings` | Extracted |
| Currency settings | settings presentation/domain/data | `feature/currency-settings` | Extracted |
| Payment settings | settings presentation/domain/data | `feature/payment-settings` | Preferences, state, and UI extracted |
| Payment shortcuts | shortcut configuration | `feature/payment-shortcuts` | State holder and UI extracted |
| Generic settings hub | settings presentation | `feature/settings` | Shared settings, contacts, and shortcuts flow extracted |
| Shared contacts and shortcuts | contacts domain/data/presentation | `feature/contacts` | Contacts UI and shortcut persistence extracted |
| Blink contact import | Blink contacts import | `apps/blip/feature/blink-contact-import` | Optional Blip-only story |
| Shared payment contracts | payment domain | `core/payment` | Fiat price contract extracted |
| Exchange-rate integration | exchange data | `integration/exchange-rate` | CoinGecko adapter extracted |
| Shared onboarding | onboarding presentation | `feature/onboarding` | Neutral shell, welcome, features, and agreement UI extracted |
| Blink integration | Blink data/domain | `apps/blip/integration/blink` | Pending |
| NWC integration | NWC data/domain | `apps/lasr/integration/nwc` | Pending |
| Blink-only stories | provider-specific presentation | `apps/blip/feature/*` | Blink wallet instructions extracted |
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
