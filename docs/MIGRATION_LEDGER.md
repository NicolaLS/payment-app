# Product extraction completion record

The retired multi-wallet reference application was archived at `papp-final` and `papp-legacy`, then
removed from the active Gradle project. Git history remains the source for future parity research.
There is no runtime provider selector and no import path for that application's credentials,
preferences, databases, or installation state.

The suite now contains three independent products:

- Blip owns Blink behavior (`xyz.lilsus.blip` on Android and `com.nicolasusca.blip` on iOS).
- Flint owns Spark behavior (`xyz.lilsus.flint`).
- Lasr owns Nostr Wallet Connect behavior (`xyz.lilsus.lasr`).
- Root `core:*`, `feature:*`, and `integration:*` modules contain only provider-neutral behavior and
  presentation used directly by real consumers.

Blink credentials, API access, contact import, wallet connection, and wallet details remain
Blip-owned. Spark credentials, SDK lifecycle, payment state, and wallet connection remain
Flint-owned. NWC credentials, discovery, relay lifecycle, wallet connection, and wallet details
remain Lasr-owned. App identity, icons, legal links, store metadata, deep links, and signing inputs
remain app-specific.

The previous extraction closeout verified repository formatting/checks, Blip and Lasr Android
Debug/E2E/Release builds, Kotlin/Native frameworks, and unsigned Xcode Release builds. Flint was
added later and is covered by current suite build workflows. Those historical signals do not
replace current release QA.

Release candidates still require the owner gates in `docs/release.md`, real-wallet smoke testing,
localized screenshots, store/reviewer declarations, and explicit publication approval. The
owner-approved `io.github.nicolals:nwc-kmp:0.3.3-SNAPSHOT` dependency remains a documented exception
whose resolved checksum must be recorded for a candidate.
