package xyz.lilsus.raylsuite.core.network

import kotlin.concurrent.AtomicReference
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
internal class IosNetworkConnectivity : NetworkConnectivity {
    private val isConnected = AtomicReference(true)
    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_set_update_handler(monitor) { path ->
            isConnected.value = nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_start(monitor)
    }

    override fun isNetworkAvailable(): Boolean = isConnected.value

    fun cancel() {
        nw_path_monitor_cancel(monitor)
    }
}

private var instance: IosNetworkConnectivity? = null

actual fun createNetworkConnectivity(): NetworkConnectivity =
    instance ?: IosNetworkConnectivity().also {
        instance = it
    }
