package xyz.lilsus.raylsuite.core.camera

/** Renderer-neutral camera authorization values; each platform owns how they are obtained. */
enum class CameraAuthorizationState {
    NOT_DETERMINED,
    AUTHORIZED,
    DENIED,
    RESTRICTED,
    UNAVAILABLE
}

val CameraAuthorizationState.isAuthorized: Boolean
    get() = this == CameraAuthorizationState.AUTHORIZED
