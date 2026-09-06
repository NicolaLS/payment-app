import SwiftUI
import Shared

private func makeLasrShellAdapter(_ experience: NwcIosExperience) -> NativeShellAdapter {
    NativeShellAdapter(
    visibleTabIds: ["scan", "recent", "hub", "settings"],
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
        AnyView(NativePaymentScanView(controller: experience.scanController()))
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
                leadingContent: AnyView(
                    LasrWalletSettingsLink(
                        controller: experience.walletSettingsController(),
                        onboardingController: experience.onboardingController()
                    )
                )
            )
        )
    },
    nativeOnboardingView: {
        AnyView(
            LasrNativeOnboardingView(controller: experience.onboardingController())
        )
    }
)

}

@MainActor
private final class NwcRemovalModel: ObservableObject {
    @Published private(set) var snapshot: NwcRemovalSnapshot?
    private var cancel: (() -> Void)?

    init(experience: NwcIosExperience) {
        cancel = experience.observeRemoval { [weak self] snapshot in
            self?.snapshot = snapshot
        }
    }

    deinit { cancel?() }
}

struct NwcExperienceView: View {
    let experience: NwcIosExperience
    private let adapter: NativeShellAdapter
    @StateObject private var removal: NwcRemovalModel

    init(experience: NwcIosExperience) {
        self.experience = experience
        self.adapter = makeLasrShellAdapter(experience)
        _removal = StateObject(wrappedValue: NwcRemovalModel(experience: experience))
    }

    var body: some View {
        if let snapshot = removal.snapshot {
            if snapshot.pending {
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

@MainActor
private final class LasrNativeWalletSettingsModel: ObservableObject {
    @Published private(set) var snapshot: LasrNativeWalletSettingsSnapshot?

    let controller: LasrNativeWalletSettingsController
    private var cancel: (() -> Void)?

    init(controller: LasrNativeWalletSettingsController) {
        self.controller = controller
        cancel = controller.observe { [weak self] snapshot in
            self?.snapshot = snapshot
        }
    }

    deinit {
        cancel?()
    }
}

private struct LasrWalletSettingsLink: View {
    @StateObject private var model: LasrNativeWalletSettingsModel
    let onboardingController: LasrNativeOnboardingController

    init(
        controller: LasrNativeWalletSettingsController,
        onboardingController: LasrNativeOnboardingController
    ) {
        _model = StateObject(wrappedValue: LasrNativeWalletSettingsModel(controller: controller))
        self.onboardingController = onboardingController
    }

    var body: some View {
        Group {
            if let snapshot = model.snapshot {
                NativeWalletSettingsLink(
                    title: snapshot.settingsTitle,
                    subtitle: snapshot.settingsSubtitle
                ) {
                    LasrWalletManagementView(snapshot: snapshot, model: model)
                }
            } else {
                ProgressView()
            }
        }
        .sheet(isPresented: walletFlowPresented) {
            LasrNativeOnboardingView(controller: onboardingController)
        }
    }

    private var walletFlowPresented: Binding<Bool> {
        Binding(
            get: { model.snapshot?.walletFlowPresented == true },
            set: { presented in
                if !presented {
                    model.controller.finishWalletConnection()
                }
            }
        )
    }
}

private struct LasrWalletManagementView: View {
    let snapshot: LasrNativeWalletSettingsSnapshot
    @ObservedObject var model: LasrNativeWalletSettingsModel

    var body: some View {
        NativeWalletManagementView(
            text: snapshot.managementText,
            wallet: snapshot.managedWallet,
            isWorking: snapshot.isWorking,
            errorMessage: snapshot.removalErrorMessage,
            onAddWallet: model.controller.requestWalletConnection,
            walletDetails: {
                LasrWalletDetailsView(snapshot: snapshot)
            },
            showsWalletDetails: snapshot.walletId != nil,
            onRemoveWallet: model.controller.removeWallet
        )
    }
}

private struct LasrWalletDetailsView: View {
    let snapshot: LasrNativeWalletSettingsSnapshot

    var body: some View {
        List {
            if let title = snapshot.walletTitle {
                Section {
                    Text(title)
                        .font(.headline)
                        .textSelection(.enabled)
                }
            }

            Section {
                detail(snapshot.walletTypeLabel, snapshot.walletType)
                if let connectionId = snapshot.walletId {
                    detail(snapshot.connectionIdLabel, connectionId)
                }
            }
        }
        .navigationTitle(snapshot.detailsTitle)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func detail(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(value)
                .textSelection(.enabled)
        }
    }
}

private extension LasrNativeWalletSettingsSnapshot {
    var managementText: NativeWalletManagementTextValue {
        NativeWalletManagementTextValue(
            screenTitle: screenTitle,
            emptyDescription: emptyDescription,
            addTitle: addTitle,
            removeTitle: removeTitle,
            removeConfirmationTitle: removeConfirmationTitle,
            removeConfirmationBody: removeConfirmationBody,
            cancelTitle: cancelTitle
        )
    }

    var managedWallet: NativeManagedWalletValue? {
        guard let walletId, let walletTitle else { return nil }
        return NativeManagedWalletValue(
            id: walletId,
            title: walletTitle,
            details: walletDetails
        )
    }
}
