import Shared
import SwiftUI
import UIKit

@MainActor
private final class NativePaymentHubModel: ObservableObject {
    @Published private(set) var snapshot: NativePaymentHubSnapshot?
    @Published var searchQuery = "" {
        didSet {
            guard searchQuery != oldValue else { return }
            controller.updateSearch(query: searchQuery)
        }
    }

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
    case library
    case targetEditor
    case groupEditor
}

/// Native iOS Hub. Kotlin owns persistence, validation, and payment intents; SwiftUI owns UI.
struct NativePaymentHubView: View {
    @StateObject private var model: NativePaymentHubModel
    @State private var arrangingCanvas = false
    @State private var showsAddTiles = false

    private let additionalLibraryContent: AnyView?

    init(
        controller: NativePaymentHubController,
        additionalLibraryContent: AnyView? = nil
    ) {
        _model = StateObject(wrappedValue: NativePaymentHubModel(controller: controller))
        self.additionalLibraryContent = additionalLibraryContent
    }

    var body: some View {
        NavigationStack(path: navigationPath) {
            Group {
                if let snapshot = model.snapshot {
                    canvas(snapshot)
                        .navigationDestination(for: NativePaymentHubRoute.self) { route in
                            destination(route, snapshot: snapshot)
                        }
                        .sheet(isPresented: groupSheetPresented) {
                            if let sheet = model.snapshot?.groupSheet {
                                groupSheet(sheet, copy: snapshot.text)
                            }
                        }
                        .sheet(isPresented: $showsAddTiles) {
                            addTilesSheet(snapshot)
                        }
                } else {
                    ProgressView()
                }
            }
        }
        .background(Color(uiColor: .systemGroupedBackground))
    }

    @ViewBuilder
    private func destination(
        _ route: NativePaymentHubRoute,
        snapshot: NativePaymentHubSnapshot
    ) -> some View {
        switch route {
        case .library:
            library(snapshot)
        case .targetEditor:
            if let editor = snapshot.targetEditor {
                targetEditor(editor, copy: snapshot.text)
            } else {
                ProgressView()
            }
        case .groupEditor:
            if let editor = snapshot.groupEditor {
                groupEditor(editor, copy: snapshot.text)
            } else {
                ProgressView()
            }
        }
    }

    private var navigationPath: Binding<[NativePaymentHubRoute]> {
        Binding(
            get: {
                switch model.snapshot?.destination {
                case "library": return [.library]
                case "targetEditor": return [.library, .targetEditor]
                case "groupEditor": return [.library, .groupEditor]
                default: return []
                }
            },
            set: { routes in
                switch routes.last {
                case .targetEditor, .groupEditor:
                    break
                case .library:
                    if model.snapshot?.destination != "library" {
                        model.controller.closeEditor()
                    }
                case nil:
                    model.controller.closeLibrary()
                }
            }
        )
    }

    private var groupSheetPresented: Binding<Bool> {
        Binding(
            get: { model.snapshot?.groupSheet != nil },
            set: { presented in
                if !presented {
                    model.controller.dismissGroup()
                }
            }
        )
    }

