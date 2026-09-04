import Shared
import SwiftUI

@MainActor
private final class NativePaymentHubModel: ObservableObject {
    @Published private(set) var snapshot: NativePaymentHubSnapshot?

    let controller: NativePaymentHubController
    private var cancel: (() -> Void)?

    init(controller: NativePaymentHubController) {
        self.controller = controller
        cancel = controller.observe { [weak self] snapshot in
            self?.snapshot = snapshot
        }
    }

    deinit {
        cancel?()
    }
}

private enum NativePaymentHubRoute: Hashable {
    case newTarget
    case groupEditor
}

private enum HubGrid {
    static let columns = 2
    static let rowHeight = CGFloat(NativeHubGrid.shared.rowHeight)
    static let gap = CGFloat(NativeHubGrid.shared.gap)
    static let gutter = CGFloat(NativeHubGrid.shared.gutter)
}

/// Native iOS Hub. Kotlin owns persistence, arrangement, and payment intents; SwiftUI owns UI.
struct NativePaymentHubView: View {
    @StateObject private var model: NativePaymentHubModel
    @State private var pendingRemoval: NativeHubTile?

    private let importButton: AnyView?

    init(controller: NativePaymentHubController, importButton: AnyView? = nil) {
        _model = StateObject(wrappedValue: NativePaymentHubModel(controller: controller))
        self.importButton = importButton
    }

    var body: some View {
        NavigationStack(path: navigationPath) {
            Group {
                if let snapshot = model.snapshot {
                    canvas(snapshot)
                        .navigationDestination(for: NativePaymentHubRoute.self) { route in
                            destination(route, snapshot: snapshot)
                        }
                } else {
                    ProgressView()
                }
            }
        }
    }

    private var navigationPath: Binding<[NativePaymentHubRoute]> {
        Binding(
            get: {
                switch model.snapshot?.destination {
                case "newTarget": return [.newTarget]
                case "groupEditor": return [.groupEditor]
                default: return []
                }
            },
            set: { routes in
                guard routes.isEmpty else { return }
                if model.snapshot?.destination == "newTarget" {
                    model.controller.stepBack()
                } else if model.snapshot?.destination == "groupEditor" {
                    model.controller.closeNewTarget()
                }
            }
        )
    }

    @ViewBuilder
    private func destination(
        _ route: NativePaymentHubRoute,
        snapshot: NativePaymentHubSnapshot
    ) -> some View {
        switch route {
        case .newTarget:
            if let newTarget = model.snapshot?.newTarget {
                NewTargetView(
                    state: newTarget,
                    copy: snapshot.text,
                    controller: model.controller,
                    importButton: importButton
                )
            } else {
                ProgressView()
            }
        case .groupEditor:
            if let editor = model.snapshot?.groupEditor {
                GroupEditorView(
                    state: editor,
                    copy: snapshot.text,
                    controller: model.controller
                )
            } else {
                ProgressView()
            }
        }
    }

    // MARK: - Canvas

