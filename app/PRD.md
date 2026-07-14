# Lasr Product Requirements Document

**Document status:** Current-product baseline and forward requirements  
**Last updated:** 2026-07-12  
**Product:** Lasr mobile app  
**Platforms:** Android and iOS  
**Repository scope:** Kotlin Multiplatform app in `app/`

## 1. Executive summary

Lasr is a non-custodial Lightning payment companion for people who want to pay in person with less friction while continuing to use their existing wallet. It connects to one or more wallets through Nostr Wallet Connect (NWC) or the Blink API, recognizes a Lightning payment request, applies the user's confirmation policy, and asks the selected wallet to pay it.

The product is deliberately narrower than a full Bitcoin wallet. Lasr does not hold funds, create a wallet, receive payments, expose a balance, or maintain a permanent ledger. Its primary experience is a camera-first checkout surface designed to turn a QR code into a clear payment outcome quickly. Contacts, reusable payment shortcuts, configurable auto-pay, local currency display, and session transaction tracking reduce repeated work without taking ownership of the user's money.

This PRD documents the behavior evidenced in the current codebase and defines that behavior as the product baseline. Requirements labeled **Future** are proposals; all other requirements describe the expected current product.

## 2. Product context and problem

Lightning wallets often make safe, deliberate trade-offs for broad wallet functionality. At a physical checkout, however, navigating a general-purpose wallet, opening its scanner, reviewing a small payment, and returning to the next transaction can introduce unnecessary delay.

Lasr addresses that moment by acting as a focused payment remote:

- It keeps the scanner ready as the home experience.
- It works with funds already held in a connected wallet.
- It can remove confirmation taps for payments below a user-selected threshold.
- It supports repeated payments to known Lightning addresses through contacts and shortcuts.
- It makes uncertain and concurrent payment states visible so users can avoid accidental duplicate payments.

The core product tension is speed versus irreversible-payment safety. Lasr must be fast enough to improve checkout while making wallet selection, amount, confirmation policy, pending state, and final outcome unambiguous.

## 3. Vision and principles

### Vision

Make everyday Lightning checkout feel as immediate as pointing a camera, without asking users to move funds into another wallet or give up control over payment policy.

### Product principles

1. **The user's wallet remains the source of truth.** Lasr delegates payments and must not imply that it holds funds.
2. **Fast by default, bounded by consent.** Auto-pay is useful only when users understand and choose its limits.
3. **Uncertainty must be visible.** A timeout is not the same as a failed payment; ambiguous outcomes must never be presented as definitive failure.
4. **Prevent duplicate intent.** The product should recognize repeated or in-flight requests and give users safe recovery choices.
5. **Local-first personalization.** Wallet metadata, preferences, contacts, and shortcuts should remain on-device unless a feature explicitly requires a remote service.
6. **The camera is the primary interaction.** Secondary features must not compromise the speed or clarity of the payment surface.
7. **Platform parity.** Core flows should behave consistently on Android and iOS while using native security, camera, lifecycle, and haptic capabilities.

## 4. Goals and non-goals

### Goals

- Let a user connect an existing NWC or Blink wallet and select the active payment source.
- Pay supported Lightning requests from scan to result with minimal interaction.
- Let users choose when a payment requires confirmation, including a bounded auto-pay threshold.
- Support fixed-amount and amountless BOLT11 invoices, LNURL-pay requests, and Lightning addresses.
- Make pending, successful, already-paid, failed, and unknown outcomes understandable.
- Make repeat payments efficient through local contacts and fixed or fiat-denominated shortcuts.
- Provide useful currency, language, theme, and haptic preferences.
- Protect wallet credentials with platform secure storage.

### Non-goals

- Custodying funds, generating seed phrases, or recovering wallets.
- Receiving Lightning payments or generating invoices.
- Displaying wallet balances or providing complete wallet transaction history.
- Paying on-chain Bitcoin addresses.
- Paying BOLT12 offers in the current baseline.
- Buying, selling, swapping, or otherwise exchanging Bitcoin.
- Managing Lightning channels, nodes, Nostr identities, or wallet budgets from within Lasr.
- Guaranteeing payment completion when a connected provider, Nostr relay, LNURL server, or network is unavailable.
- Synchronizing contacts, shortcuts, or preferences between devices in the current baseline.

