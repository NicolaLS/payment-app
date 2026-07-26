import SwiftUI
import Shared

@main
struct E2EApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onAppear {
                    E2EPaymentInput.dispatchIfPresent()
                }
                .onOpenURL { url in
                    DeepLinkEvents.shared.emit(uri: url.absoluteString)
                }
        }
    }
}
