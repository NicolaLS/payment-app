import SwiftUI

struct NativeOnboardingPageValue: Identifiable {
    let id: Int
    let title: String
    let subtitle: String
    let body: String
}

struct NativeOnboardingWelcomeView: View {
    let title: String
    let subtitle: String
    let description: String
    let actionTitle: String
    let action: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: "qrcode.viewfinder")
                .font(.system(size: 72, weight: .light))
                .foregroundStyle(.tint)
                .accessibilityHidden(true)
            Text(title)
                .font(.largeTitle.bold())
                .multilineTextAlignment(.center)
            Text(subtitle)
                .font(.headline)
                .foregroundStyle(.secondary)
            Text(description)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Spacer()
            Button(actionTitle, action: action)
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
        }
        .padding(24)
    }
}

struct NativeOnboardingFeaturesView: View {
    let pages: [NativeOnboardingPageValue]
    let currentPage: Int32
    let stepIndex: Int32
    let stepCount: Int32
    let actionTitle: String
    let onPageChanged: (Int32) -> Void
    let action: () -> Void

    var body: some View {
        NativeOnboardingProgressLayout(stepIndex: stepIndex, stepCount: stepCount) {
            TabView(
                selection: Binding(
                    get: { currentPage },
                    set: onPageChanged
                )
            ) {
                ForEach(pages) { page in
                    VStack(spacing: 18) {
                        Spacer()
                        Image(systemName: symbol(for: page.id))
                            .font(.system(size: 58, weight: .light))
                            .foregroundStyle(.tint)
                            .accessibilityHidden(true)
                        Text(page.title)
                            .font(.title2.bold())
                            .multilineTextAlignment(.center)
                        Text(page.subtitle)
                            .font(.headline)
                            .multilineTextAlignment(.center)
                        Text(page.body)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .tag(Int32(page.id))
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))

            Button(actionTitle, action: action)
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
        }
    }

    private func symbol(for index: Int) -> String {
        switch index {
        case 0: return "viewfinder"
        case 1: return "bolt.fill"
        default: return "wallet.bifold"
        }
    }
}

struct NativeOnboardingAutoPayView: View {
    let title: String
    let description: String
    let alwaysTitle: String
    let thresholdTitle: String
    let thresholdLabel: String
    let hint: String
    let actionTitle: String
    let confirmationMode: String
    let thresholdIndex: Int32
    let thresholdStepCount: Int32
    let stepIndex: Int32
    let stepCount: Int32
    let onConfirmationModeChanged: (String) -> Void
    let onThresholdIndexChanged: (Int32) -> Void
    let action: () -> Void

    init(
        title: String,
        body: String,
        alwaysTitle: String,
        thresholdTitle: String,
        thresholdLabel: String,
        hint: String,
        actionTitle: String,
        confirmationMode: String,
        thresholdIndex: Int32,
        thresholdStepCount: Int32,
        stepIndex: Int32,
        stepCount: Int32,
        onConfirmationModeChanged: @escaping (String) -> Void,
        onThresholdIndexChanged: @escaping (Int32) -> Void,
        action: @escaping () -> Void
    ) {
        self.title = title
        description = body
        self.alwaysTitle = alwaysTitle
        self.thresholdTitle = thresholdTitle
        self.thresholdLabel = thresholdLabel
        self.hint = hint
        self.actionTitle = actionTitle
        self.confirmationMode = confirmationMode
        self.thresholdIndex = thresholdIndex
        self.thresholdStepCount = thresholdStepCount
        self.stepIndex = stepIndex
        self.stepCount = stepCount
        self.onConfirmationModeChanged = onConfirmationModeChanged
        self.onThresholdIndexChanged = onThresholdIndexChanged
        self.action = action
    }

    var body: some View {
        NativeOnboardingProgressLayout(stepIndex: stepIndex, stepCount: stepCount) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text(title)
                        .font(.title2.bold())
                    Text(description)
                        .foregroundStyle(.secondary)

                    VStack(alignment: .leading, spacing: 16) {
                        Picker(
                            title,
                            selection: Binding(
                                get: { confirmationMode },
                                set: onConfirmationModeChanged
                            )
                        ) {
                            Text(alwaysTitle).tag("always")
                            Text(thresholdTitle).tag("above")
                        }
                        .pickerStyle(.inline)
                        .labelsHidden()

                        if confirmationMode == "above" {
                            Divider()
                            Text(thresholdLabel)
                                .font(.headline)
                                .foregroundStyle(.tint)
                            Slider(
                                value: Binding(
                                    get: { Double(thresholdIndex) },
                                    set: { onThresholdIndexChanged(Int32($0.rounded())) }
                                ),
                                in: 0...Double(max(0, thresholdStepCount - 1)),
                                step: 1
                            )
                        }
                    }
                    .padding()
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))

                    Text(hint)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button(actionTitle, action: action)
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
        }
    }
}

struct NativeOnboardingAgreementView: View {
    let title: String
    let description: String
    let checkboxTitle: String
    let actionTitle: String
    let hasAgreed: Bool
    let stepIndex: Int32
    let stepCount: Int32
    let onAgreementChanged: (Bool) -> Void
    let action: () -> Void

    init(
        title: String,
        body: String,
        checkboxTitle: String,
        actionTitle: String,
        hasAgreed: Bool,
        stepIndex: Int32,
        stepCount: Int32,
        onAgreementChanged: @escaping (Bool) -> Void,
        action: @escaping () -> Void
    ) {
        self.title = title
        description = body
        self.checkboxTitle = checkboxTitle
        self.actionTitle = actionTitle
        self.hasAgreed = hasAgreed
        self.stepIndex = stepIndex
        self.stepCount = stepCount
        self.onAgreementChanged = onAgreementChanged
        self.action = action
    }

    var body: some View {
        NativeOnboardingProgressLayout(stepIndex: stepIndex, stepCount: stepCount) {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text(title)
                        .font(.title2.bold())
                    Text(description)
                        .foregroundStyle(.secondary)
                    Toggle(
                        checkboxTitle,
                        isOn: Binding(
                            get: { hasAgreed },
                            set: onAgreementChanged
                        )
                    )
                    .padding()
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button(actionTitle, action: action)
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .frame(maxWidth: .infinity)
                .disabled(!hasAgreed)
        }
    }
}

struct NativeOnboardingProgressLayout<Content: View>: View {
    let stepIndex: Int32
    let stepCount: Int32
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 16) {
            ProgressView(
                value: Double(stepIndex + 1),
                total: Double(stepCount)
            )
            content()
        }
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
    }
}
