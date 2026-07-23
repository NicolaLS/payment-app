# Legacy extraction baseline

The combined `papp` application is frozen at commit
`6e57bfd2e82f5b5d9098221f0960e06a2200929a`.

- Signed tag: `papp-final`
- Protected branch: `papp-legacy`
- Android application ID: `xyz.lilsus.papp`
- Apple bundle ID: `xyz.lilsus.papp`
- Kotlin namespace: `xyz.lilsus.papp`
- Android minimum SDK: 24
- iOS deployment target: 18.2
- Providers compiled together: Blink and NWC

The frozen sources are reference material for behavior and visual appearance,
not a code-migration contract.

## Product surface

The extraction capability matrix is maintained in section 8 of
[`IMPLEMENTATION-PLAN.md`](../IMPLEMENTATION-PLAN.md). It covers:

- onboarding, risk agreement, provider setup, reconnect, and removal;
- scan, paste, manual input, standard payment links, and provider links;
- fixed and amountless BOLT11, LNURL-pay, Lightning addresses, and Bitcoin
  URIs carrying Lightning requests;
- confirmation, duplicate handling, payment submission, lookup, pending and
  unknown outcomes, and transaction detail;
- contacts, shortcuts, currencies, exchange rates, payment preferences,
  haptics, language, theme, donation, and wallet settings.

## Visual reference

The existing Play assets are retained under [`assets/play`](../assets/play):

- `phone_screenshot_01.png`
- `phone_screenshot_02.png`
- `phone_screenshot_03.png`
- `phone_screenshot_04.png`
- `feature-graphic.png`

The full light/dark and screen-state baseline is recoverable by building the
`papp-final` tag. Screenshots must never contain real credentials or customer
data.

## Do not reproduce

The following are explicitly defects or debt, not parity requirements:

- runtime `WalletType` provider routing;
- the large combined `MainViewModel` and fragmented payment truth;
- session-only attempts and mutable-current-connection lookup;
- secret-bearing routes, public state, logs, or persistence;
- handwritten Bitcoin/Lightning protocol primitives;
- custom `Result<T>` with `Loading`;
- JSON aggregates used as a database;
- service location, global lossy event channels, and hidden coroutine scopes;
- swallowed cancellation and string-matched provider failures;
- legacy tests, Maestro flows, and the NWC E2E harness.

The detailed evidence and replacement rules live in
[`ARCHITECTURE_REWRITE_REVIEW.md`](../ARCHITECTURE_REWRITE_REVIEW.md).
