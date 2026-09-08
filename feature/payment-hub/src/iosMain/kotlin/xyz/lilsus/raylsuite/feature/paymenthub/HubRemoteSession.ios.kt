package xyz.lilsus.raylsuite.feature.paymenthub

import platform.Foundation.NSBundle
import xyz.lilsus.raylsuite.core.hubapi.HubClientMetadata

internal fun createHubRemoteSession(): HubRemoteSession? {
    if (HubBackendConfiguration.baseUrl.isBlank()) return null
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
        )
    )
}
