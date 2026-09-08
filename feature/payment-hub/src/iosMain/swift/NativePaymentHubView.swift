import Shared
import SwiftUI

@MainActor
private final class NativePaymentHubModel: ObservableObject {
    @Published private(set) var snapshot: NativePaymentHubSnapshot?
    let controller: NativePaymentHubController
    private var cancel: (() -> Void)?

    init(controller: NativePaymentHubController) {
        self.controller = controller
        cancel = controller.observe { [weak self] value in
            self?.snapshot = value
        }
    }

    deinit { cancel?() }
}

private enum HubRoute: Hashable {
    case variants
    case configure
}

private enum HubGeometry {
    static let gap: CGFloat = 12
    static let gutter: CGFloat = 16

    static func span(_ unit: CGFloat, _ count: Int32) -> CGFloat {
        unit * CGFloat(count) + gap * CGFloat(max(count - 1, 0))
    }
}

/// The app owns payment behavior; the Hub presents independently configured native widgets.
struct NativePaymentHubView: View {
    @StateObject private var model: NativePaymentHubModel
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.appShellTabIsSelected) private var isSelected
    @State private var isVisible = false
    @State private var purchaseSheetHasDismissed = false
    @State private var draggedId: String?
    @State private var dragOffset = CGSize.zero
    @State private var hoveredId: String?
    init(controller: NativePaymentHubController) {
        _model = StateObject(wrappedValue: NativePaymentHubModel(controller: controller))
    }

    var body: some View {
        Group {
            if let state = model.snapshot {
                canvas(state)
            } else {
                ProgressView()
            }
        }
        .sheet(isPresented: editorPresented) {
            HubEditorSheet(model: model)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
                .interactiveDismissDisabled(model.snapshot?.busy == true)
        }
        .sheet(isPresented: purchasePresented, onDismiss: {
            purchaseSheetHasDismissed = true
            completePaymentHandoffIfReady()
        }) {
            HubServicePurchaseSheet(model: model)
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
                .interactiveDismissDisabled(model.snapshot?.purchase?.busy == true)
                .onAppear { purchaseSheetHasDismissed = false }
        }
        .onAppear {
            isVisible = true
            updateActivity()
        }
        .onDisappear {
            isVisible = false
            model.controller.setActive(value: false)
        }
        .onChange(of: scenePhase) { _, _ in updateActivity() }
        .onChange(of: isSelected) { _, _ in updateActivity() }
        .onChange(of: model.snapshot?.servicePaymentReady) { _, _ in
            completePaymentHandoffIfReady()
        }
    }

    private var editorPresented: Binding<Bool> {
        Binding(
            get: { isSelected && (model.snapshot.map { ["gallery", "variants", "configure"].contains($0.screen) } ?? false) },
            set: { if !$0 { model.controller.close() } }
        )
    }

    private var purchasePresented: Binding<Bool> {
        Binding(
            get: { isSelected && model.snapshot?.purchase != nil },
            set: { presented in
                if !presented, model.snapshot?.purchase != nil { model.controller.closePurchase() }
            }
        )
    }

    private func updateActivity() {
        model.controller.setActive(value: isVisible && isSelected && scenePhase == .active)
        completePaymentHandoffIfReady()
    }

    private func completePaymentHandoffIfReady() {
        guard purchaseSheetHasDismissed, isVisible, isSelected, scenePhase == .active,
              model.snapshot?.servicePaymentReady == true else { return }
        purchaseSheetHasDismissed = false
        model.controller.completeServicePaymentHandoff()
    }

    private func canvas(_ state: NativePaymentHubSnapshot) -> some View {
        VStack(spacing: 0) {
            HStack {
                if !state.canvas.tiles.isEmpty {
                    Button(state.canvas.arranging ? state.text.done : state.text.edit) {
                        model.controller.setArranging(value: !state.canvas.arranging)
                    }
                }
                Spacer()
                Button {
                    model.controller.openGallery()
                } label: {
                    Label(state.text.addWidget, systemImage: "plus")
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(.horizontal, HubGeometry.gutter)
            .padding(.vertical, 12)
            .disabled(state.busy)

            if state.hasServiceOrder {
                Button {
                    model.controller.openPendingServiceOrder()
                } label: {
                    Label(state.text.service.orderBanner, systemImage: "shippingbox")
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, HubGeometry.gutter)
                .padding(.bottom, 12)
            }

            if let error = state.error, state.screen == "hub" {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, HubGeometry.gutter)
                    .padding(.bottom, 12)
            }

            if state.canvas.tiles.isEmpty {
                ContentUnavailableView {
                    Label(state.text.emptyTitle, systemImage: "square.grid.2x2")
                } description: {
                    Text(state.text.emptyBody)
                } actions: {
                    Button(state.text.addWidget) { model.controller.openGallery() }
                        .buttonStyle(.borderedProminent)
                }
            } else {
                GeometryReader { viewport in
                    let columns = viewport.size.width >= 700 ? 4 : 2
                    let width = viewport.size.width - HubGeometry.gutter * 2
                    let unit = (width - HubGeometry.gap * CGFloat(columns - 1)) / CGFloat(columns)
                    ScrollView {
                        widgetGrid(state, unit: unit)
                            .padding(.horizontal, HubGeometry.gutter)
                            .padding(.bottom, 24)
                            .disabled(state.busy)
                    }
                    .refreshable { model.controller.refreshContent() }
                    .onAppear { model.controller.setCanvasColumns(value: Int32(columns)) }
                    .onChange(of: columns) { _, value in
                        model.controller.setCanvasColumns(value: Int32(value))
                    }
                }
            }
        }
        .background(Color(uiColor: .systemGroupedBackground))
    }

    private func widgetGrid(_ state: NativePaymentHubSnapshot, unit: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(state.canvas.tiles.enumerated()), id: \.element.id) { index, tile in
                WidgetContent(
                    tile: tile,
                    copy: state.text,
                    interactive: !state.canvas.arranging,
                    pay: { model.controller.pay(actionId: $0) },
                    openService: { model.controller.openService(widgetId: tile.id, offerId: $0) }
                )
                .frame(
                    width: HubGeometry.span(unit, tile.columns),
                    height: HubGeometry.span(unit, tile.rows)
                )
                .overlay(alignment: .topTrailing) {
                    if state.canvas.arranging {
                        Menu {
                            widgetActions(tile, index: index, state: state)
                        } label: {
                            Image(systemName: "ellipsis")
                                .frame(width: 36, height: 36)
                                .background(.regularMaterial, in: Circle())
                        }
                        .accessibilityLabel(state.text.widgetOptions)
                        .padding(6)
                    }
                }
                .overlay {
                    if hoveredId == tile.id {
                        RoundedRectangle(cornerRadius: 24)
                            .strokeBorder(Color.accentColor, lineWidth: 3)
                    }
                }
                .offset(
                    x: (unit + HubGeometry.gap) * CGFloat(tile.column),
                    y: (unit + HubGeometry.gap) * CGFloat(tile.row)
                )
                .offset(draggedId == tile.id ? dragOffset : .zero)
                .scaleEffect(draggedId == tile.id ? 1.025 : 1)
                .shadow(color: .black.opacity(draggedId == tile.id ? 0.18 : 0), radius: 12, y: 6)
                .zIndex(draggedId == tile.id ? 1 : 0)
                .animation(.snappy(duration: 0.22), value: tile.row)
                .animation(.snappy(duration: 0.22), value: tile.column)
                .contextMenu { widgetActions(tile, index: index, state: state) }
                .accessibilityAction(named: Text(state.text.editWidget)) {
                    model.controller.editWidget(id: tile.id)
                }
                .accessibilityAction(named: Text(state.text.moveUp)) {
                    model.controller.moveWidgetBy(id: tile.id, offset: -1)
                }
                .accessibilityAction(named: Text(state.text.moveDown)) {
                    model.controller.moveWidgetBy(id: tile.id, offset: 1)
                }
                .accessibilityAction(named: Text(state.text.remove)) {
                    model.controller.removeWidget(id: tile.id)
                }
                .gesture(
                    dragGesture(tile, state: state, unit: unit),
                    including: state.canvas.arranging ? .all : .none
                )
            }
        }
        .frame(
            maxWidth: .infinity,
            minHeight: HubGeometry.span(unit, state.canvas.rows),
            alignment: .topLeading
        )
    }

    @ViewBuilder
    private func widgetActions(_ tile: NativeHubTile, index: Int, state: NativePaymentHubSnapshot) -> some View {
        Button(state.text.editWidget, systemImage: "slider.horizontal.3") {
            model.controller.editWidget(id: tile.id)
        }
        if tile.sizes.count > 1 {
            Menu(state.text.chooseLayout) {
                ForEach(tile.sizes, id: \.id) { size in
                    Button {
                        model.controller.resizeWidget(id: tile.id, variantId: size.id)
                    } label: {
                        if size.id == tile.variantId {
                            Label(size.title, systemImage: "checkmark")
                        } else {
                            Text(size.title)
                        }
                    }
                }
            }
        }
        Button(state.text.moveUp, systemImage: "arrow.up") {
            model.controller.moveWidgetBy(id: tile.id, offset: -1)
        }
        .disabled(index == 0)
        Button(state.text.moveDown, systemImage: "arrow.down") {
            model.controller.moveWidgetBy(id: tile.id, offset: 1)
        }
        .disabled(index == state.canvas.tiles.count - 1)
        Divider()
        Button(state.text.remove, systemImage: "minus.circle", role: .destructive) {
            model.controller.removeWidget(id: tile.id)
        }
    }

    private func dragGesture(_ tile: NativeHubTile, state: NativePaymentHubSnapshot, unit: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                draggedId = tile.id
                dragOffset = value.translation
                let point = CGPoint(
                    x: CGFloat(tile.column) * (unit + HubGeometry.gap) + value.startLocation.x + value.translation.width,
                    y: CGFloat(tile.row) * (unit + HubGeometry.gap) + value.startLocation.y + value.translation.height
                )
                hoveredId = state.canvas.tiles.first { other in
                    other.id != tile.id && CGRect(
                        x: CGFloat(other.column) * (unit + HubGeometry.gap),
                        y: CGFloat(other.row) * (unit + HubGeometry.gap),
                        width: HubGeometry.span(unit, other.columns),
                        height: HubGeometry.span(unit, other.rows)
                    ).contains(point)
                }?.id
            }
            .onEnded { _ in
                if let target = hoveredId {
                    model.controller.moveWidget(id: tile.id, onto: target)
                }
                draggedId = nil
                dragOffset = .zero
                hoveredId = nil
            }
    }
}

