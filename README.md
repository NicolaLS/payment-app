# RAYL

RAYL is a suite of focused, open-source Lightning payment apps. Each app keeps
the same fast checkout idea while compiling exactly one wallet provider or
protocol integration.

The first two products are:

- **Blip** — connects to Blink with an API key.
- **Lasr** — connects through Nostr Wallet Connect.

Flint, Quark, Nutrino, and Femto currently reserve the future Spark, Ark,
Cashu, and Fedimint product shapes.

## Repository

RAYL is one Kotlin Multiplatform monorepo:

```text
apps/<app>/shared
apps/<app>/androidApp
apps/<app>/iosApp
foundation/ui
```

The Gradle root is this directory. App implementations own provider behavior,
credentials, storage, and release configuration. Shared foundation code is
limited to behavior proven identical by multiple real apps.

The frozen combined application remains available at the signed `papp-final`
tag and on the protected `papp-legacy` branch.

## Development status

The suite extraction is in progress. See
[IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md) for the active task plan and
tracked progress.

## License

MIT
