package xyz.lilsus.raylsuite.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

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

private var applicationContext: Context? = null

fun initializeNetworkConnectivity(context: Context) {
    applicationContext = context.applicationContext
}

actual fun createNetworkConnectivity(): NetworkConnectivity {
    val context = applicationContext
        ?: error("Call initializeNetworkConnectivity() before creating network connectivity")
    return AndroidNetworkConnectivity(context)
}
