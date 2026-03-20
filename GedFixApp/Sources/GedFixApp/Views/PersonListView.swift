import SwiftUI

struct PersonListView: View {
    @Environment(AppState.self) private var appState
    private let db = DatabaseService.shared
    @State private var searchText = ""
    @State private var sortBy: DatabaseService.PersonSort = .surname
    @State private var selectedPersonID: String?

    private var persons: [GedcomPerson] {
        db.fetchPersons(search: searchText, sortBy: sortBy)
    }

    private var selectedPerson: GedcomPerson? {
        guard let id = selectedPersonID else { return nil }
        return persons.first { $0.id == id }
    }

    var body: some View {
        NavigationSplitView {
            List(persons, selection: $selectedPersonID) { person in
                PersonRowView(person: person)
                    .tag(person.id)
            }
            .searchable(text: $searchText, prompt: "Search people")
            .navigationSplitViewColumnWidth(min: 280, ideal: 340, max: 500)
            .toolbar {
                ToolbarItem {
                    Picker("Sort", selection: $sortBy) {
                        Text("Surname").tag(DatabaseService.PersonSort.surname)
                        Text("Given Name").tag(DatabaseService.PersonSort.givenName)
                    }
                    .pickerStyle(.segmented)
                }
            }
        } detail: {
            if let person = selectedPerson {
                PersonDetailView(person: person)
            } else {
                ContentUnavailableView {
                    Label("Select a Person", systemImage: "person.crop.circle")
                } description: {
                    Text("Choose someone from the list to see their details.")
                }
            }
        }
    }
}

struct PersonRowView: View {
    let person: GedcomPerson
    private let db = DatabaseService.shared

    private var birthEvent: GedcomEvent? {
        db.fetchEvents(forXref: person.xref).first { $0.eventType == "BIRT" }
    }

    private var deathEvent: GedcomEvent? {
        db.fetchEvents(forXref: person.xref).first { $0.eventType == "DEAT" }
    }

    var body: some View {
        HStack(spacing: 12) {
            // Avatar circle
            ZStack {
                Circle()
                    .fill(person.sex == "M" ? Color.blue.opacity(0.15) : person.sex == "F" ? Color.pink.opacity(0.15) : Color.gray.opacity(0.15))
                    .frame(width: 36, height: 36)
                Text(person.initials)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(person.sex == "M" ? .blue : person.sex == "F" ? .pink : .gray)
            }

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(person.displayName.isEmpty ? "(Unknown)" : person.displayName)
                        .font(.body)
                        .fontWeight(.medium)

                    if person.isLiving {
                        Image(systemName: "shield.fill")
                            .font(.caption2)
                            .foregroundStyle(.green)
                            .help("Living person — details protected")
                    }
                }

                HStack(spacing: 8) {
                    if let birth = birthEvent, !birth.dateValue.isEmpty {
                        Label(birth.dateValue, systemImage: "figure.and.child.holdinghands")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    if let death = deathEvent, !death.dateValue.isEmpty {
                        Label(death.dateValue, systemImage: "leaf.fill")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Spacer()
        }
        .padding(.vertical, 2)
    }
}
