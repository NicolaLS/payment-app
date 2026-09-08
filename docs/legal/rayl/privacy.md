# Rayl privacy notice

Rayl is a prerelease payment app. It connects to one wallet at a time using
Blink in version 1.0. Blink receives the requests needed to connect and make
payments. LNURL
recipients receive requests when you use Lightning addresses or LNURL links.
Exchange-rate requests use the suite’s existing rate service. Rayl does not
operate a payment-service backend or synchronize data between suite apps.

Connection credentials are stored using platform secure storage. Preferences,
Hub shortcuts, and unresolved payment records are stored locally. The camera
processes QR codes for setup and payments. Removing a connection erases its
credentials and local payment records while keeping contacts, preferences, and
Hub shortcuts. Removing a connection does not revoke authority at the wallet
service; manage that connection with your wallet if you need to revoke it.

Rayl does not include Firebase Performance Monitoring or offer remote
performance diagnostics in this version. Local system performance traces are
available for development and troubleshooting. Wallet credentials, invoices,
payment preimages, and QR contents must not be included in diagnostic markers.
Platform permissions can be managed in device settings.

These prerelease declarations must be reviewed against the exact distribution
candidate before publication.