## 5. Target users

### Primary persona: frequent in-person payer

A Lightning user who already has a compatible wallet and wants the fastest possible payment flow at cafés, markets, events, or other face-to-face checkouts. They are comfortable setting a small auto-pay limit and value immediate visual feedback.

### Secondary persona: repeat payer

A user who frequently pays the same people or merchants. They want named contacts and shortcuts such as “Coffee — $3.00” while still settling over Lightning at the current exchange rate.

### Secondary persona: multi-wallet user

A user with separate spending contexts—for example, a small NWC budget and a Blink wallet—who needs to store multiple connections, see the active wallet, and switch deliberately.

### Excluded persona

A Bitcoin newcomer seeking an app that creates and holds a wallet. Onboarding should explain that Lasr requires a separate wallet and guide the user back after they obtain one.

## 6. Jobs to be done

- When I am at a Lightning checkout, I want to point my phone at the payment code and finish quickly so I do not hold up the line.
- When an amount is small, I want it paid under rules I chose so I do not approve every routine transaction.
- When an amount or destination needs review, I want a clear confirmation before an irreversible payment is sent.
- When a payment response is delayed, I want Lasr to keep checking and explain the uncertainty so I do not pay twice.
- When I often pay the same recipient, I want to reuse their address and a familiar amount without scanning again.
- When I have more than one wallet, I want to know which one will pay and be able to change it.

## 7. Scope and feature inventory

| Area | Current product capability | Boundary |
|---|---|---|
| Onboarding | Product introduction, wallet-type choice, risk agreement, connection guidance, and auto-pay policy | A compatible external wallet is required |
| Wallets | Add, inspect, remove, and select among NWC and Blink connections | No balances, transfers, or provider-side budget management |
| Payment input | Camera QR, supported mobile deep links, contact selection, shortcut selection, and donation action | No general text-entry payment screen in the current UI |
| Lightning formats | BOLT11, LNURL-pay, Lightning addresses, and `bitcoin:` URIs containing a Lightning parameter | On-chain addresses and BOLT12 are rejected with guidance |
| Payment control | Always confirm or confirm above a 500–100,000 sat threshold; optional confirmation for manually entered and shortcut amounts | Auto-pay cannot override provider permissions or limits |
| Results | Paid amount, fee, already-paid state, failure details, pending tracking, and preimage receipt QR when available | Session transactions are not permanent wallet history |
| Repeat payments | Local contacts, roles, Blink contact import, and fixed SAT/BTC/fiat shortcuts | No cross-device synchronization |
| Personalization | Primary/secondary currency, English/German/Spanish, light/dark/system theme, scan/payment haptics | Fiat display requires an external exchange rate |

## 8. Core user journeys

### 8.1 First launch and wallet connection

1. The user sees Lasr's value proposition and feature explanation.
2. The user chooses Blink, an NWC-compatible wallet, or indicates they do not yet have a wallet.
3. Lasr explains the non-custodial model and requires explicit acceptance of the risk statement.
4. Lasr presents provider-specific connection instructions.
5. For NWC, the user scans or pastes a `nostr+walletconnect` URI, reviews discovered wallet capabilities and encryption warnings, optionally names it, and chooses whether it is active.
6. For Blink, the user pastes an API key, names the wallet, and Lasr validates authorization and resolves the default Blink wallet.
7. The user selects an auto-pay policy and enters the payment screen after successful connection.

### 8.2 Scan and pay a fixed BOLT11 invoice

1. The camera-first payment screen is ready and shows the active wallet.
2. Lasr recognizes a BOLT11 invoice and prevents repeated scanner detections from starting multiple payments.
3. Lasr validates the invoice and extracts the amount.
4. The confirmation policy is evaluated against the amount and input source.
5. If required, the amount is shown for explicit confirmation; otherwise payment begins.
6. Lasr delegates to the wallet that was active when the attempt started.
7. The user sees pending, paid, already paid, failure, or unknown-state feedback.
8. If available, the user can view the payment preimage as a receipt.

### 8.3 Pay an amountless or dynamic request

