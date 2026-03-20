import SwiftUI

@main
struct GedFixApp: App {
    @State private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(appState)
                .frame(minWidth: 900, minHeight: 600)
                .onAppear {
                    do {
                        try DatabaseService.shared.openInMemory()
                    } catch {
                        print("Failed to open database: \(error)")
                    }
                }
        }
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("Import GEDCOM...") {
                    appState.showImportPanel = true
                }
                .keyboardShortcut("o")
            }
        }
        .defaultSize(width: 1200, height: 800)
    }
}

@MainActor
@Observable
final class AppState {
    var showImportPanel = false
    var isImporting = false
    var importProgress: String = ""
    var importedFileName: String = ""
    var selectedSection: SidebarSection = .people
    var selectedPersonXref: String?
    var navigationPath: [NavigationItem] = []

    enum SidebarSection: String, CaseIterable, Identifiable {
        case overview = "Overview"
        case people = "People"
        case families = "Families"
        case places = "Places"
        case sources = "Sources"

        var id: String { rawValue }

        var icon: String {
            switch self {
            case .overview: return "chart.pie.fill"
            case .people: return "person.3.fill"
            case .families: return "house.fill"
            case .places: return "mappin.and.ellipse"
            case .sources: return "book.closed.fill"
            }
        }
    }

    enum NavigationItem: Hashable {
        case person(String)  // xref
        case family(String)
    }

    func importGEDCOM(url: URL) {
        isImporting = true
        importProgress = "Parsing GEDCOM file..."
        importedFileName = url.lastPathComponent

        Task {
            do {
                let result = try GedcomParser.parse(fileURL: url)
                importProgress = "Importing \(result.persons.count) persons..."

                try DatabaseService.shared.importParseResult(result)

                importProgress = "Done! \(result.persons.count) persons, \(result.families.count) families"
                isImporting = false
                selectedSection = .people
            } catch {
                importProgress = "Error: \(error.localizedDescription)"
                isImporting = false
            }
        }
    }
}
