import SwiftUI

struct FamilyListView: View {
    private let db = DatabaseService.shared
    @State private var selectedFamily: GedcomFamily?

    private var families: [GedcomFamily] { db.fetchFamilies() }

    var body: some View {
        NavigationSplitView {
            List(families, selection: $selectedFamily) { family in
                FamilyRowView(family: family)
                    .tag(family)
            }
            .navigationSplitViewColumnWidth(min: 280, ideal: 340, max: 500)
        } detail: {
            if let family = selectedFamily {
                FamilyDetailView(family: family)
            } else {
                ContentUnavailableView {
                    Label("Select a Family", systemImage: "house.fill")
                } description: {
                    Text("Choose a family to see details.")
                }
            }
        }
    }
}

struct FamilyRowView: View {
    let family: GedcomFamily
    private let db = DatabaseService.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                if let p1 = db.fetchPerson(byXref: family.partner1Xref) {
                    Text(p1.displayName)
                        .fontWeight(.medium)
                }
                if !family.partner1Xref.isEmpty && !family.partner2Xref.isEmpty {
                    Image(systemName: "heart.fill")
                        .font(.caption2)
                        .foregroundStyle(.pink)
                }
                if let p2 = db.fetchPerson(byXref: family.partner2Xref) {
                    Text(p2.displayName)
                        .fontWeight(.medium)
                }
            }

            let childCount = db.fetchChildLinks(forFamily: family.xref).count
            if childCount > 0 {
                Label("\(childCount) children", systemImage: "figure.and.child.holdinghands")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
    }
}

struct FamilyDetailView: View {
    let family: GedcomFamily
    private let db = DatabaseService.shared

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("Family")
                    .font(.largeTitle)
                    .fontWeight(.bold)

                // Partners
                HStack(spacing: 32) {
                    if let p1 = db.fetchPerson(byXref: family.partner1Xref) {
                        partnerCard(person: p1, role: "Partner 1")
                    }
                    Image(systemName: "heart.fill")
                        .font(.title)
                        .foregroundStyle(.pink)
                    if let p2 = db.fetchPerson(byXref: family.partner2Xref) {
                        partnerCard(person: p2, role: "Partner 2")
                    }
                }

                // Marriage events
                let events = db.fetchEvents(forXref: family.xref)
                if !events.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("Events", systemImage: "calendar")
                            .font(.headline)
                        ForEach(events) { event in
                            HStack(spacing: 8) {
                                Image(systemName: event.eventIcon)
                                    .foregroundStyle(.tint)
                                Text("\(event.displayType): \(event.dateValue)")
                                if !event.place.isEmpty {
                                    Text("in \(event.place)")
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                    .padding()
                    .background(.background.secondary, in: RoundedRectangle(cornerRadius: 12))
                }

                // Children
                let childLinks = db.fetchChildLinks(forFamily: family.xref)
                if !childLinks.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("Children (\(childLinks.count))", systemImage: "figure.and.child.holdinghands")
                            .font(.headline)
                        ForEach(childLinks, id: \.childXref) { link in
                            if let child = db.fetchPerson(byXref: link.childXref) {
                                HStack(spacing: 8) {
                                    ZStack {
                                        Circle()
                                            .fill(child.sex == "M" ? Color.blue.opacity(0.15) : Color.pink.opacity(0.15))
                                            .frame(width: 28, height: 28)
                                        Text(child.initials)
                                            .font(.caption2.bold())
                                            .foregroundStyle(child.sex == "M" ? .blue : .pink)
                                    }
                                    Text(child.displayName)
                                }
                            }
                        }
                    }
                    .padding()
                    .background(.background.secondary, in: RoundedRectangle(cornerRadius: 12))
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func partnerCard(person: GedcomPerson, role: String) -> some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .fill(person.sex == "M" ? Color.blue.opacity(0.15) : Color.pink.opacity(0.15))
                    .frame(width: 56, height: 56)
                Text(person.initials)
                    .font(.title2.bold())
                    .foregroundStyle(person.sex == "M" ? .blue : .pink)
            }
            Text(person.displayName)
                .fontWeight(.semibold)
            Text(role)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}
