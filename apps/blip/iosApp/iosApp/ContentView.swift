import SwiftUI
import Shared

@MainActor
private final class BlipExperienceModel: ObservableObject {
    @Published private(set) var experience = BlipApplication.shared.createExperience()
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
        experience = BlipApplication.shared.createExperience()
        observeRemoval()
    }

    deinit { cancel?() }
}

struct ContentView: View {
    @StateObject private var model = BlipExperienceModel()

    var body: some View {
        BlinkExperienceView(experience: model.experience)
            .id(ObjectIdentifier(model.experience))
    }
}
