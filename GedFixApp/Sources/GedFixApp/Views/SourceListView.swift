import SwiftUI

struct SourceListView: View {
    private let db = DatabaseService.shared
    @State private var searchText = ""

    private var sources: [GedcomSource] {
        let all = db.fetchSources()
        if searchText.isEmpty { return all }
        return all.filter {
            $0.title.localizedCaseInsensitiveContains(searchText) ||
            $0.author.localizedCaseInsensitiveContains(searchText)
        }
    }

    var body: some View {
        List(sources) { source in
            VStack(alignment: .leading, spacing: 4) {
                Text(source.title.isEmpty ? "(Untitled)" : source.title)
                    .fontWeight(.medium)

                if !source.author.isEmpty {
                    Label(source.author, systemImage: "person.fill")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if !source.publisher.isEmpty {
                    Label(source.publisher, systemImage: "building.2.fill")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Text(source.xref)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
                    .monospaced()
            }
            .padding(.vertical, 4)
        }
        .searchable(text: $searchText, prompt: "Search sources")
    }
}