private struct HubEditorSheet: View {
    @ObservedObject var model: NativePaymentHubModel

    var body: some View {
        NavigationStack(path: navigationPath) {
            Group {
                if let state = model.snapshot {
                    gallery(state)
                        .navigationDestination(for: HubRoute.self) { route in
                            destination(route)
                        }
                } else {
                    ProgressView()
                }
            }
        }
    }

    private var routes: [HubRoute] {
        guard let state = model.snapshot else { return [] }
        switch state.screen {
        case "variants": return [.variants]
        case "configure": return [.variants, .configure]
        default: return []
        }
    }

    private var navigationPath: Binding<[HubRoute]> {
        Binding(
            get: { routes },
            set: { next in
                guard model.snapshot?.busy != true else { return }
                for _ in 0..<max(0, routes.count - next.count) {
                    model.controller.back()
                }
            }
        )
    }

    @ViewBuilder
    private func destination(_ route: HubRoute) -> some View {
        if let state = model.snapshot {
            switch route {
            case .variants:
                if let definition = state.selectedDefinition {
                    VariantGallery(state: state, definition: definition, controller: model.controller)
                } else {
                    unavailable(state)
                }
            case .configure:
                if let editor = state.editor, let definition = state.selectedDefinition {
                    WidgetConfigurationView(
                        model: model,
                        editor: editor,
                        definition: definition
                    )
                } else {
                    unavailable(state)
                }
            }
        }
    }

