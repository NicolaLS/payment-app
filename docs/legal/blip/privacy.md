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

## Performance diagnostics

Blip does not include Firebase Performance Monitoring or offer remote
performance diagnostics in this version. Local system performance traces are
available for development and troubleshooting.

## Questions

Questions and privacy reports can be opened at
<https://github.com/NicolaLS/lasr/issues>.