    @ViewBuilder
    private func canvas(_ snapshot: NativePaymentHubSnapshot) -> some View {
        Group {
            if snapshot.library.isEmpty {
                ContentUnavailableView {
                    Label(snapshot.text.emptyTitle, systemImage: "square.grid.2x2")
                } description: {
                    Text(snapshot.text.emptyBody)
                } actions: {
                    Button(snapshot.text.addTarget) {
                        model.controller.openLibrary()
                    }
                    .buttonStyle(.borderedProminent)
                }
            } else if snapshot.canvasTiles.isEmpty {
                ContentUnavailableView {
                    Label(snapshot.text.title, systemImage: "square.grid.2x2")
                } description: {
                    Text(snapshot.text.emptyCanvasBody)
                } actions: {
                    Button(snapshot.text.addTiles) {
                        arrangingCanvas = true
                        showsAddTiles = true
                    }
                    .buttonStyle(.borderedProminent)
                }
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(canvasRows(snapshot.canvasTiles)) { row in
                            HStack(spacing: 12) {
                                ForEach(row.tiles, id: \.item.id) { tile in
                                    canvasTile(
                                        tile,
                                        arranging: arrangingCanvas,
                                        copy: snapshot.text
                                    )
                                }
                                if row.needsPlaceholder {
                                    Color.clear
                                        .frame(maxWidth: .infinity, minHeight: 120)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
            }
        }
        .navigationTitle(snapshot.text.title)
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if arrangingCanvas {
                    Button {
                        showsAddTiles = true
                    } label: {
                        Label(snapshot.text.addTiles, systemImage: "plus")
                    }
                    .disabled(snapshot.placeableItems.isEmpty)

                    Button(role: .destructive) {
                        model.controller.resetCanvas()
                    } label: {
                        Label(snapshot.text.reset, systemImage: "arrow.counterclockwise")
                    }
                    .disabled(snapshot.canvasTiles.isEmpty)

                    Button(snapshot.text.done) {
                        arrangingCanvas = false
                    }
                } else {
                    Button {
                        arrangingCanvas = true
                    } label: {
                        Label(snapshot.text.arrange, systemImage: "slider.horizontal.3")
                    }
                    Button {
                        model.controller.openLibrary()
                    } label: {
                        Label(snapshot.text.title, systemImage: "pencil")
                    }
                }
            }
        }
    }

    private func canvasTile(
        _ tile: NativePaymentHubTile,
        arranging: Bool,
        copy: NativePaymentHubCopy
    ) -> some View {
        Group {
            if arranging {
                canvasTileContent(tile, arranging: true, copy: copy)
            } else {
                Button {
                    guard tile.item.enabled else { return }
                    model.controller.selectCanvasItem(id: tile.item.id)
                } label: {
                    canvasTileContent(tile, arranging: false, copy: copy)
                }
                .buttonStyle(.plain)
                .disabled(!tile.item.enabled)
            }
        }
        .accessibilityLabel(tile.item.title)
    }

    private func canvasTileContent(
        _ tile: NativePaymentHubTile,
        arranging: Bool,
        copy: NativePaymentHubCopy
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                NativePaymentHubGlyph(item: tile.item, size: 40)
                if tile.size == "wide" {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tile.item.title)
                            .font(.headline)
                            .lineLimit(2)
                        if let amount = tile.item.amount {
                            Text(amount)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.tint)
                        }
                    }
                }
                Spacer(minLength: 0)
            }

            Spacer(minLength: 0)

            if tile.size != "wide" {
                Text(tile.item.title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(2)
                if let amount = tile.item.amount {
                    Text(amount)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.tint)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }
            }

            if arranging {
                HStack(spacing: 4) {
                    tileAction(
                        systemImage: tile.size == "wide"
                            ? "rectangle.compress.vertical"
                            : "rectangle.expand.vertical",
                        label: tile.size == "wide" ? copy.makeCompact : copy.makeWide
                    ) {
                        model.controller.resizeTile(id: tile.item.id)
                    }
                    tileAction(systemImage: "xmark", label: copy.removeTile) {
                        model.controller.removeTile(id: tile.item.id)
                    }
                    tileAction(
                        systemImage: "arrow.left",
                        label: copy.moveEarlier,
                        disabled: tile.index == 0
                    ) {
                        model.controller.moveTile(index: tile.index, offset: -1)
                    }
                    tileAction(
                        systemImage: "arrow.right",
                        label: copy.moveLater,
                        disabled: Int(tile.index) >=
                            (model.snapshot?.canvasTiles.count ?? 0) - 1
                    ) {
                        model.controller.moveTile(index: tile.index, offset: 1)
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 120, alignment: .leading)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20))
        .contentShape(RoundedRectangle(cornerRadius: 20))
    }

    private func tileAction(
        systemImage: String,
        label: String,
        disabled: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .frame(width: 44, height: 44)
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .accessibilityLabel(label)
    }

    private func addTilesSheet(_ snapshot: NativePaymentHubSnapshot) -> some View {
        NavigationStack {
            Group {
                if snapshot.placeableItems.isEmpty {
                    ContentUnavailableView(
                        snapshot.text.allTilesPlaced,
                        systemImage: "checkmark.circle"
                    )
                } else {
                    List(snapshot.placeableItems, id: \.id) { item in
                        Button {
                            model.controller.addTile(id: item.id)
                        } label: {
                            NativePaymentHubItemRow(item: item)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .navigationTitle(snapshot.text.addSheetTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(snapshot.text.done) { showsAddTiles = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func groupSheet(
        _ sheet: NativePaymentHubGroupSheet,
        copy: NativePaymentHubCopy
    ) -> some View {
        NavigationStack {
            Group {
                if sheet.members.isEmpty {
                    ContentUnavailableView(copy.emptyGroup, systemImage: "person.2")
                } else {
                    List(sheet.members, id: \.id) { member in
                        Button {
                            model.controller.selectGroupMember(id: member.id)
                        } label: {
                            NativePaymentHubItemRow(item: member)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .navigationTitle(sheet.group.title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(role: .cancel) { model.controller.dismissGroup() } label: {
                        Image(systemName: "xmark")
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    @ViewBuilder
    private func library(_ snapshot: NativePaymentHubSnapshot) -> some View {
        let library = snapshot.library
        let hasMatches =
            !library.pinned.isEmpty || !library.groups.isEmpty ||
            !library.recent.isEmpty || !library.targets.isEmpty

        Group {
            if library.isEmpty {
                ContentUnavailableView {
                    Label(snapshot.text.emptyTitle, systemImage: "person.crop.circle.badge.plus")
                } description: {
                    Text(snapshot.text.emptyBody)
                } actions: {
                    Button(snapshot.text.addTarget) {
                        model.controller.openTargetEditor(id: nil)
                    }
                    .buttonStyle(.borderedProminent)
                    if let additionalLibraryContent {
                        additionalLibraryContent
                    }
                }
            } else if !hasMatches {
                ContentUnavailableView(
                    snapshot.text.noMatches,
                    systemImage: "magnifyingglass"
                )
            } else {
                List {
                    if let additionalLibraryContent {
                        Section { additionalLibraryContent }
                    }
                    hubSection(
                        snapshot.text.pinnedSection,
                        items: library.pinned,
                        arrangingPins: library.arrangingPins,
                        allPinned: library.pinned,
                        copy: snapshot.text
                    )
                    hubSection(snapshot.text.groupsSection, items: library.groups, copy: snapshot.text)
                    hubSection(snapshot.text.recentSection, items: library.recent, copy: snapshot.text)
                    hubSection(snapshot.text.targetsSection, items: library.targets, copy: snapshot.text)
                }
                .listStyle(.insetGrouped)
            }
        }
        .navigationTitle(snapshot.text.title)
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $model.searchQuery, prompt: snapshot.text.search)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if !library.pinned.isEmpty || library.arrangingPins {
                    Button {
                        model.controller.toggleArrangePins()
                    } label: {
                        Label(
                            library.arrangingPins
                                ? snapshot.text.doneArrangingPins
                                : snapshot.text.arrangePins,
                            systemImage: "arrow.up.arrow.down"
                        )
                    }
                    .tint(library.arrangingPins ? .accentColor : nil)
                }
                Menu {
                    Button(snapshot.text.addTarget, systemImage: "person.badge.plus") {
                        model.controller.openTargetEditor(id: nil)
                    }
                    Button(snapshot.text.addGroup, systemImage: "person.2.badge.plus") {
                        model.controller.openGroupEditor(id: nil)
                    }
                } label: {
                    Label(snapshot.text.add, systemImage: "plus")
                }
            }
        }
    }

    @ViewBuilder
    private func hubSection(
        _ title: String,
        items: [NativePaymentHubItem],
        arrangingPins: Bool = false,
        allPinned: [NativePaymentHubItem] = [],
        copy: NativePaymentHubCopy
    ) -> some View {
        if !items.isEmpty {
            Section(title) {
                ForEach(items, id: \.id) { item in
                    HStack(spacing: 8) {
                        Button {
                            if item.isGroup {
                                model.controller.openGroupEditor(id: item.id)
                            } else {
                                model.controller.openTargetEditor(id: item.id)
                            }
                        } label: {
                            NativePaymentHubItemRow(item: item)
                        }
                        .buttonStyle(.plain)

                        if arrangingPins {
                            Button {
                                model.controller.movePinned(id: item.id, offset: -1)
                            } label: {
                                Image(systemName: "arrow.up")
                            }
                            .disabled(allPinned.first?.id == item.id)
                            .accessibilityLabel(copy.moveUp)

                            Button {
                                model.controller.movePinned(id: item.id, offset: 1)
                            } label: {
                                Image(systemName: "arrow.down")
                            }
                            .disabled(allPinned.last?.id == item.id)
                            .accessibilityLabel(copy.moveDown)
                        }

                        Button {
                            model.controller.setPinned(id: item.id, pinned: !item.pinned)
                        } label: {
                            Image(systemName: item.pinned ? "pin.fill" : "pin")
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel(item.pinned ? copy.unpin : copy.pin)
                    }
                }
            }
        }
    }

    private func targetEditor(
        _ editor: NativePaymentHubTargetEditor,
        copy: NativePaymentHubCopy
    ) -> some View {
        Form {
            Section {
                TextField(
                    copy.targetName,
                    text: binding(editor.title, model.controller.updateTargetTitle)
                )
                TextField(
                    copy.targetAddress,
                    text: binding(editor.address, model.controller.updateTargetAddress)
                )
                .textContentType(.emailAddress)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            }

            Section(copy.amount) {
                Picker(
                    copy.amount,
                    selection: binding(
                        editor.amountMode,
                        model.controller.updateTargetAmountMode
                    )
                ) {
                    Text(copy.askEveryTime).tag("ask")
                    Text(copy.presetAmount).tag("preset")
                }
                .pickerStyle(.segmented)

                if editor.amountMode == "preset" {
                    HStack {
                        TextField(
                            copy.amount,
                            text: binding(editor.amount, model.controller.updateTargetAmount)
                        )
                        .keyboardType(.decimalPad)
                        Picker(
                            editor.currencyCode,
                            selection: binding(
                                editor.currencyCode,
                                model.controller.updateTargetCurrency
                            )
                        ) {
                            ForEach(editor.currencyCodes, id: \.self) { code in
                                Text(code).tag(code)
                            }
                        }
                        .labelsHidden()
                    }
                    if let hint = editor.fiatHint {
                        Text(hint).font(.footnote).foregroundStyle(.secondary)
                    }
                }
            }

            Section {
                TextField(
                    copy.comment,
                    text: binding(editor.comment, model.controller.updateTargetComment)
                )
            }

            appearanceSection(
                icon: editor.icon,
                accent: editor.accent,
                fallback: editor.title,
                copy: copy,
                onIcon: model.controller.updateTargetIcon,
                onAccent: model.controller.updateTargetAccent
            )

            Section {
                Toggle(
                    isOn: boolBinding(editor.pinned, model.controller.updateTargetPinned)
                ) {
                    VStack(alignment: .leading) {
                        Text(copy.pinLabel)
                        Text(copy.pinDescription)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section(copy.targetGroups) {
                if editor.groups.isEmpty {
                    Text(copy.targetGroupsEmpty).foregroundStyle(.secondary)
                } else {
                    ForEach(editor.groups, id: \.id) { group in
                        Button {
                            model.controller.toggleTargetGroup(id: group.id)
                        } label: {
                            HStack {
                                Text(group.title).foregroundStyle(.primary)
                                Spacer()
                                if group.selected {
                                    Image(systemName: "checkmark").foregroundStyle(.tint)
                                }
                            }
                        }
                    }
                }
            }

            if let error = editor.error {
                Section { Text(error).foregroundStyle(.red) }
            }

            Section {
                Button(copy.save, action: model.controller.saveTarget)
                    .frame(maxWidth: .infinity)
                if !editor.isNew {
                    Button(copy.delete_, role: .destructive) {
                        model.controller.deleteTarget()
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .navigationTitle(editor.isNew ? copy.newTarget : copy.editTarget)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func groupEditor(
        _ editor: NativePaymentHubGroupEditor,
        copy: NativePaymentHubCopy
    ) -> some View {
        Form {
            Section {
                TextField(
                    copy.groupName,
                    text: binding(editor.title, model.controller.updateGroupTitle)
                )
            }

            appearanceSection(
                icon: editor.icon,
                accent: editor.accent,
                fallback: editor.title,
                copy: copy,
                onIcon: model.controller.updateGroupIcon,
                onAccent: model.controller.updateGroupAccent
            )

            Section {
                Toggle(
                    isOn: boolBinding(editor.pinned, model.controller.updateGroupPinned)
                ) {
                    VStack(alignment: .leading) {
                        Text(copy.pinLabel)
                        Text(copy.pinDescription)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section(copy.members) {
                if editor.members.isEmpty {
                    Text(copy.membersEmpty).foregroundStyle(.secondary)
                }
                ForEach(Array(editor.members.enumerated()), id: \.element.id) { index, member in
                    HStack {
                        NativePaymentHubItemRow(item: member)
                        Button {
                            model.controller.moveGroupMember(id: member.id, offset: -1)
                        } label: {
                            Image(systemName: "arrow.up")
                        }
                        .disabled(index == 0)
                        .accessibilityLabel(copy.moveUp)
                        Button {
                            model.controller.moveGroupMember(id: member.id, offset: 1)
                        } label: {
                            Image(systemName: "arrow.down")
                        }
                        .disabled(index == editor.members.count - 1)
                        .accessibilityLabel(copy.moveDown)
                        Button {
                            model.controller.removeGroupMember(id: member.id)
                        } label: {
                            Image(systemName: "minus.circle")
                        }
                        .accessibilityLabel(copy.removeMember)
                    }
                }
            }

            Section(copy.availableTargets) {
                if editor.available.isEmpty {
                    Text(
                        editor.members.isEmpty
                            ? copy.noAvailableTargets
                            : copy.allTargetsAdded
                    )
                    .foregroundStyle(.secondary)
                }
                ForEach(editor.available, id: \.id) { item in
                    HStack {
                        NativePaymentHubItemRow(item: item)
                        Button {
                            model.controller.addGroupMember(id: item.id)
                        } label: {
                            Image(systemName: "plus.circle")
                        }
                        .accessibilityLabel(copy.addMember)
                    }
                }
            }

            if let error = editor.error {
                Section { Text(error).foregroundStyle(.red) }
            }

            Section {
                Button(copy.save, action: model.controller.saveGroup)
                    .frame(maxWidth: .infinity)
                if !editor.isNew {
                    Button(copy.delete_, role: .destructive) {
                        model.controller.deleteGroup()
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .navigationTitle(editor.isNew ? copy.newGroup : copy.editGroup)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func appearanceSection(
        icon: String?,
        accent: String?,
        fallback: String,
        copy: NativePaymentHubCopy,
        onIcon: @escaping (String?) -> Void,
        onAccent: @escaping (String?) -> Void
    ) -> some View {
        Section {
            Picker(copy.icon, selection: optionalBinding(icon, onIcon)) {
                Text(copy.none).tag(String?.none)
                ForEach(copy.iconOptions, id: \.id) { option in
                    Label(
                        option.title,
                        systemImage: NativePaymentHubGlyph.symbol(option.id)
                    )
                    .tag(Optional(option.id))
                }
            }
            Picker(copy.accent, selection: optionalBinding(accent, onAccent)) {
                Text(copy.none).tag(String?.none)
                ForEach(copy.accentOptions, id: \.id) { option in
                    Label {
                        Text(option.title)
                    } icon: {
                        Circle()
                            .fill(NativePaymentHubGlyph.color(option.id))
                            .frame(width: 14, height: 14)
                    }
                    .tag(Optional(option.id))
                }
            }
            HStack {
                Spacer()
                NativePaymentHubGlyph(
                    icon: icon,
                    accent: accent,
                    fallback: fallback,
                    size: 52
                )
                Spacer()
            }
        }
    }

    private func binding(
        _ value: String,
        _ update: @escaping (String) -> Void
    ) -> Binding<String> {
        Binding(get: { value }, set: update)
    }

    private func optionalBinding(
        _ value: String?,
        _ update: @escaping (String?) -> Void
    ) -> Binding<String?> {
        Binding(get: { value }, set: update)
    }

    private func boolBinding(
        _ value: Bool,
        _ update: @escaping (Bool) -> Void
    ) -> Binding<Bool> {
        Binding(get: { value }, set: update)
    }
}

private struct NativePaymentHubCanvasRow: Identifiable {
    let id: String
    let tiles: [NativePaymentHubTile]
    let needsPlaceholder: Bool
}

private func canvasRows(_ tiles: [NativePaymentHubTile]) -> [NativePaymentHubCanvasRow] {
    var rows: [NativePaymentHubCanvasRow] = []
    var compact: [NativePaymentHubTile] = []

    func flushCompact() {
        guard !compact.isEmpty else { return }
        rows.append(
            NativePaymentHubCanvasRow(
                id: compact.map(\.item.id).joined(separator: ":"),
                tiles: compact,
                needsPlaceholder: compact.count == 1
            )
        )
        compact = []
    }

    for tile in tiles {
        if tile.size == "wide" {
            flushCompact()
            rows.append(
                NativePaymentHubCanvasRow(
                    id: tile.item.id,
                    tiles: [tile],
                    needsPlaceholder: false
                )
            )
        } else {
            compact.append(tile)
            if compact.count == 2 { flushCompact() }
        }
    }
    flushCompact()
    return rows
}

private struct NativePaymentHubItemRow: View {
    let item: NativePaymentHubItem

    var body: some View {
        HStack(spacing: 12) {
            NativePaymentHubGlyph(item: item, size: 40)
            VStack(alignment: .leading, spacing: 2) {
                Text(item.title)
                    .font(.body.weight(.medium))
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                Text(item.subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
            Spacer(minLength: 8)
            if let amount = item.amount {
                Text(amount)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.tint)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
        }
        .contentShape(Rectangle())
    }
}

private struct NativePaymentHubGlyph: View {
    let icon: String?
    let accent: String?
    let fallback: String
    let size: CGFloat

    init(item: NativePaymentHubItem, size: CGFloat) {
        icon = item.icon
        accent = item.accent
        fallback = item.title
        self.size = size
    }

    init(icon: String?, accent: String?, fallback: String, size: CGFloat) {
        self.icon = icon
        self.accent = accent
        self.fallback = fallback
        self.size = size
    }

    var body: some View {
        ZStack {
            Circle().fill(Self.color(accent).opacity(0.22))
            if let icon {
                Image(systemName: Self.symbol(icon))
                    .font(.system(size: size * 0.46, weight: .semibold))
            } else {
                Text(String(fallback.prefix(1)).uppercased())
                    .font(.system(size: size * 0.38, weight: .semibold))
            }
        }
        .foregroundStyle(Self.color(accent))
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    static func symbol(_ value: String) -> String {
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

    static func color(_ value: String?) -> Color {
        switch value {
        case "orange": return .orange
        case "blue": return .blue
        case "green": return .green
        case "purple": return .purple
        case "pink": return .pink
        case "teal": return .teal
        case "amber": return Color(red: 0.75, green: 0.52, blue: 0.05)
        case "slate": return Color(red: 0.34, green: 0.42, blue: 0.48)
        default: return .secondary
        }
    }
}