1. The user scans an amountless BOLT11 invoice, LNURL-pay request, or uses a Lightning address/contact.
2. Lasr resolves any remote LNURL metadata and allowable amount range.
3. The user enters an amount in the configured primary currency, with secondary-currency context when available.
4. Lasr validates minimum, maximum, precision, exchange-rate, and optional comment constraints.
5. Lasr requests a fresh invoice when required and applies manual-entry confirmation policy.
6. Payment follows the normal pending and result flow.

### 8.4 Pay a shortcut

1. The user swipes up or opens the shortcuts/contacts sheet from the payment screen.
2. The user selects a saved shortcut tied to a Lightning address.
3. A fiat shortcut is converted at the current exchange rate; a Bitcoin-denominated shortcut uses its saved amount.
4. Lasr resolves a new payable invoice and respects the “always confirm shortcut payments” preference.
5. On success, local contact and shortcut usage statistics are updated.

### 8.5 Recover from a pending or repeated payment

1. A payment that has not resolved promptly moves into the session transaction tracker while work may continue in the background.
2. The user can return to scanning and later open the pending transaction.
3. If the same fixed invoice is detected again, Lasr offers safe retry/view options and explains that only one attempt can settle that invoice.
4. If a repeated dynamic request is detected, Lasr distinguishes retrying the same invoice from intentionally requesting a new invoice.
5. A lookup result updates the session item to success, failure, already paid, or unresolved without falsely labeling uncertainty as failure.

## 9. Functional requirements

Priority definitions: **P0** is essential to the core promise or payment safety; **P1** is important to a complete product; **P2** is valuable but deferrable.

### 9.1 Onboarding

- **ONB-01 (P0):** Lasr shall show onboarding until setup is completed with a usable wallet connection.
- **ONB-02 (P0):** Onboarding shall state that Lasr connects to an external wallet and does not hold funds.
- **ONB-03 (P0):** The user shall explicitly accept the risk agreement before proceeding to wallet connection.
- **ONB-04 (P0):** The app shall recommend limited NWC budgets or small spending balances.
- **ONB-05 (P1):** Onboarding shall provide separate, actionable connection instructions for NWC and Blink.
- **ONB-06 (P1):** A user without a wallet shall receive compatible-wallet guidance and a route back into setup.
- **ONB-07 (P0):** The user shall choose either always-confirm or threshold-based confirmation during setup.
- **ONB-08 (P1):** Threshold selection shall show the selected satoshi amount and a secondary-currency equivalent when a rate is available.

### 9.2 Wallet connection and management

- **WAL-01 (P0):** The app shall accept NWC credentials through QR scan, paste, or a `nostr+walletconnect` deep link.
- **WAL-02 (P0):** The app shall validate NWC URI structure before saving it.
- **WAL-03 (P0):** Before saving an NWC wallet, Lasr shall attempt wallet discovery and display its public key, primary relay, Lightning address, methods, encryption support, and negotiated encryption when available.
- **WAL-04 (P0):** The app shall warn when `pay_invoice` is not advertised or when modern NIP-44 encryption is unavailable; the user may make an informed choice to continue where technically possible.
- **WAL-05 (P0):** The app shall accept a Blink API key, validate it, and provide specific authentication/permission feedback.
- **WAL-06 (P0):** Wallet credentials shall be stored using Android Keystore-backed encryption or iOS Keychain storage.
- **WAL-07 (P1):** A user shall be able to store multiple NWC and Blink wallet connections, assign display aliases, and set exactly one available wallet as active.
- **WAL-08 (P0):** Every payment attempt shall remain bound to the wallet selected at attempt creation, even if the active wallet changes while the request is pending.
- **WAL-09 (P1):** A user shall be able to view wallet details, refresh the default Blink wallet identifier, remove a connection, and see the newly selected active wallet.
- **WAL-10 (P0):** Removing a Blink wallet shall also remove its locally stored API credential.
- **WAL-11 (P0):** A revoked or invalid Blink credential detected during use shall produce actionable reconnection guidance and may remove the unusable wallet automatically if this behavior is clearly disclosed.

### 9.3 Payment input and parsing

