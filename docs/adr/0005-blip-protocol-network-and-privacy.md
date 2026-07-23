# ADR 0005: Blip applies strict protocol and network trust

Status: accepted for the initial extraction

All scan, paste, manual, shortcut, donation, and app-link inputs enter one
resolver. ACINQ validates BOLT11 signatures, networks, expiry, amounts, hashes,
and BOLT12 offers; ACINQ Bech32 decodes LNURL. `uri-kmp` parses BIP21 query
parameters once.

LNURL and Lightning-address requests require public HTTPS endpoints. Blip
rejects local/private literal addresses, credentials in URLs, unsupported
payer-data requirements, excessive redirects, non-JSON responses, non-success
status, and bodies larger than 256 KiB. Callback invoices must have the exact
requested amount and ACINQ `descriptionHash` equal to SHA-256 of the exact raw
metadata. Comment lengths are bounded.

Android registers only `lightning`, `bitcoin`, and `lnurl`; it does not register
`nostr+walletconnect`. Cleartext traffic and backup are disabled. Apollo sends
the API key only in the required header to Blink's fixed GraphQL endpoint and
has no body/header logger.

Data leaves the device only for:

- Blink account discovery, contact import, payment submission, and lookup;
- LNURL/Lightning-address resolution selected by the user;
- a public CoinGecko exchange-rate query for a selected fiat code.

Hostile redirect, DNS-rebinding, callback, timeout, and provider-outcome
scenarios are recorded for QA rather than reproduced through mock servers or
integration tests in this extraction.

Rejected legacy choices include raw provider-message matching, permissive
redirects, custom cryptography, NWC link registration, and credential logging.
