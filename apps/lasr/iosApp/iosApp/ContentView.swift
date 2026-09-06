import SwiftUI
import Shared

@MainActor
private final class LasrExperienceModel: ObservableObject {
    @Published private(set) var experience = LasrApplication.shared.createExperience()
    private var cancel: (() -> Void)?

    init() { observeRemoval() }

    private func observeRemoval() {
        cancel = experience.observeRemoved { [weak self] removed in
            guard removed.boolValue else { return }
            Task { @MainActor [weak self] in self?.replaceExperience() }
        }
    }

    private func replaceExperience() {
        cancel?()
        experience.clear()
        experience = LasrApplication.shared.createExperience()
        observeRemoval()
    }

    deinit { cancel?() }
}

struct ContentView: View {
    @StateObject private var model = LasrExperienceModel()

    var body: some View {
        NwcExperienceView(experience: model.experience)
            .id(ObjectIdentifier(model.experience))
    }
}