- **PAY-01 (P0):** The default signed-in surface shall prioritize a continuously available QR scanner, subject to camera permission and lifecycle state.
- **PAY-02 (P1):** Where hardware supports it, the scanner shall provide near/far modes and gesture zoom without persisting accidental zoom between scanning sessions.
- **PAY-03 (P0):** Lasr shall recognize BOLT11 invoices, bech32 or URL LNURL-pay inputs, Lightning addresses, and supported `lightning:`, `lnurl:`, and `bitcoin:` deep links.
- **PAY-04 (P0):** A `bitcoin:` input shall be payable only when it contains a valid Lightning parameter; otherwise the app shall explain that on-chain payment is unsupported.
- **PAY-05 (P1):** BOLT12 offers shall produce a specific unsupported-format message rather than a generic invalid-code error.
- **PAY-06 (P1):** An NWC code scanned on the payment screen shall route to wallet connection rather than fail as a payment.
- **PAY-07 (P0):** Blank, malformed, expired, unrecognized, or incompatible inputs shall not initiate a wallet request.
- **PAY-08 (P0):** Input detection shall be debounced/serialized so one visible QR code cannot create uncontrolled duplicate requests.
- **PAY-09 (P1):** The app shall support payment inputs received while already running and route them to the payment flow only after onboarding is complete.

### 9.4 Amount, currency, and confirmation

- **AMT-01 (P0):** For an amountless BOLT11 invoice, the user shall enter a positive amount before payment.
- **AMT-02 (P0):** For LNURL-pay and Lightning-address payments, the entered amount shall respect the endpoint's minimum and maximum sendable amounts.
- **AMT-03 (P0):** Currency conversion shall preserve valid precision and prevent silent zero, overflow, or out-of-range payment amounts.
- **AMT-04 (P1):** The app shall support SAT, BTC, USD, EUR, GBP, CAD, AUD, CHF, and JPY as primary or secondary display currencies.
- **AMT-05 (P0):** Fiat-denominated manual and shortcut payments shall require a usable current or explicitly acceptable cached exchange rate; conversion failure shall stop payment with a recoverable error.
- **CNF-01 (P0):** Confirmation mode shall support “Always” and “Above threshold.”
- **CNF-02 (P0):** The configurable threshold shall use discrete values from 500 to 100,000 satoshis and default to 10,000 satoshis.
- **CNF-03 (P0):** Confirmation decisions shall be based on the resolved millisatoshi amount, not only the formatted display value.
- **CNF-04 (P0):** User-entered amounts shall honor the dedicated “confirm manual entry” preference.
- **CNF-05 (P0):** Shortcut payments shall honor the dedicated “always confirm shortcut payments” preference.
- **CNF-06 (P0):** A confirmation screen shall show the amount and require an explicit Pay action; dismissal shall send no payment.

### 9.5 Payment execution, pending state, and results

- **EXE-01 (P0):** Payment execution shall route through the wallet type and immutable wallet target associated with the attempt.
- **EXE-02 (P0):** Network unavailable, relay failure, timeout, wallet rejection, authentication failure, and unexpected errors shall be distinguishable where the provider supplies enough information.
- **EXE-03 (P0):** Provider-specific errors such as insufficient balance, route not found, expired invoice, self-payment, amount too small, limit exceeded, rate limiting, or invalid Blink key shall be actionable.
- **EXE-04 (P0):** A timeout or inconclusive lookup shall be labeled as unknown/unconfirmed and explain that payment may have succeeded.
- **EXE-05 (P0):** Lasr shall continue tracking an in-flight request after its blocking payment UI is dismissed and expose it in the current session transaction list.
- **EXE-06 (P0):** Duplicate detection shall distinguish fixed BOLT11 invoices from dynamic LNURL/address sources and provide context-appropriate safe actions.
- **EXE-07 (P0):** A paid result shall show the paid amount and fee when available. Blink results may explain that displayed fees can later be partially refunded.
- **EXE-08 (P1):** An already-paid invoice shall be clearly distinguished from a newly successful payment and state that no new payment was sent.
- **EXE-09 (P1):** When a preimage is available, Lasr shall display it as a payment receipt and offer a scannable QR representation.
- **EXE-10 (P1):** Session transaction entries shall show created time, amount, status, fee or error, and open into the corresponding result detail.
- **EXE-11 (P0):** Session-only transaction tracking shall not be described as complete or permanent wallet history.

### 9.6 Contacts and shortcuts

