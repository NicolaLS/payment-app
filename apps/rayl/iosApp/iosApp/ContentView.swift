import SwiftUI
import Shared

@MainActor
private final class RaylModel: ObservableObject {
    @Published private(set) var snapshot: RaylSnapshot?
    private var cancel: (() -> Void)?
    init() { cancel = RaylIosApp.shared.observe { [weak self] in self?.snapshot = $0 } }
    deinit { cancel?() }
}

struct ContentView: View {
    @StateObject private var model = RaylModel()

    var body: some View {
        Group {
            if let snapshot = model.snapshot {
                VStack(spacing: 0) {
                    if snapshot.canCancelSetup {
                        HStack {
                            Button(snapshot.text["choose_another"] ?? "") { RaylIosApp.shared.cancelSetup() }
                            Spacer()
                        }.padding()
                    }
                    if let experience = snapshot.blinkExperience {
                        BlinkExperienceView(experience: experience)
                            .id(ObjectIdentifier(experience))
                    } else if let experience = snapshot.nwcExperience {
                        NwcExperienceView(experience: experience)
                            .id(ObjectIdentifier(experience))
                    } else {
                        selection(snapshot)
                    }
                }
                .id(snapshot.wallet)
                .preferredColorScheme(colorScheme(snapshot.colorScheme))
                .alert("Rayl", isPresented: Binding(get: { snapshot.message != nil }, set: { if !$0 { RaylIosApp.shared.dismissMessage() } })) {
                    Button(snapshot.text["close"] ?? "") { RaylIosApp.shared.dismissMessage() }
                } message: { Text(snapshot.message ?? "") }
            } else { ProgressView() }
        }
    }

    private func colorScheme(_ value: String) -> ColorScheme? {
        switch value {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }

    private func selection(_ snapshot: RaylSnapshot) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text(snapshot.text[snapshot.welcomeCompleted ? "choose_wallet" : "welcome_title"] ?? "")
                    .font(.largeTitle.bold())
                Text(snapshot.text[snapshot.welcomeCompleted ? "choose_body" : "welcome_body"] ?? "")
                    .foregroundStyle(.secondary)
                if snapshot.welcomeCompleted {
                    choice("blink", snapshot)
                    choice("nwc", snapshot)
                } else {
                    Button(snapshot.text["get_started"] ?? "") { RaylIosApp.shared.completeWelcome() }
                        .buttonStyle(.borderedProminent)
                }
            }.padding(24).frame(maxWidth: 600, alignment: .leading)
        }.frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func choice(_ wallet: String, _ snapshot: RaylSnapshot) -> some View {
        Button { RaylIosApp.shared.choose(wallet: wallet) } label: {
            VStack(alignment: .leading, spacing: 8) {
                Text(snapshot.text[wallet + "_title"] ?? "").font(.title2.bold())
                Text(snapshot.text[wallet + "_body"] ?? "").font(.body)
            }.frame(maxWidth: .infinity, alignment: .leading).padding(16)
        }.buttonStyle(.bordered)
    }
}
