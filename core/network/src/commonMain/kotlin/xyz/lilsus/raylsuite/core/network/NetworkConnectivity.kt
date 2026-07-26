package xyz.lilsus.raylsuite.core.network

/**
 * Provides a quick synchronous connectivity check before network operations.
 */
interface NetworkConnectivity {
    fun isNetworkAvailable(): Boolean
}

expect fun createNetworkConnectivity(): NetworkConnectivity
