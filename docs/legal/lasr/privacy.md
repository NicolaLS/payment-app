# Lasr Privacy Policy

Last updated: September 4, 2026

Lasr is an open-source client for connecting to a wallet through Nostr Wallet
Connect (NWC). The Lasr maintainer does not operate an account service, payment
processor, analytics service, Nostr relay, or application backend for Lasr.

## Data stored on your device

Lasr stores the NWC connection URI and its credential material in app-scoped
encrypted storage. It also stores app preferences, contacts, and payment
shortcuts locally. Lasr does not include its local data in Android cloud backup
or device transfer.

Removing the wallet deletes its stored credential. Compatible app updates and
operating-system offload or archive features retain local data. Deleting Lasr
resets its local data; after reinstallation it rejects any wallet credential
that may have survived outside the app container.

## Network requests

Lasr sends encrypted NWC requests to the relay or relays named by the
connection URI you provide. Those relays and the connected wallet service
process the request metadata and wallet actions needed to fulfill your request.
Their own terms and privacy policies apply.

When required for a feature you use, Lasr may also contact:

- CoinGecko to retrieve a Bitcoin exchange rate;
- the server named by a Lightning address or LNURL you open; and
- operating-system services needed to open external links.

Those services receive the network information normally associated with a
request, such as your IP address. Lasr does not send NWC credentials to
CoinGecko or LNURL servers.

## Camera

Camera access is optional and is used to scan QR codes. Frames are processed
on the device and Lasr does not store or upload camera images.

## Optional performance diagnostics

On Android, Lasr offers an optional **Performance diagnostics** setting. It is
off by default. If you enable it, Lasr uses Google Firebase Performance
Monitoring to send technical performance measurements such as app startup,
screen rendering, and fixed-duration camera startup and shutdown timings.

Firebase receives technical metadata needed for those measurements, including
a Firebase installation identifier, a random session identifier, app version,
device model and operating system, device resources and CPU usage, network type,
locale, and country derived from the IP address. Google's Firebase privacy and
security information, including retention, is available at
<https://firebase.google.com/support/privacy/>.

Lasr does not send payment amounts, destinations, addresses, invoices, wallet
credentials, contacts, transaction history, relay traffic, NWC URIs, QR
contents, camera images, URLs, or free-form trace attributes through
performance diagnostics. It does not use Firebase Analytics or advertising
features. You can disable future collection at any time in Settings. Lasr does
not add this third-party telemetry on iOS.

## Questions

Questions and privacy reports can be opened at
<https://github.com/NicolaLS/lasr/issues>.