    @ViewBuilder
    private func canvas(_ snapshot: NativePaymentHubSnapshot) -> some View {
        let copy = snapshot.text
        ScrollView {
            canvasGrid(snapshot.canvas, copy: copy)
                .padding(.horizontal, HubGrid.gutter)
                .padding(.top, snapshot.canvas.hasItems ? 60 : 12)
                .padding(.bottom, 12)
        }
        .overlay(alignment: .bottom) {
            if let message = snapshot.canvas.message {
                Text(message)
                    .font(.footnote)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 10))
                    .padding(16)
            }
        }
        .overlay(alignment: .topTrailing) {
            if snapshot.canvas.hasItems {
                Button {
                    snapshot.canvas.editing
                        ? model.controller.stopEditing()
                        : model.controller.startEditing()
                } label: {
                    Image(systemName: snapshot.canvas.editing ? "checkmark" : "pencil")
                        .frame(width: 28, height: 28)
                }
                .buttonStyle(.bordered)
                .buttonBorderShape(.circle)
                .accessibilityLabel(snapshot.canvas.editing ? copy.done : copy.edit)
                .padding(.top, 8)
                .padding(.trailing, HubGrid.gutter)
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .alert(pendingRemoval?.removeTitle ?? "", isPresented: removalPresented) {
            Button(copy.removeConfirm, role: .destructive) {
                if let tile = pendingRemoval {
                    model.controller.removeTile(id: tile.id)
                }
                pendingRemoval = nil
            }
            Button(copy.removeCancel, role: .cancel) { pendingRemoval = nil }
        } message: {
            Text(pendingRemoval?.removeBody ?? "")
        }
    }

    private var removalPresented: Binding<Bool> {
        Binding(
            get: { pendingRemoval != nil },
            set: { presented in
                if !presented { pendingRemoval = nil }
            }
        )
    }

    private func canvasGrid(_ canvas: NativeHubCanvas, copy: NativePaymentHubCopy) -> some View {
        let rows = Int(canvas.gridRows)
        let height =
            HubGrid.rowHeight * CGFloat(rows) + HubGrid.gap * CGFloat(max(rows - 1, 0))
        return GeometryReader { proxy in
            let columnWidth =
                (proxy.size.width - HubGrid.gap * CGFloat(HubGrid.columns - 1))
                    / CGFloat(HubGrid.columns)
            ZStack(alignment: .topLeading) {
                ForEach(canvas.tiles, id: \.id) { tile in
                    CanvasTileView(
                        tile: tile,
                        editing: canvas.editing,
                        columnWidth: columnWidth,
                        copy: copy,
                        controller: model.controller,
                        onRequestRemoval: { pendingRemoval = tile }
                    )
                    .frame(
                        width: span(columnWidth, Int(tile.columns)),
                        height: span(HubGrid.rowHeight, Int(tile.rows))
                    )
                    .offset(
                        x: (columnWidth + HubGrid.gap) * CGFloat(tile.column),
                        y: (HubGrid.rowHeight + HubGrid.gap) * CGFloat(tile.row)
                    )
                    .animation(.snappy(duration: 0.24), value: tile.column)
                    .animation(.snappy(duration: 0.24), value: tile.row)
                }
                if canvas.showsAddTarget {
                    addTargetTile
                        .frame(width: columnWidth, height: HubGrid.rowHeight)
                        .offset(
                            x: (columnWidth + HubGrid.gap) * CGFloat(canvas.addTargetColumn),
                            y: (HubGrid.rowHeight + HubGrid.gap) * CGFloat(canvas.addTargetRow)
                        )
                }
            }
        }
        .frame(height: height)
    }

    private var addTargetTile: some View {
        Button {
            model.controller.openNewTarget()
        } label: {
            VStack(spacing: 8) {
                Image(systemName: "plus")
                Text(model.snapshot?.text.addTarget ?? "")
                    .font(.caption)
            }
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .strokeBorder(
                        Color.secondary.opacity(0.4),
                        style: StrokeStyle(lineWidth: 1, dash: [4, 3])
                    )
            )
        }
        .buttonStyle(.plain)
    }
}

private func span(_ unit: CGFloat, _ count: Int) -> CGFloat {
    unit * CGFloat(count) + HubGrid.gap * CGFloat(max(count - 1, 0))
}

// MARK: - Canvas tile

private struct CanvasTileView: View {
    let tile: NativeHubTile
    let editing: Bool
    let columnWidth: CGFloat
    let copy: NativePaymentHubCopy
    let controller: NativePaymentHubController
    let onRequestRemoval: () -> Void