- **CON-01 (P1):** Users shall be able to create, edit, search, categorize, and delete local contacts with a valid Lightning address and optional alias.
- **CON-02 (P1):** Contact roles shall include Favorite, People, and Merchants, and the payment sheet shall allow role filtering.
- **CON-03 (P1):** After a successful payment to a new Lightning address, Lasr shall optionally prompt to save it, controlled by a user preference.
- **CON-04 (P1):** Blink users shall be able to preview and selectively import eligible Blink contacts while avoiding duplicates.
- **SCT-01 (P1):** A shortcut shall include a title, contact, positive amount, currency, and optional description/comment.
- **SCT-02 (P1):** Shortcuts may be denominated in supported Bitcoin or fiat currencies; fiat shortcuts shall resolve using the payment-time exchange rate.
- **SCT-03 (P0):** Lasr shall request a fresh LNURL invoice for a repeated shortcut rather than reusing a previously paid dynamic invoice.
- **SCT-04 (P1):** Deleting a contact shall also remove shortcuts owned by that contact.
- **SCT-05 (P2):** Local payment counts and last-paid timestamps should influence useful ordering without exposing a misleading transaction history.

### 9.7 Settings and support

- **SET-01 (P1):** Users shall be able to change payment confirmation, manual/shortcut confirmation, contact prompt, scan haptic, and success haptic preferences.
- **SET-02 (P1):** Users shall be able to choose primary and secondary currencies, with the two selections clearly distinguished.
- **SET-03 (P1):** Users shall be able to use device language or explicitly select English, German, or Spanish.
- **SET-04 (P1):** Users shall be able to use system, light, or dark theme.
- **SET-05 (P1):** Settings shall expose app version, privacy policy, and source-code links.
- **SET-06 (P2):** Users may initiate preset developer donations through the same standard Lightning payment pipeline; donation payments receive no special authority or confirmation bypass.

## 10. Business rules and edge cases

1. No wallet connected means no payment can be initiated; the remedy is wallet setup.
2. The active wallet is a default for new attempts, not a mutable pointer for attempts already underway.
3. A confirmation threshold is inclusive for auto-pay up to the selected limit; amounts above it require confirmation.
4. An explicitly enabled manual-entry or shortcut confirmation rule takes precedence over threshold auto-pay.
5. LNURL responses and returned invoices must be validated against the requested amount and expected metadata before payment.
6. A repeated BOLT11 invoice is not equivalent to a repeated Lightning address. Fixed invoices should be retried or inspected; dynamic sources can intentionally generate a new invoice.
7. “Already paid” means the current attempt did not send a new successful payment.
8. The scanner must pause or suppress processing while amount, confirmation, result, contact, or other blocking UI is open.
9. Camera denial must not crash or trap the user; wallet deep links, contacts, shortcuts, and settings remain usable.
10. Fiat amounts are estimates until converted and rounded into millisatoshis; the final Lightning amount is authoritative.

## 11. Non-functional requirements

### Security and privacy

- **NFR-SEC-01:** NWC connection secrets and Blink API keys shall never be logged, shown in analytics, committed, or stored in plain-text general preferences.
- **NFR-SEC-02:** Android secrets shall use Keystore-backed authenticated encryption; iOS secrets shall use Keychain.
- **NFR-SEC-03:** Wallet removal and credential invalidation shall delete the associated secret material.
- **NFR-SEC-04:** NWC discovery shall surface advertised permissions and encryption downgrade risk before connection.
- **NFR-PRV-01:** Contacts, shortcuts, preferences, and their local usage statistics shall remain on-device in the baseline product.
- **NFR-PRV-02:** The privacy policy shall disclose calls made to wallet providers, Nostr relays, LNURL hosts, and CoinGecko.
- **NFR-PRV-03:** Any future telemetry must exclude invoices, preimages, API keys, NWC URIs, Lightning addresses, wallet public keys, relay URLs, comments, and exact payment identifiers by default.

### Reliability and performance

- **NFR-REL-01:** The app shall never report success without provider success or a successful payment lookup.
- **NFR-REL-02:** The app shall never convert an ambiguous timeout into a definitive failure solely to simplify UI state.
- **NFR-REL-03:** Pending tracking shall tolerate app lifecycle transitions supported by each platform and restore user attention to resolved items within the active session.
- **NFR-PERF-01:** Scanner preview and detection shall become usable promptly after permission and lifecycle readiness, without blocking on exchange-rate or wallet-metadata refresh.
- **NFR-PERF-02:** Successful exchange rates may be cached briefly; the current implementation uses a one-minute in-memory cache.
- **NFR-PERF-03:** Network operations shall have bounded timeouts and retry behavior appropriate to idempotency.

