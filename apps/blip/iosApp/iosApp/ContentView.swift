import SwiftUI
import Shared

private let blipShellAdapter = NativeShellAdapter(
    visibleTabIds: ["scan", "hub", "settings"],
    initialStage: {
        BlipIosApp.shared.isOnboarded() ? .tabs : .onboarding
    },
    initialSelectedTab: BlipIosApp.shared.selectedTab,
    selectTab: { BlipIosApp.shared.selectTab(tab: $0) },
    observeStage: { observer in
        BlipIosApp.shared.observeOnboarded { value in
            observer.send(value.boolValue ? .tabs : .onboarding)
        }
    },
    observeSelectedTab: { observer in
        BlipIosApp.shared.observeSelectedTab(onChange: observer.send)
    },
    observeTheme: { observer in
        BlipIosApp.shared.observeTheme(onChange: observer.send)
    },
    initialRecentBadgeCount: { Int(BlipIosApp.shared.recentBadgeCount()) },
    observeRecentBadgeCount: { observer in
        BlipIosApp.shared.observeRecentBadgeCount { value in
            observer.send(Int(value.intValue))
        }
    },
    observeTabTitles: { observer in
        BlipIosApp.shared.observeTabTitles(onChange: observer.send)
    },
    nativeScanView: {
        AnyView(
            NativePaymentScanView(
                controller: BlipIosApp.shared.scanController(),
                // Blip hides the Recent tab, so Scan presents this session's payments itself.
                recentController: BlipIosApp.shared.recentController()
            )
        )
    },
    nativeRecentView: {
        AnyView(NativePaymentRecentView(controller: BlipIosApp.shared.recentController()))
    },
    nativeHubView: {
        AnyView(
            NativePaymentHubView(
                controller: BlipIosApp.shared.hubController(),
                additionalLibraryContent: AnyView(
                    BlipContactsImportLink(
                        controller: BlipIosApp.shared.contactsController()
                    )
                )
            )
        )
    },
    nativeSettingsView: {
        AnyView(
            NativeSettingsView(
                controller: BlipIosApp.shared.settingsController(),
                trailingContent: AnyView(
                    BlipWalletSettingsActionsView(
                        controller: BlipIosApp.shared.blipSettingsController()
                    )
                )
            )
        )
    },
    nativeOnboardingView: {
        AnyView(
            BlipNativeOnboardingView(
                controller: BlipIosApp.shared.onboardingController(),
                contactsController: BlipIosApp.shared.contactsController()
            )
        )
    }
)

struct ContentView: View {
    var body: some View {
        NativeAppShell(adapter: blipShellAdapter)
    }
}

@MainActor
private final class BlipContactsImportModel: ObservableObject {
    @Published private(set) var snapshot: BlipNativeContactsSnapshot?
    @Published var searchQuery = "" {
        didSet {
            guard searchQuery != oldValue else { return }
            controller.updateSearch(query: searchQuery)
        }
    }

    let controller: BlipNativeContactsController
    private var cancel: (() -> Void)?

    init(controller: BlipNativeContactsController) {
        self.controller = controller
        cancel = controller.observe { [weak self] snapshot in
            self?.snapshot = snapshot
        }
    }

    deinit {
        cancel?()
    }
}

private struct BlipContactsImportLink: View {
    @StateObject private var model: BlipContactsImportModel

    init(controller: BlipNativeContactsController) {
        _model = StateObject(wrappedValue: BlipContactsImportModel(controller: controller))
    }

    var body: some View {
        NavigationLink {
            BlipContactsImportView(controller: model.controller)
        } label: {
            if let snapshot = model.snapshot {
                Label(snapshot.importTitle, systemImage: "person.crop.circle.badge.plus")
            } else {
                ProgressView()
            }
        }
    }
}

struct BlipContactsImportView: View {
    @StateObject private var model: BlipContactsImportModel
    private let onSkip: (() -> Void)?

    init(
        controller: BlipNativeContactsController,
        onSkip: (() -> Void)? = nil
    ) {
        _model = StateObject(wrappedValue: BlipContactsImportModel(controller: controller))
        self.onSkip = onSkip
    }

