import Foundation
import Security
import ComposeApp

enum E2ELaunchSeeder {
    private static let profileKey = "e2eProfile"
    private static let resetKey = "e2eReset"
    private static let fixtureKey = "e2eFixtureJson"
    private static let paymentInputKey = "e2ePaymentInput"
    private static let walletListKey = "wallet.list"
    private static let activeWalletKey = "wallet.active"
    private static let onboardingCompletedKey = "onboarding.completed"
    private static let blinkApiKeyPrefix = "blink.apikey."
    private static let blinkDefaultWalletPrefix = "blink.defaultWallet."

    static func apply() {
        let launch = Launch(
            profile: stringValue(profileKey) ?? "new_user",
            reset: boolValue(resetKey),
            fixture: fixture()
        )
        validate(launch)

        let service = "\(Bundle.main.bundleIdentifier ?? "xyz.lilsus.papp.e2e").wallet"
        if launch.reset {
            Keychain.deleteService(service)
            UserDefaults.standard.removeObject(forKey: onboardingCompletedKey)
        }

        var storedWallets: [[String: Any]] = []
        var firstWalletId: String?
        var explicitActiveId: String?

        for wallet in launch.fixture.wallets {
            let stored = storedWallet(wallet, service: service)
            guard let walletId = stored["walletPublicKey"] as? String else {
                fatalError("E2E wallet fixture did not produce a wallet id")
            }
            if firstWalletId == nil {
                firstWalletId = walletId
            }
            if wallet.active == true ||
                launch.fixture.activeWallet == walletId ||
                launch.fixture.activeWallet == (stored["alias"] as? String) {
                explicitActiveId = walletId
            }
            storedWallets.append(stored)
        }

        if !storedWallets.isEmpty {
            Keychain.putString(
                service: service,
                account: walletListKey,
                value: jsonString(storedWallets)
            )
            Keychain.putString(
                service: service,
                account: activeWalletKey,
                value: explicitActiveId ?? firstWalletId ?? ""
            )
            if launch.fixture.completeOnboarding {
                UserDefaults.standard.set(true, forKey: onboardingCompletedKey)
            }
        }

        if let network = launch.fixture.network {
            NSLog(
                "LasrE2E profile \(launch.profile) requested network policy " +
                    "\(network.policy ?? "default") (\(network.latencyMillis ?? 0)ms latency)"
            )
        }
        NSLog("LasrE2E applied profile \(launch.profile) with \(storedWallets.count) wallet(s)")
    }

    static func dispatchPaymentInputIfPresent() {
        guard let input = stringValue(paymentInputKey), !input.isEmpty else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            PaymentDeepLinkEvents.shared.emit(input: input)
        }
    }

    private static func storedWallet(_ wallet: WalletFixture, service: String) -> [String: Any] {
        switch wallet.type.lowercased() {
        case "nwc":
            guard let uri = wallet.uri, let parsed = NwcFixture(uri: uri) else {
                fatalError("NWC wallet fixture requires a valid uri")
            }
            var stored: [String: Any] = [
                "uri": uri,
                "walletPublicKey": parsed.pubKey,
                "type": "NWC",
            ]
            if let alias = wallet.alias?.trimmedNonEmpty {
                stored["alias"] = alias
            }
            if let relay = parsed.relay {
                stored["relayUrl"] = relay
            }
            if let lud16 = parsed.lud16 {
                stored["lud16"] = lud16
            }
            return stored

        case "blink":
            let walletId = wallet.walletPublicKey?.trimmedNonEmpty ??
                "blink-e2e-\((wallet.alias?.stableToken).flatMap { $0.isEmpty ? nil : $0 } ?? "wallet")"
            guard let apiKey = wallet.apiKey?.trimmedNonEmpty else {
                fatalError("Blink wallet fixture requires apiKey")
            }
            Keychain.putString(service: service, account: blinkApiKeyPrefix + walletId, value: apiKey)
            if let defaultWalletId = wallet.defaultWalletId?.trimmedNonEmpty {
                Keychain.putString(
                    service: service,
                    account: blinkDefaultWalletPrefix + walletId,
                    value: defaultWalletId
                )
            }
            return [
                "uri": "",
                "walletPublicKey": walletId,
                "alias": wallet.alias?.trimmedNonEmpty ?? "Blink E2E",
                "type": "BLINK",
            ]

        default:
            fatalError("Unsupported E2E wallet type: \(wallet.type)")
        }
    }

    private static func validate(_ launch: Launch) {
        let profile = launch.profile
        let types = Set(launch.fixture.wallets.map { $0.type.lowercased() })
        switch profile {
        case "new_user":
            return
        case "nwc_user":
            precondition(types.contains("nwc"), "Profile nwc_user requires an nwc wallet fixture")
        case "blink_user":
            precondition(types.contains("blink"), "Profile blink_user requires a blink wallet fixture")
        case "multi_user":
            precondition(launch.fixture.wallets.count > 1, "Profile multi_user requires at least two wallet fixtures")
        case "slow_internet_user":
            precondition(launch.fixture.network != nil, "Profile slow_internet_user requires a network fixture")
        default:
            fatalError("Unknown E2E profile: \(profile)")
        }
    }

    private static func fixture() -> Fixture {
        guard let raw = stringValue(fixtureKey), let data = raw.data(using: .utf8) else {
            return Fixture()
        }
        do {
            return try JSONDecoder().decode(Fixture.self, from: data)
        } catch {
            fatalError("Invalid E2E fixture JSON: \(error)")
        }
    }

    private static func jsonString(_ value: Any) -> String {
        guard JSONSerialization.isValidJSONObject(value),
              let data = try? JSONSerialization.data(withJSONObject: value, options: []),
              let string = String(data: data, encoding: .utf8) else {
            fatalError("Failed to encode E2E wallet fixture")
        }
        return string
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

    private static func boolValue(_ key: String) -> Bool {
        if let value = UserDefaults.standard.object(forKey: key) as? Bool {
            return value
        }
        return stringValue(key)?.lowercased() == "true"
    }
}

