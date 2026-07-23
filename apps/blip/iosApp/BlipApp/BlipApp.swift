import BlipShared
import SwiftUI
import UIKit

private struct ComposeView: UIViewControllerRepresentable {
    let host: IosBlipHost

    func makeUIViewController(context: Context) -> UIViewController {
        host.viewController()
    }

    func updateUIViewController(_ viewController: UIViewController, context: Context) {}
}

@main
struct BlipApp: App {
    private let host = IosBlipHost()

    var body: some Scene {
        WindowGroup {
            ComposeView(host: host)
                .ignoresSafeArea()
                .onOpenURL { url in
                    host.openPaymentUri(uri: url.absoluteString)
                }
        }
    }
}
