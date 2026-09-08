@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package xyz.lilsus.raylsuite.feature.paymenthub

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSUUID
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.Security.kSecRandomDefault

internal actual fun newHubOrderCredentials(): HubOrderCredentials {
    val bytes = ByteArray(32)
    bytes.usePinned {
        check(
            SecRandomCopyBytes(kSecRandomDefault, bytes.size.convert(), it.addressOf(0)) ==
                errSecSuccess
        )
    }
    return HubOrderCredentials(
        NSUUID().UUIDString.lowercase(),
        bytes.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    )
}
