# LASR E2E Harness

This directory contains the local Docker Compose test stack used by Maestro.
It is intentionally small and project-specific:

- Bitcoin Core regtest (`bitcoin/bitcoin:28.1`)
- two Core Lightning nodes (`payer`, `receiver`)
- `cln-nip47` installed into the CLN image, matching the image ReGLab used
- `nostr-rs-relay`
- a tiny helper API for Maestro scripts

The default setup starts from clean ignored data, mines regtest funds, opens a
`payer -> receiver` channel, and gives the app a reachable NWC URI.

## Requirements

Install these once:

- Docker Desktop or OrbStack, with Docker running
- `jq`
- `curl`
- Maestro CLI
- Maestro Studio Desktop, if you use the visual editor

## Local Workflow

Start the stack:

```bash
e2e/bin/up
```

This writes `e2e/.env.local` and prints the relay/helper URLs.

Install the E2E app on the target device or simulator:

```bash
./gradlew :androidApp:installE2e      # Android
./gradlew installE2eIos              # booted iOS simulator
```

Run Maestro:

```bash
source e2e/.env.local
maestro test flows/tests/new_users/onboarding_complete_nwc.yaml
maestro test flows
```

Or use the wrapper:

```bash
e2e/bin/maestro-suite
```

For Maestro Studio Desktop:

1. Start the stack with `e2e/bin/up`.
2. Open the desktop app, not `maestro studio`.
3. Choose workspace `app/flows`.
4. Select `No environment`.
5. Run a flow.

The JavaScript helpers have local defaults, so Studio does not need a custom
environment for the normal Android emulator/iOS simulator case.

Stop the stack:

```bash
e2e/bin/down
```

Remove the local Docker volumes too:

```bash
e2e/bin/down --clean
```

## Device URLs

The helper API is host-side and defaults to:

```text
TEST_WALLET_SERVICE_URL=http://127.0.0.1:8082
```

The nostr relay is exposed on host port `8081`.

- Android emulator: Maestro rewrites NWC relay URLs to `ws://10.0.2.2:8081`.
- iOS simulator: URLs stay on `ws://127.0.0.1:8081`.
- Physical devices: set `TEST_NWC_RELAY_DEVICE_URL` to a `ws://...` URL the
  device can reach, and set `PAPP_E2E_BIND_HOST=0.0.0.0` before `e2e/bin/up`.

Useful overrides:

```bash
PAPP_E2E_RELAY_PORT=18081 PAPP_E2E_HELPER_PORT=18082 e2e/bin/up
PAPP_E2E_BIND_HOST=0.0.0.0 TEST_NWC_RELAY_DEVICE_URL=ws://192.168.1.20:8081 e2e/bin/up
```

## Helper API

The helper API is served from the `helper` compose service. It calls
`lightning-cli` through the mounted CLN RPC sockets.

Endpoints:

- `GET /health`
- `GET /get-nwc-uri?node=payer`
- `GET /wallets?nodes=payer,receiver`
- `POST /create-invoice`
- `POST /wait-invoice-paid`
- `POST /invoice-status`
- `POST /pay-invoice`

Manual helpers:

```bash
e2e/bin/nwc-uri              # payer NWC URI
e2e/bin/nwc-uri receiver     # receiver NWC URI
e2e/bin/invoice 21           # receiver invoice for 21 sats
```

For multi-wallet Maestro fixtures, run `flows/utils/get_nwc_uri.js` with:

```text
NWC_NODES=payer,receiver
NWC_ACTIVE_NODE=payer
```

## Flow Contract

The default Maestro suite targets the E2E app id `xyz.lilsus.papp.e2e`.

The NWC flows use:

- `flows/utils/get_nwc_uri.js`
- `flows/utils/create_invoice.js`
- `flows/utils/assert_invoice_paid.js`

`get_nwc_uri.js` emits the fixture JSON expected by the app:

```text
e2eProfile       new_user | nwc_user | blink_user | multi_user | slow_internet_user
e2eReset         true clears E2E wallet storage before applying fixtures
e2eFixtureJson   JSON fixture data with concrete wallet values
e2ePaymentInput  payment input to inject for a payment test run
e2ePaymentInputSource  deep_link (default) | camera
```

Public deep-link tests should live in a separate release-oriented suite.
