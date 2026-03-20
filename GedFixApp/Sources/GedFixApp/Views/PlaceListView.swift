import SwiftUI

struct PlaceListView: View {
    private let db = DatabaseService.shared
    @State private var searchText = ""

    private var places: [GedcomPlace] {
        let all = db.fetchPlaces()
        if searchText.isEmpty { return all }
        return all.filter { $0.name.localizedCaseInsensitiveContains(searchText) }
    }

    var body: some View {
        List(places) { place in
            HStack(spacing: 12) {
                Image(systemName: "mappin.circle.fill")
                    .font(.title3)
                    .foregroundStyle(.green)

                VStack(alignment: .leading, spacing: 2) {
                    Text(place.name)
                        .fontWeight(.medium)
                    Text("\(place.eventCount) event\(place.eventCount == 1 ? "" : "s")")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Text("\(place.eventCount)")
                    .font(.caption)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(.green.opacity(0.1), in: Capsule())
                    .foregroundStyle(.green)
            }
            .padding(.vertical, 2)
        }
        .searchable(text: $searchText, prompt: "Search places")
    }
}