### Accessibility, localization, and compatibility

- **NFR-ACC-01:** Every actionable icon and QR/receipt image shall have a meaningful accessible label.
- **NFR-ACC-02:** Payment status shall not be communicated by color or animation alone.
- **NFR-ACC-03:** Controls shall remain usable with large text and screen readers; critical payment actions must have stable semantic identities for automation.
- **NFR-L10N-01:** User-facing product copy shall use Compose resources and remain complete across English, German, and Spanish before release.
- **NFR-CMP-01:** Core behavior shall remain consistent across Android and iOS, with platform-specific implementations for camera, secure storage, clipboard, lifecycle, haptics, time formatting, and system UI.
- **NFR-CMP-02:** Android shall support API 24 and later; release target requirements follow the configured Android target SDK. iOS minimum support follows the Xcode project configuration.

## 12. Success metrics

The current repository does not show a product analytics implementation. Metrics below are recommended product measures, not evidence of existing collection. Prefer privacy-preserving, opt-in, coarse event aggregation or structured usability studies.

### North-star measure

**Successful checkout rate:** percentage of user-intended payment attempts that reach a confirmed paid or already-paid resolution without an unsafe duplicate attempt.

### Supporting measures

- Median time from valid QR recognition to payment result, segmented by auto-pay versus confirmed payment.
- Payment resolution rate: paid + already paid / initiated wallet requests.
- Unknown outcome rate after the final lookup window.
- Duplicate-protection intervention rate and subsequent safe-resolution rate.
- Wallet connection completion rate by NWC and Blink.
- Scanner recognition success rate for supported QR types.
- Repeat-payment adoption: active users with at least one contact or shortcut and successful payments from each surface.
- Crash-free payment sessions and platform-specific camera failure rate.
- Percentage of users choosing threshold auto-pay and the distribution of threshold bands, collected only if privacy requirements can be met.

### Initial product targets

Targets require baseline measurement before commitment. A launch-quality objective should be:

- No known defect that can send a payment without satisfying the selected confirmation policy.
- No known defect that can route an in-flight attempt to a newly selected wallet.
- No known secret exposure in logs, crash reports, UI automation output, or general preferences.
- At least 99% crash-free completion across tested happy-path payment sessions.
- All P0 acceptance scenarios automated at the domain/ViewModel level, with representative NWC and Blink end-to-end coverage.

## 13. Acceptance criteria for release

A release is acceptable when:

1. A new Android or iOS user can complete both supported onboarding branches with the correct test credentials and reach the payment screen.
2. A valid fixed BOLT11 invoice can be paid through NWC and Blink, subject to confirmation policy.
3. Amountless BOLT11 and LNURL-pay flows enforce amount entry and range validation.
4. Lightning-address and shortcut payments request fresh invoices and apply current exchange rates where needed.
5. Always-confirm, threshold-confirm, manual-entry-confirm, and shortcut-confirm policies each have automated tests proving both allow and block paths.
6. Changing the active wallet during an in-flight payment does not change the attempt's wallet target.
7. Pending, timeout, unknown, already-paid, duplicate, and successful lookup behaviors have deterministic tests.
8. On-chain and BOLT12 codes cannot trigger a Lightning wallet payment and show specific feedback.
9. Wallet add/remove and invalid-credential flows leave no orphaned Blink secret.
10. English, German, and Spanish resources build successfully with no missing user-facing string.
11. `./gradlew check :androidApp:assembleDebug` passes.
12. Representative Maestro flows pass against the local E2E harness for onboarding and BOLT11 payment.

## 14. Dependencies and operational constraints

- NWC behavior depends on compatible wallets, their advertised methods/encryption, configured Nostr relays, and the `nwc-kmp` client library.
- Blink behavior depends on Blink GraphQL API availability, API-key permissions, and the user's configured default wallet.
- LNURL and Lightning-address flows depend on recipient-host availability and standards-compliant responses.
- Fiat display and fiat-denominated shortcuts depend on CoinGecko's public price API.
- Camera behavior depends on CameraX/ML Kit on Android and AVFoundation on iOS, plus user permission and device hardware.
- Android currently has a minimum SDK of 24, targets SDK 36, and restricts packaged native ABIs according to build configuration.
- Session transactions are held by the active payment ViewModel and should be assumed ephemeral across process death or a fresh app session.

