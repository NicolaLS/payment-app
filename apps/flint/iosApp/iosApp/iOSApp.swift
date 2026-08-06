import SwiftUI
import Shared

#if FLINT_DEBUG && FLINT_PRODUCTION
#error("Flint environment conditions are contradictory")
#elseif FLINT_DEBUG
private let flintBootstrapConfig = PaymentAppBootstrapConfig(
    environment: PaymentAppEnvironment.debug,
    breezApiKey: nil
)
#elseif FLINT_PRODUCTION
private let flintBootstrapConfig = PaymentAppBootstrapConfig(
    environment: PaymentAppEnvironment.production,
    breezApiKey: (Bundle.main.object(forInfoDictionaryKey: "FlintBreezAPIKey") as? String)?
        .trimmingCharacters(in: .whitespacesAndNewlines)
)
#else
#error("A Flint environment compilation condition is required")
#endif

private let flintRuntime = IOSWalletPlatformKt.createIOSAppRuntime(
    bootstrapConfig: flintBootstrapConfig
)
private let flintPaymentLinks = AppKt.createAppPaymentLinkInbox()

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView(
                bootstrapConfig: flintBootstrapConfig,
                runtime: flintRuntime,
                paymentLinks: flintPaymentLinks
            )
            .onOpenURL { url in
                flintPaymentLinks.offer(rawUrl: url.absoluteString)
            }
        }
    }
}