    var body: some View {
        Group {
            if let snapshot = model.snapshot {
                contacts(snapshot)
            } else {
                ProgressView()
            }
        }
        .task {
            model.controller.updateSearch(query: model.searchQuery)
            model.controller.load()
        }
    }

    private func contacts(_ snapshot: BlipNativeContactsSnapshot) -> some View {
        List {
            Section {
                Text(snapshot.hint)
                    .foregroundStyle(.secondary)
            }

            if snapshot.isLoading {
                Section {
                    HStack(spacing: 12) {
                        ProgressView()
                        Text(snapshot.loadingTitle)
                    }
                }
            } else if !snapshot.hasAnyItems && snapshot.errorMessage == nil {
                Section {
                    ContentUnavailableView(
                        snapshot.emptyTitle,
                        systemImage: "person.crop.circle.badge.questionmark"
                    )
                }
            } else if snapshot.hasAnyItems && snapshot.items.isEmpty {
                Section {
                    ContentUnavailableView(
                        snapshot.noMatchesTitle,
                        systemImage: "magnifyingglass"
                    )
                }
            } else {
                Section {
                    Button {
                        model.controller.toggleAll()
                    } label: {
                        HStack {
                            Image(
                                systemName: snapshot.allSelected
                                    ? "checkmark.circle.fill"
                                    : "circle"
                            )
                            Text(snapshot.selectAllTitle)
                            Spacer()
                            Text(snapshot.selectedSummary)
                                .foregroundStyle(.secondary)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(!snapshot.canSelectAll)
                }

                Section {
                    ForEach(snapshot.items, id: \.id) { contact in
                        Button {
                            model.controller.toggleContact(id: contact.id)
                        } label: {
                            HStack(spacing: 12) {
                                Image(
                                    systemName: contact.selected
                                        ? "checkmark.circle.fill"
                                        : "circle"
                                )
                                .foregroundStyle(
                                    contact.selected ? Color.accentColor : Color.secondary
                                )
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(contact.title)
                                        .foregroundStyle(.primary)
                                    Text(contact.address)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    Text(contact.status)
                                        .font(.caption2)
                                        .foregroundStyle(
                                            contact.enabled ? Color.secondary : Color.accentColor
                                        )
                                }
                                Spacer()
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .disabled(!contact.enabled)
                    }
                }
            }

            if let errorMessage = snapshot.errorMessage {
                Section {
                    Text(errorMessage).foregroundStyle(.red)
                }
            }
        }
        .navigationTitle(snapshot.title)
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $model.searchQuery, prompt: snapshot.searchTitle)
        .safeAreaInset(edge: .bottom) {
            VStack(alignment: .leading, spacing: 8) {
                if let successMessage = snapshot.successMessage {
                    Text(successMessage)
                        .font(.caption)
                        .foregroundStyle(.tint)
                }
                Text(snapshot.selectedSummary)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Button {
                    model.controller.importSelected()
                } label: {
                    HStack {
                        if snapshot.isImporting {
                            ProgressView()
                        }
                        Text(snapshot.importTitle)
                            .frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.borderedProminent)
                .disabled(!snapshot.canImport)

                if let onSkip {
                    Button(snapshot.skipTitle, action: onSkip)
                        .buttonStyle(.bordered)
                }
            }
            .padding()
            .background(.bar)
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
    @State private var confirmsRemoval = false

    init(controller: BlipNativeSettingsController) {
        _model = StateObject(wrappedValue: BlipWalletSettingsModel(controller: controller))
    }

    var body: some View {
        Group {
            if let snapshot = model.snapshot {
                VStack(alignment: .leading, spacing: 12) {
                    Button(action: model.controller.refreshConnection) {
                        HStack {
                            Label(snapshot.refreshTitle, systemImage: "arrow.clockwise")
                            Spacer()
                            if snapshot.isRefreshing {
                                ProgressView()
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(snapshot.isRefreshing)

                    if let statusMessage = snapshot.statusMessage {
                        Text(statusMessage)
                            .font(.caption)
                            .foregroundStyle(
                                snapshot.statusIsError ? Color.red : Color.accentColor
                            )
                    }

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
                        }
                    } message: {
                        Text(snapshot.removeDialogDescription)
                    }
                }
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity)
            }
        }
    }
}
