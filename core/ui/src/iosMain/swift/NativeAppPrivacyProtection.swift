import SwiftUI
import UIKit

/// App-owned opt-in. A separate scene window also covers presented sheets and alerts.
struct NativeAppPrivacyProtection: UIViewRepresentable {
    let appName: String
    let captureMessage: () -> String

    func makeUIView(context: Context) -> PrivacyAnchorView {
        PrivacyAnchorView(appName: appName, captureMessage: captureMessage)
    }

    func updateUIView(_ view: PrivacyAnchorView, context: Context) {
        view.captureMessage = captureMessage
        view.updateProtection()
    }

    static func dismantleUIView(_ view: PrivacyAnchorView, coordinator: ()) {
        view.detach()
    }
}

final class PrivacyAnchorView: UIView {
    var captureMessage: () -> String
    private let appName: String
    private weak var scene: UIWindowScene?
    private var privacyWindow: UIWindow?
    private var isActive = false
    private let messageLabel = UILabel()

    init(appName: String, captureMessage: @escaping () -> String) {
        self.appName = appName
        self.captureMessage = captureMessage
        super.init(frame: .zero)
        isUserInteractionEnabled = false
        isAccessibilityElement = false
        registerForTraitChanges([UITraitSceneCaptureState.self]) {
            (view: PrivacyAnchorView, _: UITraitCollection) in
            view.updateProtection()
        }
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }

    override func layoutSubviews() {
        super.layoutSubviews()
        if let scene { privacyWindow?.frame = scene.coordinateSpace.bounds }
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        guard window?.windowScene !== scene else { return }
        detach()
        guard let scene = window?.windowScene else { return }
        self.scene = scene
        isActive = scene.activationState == .foregroundActive
        let center = NotificationCenter.default
        center.addObserver(self, selector: #selector(obscure),
                           name: UIScene.willDeactivateNotification, object: scene)
        center.addObserver(self, selector: #selector(obscure),
                           name: UIScene.didEnterBackgroundNotification, object: scene)
        center.addObserver(self, selector: #selector(activate),
                           name: UIScene.didActivateNotification, object: scene)
        center.addObserver(self, selector: #selector(disconnect),
                           name: UIScene.didDisconnectNotification, object: scene)
        updateProtection()
    }

    @objc private func obscure() {
        isActive = false
        // Synchronous UIKit updates precede the system's background snapshot.
        updateProtection()
    }

    @objc private func activate() {
        isActive = true
        updateProtection()
    }

    @objc private func disconnect() { detach() }

    func detach() {
        NotificationCenter.default.removeObserver(self)
        privacyWindow?.isHidden = true
        privacyWindow = nil
        scene = nil
    }

    func updateProtection() {
        guard let scene else { return }
        let isCaptured = traitCollection.sceneCaptureState == .active
        guard !isActive || isCaptured else {
            privacyWindow?.isHidden = true
            return
        }
        if privacyWindow == nil { privacyWindow = makePrivacyWindow(scene: scene) }
        privacyWindow?.frame = scene.coordinateSpace.bounds
        messageLabel.text = isCaptured ? captureMessage() : nil
        messageLabel.isHidden = !isCaptured
        privacyWindow?.isHidden = false
        privacyWindow?.layoutIfNeeded()
    }

    private func makePrivacyWindow(scene: UIWindowScene) -> UIWindow {
        let controller = UIViewController()
        controller.view.backgroundColor = .systemBackground
        controller.view.accessibilityViewIsModal = true

        let symbol = UIImageView(image: UIImage(systemName: "lock.fill"))
        symbol.preferredSymbolConfiguration = .init(pointSize: 32, weight: .medium)
        symbol.tintColor = .secondaryLabel
        symbol.contentMode = .scaleAspectFit
        symbol.isAccessibilityElement = false
        let title = UILabel()
        title.text = appName
        title.font = .preferredFont(forTextStyle: .title2)
        title.adjustsFontForContentSizeCategory = true
        title.textAlignment = .center
        title.numberOfLines = 0
        messageLabel.font = .preferredFont(forTextStyle: .body)
        messageLabel.adjustsFontForContentSizeCategory = true
        messageLabel.textColor = .secondaryLabel
        messageLabel.numberOfLines = 0
        messageLabel.textAlignment = .center

        let content = UIStackView(arrangedSubviews: [symbol, title, messageLabel])
        content.axis = .vertical
        content.spacing = 16
        content.translatesAutoresizingMaskIntoConstraints = false
        controller.view.addSubview(content)
        NSLayoutConstraint.activate([
            content.centerXAnchor.constraint(equalTo: controller.view.centerXAnchor),
            content.centerYAnchor.constraint(equalTo: controller.view.centerYAnchor),
            content.widthAnchor.constraint(lessThanOrEqualToConstant: 420),
            content.leadingAnchor.constraint(greaterThanOrEqualTo: controller.view.leadingAnchor, constant: 32),
            content.trailingAnchor.constraint(lessThanOrEqualTo: controller.view.trailingAnchor, constant: -32),
        ])

        let cover = UIWindow(windowScene: scene)
        cover.frame = scene.coordinateSpace.bounds
        cover.windowLevel = .init(rawValue: UIWindow.Level.alert.rawValue + 1)
        cover.rootViewController = controller
        // Do not become key: preserve the app's first responder and navigation state.
        return cover
    }
}
