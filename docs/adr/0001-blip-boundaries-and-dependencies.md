# ADR 0001: Blip owns one provider and keeps a pure domain

Status: accepted for the initial extraction

Blip is a Blink-only application. Its shared module may depend directly on the
ACINQ Bitcoin and Lightning libraries, Apollo generated from Blip-owned
operations, SQLDelight, Ktor, Compose, and narrow platform libraries needed by
the product. It does not contain a provider registry, `WalletType` switch, NWC
SDK, NWC credential model, or runtime provider dispatch.

Pure values, policies, states, and provider-neutral ports live in `domain`.
Serialized workflows live in `application`; provider and persistence adapters
live in `data`; UI contracts live in `presentation`; native implementations
live in platform source sets. The domain cannot import Compose, Apollo, Ktor,
Settings, platform code, presentation code, or data implementations.

ACINQ types are the protocol source of truth. Blip does not wrap or reimplement
BOLT11, BOLT12 validation, Bech32, hashing, Bitcoin identities, or secp256k1.
App-local types exist only for durable identity, bounded outcomes, money
policy, and UI state.

Rejected legacy choices include provider routing, service location, pass-through
use cases, a single giant ViewModel, and custom protocol primitives.

Enforcement: `:apps:blip:shared:verifyBlipArchitecture` checks the domain import
boundary and rejects NWC dependencies/imports.
