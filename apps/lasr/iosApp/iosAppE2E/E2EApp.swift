import SwiftUI
import Shared

@main
struct E2EApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    LasrDeepLinks.shared.emit(uri: url.absoluteString)
                }
        }
    }
}
