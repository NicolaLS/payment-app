import SwiftUI

struct NativeManagedWalletValue {
    let id: String
    let title: String
    let details: [String]
}

struct NativeWalletManagementTextValue {
    let screenTitle: String
    let emptyDescription: String
    let addTitle: String
    let removeTitle: String
    let removeConfirmationTitle: String
    let removeConfirmationBody: String
    let cancelTitle: String
}

struct NativeWalletSettingsLink<Destination: View>: View {
    let title: String
    let subtitle: String
    @ViewBuilder let destination: () -> Destination

    var body: some View {
        NavigationLink(destination: destination) {
            Label {
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            } icon: {
                Image(systemName: "wallet.pass")
                    .foregroundStyle(.tint)
            }
        }
    }
}

struct NativeWalletManagementView<WalletDetails: View>: View {
    let text: NativeWalletManagementTextValue
    let wallet: NativeManagedWalletValue?
    let isWorking: Bool
    let errorMessage: String?
    let onAddWallet: () -> Void
    @ViewBuilder let walletDetails: () -> WalletDetails
    let showsWalletDetails: Bool
    let onRemoveWallet: () -> Void

    @State private var confirmsRemoval = false

    var body: some View {
        Group {
            if let wallet {
                walletList(wallet)
            } else {
                emptyWallet
            }
        }
        .navigationTitle(text.screenTitle)
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog(
            text.removeConfirmationTitle,
            isPresented: $confirmsRemoval,
            titleVisibility: .visible
        ) {
            Button(text.removeTitle, role: .destructive, action: onRemoveWallet)
            Button(text.cancelTitle, role: .cancel) {}
        } message: {
            Text(text.removeConfirmationBody)
        }
    }

    private func walletList(_ wallet: NativeManagedWalletValue) -> some View {
        List {
            Section {
                if showsWalletDetails {
                    NavigationLink(destination: walletDetails) {
                        walletLabel(wallet)
                    }
                } else {
                    walletLabel(wallet)
                }
            }

            if let errorMessage {
                Section {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                        .accessibilityAddTraits(.isStaticText)
                }
            }

            Section {
                Button(role: .destructive) {
                    confirmsRemoval = true
                } label: {
                    HStack {
                        if isWorking {
                            ProgressView()
                        }
                        Text(text.removeTitle)
                    }
                    .frame(maxWidth: .infinity)
                }
                .disabled(isWorking)
            }
        }
    }

    private var emptyWallet: some View {
        ContentUnavailableView {
            Label(text.screenTitle, systemImage: "wallet.pass")
        } description: {
            Text(text.emptyDescription)
        } actions: {
            Button(action: onAddWallet) {
                Text(text.addTitle)
            }
            .buttonStyle(.borderedProminent)
        }
    }

    private func walletLabel(_ wallet: NativeManagedWalletValue) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(wallet.title)
                .font(.headline)
                .lineLimit(2)
            ForEach(Array(wallet.details.enumerated()), id: \.offset) { _, detail in
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
            }
        }
        .padding(.vertical, 4)
    }
}
