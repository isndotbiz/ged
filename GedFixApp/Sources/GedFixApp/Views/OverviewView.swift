import SwiftUI

struct OverviewView: View {
    private let db = DatabaseService.shared

    private var topSurnames: [(String, Int)] { db.fetchTopSurnames(limit: 10) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Tree Overview")
                    .font(.largeTitle)
                    .fontWeight(.bold)

                // Stats cards
                LazyVGrid(columns: [
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                    GridItem(.flexible()),
                ], spacing: 16) {
                    StatCard(title: "People", count: db.personCount, icon: "person.3.fill", color: .blue)
                    StatCard(title: "Families", count: db.familyCount, icon: "house.fill", color: .pink)
                    StatCard(title: "Events", count: db.eventCount, icon: "calendar", color: .orange)
                    StatCard(title: "Places", count: db.placeCount, icon: "mappin.and.ellipse", color: .green)
                    StatCard(title: "Sources", count: db.sourceCount, icon: "book.closed.fill", color: .purple)
                }

                if !topSurnames.isEmpty {
                    Divider()

                    VStack(alignment: .leading, spacing: 12) {
                        Text("Top Surnames")
                            .font(.headline)

                        let maxCount = topSurnames.first?.1 ?? 1
                        ForEach(topSurnames, id: \.0) { name, count in
                            HStack(spacing: 12) {
                                Text(name)
                                    .frame(width: 120, alignment: .trailing)
                                    .fontWeight(.medium)

                                GeometryReader { geo in
                                    RoundedRectangle(cornerRadius: 4)
                                        .fill(Color.accentColor.opacity(0.7))
                                        .frame(width: geo.size.width * CGFloat(count) / CGFloat(maxCount))
                                }
                                .frame(height: 20)

                                Text("\(count)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .monospacedDigit()
                                    .frame(width: 40, alignment: .leading)
                            }
                        }
                    }
                }

                if db.personCount == 0 {
                    ContentUnavailableView {
                        Label("No Tree Loaded", systemImage: "doc.badge.plus")
                    } description: {
                        Text("Import a GEDCOM file to get started.")
                    } actions: {
                        Button("Import GEDCOM...") {
                            // Handled by toolbar/menu
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 40)
                }
            }
            .padding(24)
        }
    }
}

struct StatCard: View {
    let title: String
    let count: Int
    let icon: String
    let color: Color

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(color)

            Text("\(count)")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .monospacedDigit()

            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 12))
    }
}
