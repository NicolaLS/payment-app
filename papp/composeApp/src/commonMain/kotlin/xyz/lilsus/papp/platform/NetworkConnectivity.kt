package xyz.lilsus.papp.platform

/**
 * Provides network connectivity information.
 *
 * Used to quickly check if network is available before attempting
 * operations, providing instant feedback instead of waiting for
 * connection timeouts.
 */
interface NetworkConnectivity {
    /**
     * Returns true if the device has network connectivity.
     *
     * This is a quick synchronous check that should be called before
     * network operations to provide immediate feedback when offline.
     */
    fun isNetworkAvailable(): Boolean
}

/**
 * Creates a platform-specific [NetworkConnectivity] instance.
 */
expect fun createNetworkConnectivity(): NetworkConnectivity
