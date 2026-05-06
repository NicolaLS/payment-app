# LASR E2E

LASR's Maestro flows can run against either local or remote ReGLab. Local is the
default. The normal contributor workflow is:

```bash
e2e/bin/up
```

Then use Maestro Studio Desktop or `maestro test` directly.

## Requirements

Install these once:

- Docker Desktop or OrbStack, with Docker running
- Maestro CLI
- ReGLab CLI available as `reglab`
- Maestro Studio Desktop, if you use the visual editor

The root `.envrc` intentionally does not load E2E variables. E2E env is loaded
only when you explicitly source the generated file for Maestro CLI.

## Local Workflow

Start local ReGLab:

```bash
e2e/bin/up
```

This starts ReGLab, writes `e2e/.env.local`, and prints the next steps.

Install the E2E app on the target device or simulator:

```bash
./gradlew :composeApp:installE2e      # Android
./gradlew installE2eIos              # booted iOS simulator
```

For Maestro Studio Desktop:

1. Open the desktop app, not `maestro studio`.
2. Choose workspace `app/flows`.
3. Select `No environment`.
4. Run a flow.

Local Studio works with `No environment` because the ReGLab hook has local
defaults:

```text
REGLAB_URL=https://reglab.localhost
REGLAB_RUNNER_TOKEN=dev-runner
REGLAB_TEMPLATE=payments-basic
REGLAB_SNAPSHOT=default
```

If `e2e/bin/up` says Studio does not trust the local ReGLab CA, or a Studio flow
fails with a Java `PKIX path` error, run this once and then fully quit and
reopen Studio:

```bash
e2e/bin/trust-maestro-studio
```

For Maestro CLI:

```bash
source e2e/.env.local

maestro test flows/tests/new_users/onboarding_complete_nwc.yaml
maestro test flows
```

For parallel CLI runs, start enough local slots and use Maestro sharding:

```bash
e2e/bin/up --slots 4
source e2e/.env.local
maestro test --shard-split 2 --device "emulator-5554,emulator-5556" flows
```

The top-level NWC flows claim and release their own ReGLab leases in Maestro
hooks. If every warm slot is busy, the hook retries with backoff until a slot is
available or `REGLAB_LEASE_CLAIM_TIMEOUT_SEC` expires.

Stop local ReGLab when you are done:

```bash
e2e/bin/down
```

## Remote Workflow

Trusted contributors can ask for a remote runner token. Put remote credentials
in the ignored `e2e/.remote.env` file:

```bash
cp e2e/remote.env.example e2e/.remote.env
$EDITOR e2e/.remote.env
```

Required values:

```bash
export REGLAB_URL=https://reglab.nicolasusca.com
export REGLAB_RUNNER_TOKEN=replace-with-runner-token
```

Then use the same setup command:

```bash
e2e/bin/up
```

If `e2e/.remote.env` exists, or `REGLAB_URL` and `REGLAB_RUNNER_TOKEN` are set
in the current shell, `e2e/bin/up` writes remote mode into `e2e/.env.local` and
does not start local ReGLab.

For remote Maestro Studio Desktop, create/select a Studio environment and paste
the values printed by:

```bash
e2e/bin/studio-env
```

For remote Maestro CLI:

```bash
source e2e/.env.local
maestro test flows
```

To force local while keeping `e2e/.remote.env` around:

```bash
e2e/bin/up --local
```

## Device URLs

Host-side ReGLab always uses `REGLAB_URL`. Device-side relay URLs sometimes need
a different host address:

- Android emulator: the hook uses `http://10.0.2.2` by default.
- iOS simulator: the hook keeps `https://reglab.localhost`.
- Physical devices: set `REGLAB_DEVICE_URL` to a URL the device can reach.

## Commands

Daily commands:

- `e2e/bin/up`: configure E2E and start local ReGLab unless remote is configured.
- `e2e/bin/down`: stop the local ReGLab stack.
- `maestro test ...`: run one flow, the whole suite, or sharded runs.

Occasional helpers:

- `e2e/bin/studio-env`: print values for Maestro Studio remote environments.
- `e2e/bin/trust-maestro-studio`: trust local HTTPS in Studio's bundled JVM.
- `e2e/bin/maestro-suite`: compatibility wrapper around `maestro test`.

Manual lease debugging:

```bash
eval "$(e2e/bin/claim-lease)"
e2e/bin/nwc-uri
e2e/bin/invoice 21
e2e/bin/release-lease "$REGLAB_LEASE_ID"
```

These helpers source `e2e/.env.local`, so run `e2e/bin/up` first.

## Flow Contract

ReGLab-backed flows use these hooks:

```yaml
onFlowStart:
  - runScript: "../../utils/reglab_claim_lease.js"
onFlowComplete:
  - runScript: "../../utils/reglab_release_lease.js"
```

Lease-scoped values are passed through Maestro `output.reglab`. Flow helper
scripts call the lease ops endpoint with `X-Reglab-Lease-Token`.

The default suite covers ReGLab-backed NWC onboarding and the NWC+BOLT11 happy
path. Blink flows remain checked in for manual provider-specific testing.

## App Contract

The default Maestro suite targets the E2E app id `xyz.lilsus.papp.e2e`.

E2E setup uses `launchApp.arguments`, not public deep links:

```text
e2eProfile       new_user | nwc_user | blink_user | multi_user | slow_internet_user
e2eReset         true clears E2E wallet storage before applying fixtures
e2eFixtureJson   JSON fixture data with concrete values from host scripts
e2ePaymentInput  payment input to inject for a payment test run
e2ePaymentInputSource  deep_link (default) | camera
```

Public deep-link tests should live in a separate release-oriented suite.

`e2eFixtureJson` also supports optional `paymentPreferences` and `network`
objects for deterministic Maestro setup. The checked-in helpers in `flows/utils`
emit the fixture JSON used by the default P0 suite.

## Admin Reference

Use this only when setting up or repairing a ReGLab server for LASR:

```bash
cd /Users/sus/src/personal/github.com/NicolaLS/lasr/app
export REGLAB_ADMIN_TOKEN=<admin-token>
e2e/admin/setup-reglab
```

The admin script applies `e2e/admin/config.yaml`, applies the built-in
`payments-basic` template, and prints pool status. It does not run Maestro or
claim runner leases.
