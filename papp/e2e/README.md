# Regtest NWC E2E Stack

This directory contains a lightweight local stack for app E2E tests against real regtest
Lightning payments.

## Services

- `bitcoind`: Bitcoin regtest chain.
- `cln-payer`: Core Lightning node with `cln-nip47`; the app pays through this wallet.
- `cln-receiver`: Core Lightning node that creates BOLT11 invoices.
- `nostr-relay`: Private local relay for NWC request/response events.
- `test-harness`: HTTP API used by Maestro and local scripts.

## Local Usage

Start the stack:

```bash
e2e/bin/up
```

Every `up` is a cold boot: the script wipes all volumes (bitcoind/lightningd/relay/nostr
DBs) before bringing the services back up. This makes each session deterministic and
removes any need to clean up between runs, at the cost of a slower bootstrap (~30s while
the chain is mined and the channel is opened).

Wait until the harness reports ready:

```bash
curl http://127.0.0.1:3021/health
```

Fetch an NWC URI:

```bash
e2e/bin/nwc-uri
```

Create an invoice:

```bash
e2e/bin/invoice 21
```

Reset all regtest state:

```bash
e2e/bin/reset
```

Optional local Maestro environment variables can be stored in `e2e/.env.local`. This file is
gitignored and is sourced by `e2e/bin/maestro-suite` before running the CLI suite:

```bash
export INVOICE_SATS=21
export TEST_NOSTR_RELAY_URL=ws://127.0.0.1:7777
```

## Local Device Scope

This stack is scoped to local Android emulators and iOS simulators. Physical devices are
intentionally out of scope for now, so there is no `adb reverse`, tunnel, or public staging
infra in the default workflow.

Running against a physical Android device would require a different host-reachability setup,
such as `adb reverse tcp:7777 tcp:7777` plus a device-appropriate relay URL, or a tunnel.

The CLN plugin talks to the relay through Docker DNS, but the app must use a relay URL that is
reachable from the emulator or simulator.

The relay binds host port `7777` (not the more conventional `7000`): macOS Monterey+ silently
claims port `7000` for AirPlay Receiver, and AirPlay answers `127.0.0.1:7000` before Docker's
forward gets a chance. Keep the host port off `7000` to avoid that trap.

The Maestro `get_nwc_uri.js` helper rewrites the NWC URI relay parameter to
`TEST_NOSTR_RELAY_URL`. When that variable is not set, it chooses the local route from
Maestro's platform:

- Android emulator: `ws://10.0.2.2:7777`
- iOS simulator: `ws://127.0.0.1:7777`

The Android e2e build allows cleartext traffic because the local relay is served as `ws://`.
Release builds keep cleartext disabled.

For Android emulator flows:

```bash
e2e/bin/up
maestro test \
  flows/tests/existing_users/payments_nwc_bolt11_with_amount.yaml
```

For iOS simulator flows:

```bash
e2e/bin/up
maestro test \
  flows/tests/existing_users/payments_nwc_bolt11_with_amount.yaml
```

To run all local flows included by `flows/config.yaml`:

```bash
e2e/bin/up
e2e/bin/maestro-suite
```

The suite runner sources `e2e/.env.local` if present, waits for `GET /health` to report ready,
then runs `maestro test flows/`. Maestro Studio is useful for developing individual flows, but
local Studio runs do not currently honor `config.yaml` flow discovery. Use the CLI command above
for the full local suite.

For host-side manual checks, `e2e/bin/nwc-uri` shows the harness URI directly.

## HTTP API

- `GET /health`
- `GET /nwc-uri`
- `POST /invoice`
- `POST /invoice/wait-paid`
- `POST /mine/{blocks}`

The existing Maestro helper scripts under `flows/utils/` already target this API shape.

## Current Scope

The first milestone is the NWC + BOLT11 happy path only. Blink and LNURL are intentionally
out of scope for this stack.
