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
            case .people:
                PersonListView()
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
