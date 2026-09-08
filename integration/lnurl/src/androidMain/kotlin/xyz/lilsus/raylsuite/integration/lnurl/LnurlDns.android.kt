package xyz.lilsus.raylsuite.integration.lnurl

import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual suspend fun resolveLnurlHost(host: String): List<ByteArray> =
    withContext(Dispatchers.IO) {
        InetAddress.getAllByName(host).map { it.address }
    }
