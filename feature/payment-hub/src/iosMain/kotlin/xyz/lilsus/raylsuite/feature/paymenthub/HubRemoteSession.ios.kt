package xyz.lilsus.raylsuite.feature.paymenthub

import platform.Foundation.NSBundle
import xyz.lilsus.raylsuite.core.hubapi.HubClientMetadata
import xyz.lilsus.raylsuite.core.settings.createSecureSettings

internal fun createHubRemoteSession(): HubRemoteSession {
    val bundle = NSBundle.mainBundle
    return HubRemoteSession(
        HubClientMetadata(
            app = bundle.bundleIdentifier ?: "unknown",
            version =
                bundle.objectForInfoDictionaryKey(
                    "CFBundleShortVersionString"
                )?.toString() ?: "unknown",
            build = bundle.objectForInfoDictionaryKey("CFBundleVersion")?.toString() ?: "unknown",
            platform = "ios"
        ),
        createSecureSettings("${bundle.bundleIdentifier ?: "rayl-suite"}.hub.orders")
    )
}