    @State private var dragging = false
    @State private var drop: NativeHubDropTarget?
    @State private var dragOffset = CGSize.zero
    @State private var jiggling = false
    @State private var menuPresented = false
    @State private var menuFeedback = 0

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 14).fill(Color(uiColor: .secondarySystemBackground))
            )
            .overlay(highlight)
            .contentShape(RoundedRectangle(cornerRadius: 14))
            .offset(dragOffset)
            .scaleEffect(dragging ? 1.025 : 1)
            .opacity(dragging ? 0.96 : 1)
            .shadow(color: .black.opacity(dragging ? 0.2 : 0), radius: 12, y: 6)
            .rotationEffect(.degrees(jiggling ? 0.45 : -0.45))
            .animation(
                editing
                    ? .easeInOut(duration: 0.55).repeatForever(autoreverses: true)
                    : .default,
                value: jiggling
            )
            .onTapGesture(perform: tapped)
            .onLongPressGesture(minimumDuration: 0.42) {
                guard !editing else { return }
                menuFeedback += 1
                menuPresented = true
            }
            .gesture(editing ? dragGesture : nil)
            .popover(
                isPresented: $menuPresented,
                attachmentAnchor: .rect(.bounds),
                arrowEdge: .top
            ) {
                TileActionMenu(
                    tile: tile,
                    copy: copy,
                    onResize: { size in
                        performMenuAction {
                            controller.resizeTile(id: tile.id, size: size)
                        }
                    },
                    onEdit: {
                        performMenuAction {
                            controller.editTile(id: tile.id)
                        }
                    },
                    onMove: {
                        performMenuAction {
                            controller.startEditing()
                        }
                    },
                    onRemove: {
                        performMenuAction(onRequestRemoval)
                    }
                )
                .presentationCompactAdaptation(.popover)
            }
            .sensoryFeedback(.impact(weight: .medium), trigger: menuFeedback)
            .accessibilityLabel(tile.accessibilityLabel)
            .onAppear { jiggling = editing }
            .onChange(of: editing) { _, value in jiggling = value }
            .zIndex(dragging ? 1 : 0)
    }

    @ViewBuilder
    private var content: some View {
        if tile.isContainer {
            ContainerTileContent(tile: tile, editing: editing, controller: controller)
        } else {
            LeafTileContent(tile: tile, editing: editing)
        }
    }

    @ViewBuilder
    private var highlight: some View {
        if drop != nil {
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(Color.accentColor, lineWidth: 2)
        }
    }

    private func performMenuAction(_ action: @escaping () -> Void) {
        menuPresented = false
        DispatchQueue.main.async(execute: action)
    }

    private var dragGesture: some Gesture {
        DragGesture(minimumDistance: 6)
            .onChanged { value in
                dragging = true
                dragOffset = value.translation
                drop = resolve(dragLocation(value))
            }
            .onEnded { value in
                if let target = resolve(dragLocation(value)) {
                    controller.moveTile(id: tile.id, onto: target.id)
                }
                dragging = false
                dragOffset = .zero
                drop = nil
            }
    }

    private func dragLocation(_ value: DragGesture.Value) -> CGPoint {
        CGPoint(
            x: value.startLocation.x + value.translation.width,
            y: value.startLocation.y + value.translation.height
        )
    }

    private func resolve(_ location: CGPoint) -> NativeHubDropTarget? {
        let originX = (columnWidth + HubGrid.gap) * CGFloat(tile.column)
        let originY = (HubGrid.rowHeight + HubGrid.gap) * CGFloat(tile.row)
        return controller.resolveDrop(
            draggedId: tile.id,
            x: Double(originX + location.x),
            y: Double(originY + location.y),
            columnWidth: Double(columnWidth),
            rowHeight: Double(HubGrid.rowHeight),
            gap: Double(HubGrid.gap)
        )
    }

    private func tapped() {
        if editing {
            return
        } else if tile.expandable {
            controller.expandTile(id: tile.id)
        } else if !tile.isContainer {
            controller.payTile(id: tile.id)
        }
    }
}

