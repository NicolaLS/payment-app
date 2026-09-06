import SwiftUI

struct NativeWalletRemovalRecoveryView: View {
    let title: String
    let message: String
    let retryTitle: String
    let isWorking: Bool
    let onRetry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text(title)
                .font(.title2.bold())
            Text(message)
            if isWorking {
                ProgressView()
            }
            Button(retryTitle, action: onRetry)
                .buttonStyle(.borderedProminent)
                .disabled(isWorking)
            Spacer()
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Color(uiColor: .systemBackground))
    }
}
