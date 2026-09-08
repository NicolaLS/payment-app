@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package xyz.lilsus.raylsuite.integration.lnurl

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.AF_UNSPEC
import platform.posix.SOCK_STREAM
import platform.posix.addrinfo
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.memset
import platform.posix.sockaddr_in
import platform.posix.sockaddr_in6

internal actual suspend fun resolveLnurlHost(host: String): List<ByteArray> =
    withContext(Dispatchers.IO) {
        memScoped {
            val hints = alloc<addrinfo>()
            memset(hints.ptr, 0, sizeOf<addrinfo>().convert())
            hints.ai_family = AF_UNSPEC
            hints.ai_socktype = SOCK_STREAM
            val result = alloc<CPointerVar<addrinfo>>()
            result.value = null
            if (getaddrinfo(host, null, hints.ptr, result.ptr) != 0) {
                throw IOException("LNURL host lookup failed")
            }
            try {
                buildList {
                    var current = result.value
                    while (current != null) {
                        val entry = current.pointed
                        when (entry.ai_family) {
                            AF_INET -> entry.ai_addr?.let { address ->
                                val ipv4 = address.reinterpret<sockaddr_in>().pointed.sin_addr
                                add(ipv4.ptr.reinterpret<ByteVar>().readBytes(4))
                            }

                            AF_INET6 -> entry.ai_addr?.let { address ->
                                val ipv6 = address.reinterpret<sockaddr_in6>().pointed.sin6_addr
                                add(ipv6.ptr.reinterpret<ByteVar>().readBytes(16))
                            }
                        }
                        current = entry.ai_next
                    }
                }
            } finally {
                result.value?.let(::freeaddrinfo)
            }
        }
    }
