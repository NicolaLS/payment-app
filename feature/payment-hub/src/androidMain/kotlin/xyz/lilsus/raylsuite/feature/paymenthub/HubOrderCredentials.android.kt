package xyz.lilsus.raylsuite.feature.paymenthub

import java.security.SecureRandom
import java.util.UUID

internal actual fun newHubOrderCredentials(): HubOrderCredentials {
    val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
    return HubOrderCredentials(
        UUID.randomUUID().toString(),
        bytes.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    )
}
