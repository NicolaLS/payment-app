import SwiftUI
import ComposeApp

@main
struct E2EApp: App {
    init() {
        E2ELaunchSeeder.apply()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    E2ELaunchSeeder.dispatchPaymentInputIfPresent()
                }
                .onOpenURL { url in
                    DeepLinkEvents.shared.emit(uri: url.absoluteString)
                }
        }
    }
}
