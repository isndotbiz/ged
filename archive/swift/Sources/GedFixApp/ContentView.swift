import SwiftUI

struct ContentView: View {
    @Environment(AppState.self) private var appState
    private let db = DatabaseService.shared

    var body: some View {
        @Bindable var state = appState

        NavigationSplitView {
            SidebarView()
        } detail: {
            switch appState.selectedSection {
            case .overview:
                OverviewView()
            case .issues:
                IssuesView()
            case .people:
                PersonListView()
            case .pedigree:
                PedigreeChartView(rootXref: appState.selectedPersonXref)
            case .families:
                FamilyListView()
            case .places:
                PlaceListView()
            case .sources:
                SourceListView()
            }
        }
        .navigationTitle(appState.importedFileName.isEmpty ? "GedFix" : appState.importedFileName)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    appState.showImportPanel = true
                } label: {
                    Label("Import", systemImage: "square.and.arrow.down")
                }
            }
            ToolbarItem(placement: .automatic) {
                if CLIBridge.isAvailable {
                    Label("CLI Ready", systemImage: "terminal.fill")
                        .foregroundStyle(.green)
                        .help("gedfix CLI is available")
                } else {
                    Label("CLI Missing", systemImage: "terminal.fill")
                        .foregroundStyle(.secondary)
                        .help("Install gedfix: pip install -e /path/to/ged")
                }
            }
        }
        .sheet(isPresented: $state.showImportPanel) {
            ImportView()
        }
        .overlay {
            if appState.isImporting {
                ImportProgressOverlay()
            }
        }
        .onChange(of: appState.showExportPanel) { _, show in
            if show { exportGEDCOM(filterLiving: false) }
        }
        .onChange(of: appState.showExportPanelFiltered) { _, show in
            if show { exportGEDCOM(filterLiving: true) }
        }
    }
}

extension ContentView {
    func exportGEDCOM(filterLiving: Bool) {
        let panel = NSSavePanel()
        panel.title = "Export GEDCOM"
        panel.allowedContentTypes = [.plainText]
        panel.nameFieldStringValue = "export.ged"
        panel.canCreateDirectories = true

        panel.begin { response in
            defer {
                Task { @MainActor in
                    appState.showExportPanel = false
                    appState.showExportPanelFiltered = false
                }
            }
            guard response == .OK, let url = panel.url else { return }

            Task { @MainActor in
                var exporter = GedcomExporter()
                exporter.filterLiving = filterLiving
                let content = exporter.export()
                do {
                    try content.write(to: url, atomically: true, encoding: .utf8)
                } catch {
                    print("Export error: \(error)")
                }
            }
        }
    }
}

struct ImportProgressOverlay: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        ZStack {
            Color.black.opacity(0.3)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                ProgressView()
                    .scaleEffect(1.5)
                Text(appState.importProgress)
                    .font(.headline)
                Text(appState.importedFileName)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(40)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        }
    }
}
