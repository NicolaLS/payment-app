import SwiftUI
import Shared

private let flintShellAdapter = NativeShellAdapter(
    visibleTabIds: ["scan", "recent", "hub", "settings"],
    initialStage: {
        switch flintIosApp.stage() {
        case "tabs": return .tabs
        case "onboarding": return .onboarding
        default: return .loading
        }
    },
    initialSelectedTab: flintIosApp.selectedTab,
    selectTab: { flintIosApp.selectTab(tab: $0) },
    observeStage: { observer in
        flintIosApp.observeStage { value in
            switch value {
            case "tabs": observer.send(.tabs)
            case "onboarding": observer.send(.onboarding)
            default: observer.send(.loading)
            }
        }
    },
    observeSelectedTab: { observer in
        flintIosApp.observeSelectedTab(onChange: observer.send)
    },
    observeTheme: { observer in
        flintIosApp.observeTheme(onChange: observer.send)
    },
    initialRecentBadgeCount: { Int(flintIosApp.recentBadgeCount()) },
    observeRecentBadgeCount: { observer in
        flintIosApp.observeRecentBadgeCount { value in
            observer.send(Int(value.intValue))
        }
    },
    observeTabTitles: { observer in
        flintIosApp.observeTabTitles(onChange: observer.send)
    },
    nativeScanView: {
        AnyView(NativePaymentScanView(controller: flintIosApp.scanController()))
    },
    nativeRecentView: {
        AnyView(NativePaymentRecentView(controller: flintIosApp.recentController()))
    },
    nativeHubView: {
        AnyView(NativePaymentHubView(controller: flintIosApp.hubController()))
    },
    nativeSettingsView: {
        AnyView(
            NativeSettingsView(
                controller: flintIosApp.settingsController(),
                leadingContent: AnyView(
                    FlintWalletSettingsLink(
                        controller: flintIosApp.walletSettingsController()
                    )
                )
            )
        )
    },
    nativeOnboardingView: {
        AnyView(FlintNativeOnboardingView(controller: flintIosApp.onboardingController()))
    }
)

struct ContentView: View {
    var body: some View {
        NativeAppShell(adapter: flintShellAdapter)
    }
}

@MainActor
private final class FlintNativeWalletSettingsModel: ObservableObject {
    @Published private(set) var snapshot: FlintNativeWalletSettingsSnapshot?

    let controller: FlintNativeWalletSettingsController
    private var cancel: (() -> Void)?

    init(controller: FlintNativeWalletSettingsController) {
        self.controller = controller
        cancel = controller.observe { [weak self] snapshot in
            self?.snapshot = snapshot
        }
    }

    deinit {
        cancel?()
    }
}

private struct FlintWalletSettingsLink: View {
    @StateObject private var model: FlintNativeWalletSettingsModel

    init(controller: FlintNativeWalletSettingsController) {
        _model = StateObject(wrappedValue: FlintNativeWalletSettingsModel(controller: controller))
    }

    var body: some View {
        Group {
            if let snapshot = model.snapshot {
                NativeWalletSettingsLink(
                    title: snapshot.settingsTitle,
                    subtitle: snapshot.settingsSubtitle
                ) {
                    FlintWalletManagementView(snapshot: snapshot, model: model)
                }
            } else {
                ProgressView()
            }
        }
    }
}

private struct FlintWalletManagementView: View {
    let snapshot: FlintNativeWalletSettingsSnapshot
    @ObservedObject var model: FlintNativeWalletSettingsModel

    var body: some View {
        NativeWalletManagementView(
            text: snapshot.managementText,
            wallet: snapshot.managedWallet,
            isWorking: snapshot.isWorking,
            errorMessage: snapshot.errorMessage,
            onAddWallet: {},
            walletDetails: { EmptyView() },
            showsWalletDetails: false,
            onRemoveWallet: model.controller.removeWallet
        )
    }
}

private extension FlintNativeWalletSettingsSnapshot {
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
