import SwiftUI
import Shared

private func makeBlipShellAdapter(_ experience: BlinkIosExperience) -> NativeShellAdapter {
    NativeShellAdapter(
    visibleTabIds: ["scan", "hub", "settings"],
    initialStage: {
        experience.isOnboarded() ? .tabs : .onboarding
    },
    initialSelectedTab: experience.selectedTab,
    selectTab: { experience.selectTab(tab: $0) },
    observeStage: { observer in
        experience.observeOnboarded { value in
            observer.send(value.boolValue ? .tabs : .onboarding)
        }
    },
    observeSelectedTab: { observer in
        experience.observeSelectedTab(onChange: observer.send)
    },
    observeTheme: { observer in
        experience.observeTheme(onChange: observer.send)
    },
    initialRecentBadgeCount: { Int(experience.recentBadgeCount()) },
    observeRecentBadgeCount: { observer in
        experience.observeRecentBadgeCount { value in
            observer.send(Int(value.intValue))
        }
    },
    observeTabTitles: { observer in
        experience.observeTabTitles(onChange: observer.send)
    },
    nativeScanView: {
        AnyView(
            NativePaymentScanView(
                controller: experience.scanController(),
                // Blip hides the Recent tab, so Scan presents this session's payments itself.
                recentController: experience.recentController()
            )
        )
    },
    nativeRecentView: {
        AnyView(NativePaymentRecentView(controller: experience.recentController()))
    },
    nativeHubView: {
        AnyView(NativePaymentHubView(controller: experience.hubController()))
    },
    nativeSettingsView: {
        AnyView(
            NativeSettingsView(
                controller: experience.settingsController(),
                trailingContent: AnyView(
                    BlipWalletSettingsActionsView(
                        controller: experience.blipSettingsController()
                    )
                )
            )
        )
    },
    nativeOnboardingView: {
        AnyView(
            BlipNativeOnboardingView(controller: experience.onboardingController())
        )
    }
)

}

@MainActor
private final class BlinkRemovalRecoveryModel: ObservableObject {
    @Published private(set) var snapshot: BlinkRemovalRecoverySnapshot?
    private var cancel: (() -> Void)?

    init(experience: BlinkIosExperience) {
        cancel = experience.observeRemovalRecovery { [weak self] snapshot in
            self?.snapshot = snapshot
        }
    }

    deinit {
        cancel?()
    }
}

struct BlinkExperienceView: View {
    let experience: BlinkIosExperience
    private let adapter: NativeShellAdapter
    @StateObject private var recovery: BlinkRemovalRecoveryModel

    init(experience: BlinkIosExperience) {
        self.experience = experience
        self.adapter = makeBlipShellAdapter(experience)
        _recovery = StateObject(wrappedValue: BlinkRemovalRecoveryModel(experience: experience))
    }

    var body: some View {
        Group {
            if let snapshot = recovery.snapshot {
                if snapshot.required {
                    NativeWalletRemovalRecoveryView(
                        title: snapshot.title,
                        message: snapshot.message,
                        retryTitle: snapshot.retryTitle,
                        isWorking: snapshot.isWorking,
                        onRetry: experience.retryRemoval
                    )
                } else {
                    NativeAppShell(adapter: adapter)
                }
            } else {
                ProgressView()
            }
        }
    }
}

@MainActor
private final class BlipWalletSettingsModel: ObservableObject {
    @Published private(set) var snapshot: BlipNativeWalletSettingsSnapshot?

    let controller: BlipNativeSettingsController
    private var cancel: (() -> Void)?

    init(controller: BlipNativeSettingsController) {
        self.controller = controller
        cancel = controller.observe { [weak self] snapshot in
            self?.snapshot = snapshot
        }
    }

    deinit {
        cancel?()
    }
}

private struct BlipWalletSettingsActionsView: View {
    @StateObject private var model: BlipWalletSettingsModel
    @State private var showsFundingWallets = false
    @State private var confirmsRemoval = false

    init(controller: BlipNativeSettingsController) {
        _model = StateObject(wrappedValue: BlipWalletSettingsModel(controller: controller))
    }

    var body: some View {
        Group {
            if let snapshot = model.snapshot {
                VStack(alignment: .leading, spacing: 12) {
                    Button {
                        showsFundingWallets = true
                        model.controller.loadFundingWallets()
                    } label: {
                        HStack {
                            Label {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(snapshot.fundingWalletTitle)
                                    Text(snapshot.selectedFundingWalletTitle)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            } icon: {
                                Image(systemName: "wallet.bifold")
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.tertiary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    Divider()

                    Button(role: .destructive) {
                        confirmsRemoval = true
                    } label: {
                        Label(snapshot.removeTitle, systemImage: "trash")
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .alert(snapshot.removeDialogTitle, isPresented: $confirmsRemoval) {
                        Button(snapshot.removeCancelTitle, role: .cancel) {}
                        Button(snapshot.removeConfirmTitle, role: .destructive) {
                            model.controller.removeWallet()
                        }.disabled(!snapshot.canRemove)
                    } message: {
                        Text(snapshot.removeDialogDescription)
                    }
                }
                .sheet(isPresented: $showsFundingWallets) {
                    fundingWalletPicker(snapshot)
                }
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func fundingWalletPicker(
        _ snapshot: BlipNativeWalletSettingsSnapshot
    ) -> some View {
        NavigationStack {
            List {
                if snapshot.isLoadingFundingWallets {
                    HStack(spacing: 12) {
                        ProgressView()
                        Text(snapshot.fundingWalletLoadingTitle)
                    }
                }

                if let message = snapshot.fundingWalletUnavailableMessage {
                    Text(message)
                        .foregroundStyle(.red)
                }

                if let message = snapshot.fundingWalletErrorMessage {
                    Text(message)
                        .foregroundStyle(.red)
                }

                ForEach(snapshot.fundingWalletOptions, id: \.id) { option in
                    Button {
                        model.controller.selectFundingWallet(id: option.id)
                        showsFundingWallets = false
                    } label: {
                        HStack {
                            Text(option.title)
                                .foregroundStyle(.primary)
                            Spacer()
                            if option.selected {
                                Image(systemName: "checkmark")
                                    .fontWeight(.semibold)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .navigationTitle(snapshot.fundingWalletPickerTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(snapshot.fundingWalletCloseTitle) {
                        showsFundingWallets = false
                    }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
