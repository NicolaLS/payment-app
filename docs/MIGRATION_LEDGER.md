# Extraction completion record

The read-only multi-wallet reference application was archived at `papp-final`
and `papp-legacy`, then removed from the active Gradle project. Git history
remains the source for any future parity investigation.

Blip and Lasr are independent, pre-release applications:

- Blip owns the Blink integration and uses `xyz.lilsus.blip`.
- Lasr owns the NWC integration and uses `xyz.lilsus.lasr`.
- Shared `core:*`, `feature:*`, and `integration:*` modules contain only
  provider-neutral code.
- Neither app imports or decodes legacy preferences, credentials, databases,
  identifiers, or installation state.

## Completed boundaries

- Shared model, UI, settings, camera, network, payment, onboarding, contacts,
  wallet management, exchange-rate, and LNURL modules are consumed by both
  apps where applicable.
- Blink credentials, API access, contact import, wallet connection, and wallet
  details remain Blip-owned.
- NWC credentials, discovery, relay lifecycle, wallet connection, and wallet
  details remain Lasr-owned.
- Provider selection and provider branching were removed from the new apps.
- App identity, icons, legal links, store metadata, deep links, backup
  exclusions, Android release signing inputs, and iOS Release builds are
  app-specific and ready for candidate preparation.

## Verified closeout

- Repository ktlint and existing checks.
- Blip and Lasr Android debug and minified E2E APK assembly.
- Blip and Lasr Android Release bundle and lint tasks.
- Blip and Lasr Kotlin/Native Release frameworks for arm64 iOS devices and
  arm64 simulators.
- Blip and Lasr unsigned Xcode Release builds for a generic iOS destination.

No new unit or integration tests were added during closeout. Manual wallet,
payment, localization, accessibility, and store-submission verification belongs
to the dedicated QA/release pass.

## Accepted release exceptions and owner gates

- Lasr intentionally resolves maintainer-owned
  `io.github.nicolals:nwc-kmp:0.3.2-SNAPSHOT`. Every candidate must record the
  resolved artifact checksum.
- Kotlin/Native Release frameworks disable only
  `RemoveRedundantCallsToStaticInitializersPhase` as a narrowly scoped
  workaround for KT-64508. The same failure is present in the archived legacy
  app after the ACINQ dependency graph was introduced; Debug builds did not
  exercise this Release optimizer phase.
- One Google-managed Android app-signing key and one local Play upload key are
  shared by both packages. Play-signed universal APKs are the artifacts
  redistributed through GitHub and Zapstore.
- The Zapstore publisher key, store-account configuration, final localized
  screenshots, live-wallet smoke tests, signing/archive uploads, review forms,
  and production publication require owner or QA credentials and approval.
- A provider-neutral shared E2E harness remains optional follow-up work. The
  archived harness is coupled to the old multi-provider runtime and was not
  copied into the completed product tree.
