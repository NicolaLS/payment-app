package xyz.lilsus.papp.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Android implementation using ConnectivityManager.
 *
 * Checks both WiFi and cellular connectivity to determine
 * if the device can reach the network.
 */
internal class AndroidNetworkConnectivity(private val context: Context) : NetworkConnectivity {

    override fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

private var appContext: Context? = null

/**
 * Initialize the network connectivity checker with the application context.
 * Call this from Application.onCreate().
 */
fun initializeNetworkConnectivity(context: Context) {
    appContext = context.applicationContext
}

actual fun createNetworkConnectivity(): NetworkConnectivity {
    val context = appContext
        ?: throw IllegalStateException(
            "NetworkConnectivity not initialized. Call initializeNetworkConnectivity() first."
        )
    return AndroidNetworkConnectivity(context)
}
