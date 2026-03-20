import SwiftUI

struct SidebarView: View {
    @Environment(AppState.self) private var appState
    private let db = DatabaseService.shared

    var body: some View {
        @Bindable var state = appState

        List(selection: $state.selectedSection) {
            Section("Tree") {
                ForEach(AppState.SidebarSection.allCases) { section in
                    Label {
                        HStack {
                            Text(section.rawValue)
                            Spacer()
                            Text(countBadge(for: section))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .monospacedDigit()
                        }
                    } icon: {
                        Image(systemName: section.icon)
                            .foregroundStyle(iconColor(for: section))
                    }
                    .tag(section)
                }
            }
        }
        .listStyle(.sidebar)
        .navigationSplitViewColumnWidth(min: 200, ideal: 240, max: 320)
    }

    private func countBadge(for section: AppState.SidebarSection) -> String {
        switch section {
        case .overview: return ""
        case .issues: return appState.issueCount > 0 ? "\(appState.issueCount)" : ""
        case .people: return db.personCount > 0 ? "\(db.personCount)" : ""
        case .pedigree: return ""
        case .families: return db.familyCount > 0 ? "\(db.familyCount)" : ""
        case .places: return db.placeCount > 0 ? "\(db.placeCount)" : ""
        case .sources: return db.sourceCount > 0 ? "\(db.sourceCount)" : ""
        }
    }

    private func iconColor(for section: AppState.SidebarSection) -> Color {
        switch section {
        case .overview: return .purple
        case .issues: return appState.issueCount > 0 ? .red : .orange
        case .people: return .blue
        case .pedigree: return .teal
        case .families: return .pink
        case .places: return .green
        case .sources: return .orange
        }
    }
}