    private func unavailable(_ state: NativePaymentHubSnapshot) -> some View {
        ContentUnavailableView(state.text.unavailable, systemImage: "square.dashed")
            .toolbar { dismissButton(state.text.cancel) }
    }

    private func gallery(_ state: NativePaymentHubSnapshot) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(state.text.galleryBody)
                    .font(.title3.weight(.medium))
                    .foregroundStyle(.secondary)
                    .padding(.top, 8)
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 145), spacing: 16)], spacing: 24) {
                    ForEach(state.gallery, id: \.id) { definition in
                        Button {
                            model.controller.selectDefinition(id: definition.id)
                        } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                GalleryIllustration(kind: definition.kind, symbol: definition.symbol)
                                    .frame(height: 126)
                                    .frame(maxWidth: .infinity)
                                    .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 22))
                                Text(definition.title)
                                    .font(.headline)
                                    .foregroundStyle(.primary)
                                Text(definition.detail)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(3)
                                    .multilineTextAlignment(.leading)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.plain)
                    }
                }
                if state.catalogLoading {
                    ProgressView(state.text.loading).frame(maxWidth: .infinity)
                }
                if state.catalogUnavailable {
                    VStack(spacing: 8) {
                        Text(state.text.catalogUnavailable).font(.footnote).foregroundStyle(.secondary)
                        Button(state.text.retry) { model.controller.refreshCatalog() }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(20)
            .frame(maxWidth: 700)
            .frame(maxWidth: .infinity)
        }
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle(state.text.galleryTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { dismissButton(state.text.cancel) }
    }

    @ToolbarContentBuilder
    private func dismissButton(_ label: String) -> some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Button { model.controller.close() } label: { Image(systemName: "xmark") }
                .accessibilityLabel(label)
                .disabled(model.snapshot?.busy == true)
        }
    }
}

private struct GalleryIllustration: View {
    let kind: String
    let symbol: String