private struct Launch {
    let profile: String
    let reset: Bool
    let fixture: Fixture
}

private struct Fixture: Decodable {
    var wallets: [WalletFixture] = []
    var activeWallet: String?
    var completeOnboarding: Bool = true
    var network: NetworkFixture?

    private enum CodingKeys: String, CodingKey {
        case wallets
        case activeWallet
        case completeOnboarding
        case network
    }

    init() {
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        wallets = try container.decodeIfPresent([WalletFixture].self, forKey: .wallets) ?? []
        activeWallet = try container.decodeIfPresent(String.self, forKey: .activeWallet)
        completeOnboarding = try container.decodeIfPresent(Bool.self, forKey: .completeOnboarding) ?? true
        network = try container.decodeIfPresent(NetworkFixture.self, forKey: .network)
    }
}

private struct NetworkFixture: Decodable {
    var policy: String?
    var latencyMillis: Int?
}

private struct WalletFixture: Decodable {
    var type: String
    var alias: String?
    var uri: String?
    var walletPublicKey: String?
    var apiKey: String?
    var defaultWalletId: String?
    var active: Bool?
}

private struct NwcFixture {
    let pubKey: String
    let relay: String?
    let lud16: String?

    init?(uri: String) {
        let marker = "nostr+walletconnect://"
        guard uri.lowercased().hasPrefix(marker) else { return nil }
        let afterScheme = String(uri.dropFirst(marker.count))
        let pieces = afterScheme.split(separator: "?", maxSplits: 1).map(String.init)
        guard let pubKey = pieces.first?.trimmedNonEmpty else { return nil }
        self.pubKey = pubKey

        let query = pieces.count > 1 ? pieces[1] : ""
        let items = query.split(separator: "&").compactMap { pair -> (String, String)? in
            let parts = pair.split(separator: "=", maxSplits: 1).map(String.init)
            guard let key = parts.first?.removingPercentEncoding else { return nil }
            let value = parts.count > 1 ? (parts[1].removingPercentEncoding ?? parts[1]) : ""
            return (key.lowercased(), value)
        }
        self.relay = items.first { $0.0 == "relay" }?.1
        self.lud16 = items.first { $0.0 == "lud16" }?.1
    }
}

private enum Keychain {
    static func putString(service: String, account: String, value: String) {
        delete(service: service, account: account)
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
        ]
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            fatalError("Failed to store E2E keychain value \(account): \(status)")
        }
    }

    static func deleteService(_ service: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
        ]
        SecItemDelete(query as CFDictionary)
    }

    private static func delete(service: String, account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

private extension String {
    var trimmedNonEmpty: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }

    var stableToken: String {
        lowercased()
            .map { $0.isLetter || $0.isNumber ? String($0) : "-" }
            .joined()
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
    }
}
