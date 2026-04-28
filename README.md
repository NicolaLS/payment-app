# Lasr

Lasr is a fast Lightning checkout app for Android and iOS. It connects to the wallet you
already use, scans payment requests, and pays through Nostr Wallet Connect.

The app is built for in-person payments where the flow should stay simple: scan a QR code,
review only when needed, and keep moving.

## What It Supports

- BOLT11 invoices, with or without an amount
- LNURL-pay
- Lightning Addresses (LUD16)
- Nostr Wallet Connect wallet links
- Android and iOS from one Kotlin Multiplatform codebase

## Wallet Connections

Lasr does not custody funds. It connects to an external Lightning wallet through NWC, so
wallet permissions, balances, budgets, and payment limits stay with the wallet provider.

Wallet connections can be added with `nostr+walletconnect://...` links.

## Privacy and Safety

Lasr is intentionally narrow: it focuses on scanning and paying. The app should be used
with wallets or NWC connections that have spending limits appropriate for quick checkout
payments.

- [Privacy Policy](privacy-policy.md)
- [Terms and Conditions](terms-and-conditions.md)

## Development

The app workspace lives in [`papp/`](papp/). Build, test, and IDE commands should be run
from that directory.

- [Build and run the app](papp/README.md)
- [Release builds](docs/release.md)
- [E2E and Maestro testing](docs/e2e.md)

## License

MIT
