import SwiftUI
import Shared

#if FLINT_DEBUG && FLINT_PRODUCTION
#error("Flint environment conditions are contradictory")
#elseif FLINT_DEBUG
private let flintAppHost = IOSWalletPlatformKt.createIOSAppHost(
    environment: FlintEnvironment.debug,
    breezApiKey: nil
)
#elseif FLINT_PRODUCTION
private let flintAppHost = IOSWalletPlatformKt.createIOSAppHost(
    environment: FlintEnvironment.production,
    breezApiKey: (Bundle.main.object(forInfoDictionaryKey: "FlintBreezAPIKey") as? String)?
        .trimmingCharacters(in: .whitespacesAndNewlines)
)
#else
#error("A Flint environment compilation condition is required")
#endif

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView(appHost: flintAppHost)
            .onOpenURL { url in
                flintAppHost.offerPaymentLink(rawUrl: url.absoluteString)
            }
        }
    }
}
