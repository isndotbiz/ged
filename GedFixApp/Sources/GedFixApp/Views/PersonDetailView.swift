import SwiftUI

struct PersonDetailView: View {
    let person: GedcomPerson
    private let db = DatabaseService.shared

    private var events: [GedcomEvent] { db.fetchEvents(forXref: person.xref) }
    private var spouseFamilies: [GedcomFamily] { db.fetchFamiliesAsSpouse(forXref: person.xref) }
    private var parentFamilies: [GedcomFamily] { db.fetchFamiliesAsChild(forXref: person.xref) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                // Header
                personHeader

                Divider()

                // Events
                if !events.isEmpty {
                    eventsSection
                }

                // Spouse/Partner families
                if !spouseFamilies.isEmpty {
                    spouseFamiliesSection
                }

                // Parents
                if !parentFamilies.isEmpty {
                    parentFamiliesSection
                }
            }
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    // MARK: - Header

    private var personHeader: some View {
        HStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(person.sex == "M" ? Color.blue.opacity(0.15) : person.sex == "F" ? Color.pink.opacity(0.15) : Color.gray.opacity(0.15))
                    .frame(width: 64, height: 64)
                Text(person.initials)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundStyle(person.sex == "M" ? .blue : person.sex == "F" ? .pink : .gray)
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(person.displayName.isEmpty ? "(Unknown)" : person.displayName)
                        .font(.title)
                        .fontWeight(.bold)

                    if person.isLiving {
                        HStack(spacing: 4) {
                            Image(systemName: "shield.fill")
                            Text("Living")
                        }
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(.green.opacity(0.15), in: Capsule())
                        .foregroundStyle(.green)
                    }
                }

                HStack(spacing: 16) {
                    if let birth = events.first(where: { $0.eventType == "BIRT" }) {
                        Text("b. \(birth.dateValue)\(!birth.place.isEmpty ? ", \(birth.place)" : "")")
                            .foregroundStyle(.secondary)
                    }
                    if let death = events.first(where: { $0.eventType == "DEAT" }) {
                        Text("d. \(death.dateValue)\(!death.place.isEmpty ? ", \(death.place)" : "")")
                            .foregroundStyle(.secondary)
                    }
                }
                .font(.subheadline)

                Text(person.xref)
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                    .monospaced()
            }
        }
    }

    // MARK: - Events

    private var eventsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Events", systemImage: "calendar")
                .font(.headline)

            ForEach(events) { event in
                HStack(spacing: 12) {
                    Image(systemName: event.eventIcon)
                        .frame(width: 24)
                        .foregroundStyle(.tint)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(event.displayType)
                            .fontWeight(.medium)
                        if !event.dateValue.isEmpty {
                            Text(event.dateValue)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        if !event.place.isEmpty {
                            Label(event.place, systemImage: "mappin")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(.vertical, 4)

                if event != events.last {
                    Divider().padding(.leading, 36)
                }
            }
        }
        .padding()
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - Spouse Families

    private var spouseFamiliesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Families", systemImage: "heart.fill")
                .font(.headline)

            ForEach(spouseFamilies) { family in
                VStack(alignment: .leading, spacing: 8) {
                    // Spouse
                    let spouseXref = family.partner1Xref == person.xref ? family.partner2Xref : family.partner1Xref
                    if let spouse = db.fetchPerson(byXref: spouseXref) {
                        HStack(spacing: 8) {
                            Image(systemName: "person.fill")
                                .foregroundStyle(.pink)
                            Text("Spouse: \(spouse.displayName)")
                                .fontWeight(.medium)
                        }
                    }

                    // Marriage event
                    let marriageEvents = db.fetchEvents(forXref: family.xref).filter { $0.eventType == "MARR" }
                    ForEach(marriageEvents) { marr in
                        HStack(spacing: 8) {
                            Image(systemName: "heart.fill")
                                .foregroundStyle(.pink.opacity(0.5))
                            Text("Married \(marr.dateValue)\(!marr.place.isEmpty ? " in \(marr.place)" : "")")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }

                    // Children
                    let childLinks = db.fetchChildLinks(forFamily: family.xref)
                    if !childLinks.isEmpty {
                        HStack(spacing: 8) {
                            Image(systemName: "figure.and.child.holdinghands")
                                .foregroundStyle(.blue)
                            Text("Children:")
                                .font(.subheadline)
                                .fontWeight(.medium)
                        }
                        ForEach(childLinks, id: \.childXref) { link in
                            if let child = db.fetchPerson(byXref: link.childXref) {
                                HStack(spacing: 8) {
                                    Text("  ")
                                    Image(systemName: "arrow.turn.down.right")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                    Text(child.displayName)
                                        .font(.subheadline)
                                }
                            }
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        }
        .padding()
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 12))
    }

    // MARK: - Parents

    private var parentFamiliesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Parents", systemImage: "figure.2.and.child")
                .font(.headline)

            ForEach(parentFamilies) { family in
                VStack(alignment: .leading, spacing: 6) {
                    if let father = db.fetchPerson(byXref: family.partner1Xref) {
                        HStack(spacing: 8) {
                            Image(systemName: "person.fill")
                                .foregroundStyle(.blue)
                            Text("Father: \(father.displayName)")
                        }
                    }
                    if let mother = db.fetchPerson(byXref: family.partner2Xref) {
                        HStack(spacing: 8) {
                            Image(systemName: "person.fill")
                                .foregroundStyle(.pink)
                            Text("Mother: \(mother.displayName)")
                        }
                    }
                }
            }
        }
        .padding()
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 12))
    }
}
