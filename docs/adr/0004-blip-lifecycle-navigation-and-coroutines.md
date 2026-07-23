# ADR 0004: Blip has explicit lifecycle, navigation, and coroutine ownership

Status: accepted for the initial extraction

Onboarding, pay, and settings expose separate complete immutable `UiState`
streams and explicit actions. Composables receive state and callbacks; they do
not locate services or access SQL/Apollo directly.

Navigation uses typed in-memory routes. Routes contain only object destinations
or durable IDs. API keys never enter a route or saveable UI state. The root
coordinator queues at most one standard payment input until the risk agreement
and onboarding are complete. NWC links are classified as unsupported and
cannot provision anything.

Feature stores receive the composition-owned coroutine scope. The payment
coordinator provides process-durable truth. Launch and foreground reconciliation
are idempotent at the presentation boundary and serialized at the application
boundary. Camera ownership follows composition/lifecycle and binds or unbinds
one CameraX analysis pipeline.

Rejected legacy choices include global event channels, secret-bearing routes,
Koin calls from navigation, hidden retained instances, and independent UI
booleans that can disagree about scanner activity.
