# Flint Privacy Policy

Last updated: September 4, 2026

Flint is an open-source client for a self-custodial Spark wallet. The Flint
maintainer does not operate an account service, payment processor, analytics
service, or application backend for Flint.

## Data stored on your device

Flint stores the wallet recovery phrase and related credential material in
app-scoped encrypted storage. It also stores wallet data, app preferences,
contacts, and payment shortcuts locally. Flint does not include its local data
in Android cloud backup or device transfer.

Removing the wallet deletes its stored credential. Compatible app updates and
operating-system offload or archive features retain local data. Deleting Flint
resets its local data; after reinstallation it rejects any wallet credential
that may have survived outside the app container.

## Network requests

Flint uses the Breez Spark SDK and its services to synchronize the Spark wallet
and perform actions you request. Their own terms and privacy policies apply.

When required for a feature you use, Flint may also contact:

- CoinGecko to retrieve a Bitcoin exchange rate;
- the server named by a Lightning address or LNURL you open; and
- operating-system services needed to open external links.

Those services receive the network information normally associated with a
request, such as your IP address. Flint does not send the wallet recovery phrase
to CoinGecko or LNURL servers.

## Camera

Camera access is optional and is used to scan QR codes. Frames are processed on
the device and Flint does not store or upload camera images.

## Optional performance diagnostics

On Android, Flint offers an optional **Performance diagnostics** setting. It is
off by default. If you enable it, Flint uses Google Firebase Performance
Monitoring to send technical performance measurements such as app startup,
screen rendering, and fixed-duration camera startup and shutdown timings.

Firebase receives technical metadata needed for those measurements, including
a Firebase installation identifier, a random session identifier, app version,
device model and operating system, device resources and CPU usage, network type,
locale, and country derived from the IP address. Google's Firebase privacy and
security information, including retention, is available at
<https://firebase.google.com/support/privacy/>.

Flint does not send payment amounts, destinations, addresses, invoices, wallet
credentials or recovery phrases, contacts, transaction history, QR contents,
camera images, URLs, or free-form trace attributes through performance
diagnostics. It does not use Firebase Analytics or advertising features. You
can disable future collection at any time in Settings. Flint does not add this
third-party telemetry on iOS.

## Questions

Questions and privacy reports can be opened at
<https://github.com/NicolaLS/lasr/issues>.