private struct TileActionMenu: View {
    let tile: NativeHubTile
    let copy: NativePaymentHubCopy
    let onResize: (String) -> Void
    let onEdit: () -> Void
    let onMove: () -> Void
    let onRemove: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 8) {
                Text(copy.sizeLabel)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(.secondary)

                HStack(spacing: 7) {
                    ForEach(tile.sizes, id: \.id) { size in
                        sizeButton(size)
                    }
                }
            }
            .padding(12)

            Divider()
            menuButton(copy.edit, systemImage: "slider.horizontal.3", action: onEdit)
            menuButton(copy.move, systemImage: "arrow.up.and.down.and.arrow.left.and.right", action: onMove)
            Divider()
            menuButton(
                copy.removeConfirm,
                systemImage: "minus.circle",
                role: .destructive,
                action: onRemove
            )
        }
        .frame(width: 276)
    }

    private func sizeButton(_ size: NativeHubSizeOption) -> some View {
        Button { onResize(size.id) } label: {
            VStack(spacing: 4) {
                TileSizeGlyph(columns: Int(size.columns), rows: Int(size.rows))
                Text("\(size.columns) × \(size.rows)")
                    .font(.caption2.weight(.medium))
            }
            .foregroundStyle(size.selected ? Color.accentColor : Color.secondary)
            .frame(maxWidth: .infinity, minHeight: 62)
            .background(
                size.selected
                    ? Color.accentColor.opacity(0.14)
                    : Color(uiColor: .tertiarySystemFill),
                in: RoundedRectangle(cornerRadius: 10)
            )
            .overlay {
                if size.selected {
                    RoundedRectangle(cornerRadius: 10)
                        .strokeBorder(Color.accentColor.opacity(0.55), lineWidth: 1)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(size.label), \(size.columns) × \(size.rows)")
    }

    private func menuButton(
        _ title: String,
        systemImage: String,
        role: ButtonRole? = nil,
        action: @escaping () -> Void
    ) -> some View {
        Button(role: role, action: action) {
            Label(title, systemImage: systemImage)
                .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                .padding(.horizontal, 16)
        }
        .buttonStyle(.plain)
    }
}

private struct TileSizeGlyph: View {
    let columns: Int
    let rows: Int

    var body: some View {
        RoundedRectangle(cornerRadius: 3)
            .strokeBorder(lineWidth: 2)
            .frame(width: columns == 1 ? 17 : 29, height: rows == 1 ? 17 : 29)
            .frame(width: 30, height: 30)
    }
}

private struct LeafTileContent: View {
    let tile: NativeHubTile
    let editing: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HubMarkView(mark: tile.mark, size: 32)
            Spacer(minLength: 0)
            Text(tile.label)
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
            // Only a two-row tile has room for the address; at one row the amount line wins.
            if tile.rows >= 2, let subtitle = tile.subtitle, !editing {
                Text(subtitle)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            if let amount = tile.amountLine, !editing {
                Text(amount)
                    .font(.caption.weight(.medium))
                    .monospacedDigit()
                    .lineLimit(1)
                    .padding(.top, 4)
            }
        }
        .padding(12)
    }
}

private struct ContainerTileContent: View {
    let tile: NativeHubTile
    let editing: Bool
    let controller: NativePaymentHubController

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(tile.label)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Spacer(minLength: 4)
                if tile.columns >= 2 && !editing {
                    Text(tile.memberCount)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            if !tile.showsMembers {
                Spacer(minLength: 0)
                HStack(spacing: 4) {
                    ForEach(tile.members.prefix(5), id: \.id) { member in
                        HubMarkView(mark: member.mark, size: 26)
                    }
                }
            } else if tile.rows == 1 {
                HStack(spacing: 5) {
                    ForEach(tile.members, id: \.id) { member in
                        memberCard(member)
                    }
                }
                .padding(.top, 8)
            } else {
                VStack(spacing: 5) {
                    ForEach(tile.members, id: \.id) { member in
                        memberRow(member)
                    }
                }
                .padding(.top, 8)
            }
        }
        .padding(12)
    }

    private func memberCard(_ member: NativeHubTileMember) -> some View {
        Button {
            controller.payTile(id: member.id)
        } label: {
            VStack(alignment: .leading, spacing: 0) {
                HubMarkView(mark: member.mark, size: 22)
                Spacer(minLength: 0)
                Text(member.label).font(.caption).lineLimit(1)
                if !editing {
                    Text(member.amountLine)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .padding(8)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(memberBackground)
        }
        .buttonStyle(.plain)
        .disabled(editing)
    }

    private func memberRow(_ member: NativeHubTileMember) -> some View {
        Button {
            controller.payTile(id: member.id)
        } label: {
            HStack(spacing: 8) {
                HubMarkView(mark: member.mark, size: 20)
                Text(member.label).font(.caption).lineLimit(1)
                Spacer(minLength: 4)
                if !editing {
                    Text(member.amountLine)
                        .font(.caption2)
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
            .background(memberBackground)
        }
        .buttonStyle(.plain)
        .disabled(editing)
    }

    private var memberBackground: some View {
        RoundedRectangle(cornerRadius: 8).fill(Color(uiColor: .tertiarySystemBackground))
    }
}

// MARK: - Compose a target

private struct NewTargetView: View {
    let state: NativeNewTarget
    let copy: NativePaymentHubCopy
    let controller: NativePaymentHubController
    let importButton: AnyView?

    var body: some View {
        content
            .navigationTitle(state.title)
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(true)
            .toolbar(.visible, for: .navigationBar)
            .toolbar { toolbarContent }
            .alert(comingSoonTitle, isPresented: comingSoonPresented) {
                Button(copy.comingSoonConfirm) { controller.dismissComingSoon() }
            } message: {
                Text(state.comingSoon?.body ?? "")
            }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Button(action: controller.stepBack) {
                Image(systemName: "chevron.left")
            }
            .accessibilityLabel(copy.back)
        }
    }

    private var comingSoonTitle: String { state.comingSoon?.title ?? "" }

    private var comingSoonPresented: Binding<Bool> {
        Binding(
            get: { state.comingSoon != nil },
            set: { presented in
                if !presented { controller.dismissComingSoon() }
            }
        )
    }

    @ViewBuilder
    private var content: some View {
        switch state.view {
        case "contacts": contacts
        case "services": services
        case "configure":
            if let configure = state.configure {
                ConfigureView(state: configure, copy: copy, controller: controller)
            } else {
                ProgressView()
            }
        default: launchpad
        }
    }

    private var launchpad: some View {
        LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 2),
            spacing: 10
        ) {
            LaunchpadActionCell(
                label: copy.sectionPeople,
                symbol: "person.2",
                action: controller.openContacts
            )
            ForEach(state.featuredServices, id: \.id) { service in
                LaunchpadCell(label: service.name) {
                    controller.selectService(id: service.id)
                } mark: {
                    AnyView(HubServiceMarkView(initials: service.mark, size: 58))
                }
            }
            LaunchpadActionCell(
                label: copy.more,
                symbol: "ellipsis",
                action: controller.openServices
            )
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }

    private var contacts: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(copy.contactsTitle)
                .font(.title2.weight(.semibold))
                .padding(.horizontal, 16)
                .padding(.top, 18)
                .padding(.bottom, 12)
            // These sit here, not in settings: this is the moment someone fails to find a name.
            HStack(spacing: 8) {
                if let importButton {
                    importButton.frame(maxWidth: .infinity)
                }
                Button(copy.addManually) { controller.addManually() }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, 16)

            if state.contacts.isEmpty {
                Text(state.hasContacts ? copy.noMatches : copy.noContacts)
                    .foregroundStyle(.secondary)
                    .padding(16)
                Spacer()
            } else {
                List(state.contacts, id: \.id) { contact in
                    Button {
                        controller.selectContact(id: contact.id)
                    } label: {
                        HubContactRow(contact: contact)
                    }
                    .buttonStyle(.plain)
                }
                .listStyle(.plain)
            }
        }
        .searchable(
            text: Binding(
                get: { state.query },
                set: { controller.updateQuery(value: $0) }
            ),
            prompt: copy.search
        )
    }

    private var services: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(copy.servicesTitle)
                .font(.title2.weight(.semibold))
                .padding(.horizontal, 16)
                .padding(.top, 18)
                .padding(.bottom, 12)
            List(state.services, id: \.id) { service in
                Button {
                    controller.selectService(id: service.id)
                } label: {
                    HStack(spacing: 11) {
                        HubServiceMarkView(initials: service.mark, size: 32)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(service.name).font(.body.weight(.semibold))
                            Text(service.subtitle)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.tertiary)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .listStyle(.plain)
        }
    }
}