## 15. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Auto-pay sends an unintended payment | Irreversible loss of funds | Explicit onboarding consent, bounded thresholds, manual/shortcut overrides, immutable policy evaluation, limited-wallet recommendation |
| Timeout encourages a duplicate payment | Double-payment risk or user distrust | Preserve unknown state, background lookup, duplicate detection, safe retry language |
| Active wallet changes mid-payment | Wrong wallet charged | Snapshot wallet target when attempt starts and use it for payment and lookup |
| Credential leakage | Wallet compromise | Platform secure storage, secret redaction, no sensitive telemetry, removal cleanup |
| NWC capability or encryption mismatch | Failed payment or weaker privacy | Discovery, capability display, explicit warnings, modern encryption preference |
| Fiat rate unavailable or stale | Wrong shortcut amount | Bounded cache, stop unsafe conversion, show recoverable error, retain Bitcoin-denominated alternatives |
| Dynamic request reused | Duplicate or invalid invoice behavior | Generate a fresh invoice for every intentional repeat payment |
| Session list mistaken for wallet history | Incorrect user accounting | Label as session transactions and defer canonical history to the wallet |
| Cross-platform scanner divergence | Uneven reliability | Shared scanner contract, platform tests, stable E2E tags, device coverage |

## 16. Future opportunities

These are not part of the current baseline and require separate discovery, threat modeling, and prioritization.

- **FUT-01:** Privacy-preserving diagnostics that help quantify scanner and payment reliability without collecting payment metadata.
- **FUT-02:** Optional encrypted backup/sync for contacts and shortcuts.
- **FUT-03:** Persistent local transaction references reconciled against the connected wallet, clearly separated from authoritative provider history.
- **FUT-04:** BOLT12 support after ecosystem compatibility and duplicate-payment semantics are defined.
- **FUT-05:** Additional wallet providers behind the existing payment-provider abstraction.
- **FUT-06:** Better camera-denied/manual input alternatives while maintaining the scanner-first product identity.
- **FUT-07:** Accessibility audits, dynamic-type hardening, and expanded translations beyond English, German, and Spanish.

## 17. Open product questions

1. Is Lasr primarily positioned for consumers paying merchants, merchants/staff making outbound payouts, or both? Current onboarding copy uses both “take payments” and payer-oriented behavior, which should be reconciled.
2. Should invalid Blink credentials automatically remove a connection, or should the app retain metadata and offer an explicit reconnect action?
3. What is the promised duration of background pending tracking on each platform, especially after process termination?
4. Should users have a non-camera paste/manual-entry affordance for payment requests, distinct from amount entry?
5. What freshness limit is acceptable for fiat shortcut conversion if CoinGecko is temporarily unavailable?
6. Should NWC wallets lacking `pay_invoice` be connectable at all, or should the current warning become a hard block?
7. Is session transaction persistence desirable, and if so, what minimal data can be stored without creating privacy or accounting confusion?
8. What quantitative latency and payment-success targets are appropriate after baseline field measurement?

## 18. Evidence used for this PRD

This document was derived from the repository's shared domain, data, presentation, navigation, localization, unit-test, and Maestro-flow code. Key evidence includes:

- `shared/src/commonMain/kotlin/xyz/lilsus/papp/App.kt`
- `shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/main/`
- `shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/onboarding/`
- `shared/src/commonMain/kotlin/xyz/lilsus/papp/presentation/settings/`
- `shared/src/commonMain/kotlin/xyz/lilsus/papp/domain/`
- `shared/src/commonMain/kotlin/xyz/lilsus/papp/data/`
- `shared/src/commonMain/composeResources/values/strings.xml`
- `shared/src/commonTest/kotlin/xyz/lilsus/papp/`
- `flows/tests/` and `e2e/`

Where implementation details and product copy differed or left intent ambiguous, this PRD records the ambiguity under open questions rather than treating an inference as a settled requirement.
