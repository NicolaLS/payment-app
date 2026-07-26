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
| Blip shell | `apps/legacy` | `apps/blip` | Manual-verification candidate: Blink onboarding, payment home, settings, wallet management, and app links wired |
| Lasr shell | `apps/legacy` | `apps/lasr` | Manual-verification candidate: NWC onboarding, payment home, settings, wallet management, and app links wired |
| Provider-neutral values | `domain/model` | `core/model` | Extracted |
| Theme and primitives | `presentation/theme`, reusable components | `core/ui` | Theme extracted with system sans body and system serif display typography |
| Network connectivity | platform connectivity checks | `core/network` | Android and iOS implementations extracted |
| Camera scanning | QR scanner and permission state | `core/camera` | Android and iOS scanner implementations extracted |
| Localized amount formatting | `domain/format` | `core/ui` | Extracted |
| Payment UI platform helpers | lifecycle, clipboard, haptics, retained state, time formatting | `core/ui` | Android and iOS implementations extracted |
| Theme settings | settings presentation/domain/data | `feature/theme-settings` | Extracted |
| Language settings | settings presentation/domain/data | `feature/language-settings` | Extracted |
| Currency settings | settings presentation/domain/data | `feature/currency-settings` | Extracted |
| Payment settings | settings presentation/domain/data | `feature/payment-settings` | Preferences, state, and UI extracted |
| Payment shortcuts | shortcut configuration | `feature/payment-shortcuts` | State holder and UI extracted |
| Generic settings hub | settings presentation | `feature/settings` | Shared settings, contacts, shortcuts, donation, and footer flow extracted |
| Shared contacts and shortcuts | contacts domain/data/presentation | `feature/contacts` | Contacts UI and shortcut persistence extracted |
| Blink contact import | Blink contacts import | `apps/blip/feature/blink-contacts` | Extracted |
| Blink wallet connection | Add Blink wallet state and UI | `apps/blip/feature/wallet-connection` | Extracted |
| Blink wallet details | Blink wallet details and refresh | `apps/blip/feature/wallet-details` | Extracted without legacy provider-choice rows |
| Wallet management | wallet list and removal presentation | `feature/wallet-management` | Provider-neutral screen extracted and wired to Blip and Lasr |
| Shared payment contracts | payment domain | `core/payment` | Fiat price and provider-neutral wallet payment contracts extracted |
| Exchange-rate integration | exchange data | `integration/exchange-rate` | CoinGecko adapter extracted |
| LNURL integration | LNURL pay data/domain | `core/payment`, `integration/lnurl` | Contracts and Ktor adapter extracted |
| Shared onboarding | onboarding presentation | `feature/onboarding` | Neutral shell, welcome, features, and agreement UI extracted |
| Blink integration | Blink data/domain | `apps/blip/integration/blink` | Fresh credentials, Apollo API client, account, contacts, and payments extracted |
| NWC integration | NWC data/domain | `apps/lasr/integration/nwc` | Fresh credentials, discovery, lifecycle, and payments extracted |
| Blink-only stories | provider-specific presentation | `apps/blip/feature/*` | Wallet instructions, wallet connection, wallet details, and Blink contact import extracted; onboarding import restored |
| NWC-only stories | provider-specific presentation | `apps/lasr/feature/*` | NWC wallet instructions, connection, discovery confirmation, and wallet details extracted |
| Shared payment stories | provider-neutral presentation | `feature/payment` | Scanner, input parsing, payment coordination, contacts, shortcuts, session transactions, and result flow extracted and wired to Blip and Lasr |
| Payment app links | Android and iOS payment URI handling | `feature/payment`, `apps/blip`, `apps/lasr` | `lightning`, `bitcoin`, and `lnurl` links wired in both apps; NWC connection links remain Lasr-only |
| Android shell behavior | activity and application setup | `apps/blip/androidApp`, `apps/lasr/androidApp` | Adaptive orientation, CameraX setup, locale persistence, fresh package variants, and Android-only native packaging wired in both apps |

## Latest Blip milestone

Blip is ready for manual feature verification as a replacement for the legacy
Blink path. This is intentionally an early verification milestone, not a
production-readiness claim.

- Android debug and e2e APKs assemble.
- Android debug installs as `xyz.lilsus.blip.dev` and reaches the Blink-backed
  payment home on a connected phone.
- Settings, wallet management, wallet details, and Blink contact import open
  without a runtime crash.
- The shared module compiles for Android and arm64 iOS Simulator.
- The Swift iOS app target builds for a concrete arm64 simulator destination.
- A complete payment should still be exercised manually with a low-value
  invoice before declaring behavioral parity complete.

## Latest Lasr milestone

Lasr is ready for manual feature verification as a replacement for the legacy
NWC path. This is intentionally an early verification milestone, not a
production-readiness claim.

- Android debug and e2e APKs assemble and pass Android lint.
- The packaged Android variants use `xyz.lilsus.lasr.dev` and
  `xyz.lilsus.lasr.e2e`, and both include the secp256k1 native runtime.
- Android debug was installed fresh on a connected phone. The unchanged
  onboarding reaches the NWC camera screen, and a test NWC app link reaches
  wallet discovery and its confirmation dialog without a crash.
- NWC credentials and preferences use new Lasr-only stores; no legacy
  compatibility or data migration is present.
- The shared module compiles for Android and arm64 iOS Simulator.
- Both the regular and e2e Swift iOS targets build for a concrete arm64
  simulator destination.
- A real NWC connection was not available during the automated smoke check.
  Connecting a wallet, sending a low-value payment, and exercising settings,
  wallet details, and removal remain the next manual parity check.

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
