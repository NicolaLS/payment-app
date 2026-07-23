import SwiftUI

@main
struct BlipApp: App {
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 16) {
                Image(systemName: "bolt.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(.orange)
                Text("Blip")
                    .font(.largeTitle.bold())
                Text("The iOS application shell is configured for the BlipShared framework.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
            }
            .padding(32)
        }
    }
}
