# Blip distribution readiness

This document records the repository-backed facts and owner decisions needed
for Blip's first public release. Console answers must be reviewed against the
exact candidate; this file is not a substitute for the Play Console or App
Store Connect declarations.

## Verified application behavior

- Package and bundle identifier: `xyz.lilsus.blip`.
- Version name/build: `1.0.0`/`1`.
- Public store name: **Blip by RAYL**. The installed app name remains **Blip**.
- Publisher: **Bitcoin Coast**. The in-app onboarding continues to identify
  Blip as "by Bitcoin Coast."
- Canonical repository: <https://github.com/NicolaLS/rayl-suite>.
- Support email: <rayl@nicolasusca.com>.
- Support URL:
  <https://github.com/NicolaLS/rayl-suite/blob/main/docs/support.md>.
- Blip connects to one existing Blink wallet. It does not create an account,
  custody funds, exchange assets, sell cryptocurrency, or provide lending.
- Connecting requires a Blink API key with `READ` and `WRITE` permissions. Blip
  stores the API key and default Blink wallet identifier in Android
  Keystore-backed encrypted preferences or the iOS Keychain.
- Onboarding opens the dashboard's API Keys route directly, suggests email
  sign-in while allowing any working login method, and guides the user to
  create a dedicated expiring API key with `READ` and `WRITE` enabled.
- Removing the wallet deletes the stored Blink credential. Contacts,
  shortcuts, and ordinary app preferences intentionally remain until the user
  removes them or clears/uninstalls the app.
- Android excludes the Blink credential preferences from cloud backup and
  device transfer. Ordinary app data can remain eligible for system-managed
  backup and transfer.
- Blip communicates with Blink's GraphQL API, CoinGecko, and the endpoint named
  by a Lightning address or LNURL. It has no maintainer-operated backend.
- LNURL requests can transmit the requested amount and an optional payment
  comment to the payee endpoint. Blink payment requests transmit the wallet
  identifier, invoice, and amount when applicable.
- QR frames and decoded contents are processed on the device. Blip does not
  store or upload them.
- Android's bundled Google ML Kit barcode scanner sends Google limited app,
  device, per-installation, performance, API-use, event, and error metrics for
  diagnostics and usage analytics. Google states that camera input and barcode
  output remain on device.
- Blip contains no advertising or maintainer-operated analytics or telemetry.

## Google Play review draft

### Financial features

The implementation supports the following likely declarations:

- Mobile payments and digital wallets.
- Cryptocurrency wallet.

The account owner must confirm whether Google also expects **Money transfer and
wire services** for user-directed Lightning payments. Blip does not offer any
loan, credit, banking, exchange, trading, rewards, insurance, or financial
advice feature.

### Data safety evidence

Do not answer "no data collected" solely because the maintainer has no backend.
The form also covers data handled by integrated third-party services and data
transmitted for app functionality.

| Data or activity | Destination | Purpose | Maintainer receives it |
| --- | --- | --- | --- |
| Blink API key and default wallet identifier | Blink | Authentication and app functionality | No |
| Lightning invoice, requested amount, and payment result | Blink | Complete the user-requested payment | No |
| Lightning address or LNURL request, amount, and optional comment | Payee-selected endpoint | Resolve and complete the user-requested payment | No |
| Display currency code | CoinGecko | Retrieve a Bitcoin exchange rate | No |
| Imported Blink contact handles and aliases | From Blink to the device | Optional contact import | No |
| Camera frames and scanned QR content | On device only | Payment input | No |
| App/device information, per-installation identifier, performance/API-use/event/error metrics | Google ML Kit on Android | SDK diagnostics and usage analytics | No |
| Contacts, shortcuts, and preferences | App-local storage; possibly OS-managed backup | App functionality and personalization | No |

Candidate answers must account for Blink and payee retention practices. Likely
data categories to review are user/account identifiers, financial or purchase
activity, other contact information, other user content for an optional
payment comment, device or other identifiers, app interactions, and diagnostic
information. Blip's payment-service uses are app functionality; ML Kit metrics
are diagnostics and usage analytics. There is no advertising or tracking use.

Do not claim that all user data is encrypted in transit based on the current
implementation. The owner will reimplement the Lightning input parser and has
explicitly kept that work outside this release-readiness documentation pass;
re-audit endpoint and callback transport after that implementation lands.

The privacy-policy URL is required. Removing a wallet deletes only Blip's local
credential; Blink account or transaction deletion remains governed by Blink.

## App Store review draft

- Primary category: Finance.
- App privacy must cover third-party partners, including Blink, CoinGecko, and
  user-selected Lightning/LNURL services. Confirm whether the Android-only ML
  Kit metrics affect an app-level answer that covers multiple platforms.
- Candidate data types to review in App Store Connect are user identifiers,
  purchase history or other financial information, and other user content for
  optional payment comments.
- Intended purpose: App Functionality.
- Data may be linked to the user's Blink account or payment identifier.
- No data is used for tracking.
- The privacy policy is linked from the app settings. Use
  <https://github.com/NicolaLS/rayl-suite/blob/main/docs/legal/blip/privacy.md>
  as the App Store Connect privacy-policy URL.
- Use
  <https://github.com/NicolaLS/rayl-suite/blob/main/docs/support.md>
  as the support URL.
- The existing privacy manifest declares the required-reason use of user
  defaults. Confirm the archive privacy report and embedded third-party
  manifests from the exact candidate.
- Confirmed product policy: iPhone-only, portrait-only, and full-screen.

## Signing identity

Status: **Deferred and blocking public Android distribution.**

- Complete the ownership, certificate-subject, custody, recovery, and Play
  enrollment decision record in
  [`docs/signing-identity.md`](../../docs/signing-identity.md).
- Do not publish an artifact signed with the currently configured retired
  `papp` keys.
- A personal name in the Android certificate is technically compatible with a
  Bitcoin Coast Play publisher account, but the owner and custodian must be an
  explicit long-term decision. The certificate subject is not the verified
  Play Store publisher identity.

## Zapstore and GitHub

- Add the owner-approved suite publisher `pubkey` to `zapstore.yaml`.
- Validate the final config with `zsp publish --check` against the draft,
  app-qualified GitHub release.
- Publish the same app-signing-key universal APK that passed review; do not
  rebuild between GitHub and Zapstore.
- Record the APK and app-signing certificate SHA-256 values in the release
  evidence.

## F-Droid

Status: **Deferred beyond Blip 1.0.**

Official F-Droid publication is more than a listing-copy task. To preserve the
suite signing identity across channels, use F-Droid's reproducible-build path
for the upstream developer-signed APK, including `Binaries` and
`AllowedAPKSigningKeys`, rather than accepting a separately signed APK.

Before preparing metadata, confirm that Blip is eligible and reproducible in
the F-Droid build environment. In particular, review the bundled Google ML Kit
barcode-scanning dependency, its metrics collection, and the required Blink
network service for F-Droid licensing and anti-feature treatment.

## Assets and account-owned inputs

- Capture phone screenshots from the final Release candidate in English,
  German, and Spanish, then export the required store sizes without altering
  the represented UI.
- Provide any required feature graphic and promotional artwork from the final
  approved Blip identity.
- Use the confirmed RAYL Suite repository, issue tracker, support page, and
  app-specific legal URLs in store metadata.
- Use **Bitcoin Coast** as the publisher/seller identity and draft the copyright
  as **2026 Bitcoin Coast**. Confirm the age rating, availability territories,
  pricing, and export-compliance answers in each store account.
- Deliver the dedicated funded review API key only through each store's private
  reviewer-notes field.
