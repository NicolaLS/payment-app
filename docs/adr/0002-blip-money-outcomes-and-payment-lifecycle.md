# ADR 0002: Blip uses typed money, outcomes, and durable attempts

Status: accepted for the initial extraction

Amounts use ACINQ `MilliSatoshi`; durable IDs, Blink IDs, API keys, payment
hashes, and currency codes are validated types. Fiat input and display use
integer fixed-point arithmetic. Every conversion that affects a payment
records the rate snapshot used and rejects stale, non-positive, malformed, or
overflowing values.

Expected failures are finite sealed outcomes. Loading belongs only to complete
feature `UiState`. Provider messages and `Throwable` never become user-visible
domain state.

One mutex-serialized coordinator owns payment commands. It:

1. selects the current immutable connection generation;
2. blocks a duplicate fixed invoice when an authoritative prior attempt exists;
3. inserts `Created` before provider I/O;
4. records `Submitted` and a lookup correlation;
5. persists `Settled`, `AlreadyPaid`, `Rejected`, `Pending`, or `Unknown`;
6. reconciles non-final attempts with their original `ConnectionId`.

Cancellation is rethrown. If submission may have escaped before cancellation,
the durable result is `Unknown`, never an invented failure. Dynamic LNURL
requests may obtain a new invoice while fixed BOLT11 invoices cannot be sent
twice accidentally.

Rejected legacy choices include session-only truth, mutable-current-connection
lookup for old attempts, swallowed cancellation, and ViewModel-owned settlement
state.
