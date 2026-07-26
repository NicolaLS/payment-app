import Foundation
import Shared

enum E2EPaymentInput {
    private static let inputKey = "e2ePaymentInput"
    private static let sourceKey = "e2ePaymentInputSource"

    static func dispatchIfPresent() {
        guard let input = stringValue(inputKey), !input.isEmpty else { return }
        let source: PaymentInputSource
        switch stringValue(sourceKey)?.lowercased() {
        case nil, "", "deep_link":
            source = .deeplink
        case "camera":
            source = .camera
        default:
            fatalError("Unknown E2E payment input source")
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            PaymentDeepLinkEvents.shared.emit(input: input, source: source)
        }
    }

    private static func stringValue(_ key: String) -> String? {
        if let value = UserDefaults.standard.object(forKey: key) {
            return "\(value)"
        }
        let args = ProcessInfo.processInfo.arguments
        if let index = args.firstIndex(of: key), args.indices.contains(index + 1) {
            return args[index + 1]
        }
        if let prefixed = args.first(where: { $0.hasPrefix("\(key)=") }) {
            return String(prefixed.dropFirst(key.count + 1))
        }
        return nil
    }
}
