import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    let bootstrapConfig: PaymentAppBootstrapConfig
    let runtime: AppRuntime
    let paymentLinks: PaymentPaymentLinkInbox

    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            bootstrapConfig: bootstrapConfig,
            runtime: runtime,
            paymentLinks: paymentLinks
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

struct ContentView: View {
    let bootstrapConfig: PaymentAppBootstrapConfig
    let runtime: AppRuntime
    let paymentLinks: PaymentPaymentLinkInbox

    var body: some View {
        ComposeView(
            bootstrapConfig: bootstrapConfig,
            runtime: runtime,
            paymentLinks: paymentLinks
        )
            .ignoresSafeArea()
            .privacySensitive()
    }
}