private struct LaunchpadCell: View {
    let label: String
    let action: () -> Void
    @ViewBuilder let mark: () -> AnyView

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                mark()
                Text(label)
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity, minHeight: 112)
            .background(
                RoundedRectangle(cornerRadius: 14).fill(Color(uiColor: .secondarySystemBackground))
            )
            .contentShape(RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }
}

private struct LaunchpadActionCell: View {
    let label: String
    let symbol: String
    let action: () -> Void

    var body: some View {
        LaunchpadCell(label: label, action: action) {
            AnyView(
                RoundedRectangle(cornerRadius: 10)
                    .strokeBorder(
                        Color.secondary.opacity(0.5),
                        style: StrokeStyle(lineWidth: 1, dash: [4, 3])
                    )
                    .frame(width: 58, height: 58)
                    .overlay(Image(systemName: symbol).foregroundStyle(.secondary))
            )
        }
    }
}

private struct HubContactRow: View {
    let contact: NativeHubContact

    var body: some View {
        HStack(spacing: 11) {
            HubMarkView(mark: contact.mark, size: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(contact.title).font(.body.weight(.semibold)).lineLimit(1)
                Text(contact.subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 4)
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .contentShape(Rectangle())
    }
}

private struct ConfigureView: View {
    let state: NativeHubConfigure
    let copy: NativePaymentHubCopy
    let controller: NativePaymentHubController

    var body: some View {
        VStack(spacing: 0) {
            Form {
                Section {
                    TextField(
                        copy.nameLabel,
                        text: Binding(
                            get: { state.title },
                            set: { controller.updateTargetTitle(value: $0) }
                        )
                    )
                    TextField(
                        copy.addressLabel,
                        text: Binding(
                            get: { state.address },
                            set: { controller.updateTargetAddress(value: $0) }
                        )
                    )
                    .textContentType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                }

                Section(copy.amountLabel) {
                    chips
                    if state.showsCustomAmount {
                        HStack {
                            TextField(
                                copy.amountLabel,
                                text: Binding(
                                    get: { state.customAmount },
                                    set: { controller.updateCustomAmount(value: $0) }
                                )
                            )
                            .keyboardType(.decimalPad)
                            Picker(
                                state.currencyCode,
                                selection: Binding(
                                    get: { state.currencyCode },
                                    set: { controller.selectCurrency(code: $0) }
                                )
                            ) {
                                ForEach(state.currencyCodes, id: \.self) { code in
                                    Text(code).tag(code)
                                }
                            }
                            .labelsHidden()
                        }
                    }
                    if let hint = state.fiatHint {
                        Text(hint).font(.footnote).foregroundStyle(.secondary)
                    }
                }

                Section {
                    TextField(
                        copy.commentLabel,
                        text: Binding(
                            get: { state.comment },
                            set: { controller.updateTargetComment(value: $0) }
                        )
                    )
                }

                Section(copy.sizeLabel) {
                    HStack(spacing: 8) {
                        ForEach(state.sizes, id: \.id) { size in
                            SizeCard(option: size) { controller.selectSize(id: size.id) }
                        }
                    }
                    Text(state.sizeHint).font(.footnote).foregroundStyle(.secondary)
                }

                if let error = state.error {
                    Section { Text(error).foregroundStyle(.red) }
                }

                if !state.isNew {
                    Section {
                        Button(copy.deleteTarget, role: .destructive) {
                            controller.deleteTarget()
                        }
                    }
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            Button(state.submitTitle) { controller.submitTarget() }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(.bar)
        }
    }

    private var chips: some View {
        // A wrapping row of chips; the design's grammar is "ask, a preset, or something else".
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: 96), spacing: 6)],
            alignment: .leading,
            spacing: 6
        ) {
            ForEach(state.amountChips, id: \.id) { chip in
                Button {
                    controller.selectAmountChip(id: chip.id)
                } label: {
                    Text(chip.label)
                        .font(.subheadline.weight(chip.selected ? .semibold : .medium))
                        .padding(.horizontal, 13)
                        .padding(.vertical, 10)
                        .frame(maxWidth: .infinity)
                        .background(
                            RoundedRectangle(cornerRadius: 10)
                                .fill(chip.selected ? Color.accentColor : Color.clear)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .strokeBorder(
                                    chip.selected ? Color.clear : Color.secondary.opacity(0.4)
                                )
                        )
                        .foregroundStyle(chip.selected ? Color.white : Color.primary)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct SizeCard: View {
    let option: NativeHubSizeOption
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                ZStack {
                    Color.clear.frame(height: 38)
                    glyph
                }
                Text(option.label).font(.caption.weight(.semibold))
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 9)
            .padding(.vertical, 10)
            .overlay(
                RoundedRectangle(cornerRadius: 10)
                    .strokeBorder(
                        option.selected ? Color.accentColor : Color.secondary.opacity(0.35),
                        lineWidth: option.selected ? 2 : 1
                    )
            )
        }
        .buttonStyle(.plain)
    }

    private var glyph: some View {
        RoundedRectangle(cornerRadius: 4)
            .fill(Color.secondary.opacity(0.25))
            .frame(
                width: option.columns >= 2 ? 46 : 22,
                height: option.rows >= 2 ? 38 : 22
            )
            .overlay(alignment: .top) {
                // A large tile draws its internal rows to read as "always open".
                if option.rows >= 2 {
                    VStack(spacing: 3) {
                        ForEach(0..<3, id: \.self) { _ in
                            RoundedRectangle(cornerRadius: 1)
                                .fill(Color.secondary)
                                .frame(height: 4)
                        }
                    }
                    .padding(4)
                }
            }
    }
}

// MARK: - Group editor

private struct GroupEditorView: View {
    let state: NativeHubGroupEditor
    let copy: NativePaymentHubCopy
    let controller: NativePaymentHubController

    var body: some View {
        Form {
            Section {
                TextField(
                    copy.groupNameLabel,
                    text: Binding(
                        get: { state.title },
                        set: { controller.updateGroupTitle(value: $0) }
                    )
                )
            }

            Section {
                Picker(
                    copy.appearanceIcon,
                    selection: Binding(
                        get: { state.icon },
                        set: { controller.updateGroupIcon(value: $0) }
                    )
                ) {
                    Text(copy.appearanceNone).tag(String?.none)
                    ForEach(copy.iconOptions, id: \.id) { option in
                        Label(option.title, systemImage: hubSymbol(option.id))
                            .tag(Optional(option.id))
                    }
                }
                Picker(
                    copy.appearanceAccent,
                    selection: Binding(
                        get: { state.accent },
                        set: { controller.updateGroupAccent(value: $0) }
                    )
                ) {
                    Text(copy.appearanceNone).tag(String?.none)
                    ForEach(copy.accentOptions, id: \.id) { option in
                        Label {
                            Text(option.title)
                        } icon: {
                            Circle().fill(hubAccentColor(option.id)).frame(width: 14, height: 14)
                        }
                        .tag(Optional(option.id))
                    }
                }
            }

            Section(copy.groupMembersLabel) {
                if state.members.isEmpty {
                    Text(copy.groupMembersEmpty).foregroundStyle(.secondary)
                }
                ForEach(Array(state.members.enumerated()), id: \.element.id) { index, member in
                    HStack {
                        HubContactRow(contact: member)
                        Button {
                            controller.moveGroupMember(id: member.id, offset: -1)
                        } label: {
                            Image(systemName: "arrow.up")
                        }
                        .disabled(index == 0)
                        .accessibilityLabel(copy.moveUp)
                        Button {
                            controller.moveGroupMember(id: member.id, offset: 1)
                        } label: {
                            Image(systemName: "arrow.down")
                        }
                        .disabled(index == state.members.count - 1)
                        .accessibilityLabel(copy.moveDown)
                        Button {
                            controller.removeGroupMember(id: member.id)
                        } label: {
                            Image(systemName: "minus.circle")
                        }
                        .accessibilityLabel(copy.removeMember)
                    }
                }
            }

            Section(copy.groupAvailableLabel) {
                if state.available.isEmpty {
                    Text(
                        state.members.isEmpty
                            ? copy.groupAvailableNone
                            : copy.groupAvailableAllAdded
                    )
                    .foregroundStyle(.secondary)
                }
                ForEach(state.available, id: \.id) { item in
                    HStack {
                        HubContactRow(contact: item)
                        Button {
                            controller.addGroupMember(id: item.id)
                        } label: {
                            Image(systemName: "plus.circle")
                        }
                        .accessibilityLabel(copy.addMember)
                    }
                }
            }

            if let error = state.error {
                Section { Text(error).foregroundStyle(.red) }
            }

            Section {
                Button(copy.save) { controller.saveGroup() }
                    .frame(maxWidth: .infinity)
                if !state.isNew {
                    Button(copy.delete_, role: .destructive) { controller.deleteGroup() }
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .navigationTitle(state.isNew ? copy.groupEditorNew : copy.groupEditorEdit)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
    }
}

// MARK: - Marks

private struct HubMarkView: View {
    let mark: NativeHubMark
    let size: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size >= 44 ? 10 : 6)
        return ZStack {
            shape.fill(hubAccentColor(mark.accent).opacity(0.22))
            if let symbol = mark.symbol {
                Image(systemName: hubSymbol(symbol))
                    .font(.system(size: size * 0.46, weight: .semibold))
            } else {
                Text(mark.initials)
                    .font(.system(size: size * 0.42, weight: .semibold))
                    .lineLimit(1)
            }
        }
        .foregroundStyle(hubAccentColor(mark.accent))
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// A catalogue service is outlined, which is the whole difference the hub draws from a person.
private struct HubServiceMarkView: View {
    let initials: String
    let size: CGFloat

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size >= 44 ? 10 : 6)
        return Text(initials)
            .font(.system(size: size * 0.36, weight: .semibold))
            .frame(width: size, height: size)
            .overlay(shape.strokeBorder(Color.secondary.opacity(0.5)))
            .accessibilityHidden(true)
    }
}

private func hubSymbol(_ value: String) -> String {
    switch value {
    case "person": return "person.fill"
    case "group": return "person.2.fill"
    case "store": return "storefront.fill"
    case "restaurant": return "fork.knife"
    case "coffee": return "cup.and.saucer.fill"
    case "gift": return "gift.fill"
    case "heart": return "heart.fill"
    case "star": return "star.fill"
    case "bolt": return "bolt.fill"
    case "home": return "house.fill"
    case "wallet": return "wallet.bifold.fill"
    case "work": return "briefcase.fill"
    default: return "circle.fill"
    }
}

private func hubAccentColor(_ value: String?) -> Color {
    switch value {
    case "orange": return .orange
    case "blue": return .blue
    case "green": return .green
    case "purple": return .purple
    case "pink": return .pink
    case "teal": return .teal
    case "amber": return Color(red: 0.75, green: 0.52, blue: 0.05)
    case "slate": return Color(red: 0.34, green: 0.42, blue: 0.48)
    default: return .primary
    }
}
