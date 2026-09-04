# Blip Privacy Policy

Last updated: September 4, 2026

Blip is an open-source client for connecting to a Blink wallet. The Blip
maintainer does not operate an account service, payment processor, analytics
service, or application backend for Blip.

## Data stored on your device

Blip stores the Blink API key you provide in app-scoped encrypted storage. It
also stores app preferences, contacts, and payment shortcuts locally. Blip does
not include its local data in Android cloud backup or device transfer.

Removing the wallet deletes its stored credential. Compatible app updates and
operating-system offload or archive features retain local data. Deleting Blip
resets its local data; after reinstallation it rejects any wallet credential
that may have survived outside the app container.

## Network requests

Blip sends the API key and wallet requests to the Blink GraphQL API in order to
display wallet information and perform actions you request. Blink processes
that data under its own terms and privacy policy.

When required for a feature you use, Blip may also contact:

- CoinGecko to retrieve a Bitcoin exchange rate;
- the server named by a Lightning address or LNURL you open; and
- operating-system services needed to open external links.

Those services receive the network information normally associated with a
request, such as your IP address. Blip does not send wallet credentials to
CoinGecko or LNURL servers.

## Camera

Camera access is optional and is used to scan QR codes. Frames are processed
on the device and Blip does not store or upload camera images.

## Optional performance diagnostics

On Android, Blip offers an optional **Performance diagnostics** setting. It is
off by default. If you enable it, Blip uses Google Firebase Performance
Monitoring to send technical performance measurements such as app startup,
screen rendering, and fixed-duration camera startup and shutdown timings.

Firebase receives technical metadata needed for those measurements, including
a Firebase installation identifier, a random session identifier, app version,
device model and operating system, device resources and CPU usage, network type,
locale, and country derived from the IP address. Google's Firebase privacy and
security information, including retention, is available at
<https://firebase.google.com/support/privacy/>.

Blip does not send payment amounts, destinations, addresses, invoices, wallet
credentials, contacts, transaction history, QR contents, camera images, URLs,
or free-form trace attributes through performance diagnostics. It does not use
Firebase Analytics or advertising features. You can disable future collection
at any time in Settings. Blip does not add this third-party telemetry on iOS.

## Questions

Questions and privacy reports can be opened at
<https://github.com/NicolaLS/lasr/issues>.
