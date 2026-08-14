import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    let appHost: FlintAppHost

    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController(host: appHost)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    let appHost: FlintAppHost

    var body: some View {
        ComposeView(appHost: appHost)
            .ignoresSafeArea()
            .privacySensitive()
    }
}