    var body: some View {
        Group {
            if kind == "contacts" {
                HStack(spacing: -10) {
                    HubAvatar(initials: "", size: 54, seed: 0)
                    HubAvatar(initials: "", size: 64, seed: 1)
                    HubAvatar(initials: "", size: 54, seed: 2)
                }
            } else {
                Image(systemName: symbol)
                    .font(.system(size: 42, weight: .medium))
                    .foregroundStyle(Color.accentColor)
                    .frame(width: 82, height: 82)
                    .background(Color.accentColor.opacity(0.10), in: RoundedRectangle(cornerRadius: 24))
            }
        }
        .accessibilityHidden(true)
    }
}

private struct VariantGallery: View {
    let state: NativePaymentHubSnapshot
    let definition: NativeHubDefinition
    let controller: NativePaymentHubController

    private var selected: NativeHubVariant? {
        definition.variants.first { $0.id == state.editor?.variantId }
    }

    var body: some View {
        VStack(spacing: 12) {
            Text(definition.title).font(.largeTitle.bold()).padding(.top, 20)
            Text(definition.detail)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            TabView(selection: Binding(
                get: { state.editor?.variantId ?? definition.variants.first?.id ?? "" },
                set: { controller.selectVariant(id: $0) }
            )) {
                ForEach(definition.variants, id: \.id) { variant in
                    GeometryReader { proxy in
                        let aspect = CGFloat(variant.columns) / CGFloat(variant.rows)
                        let width = min(proxy.size.width - 40, variant.columns == 1 ? 180 : 360, proxy.size.height * aspect)
                        WidgetContent(tile: variant.preview, copy: state.text, interactive: false, pay: { _ in })
                            .frame(width: width, height: width * CGFloat(variant.rows) / CGFloat(variant.columns))
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .padding(.bottom, 25)
                    .tag(variant.id)
                    .accessibilityLabel(variant.title)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: definition.variants.count > 1 ? .always : .never))
            .indexViewStyle(.page(backgroundDisplayMode: .always))
            .frame(minHeight: 220, maxHeight: 400)
            if let selected {
                Text(selected.title).font(.headline)
                Text(selected.detail)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }
            Spacer(minLength: 0)
        }
        .safeAreaInset(edge: .bottom) {
            HubPrimaryButton(title: state.text.continueTitle, busy: false) {
                controller.configureSelected()
            }
        }
        .navigationTitle(definition.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct WidgetConfigurationView: View {
    @ObservedObject var model: NativePaymentHubModel
    let editor: NativeHubEditor
    let definition: NativeHubDefinition
    @State private var showingNewContact = false
    @State private var pendingContactDeletion: NativeHubContact?

    private var controller: NativePaymentHubController { model.controller }
    private var isPersonal: Bool { editor.kind == "contacts" || editor.kind == "shortcut" }

    var body: some View {
        Group {
            if let state = model.snapshot {
                Form {
                    Section {
                        if definition.variants.count > 1 {
                            Picker(state.text.chooseLayout, selection: Binding(
                                get: { editor.variantId },
                                set: { controller.selectVariant(id: $0) }
                            )) {
                                ForEach(definition.variants, id: \.id) { variant in
                                    Text(variant.title).tag(variant.id)
                                }
                            }
                        }
                        TextField(state.text.widgetName, text: Binding(
                            get: { editor.title },
                            set: { controller.updateTitle(value: $0) }
                        ))
                    }
                    if isPersonal {
                        contacts(state)
                    } else if editor.kind == "favorites" || editor.kind == "recents" {
                        Section {
                            Text(definition.detail)
                            Text(state.text.automaticHint).font(.footnote).foregroundStyle(.secondary)
                        }
                    }
                    if editor.kind == "shortcut" {
                        Section(state.text.amount) {
                            HStack {
                                TextField(state.text.amount, text: Binding(
                                    get: { editor.amount },
                                    set: { controller.updateAmount(value: $0) }
                                ))
                                .keyboardType(.decimalPad)
                                Picker(editor.currencyCode, selection: Binding(
                                    get: { editor.currencyCode },
                                    set: { controller.selectCurrency(code: $0) }
                                )) {
                                    ForEach(editor.currencyCodes, id: \.self) { code in
                                        Text(code).tag(code)
                                    }
                                }
                                .labelsHidden()
                            }
                            TextField(state.text.comment, text: Binding(
                                get: { editor.comment },
                                set: { controller.updateComment(value: $0) }
                            ))
                        }
                    }
                    if !editor.fields.isEmpty {
                        Section {
                            ForEach(editor.fields, id: \.key) { field in
                                configurationField(field)
                            }
                        }
                    }
                    if let error = state.error, !showingNewContact {
                        Section { Text(error).foregroundStyle(.red) }
                    }
                }
                .disabled(state.busy)
                .safeAreaInset(edge: .bottom) {
                    HubPrimaryButton(
                        title: editor.isEditing ? state.text.save : state.text.addWidget,
                        busy: state.busy,
                        action: controller.saveWidget
                    )
                }
                .navigationTitle(editor.isEditing ? state.text.editWidget : definition.title)
                .navigationBarTitleDisplayMode(.inline)
                .navigationBarBackButtonHidden(state.busy)
                .sheet(isPresented: $showingNewContact) {
                    AddHubContactSheet(model: model)
                        .interactiveDismissDisabled(model.snapshot?.busy == true)
                }
                .alert(state.text.deleteContactTitle, isPresented: deletionPresented, presenting: pendingContactDeletion) { contact in
                    Button(state.text.deleteContact, role: .destructive) {
                        controller.deleteContact(id: contact.id)
                        pendingContactDeletion = nil
                    }
                    Button(state.text.cancel, role: .cancel) { pendingContactDeletion = nil }
                } message: { contact in
                    Text(contact.title + "\n" + contact.address + "\n\n" + state.text.deleteContactBody)
                }
            }
        }
    }

    @ViewBuilder
    private func contacts(_ state: NativePaymentHubSnapshot) -> some View {
        Section {
            Button { showingNewContact = true } label: {
                Label(state.text.addContact, systemImage: "person.crop.circle.badge.plus")
            }
        }
        Section {
            TextField(state.text.search, text: Binding(
                get: { state.query },
                set: { controller.updateQuery(value: $0) }
            ))
            ForEach(Array(editor.selectedContacts.enumerated()), id: \.element.id) { index, contact in
                contactRow(contact, selected: true, copy: state.text)
                    .contextMenu {
                        Button(state.text.moveUp, systemImage: "arrow.up") {
                            controller.moveContact(id: contact.id, offset: -1)
                        }
                        .disabled(index == 0)
                        Button(state.text.moveDown, systemImage: "arrow.down") {
                            controller.moveContact(id: contact.id, offset: 1)
                        }
                        .disabled(index == editor.selectedContacts.count - 1)
                        Button(state.text.deleteContact, systemImage: "trash", role: .destructive) {
                            pendingContactDeletion = contact
                        }
                    }
            }
            ForEach(editor.availableContacts, id: \.id) { contact in
                contactRow(contact, selected: false, copy: state.text)
            }
            if editor.selectedContacts.isEmpty && editor.availableContacts.isEmpty {
                Text(state.contactsEmpty ? state.text.noContacts : state.text.noMatches)
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text(editor.selectionTitle)
        } footer: {
            Text(editor.selectionCount)
        }
    }

    private func contactRow(_ contact: NativeHubContact, selected: Bool, copy: NativePaymentHubCopy) -> some View {
        Button { controller.toggleContact(id: contact.id) } label: {
            HStack(spacing: 12) {
                HubAvatar(initials: contact.initials, size: 38)
                VStack(alignment: .leading, spacing: 3) {
                    Text(contact.title).foregroundStyle(.primary)
                    Text(contact.address).font(.caption).foregroundStyle(.secondary)
                }
                Spacer(minLength: 4)
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(selected ? Color.accentColor : Color.secondary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button { pendingContactDeletion = contact } label: {
                Label(copy.deleteContact, systemImage: "trash")
            }
            .tint(.red)
        }
    }

    @ViewBuilder
    private func configurationField(_ field: NativeHubField) -> some View {
        let binding = Binding(
            get: { field.value },
            set: { controller.updateConfiguration(key: field.key, value: $0) }
        )
        if field.type == "choice" {
            Picker(field.label, selection: binding) {
                if !field.required || field.value.isEmpty { Text("—").tag("") }
                ForEach(field.options, id: \.id) { option in
                    Text(option.label).tag(option.id)
                }
            }
        } else {
            TextField(field.label, text: binding)
                .keyboardType(field.type == "phone" ? .phonePad : .default)
                .textContentType(field.type == "phone" ? .telephoneNumber : nil)
                .textInputAutocapitalization(field.type == "phone" ? .never : .sentences)
                .autocorrectionDisabled(field.type == "phone")
        }
    }

    private var deletionPresented: Binding<Bool> {
        Binding(
            get: { pendingContactDeletion != nil },
            set: { if !$0 { pendingContactDeletion = nil } }
        )
    }
}

private struct AddHubContactSheet: View {
    @ObservedObject var model: NativePaymentHubModel
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var address = ""
    @State private var attemptedSave = false
    @State private var initialSerial: Int32

    init(model: NativePaymentHubModel) {
        self.model = model
        _initialSerial = State(initialValue: model.snapshot?.contactSavedSerial ?? 0)
    }

    var body: some View {
        NavigationStack {
            if let state = model.snapshot {
                Form {
                    Section {
                        TextField(state.text.name, text: $title)
                            .textContentType(.name)
                        TextField(state.text.address, text: $address)
                            .textContentType(.emailAddress)
                            .keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                    if attemptedSave, let error = state.error {
                        Section { Text(error).foregroundStyle(.red) }
                    }
                }
                .disabled(state.busy)
                .navigationTitle(state.text.addContact)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(state.text.cancel) { dismiss() }
                            .disabled(state.busy)
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(state.text.saveContact) {
                            attemptedSave = true
                            model.controller.addContact(title: title, address: address)
                        }
                        .disabled(state.busy)
                    }
                }
                .onChange(of: state.contactSavedSerial) { _, value in
                    if value != initialSerial { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

private struct HubPrimaryButton: View {
    let title: String
    let busy: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if busy { ProgressView().tint(.white) }
                Text(title).fontWeight(.semibold)
            }
            .frame(maxWidth: .infinity, minHeight: 36)
        }
        .buttonStyle(.borderedProminent)
        .disabled(busy)
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(.bar)
    }
}

private struct WidgetContent: View {
    let tile: NativeHubTile
    let copy: NativePaymentHubCopy
    let interactive: Bool
    let pay: (String) -> Void
    var openService: (String?) -> Void = { _ in }

    var body: some View {
        GeometryReader { proxy in
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 5) {
                    if tile.kind == "shortcut" { Image(systemName: "bolt.fill") }
                    Text(tile.title).lineLimit(1)
                    Spacer(minLength: 0)
                }
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                if tile.kind == "metric" {
                    metric
                } else if tile.kind == "service" {
                    HubServiceWidgetContent(
                        tile: tile,
                        copy: copy,
                        interactive: interactive,
                        open: openService
                    )
                } else if tile.people.isEmpty {
                    Text(tile.emptyText)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                        .multilineTextAlignment(.center)
                } else {
                    people(size: proxy.size)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 24))
        }
        .accessibilityElement(children: .contain)
    }

    @ViewBuilder
    private var metric: some View {
        if tile.metric == nil && tile.loading {
            ProgressView(copy.loading).frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if tile.metric == nil && tile.unavailable {
            Text(copy.unavailable).foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Spacer(minLength: 0)
                HStack(alignment: .firstTextBaseline, spacing: 5) {
                    Text(tile.metric?.value ?? "—")
                        .font(.system(.title, design: .rounded, weight: .semibold))
                        .minimumScaleFactor(0.5)
                        .lineLimit(1)
                    if let metric = tile.metric, !metric.unit.isEmpty {
                        Text(metric.unit).font(.caption).foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
                if let metric = tile.metric {
                    if !metric.label.isEmpty {
                        Text(metric.label).font(.caption).lineLimit(tile.rows > 1 ? 2 : 1)
                    }
                    if let asOf = metric.asOf, let date = metricDate(asOf) {
                        Text(date, format: .relative(presentation: .named))
                            .font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                    }
                    if tile.loading {
                        ProgressView().controlSize(.mini).accessibilityLabel(copy.loading)
                    } else if tile.unavailable {
                        Label(copy.unavailable, systemImage: "exclamationmark.circle")
                            .font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func metricDate(_ value: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: value) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)
    }

    @ViewBuilder
    private func people(size: CGSize) -> some View {
        let columns = tile.columns == 1 ? 1 : (tile.rows >= 2 ? 3 : 4)
        let singleHeightAllowance: CGFloat = tile.people.first?.amount == nil ? 82 : 108
        let avatarSize = min(
            tile.columns == 1 ? 66 : (tile.rows >= 2 ? 72 : 50),
            (size.width - 40) / CGFloat(columns) - 10,
            tile.columns == 1 ? size.height - singleHeightAllowance : size.height
        )
        if tile.columns == 1 {
            if let person = tile.people.first {
                personButton(person, avatarSize: max(32, avatarSize))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        } else {
            LazyVGrid(
                columns: Array(repeating: GridItem(.flexible(), spacing: 8), count: columns),
                spacing: tile.rows >= 2 ? 20 : 8
            ) {
                ForEach(Array(tile.people.enumerated()), id: \.element.id) { index, person in
                    personButton(person, avatarSize: max(28, avatarSize), seed: index)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        }
    }

    private func personButton(_ person: NativeHubPerson, avatarSize: CGFloat, seed: Int = 0) -> some View {
        Button {
            if !person.actionId.isEmpty { pay(person.actionId) }
        } label: {
            VStack(spacing: 6) {
                HubAvatar(initials: person.initials, size: avatarSize, seed: seed)
                Text(person.title)
                    .font(tile.columns == 1 ? .subheadline.weight(.semibold) : .caption.weight(.medium))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                if let amount = person.amount {
                    Text(amount)
                        .font(tile.kind == "shortcut" ? .headline : .caption2)
                        .foregroundStyle(Color.accentColor)
                        .monospacedDigit()
                        .lineLimit(1)
                        .minimumScaleFactor(0.65)
                }
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .allowsHitTesting(interactive)
        .accessibilityLabel(person.title + (person.amount.map { ", " + $0 } ?? ""))
    }
}

private struct HubAvatar: View {
    let initials: String
    let size: CGFloat
    var seed: Int = 0

    private var color: Color {
        let colors: [Color] = [.blue, .purple, .pink, .teal, .indigo, .orange]
        let value = initials.unicodeScalars.reduce(seed) { $0 + Int($1.value) }
        return colors[value % colors.count]
    }

    var body: some View {
        ZStack {
            Circle().fill(color.gradient)
            if initials.isEmpty {
                Image(systemName: "person.fill").font(.system(size: size * 0.45, weight: .medium))
            } else {
                Text(initials).font(.system(size: size * 0.36, weight: .semibold, design: .rounded))
            }
        }
        .foregroundStyle(.white)
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

private struct HubServiceWidgetContent: View {
    let tile: NativeHubTile
    let copy: NativePaymentHubCopy
    let interactive: Bool
    let open: (String?) -> Void

    private var isPackages: Bool { tile.template_ == "service-packages" }
    private var isPreview: Bool { tile.id.hasPrefix("preview:") }
    private var hasOffers: Bool {
        tile.service?.offers.contains { $0.kind == (isPackages ? "package" : "topup") } == true
    }

    var body: some View {
        Group {
            if tile.service == nil && tile.loading {
                ProgressView(copy.loading).frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if tile.unavailable || (!isPreview && !hasOffers) {
                Text(copy.unavailable).font(.footnote).foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if isPackages {
                packages
            } else {
                Button { open(nil) } label: {
                    VStack(alignment: .leading, spacing: 8) {
                        Image(systemName: "iphone.gen3.radiowaves.left.and.right")
                            .font(.title2).foregroundStyle(Color.accentColor)
                        if !tile.servicePhone.isEmpty {
                            Text(tile.servicePhone).font(.caption).foregroundStyle(.secondary)
                                .lineLimit(1).minimumScaleFactor(0.8).privacySensitive()
                        }
                        Text(copy.service.topup).font(.headline).foregroundStyle(.primary)
                            .lineLimit(1).minimumScaleFactor(0.8)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .allowsHitTesting(interactive && !isPreview && !tile.unavailable && hasOffers)
    }

    private var packages: some View {
        VStack(alignment: .leading, spacing: 6) {
            if !tile.servicePhone.isEmpty {
                Text(tile.servicePhone).font(.caption).foregroundStyle(.secondary)
                    .lineLimit(1).privacySensitive()
            }
            if let service = tile.service {
                ForEach(Array(service.offers.filter { $0.kind == "package" }.prefix(tile.rows > 1 ? 3 : 1)), id: \.id) { offer in
                    Button { open(offer.id) } label: {
                        HStack(spacing: 10) {
                            Text(offer.title).font(.subheadline.weight(.medium))
                                .foregroundStyle(.primary).lineLimit(tile.rows > 1 ? 2 : 1)
                            Spacer(minLength: 0)
                            if let amount = offer.amountText {
                                Text(amount).font(.caption.weight(.semibold))
                                    .foregroundStyle(Color.accentColor).lineLimit(1)
                            }
                            Image(systemName: "chevron.right").font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, tile.rows > 1 ? 10 : 3)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            } else {
                Image(systemName: "cellularbars").font(.title).foregroundStyle(Color.accentColor)
                Text(copy.service.packagesBody).font(.footnote).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            Button { open(nil) } label: {
                HStack {
                    Text(copy.service.packages).font(.subheadline.weight(.semibold))
                    Spacer()
                    Image(systemName: "arrow.right").font(.caption)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .foregroundStyle(Color.accentColor)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

private struct HubServicePurchaseSheet: View {
    @ObservedObject var model: NativePaymentHubModel

    var body: some View {
        NavigationStack {
            if let state = model.snapshot, let purchase = state.purchase {
                Form {
                    if let order = purchase.order {
                        orderSummary(order, copy: state.text.service)
                    } else if purchase.offers.isEmpty && state.hasServiceOrder {
                        Section {
                            Text(state.text.service.unknownHint)
                            if purchase.busy { ProgressView(state.text.loading) }
                        }
                    } else {
                        purchaseInputs(purchase, copy: state.text.service)
                    }
                    if let error = purchase.error {
                        Section { Text(error).foregroundStyle(.red) }
                    }
                }
                .disabled(purchase.busy)
                .scrollDismissesKeyboard(.interactively)
                .safeAreaInset(edge: .bottom) {
                    purchaseAction(purchase, state: state)
                }
                .navigationTitle(purchase.title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(state.text.done) { model.controller.closePurchase() }
                            .disabled(purchase.busy)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func purchaseInputs(_ purchase: NativeHubServicePurchase, copy: NativeHubServiceCopy) -> some View {
        Section(copy.phone) {
            TextField(copy.phone, text: Binding(
                get: { purchase.phone },
                set: { model.controller.updateServicePhone(value: $0) }
            ))
            .textContentType(.telephoneNumber)
            .keyboardType(.phonePad)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .privacySensitive()
        }
        Section(copy.chooseOffer) {
            ForEach(purchase.offers, id: \.id) { offer in
                Button { model.controller.selectServiceOffer(id: offer.id) } label: {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: purchase.selectedOfferId == offer.id ? "largecircle.fill.circle" : "circle")
                            .foregroundStyle(purchase.selectedOfferId == offer.id ? Color.accentColor : .secondary)
                            .padding(.top, 3)
                        VStack(alignment: .leading, spacing: 5) {
                            Text(offer.title).font(.body.weight(.medium)).foregroundStyle(.primary)
                            if let detail = offer.detail, !detail.isEmpty {
                                Text(detail).font(.footnote).foregroundStyle(.secondary)
                            }
                            if let amount = offer.amountText {
                                Text(amount).font(.subheadline).foregroundStyle(Color.accentColor)
                            } else if let range = offer.rangeText {
                                Text(range).font(.footnote).foregroundStyle(.secondary)
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(purchase.selectedOfferId == offer.id ? .isSelected : [])
            }
        }
        if let selected = purchase.selectedOffer, selected.requiresAmount {
            Section {
                TextField(purchase.amountLabel, text: Binding(
                    get: { purchase.amount },
                    set: { model.controller.updateServiceAmount(value: $0) }
                ))
                .keyboardType(.decimalPad)
            } header: {
                Text(purchase.amountLabel)
            } footer: {
                if let range = selected.rangeText { Text(range) }
            }
        }
    }

    @ViewBuilder
    private func orderSummary(_ order: NativeHubServiceOrder, copy: NativeHubServiceCopy) -> some View {
        Section {
            LabeledContent(copy.recipient, value: order.phone).privacySensitive()
            LabeledContent(copy.item) {
                VStack(alignment: .trailing, spacing: 4) {
                    Text(order.item)
                    if let amount = order.amountText { Text(amount).font(.footnote) }
                }
                .multilineTextAlignment(.trailing)
            }
            if let price = order.lightningPrice {
                LabeledContent(copy.lightningPrice) {
                    Text(price).fontWeight(.semibold).monospacedDigit()
                }
            }
            if order.state == "awaiting_payment", let rawDate = order.expiresAt,
               let date = serviceDate(rawDate) {
                Text(copy.quoteExpires.replacingOccurrences(
                    of: "%1$@", with: date.formatted(date: .abbreviated, time: .shortened)
                ))
                .font(.footnote).foregroundStyle(.secondary)
            }
        }
        Section {
            LabeledContent(copy.orderStatus) {
                Label(order.status, systemImage: statusSymbol(order.state))
            }
            LabeledContent(copy.paymentStatus, value: order.paymentStatus)
            LabeledContent(copy.fulfillmentStatus, value: order.fulfillmentStatus)
        } footer: {
            Text(order.unconfirmed ? copy.unknownHint : copy.paymentHint)
        }
        Section(copy.orderReference) {
            Text(order.id).font(.footnote.monospaced()).textSelection(.enabled)
        }
    }

    @ViewBuilder
    private func purchaseAction(_ purchase: NativeHubServicePurchase, state: NativePaymentHubSnapshot) -> some View {
        if purchase.order == nil && !purchase.offers.isEmpty {
            HubPrimaryButton(title: state.text.service.review, busy: purchase.busy) {
                model.controller.prepareServiceOrder()
            }
            .disabled(purchase.selectedOfferId == nil || purchase.phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        } else if purchase.canPay {
            HubPrimaryButton(title: state.text.service.pay, busy: purchase.busy) {
                model.controller.payServiceOrder()
            }
        } else {
            HubPrimaryButton(title: state.text.service.checkStatus, busy: purchase.busy) {
                model.controller.refreshServiceOrder()
            }
        }
    }

    private func statusSymbol(_ state: String) -> String {
        switch state {
        case "delivered": return "checkmark.circle.fill"
        case "failed", "expired": return "exclamationmark.circle"
        case "unknown": return "questionmark.circle"
        default: return "clock"
        }
    }

    private func serviceDate(_ value: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: value) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)
    }
}
