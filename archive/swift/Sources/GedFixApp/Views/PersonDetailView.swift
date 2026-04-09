import SwiftUI

struct PersonDetailView: View {
    @State var person: GedcomPerson
    private let db = DatabaseService.shared

    @State private var showEditPerson = false
    @State private var showAddEvent = false
    @State private var editingEvent: GedcomEvent?
    @State private var showDeleteConfirm = false
    @State private var showAddToFamily = false
    @State private var showCreateFamily = false
    @State private var refreshToken = UUID()

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
                eventsSection

                // Spouse/Partner families
                if !spouseFamilies.isEmpty {
                    spouseFamiliesSection
                }

                // Parents
                if !parentFamilies.isEmpty {
                    parentFamiliesSection
                }

                // Family actions
                familyActionsSection
            }
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .id(refreshToken)
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                Button {
                    showEditPerson = true
                } label: {
                    Label("Edit Person", systemImage: "pencil")
                }

                Button(role: .destructive) {
                    showDeleteConfirm = true
                } label: {
                    Label("Delete Person", systemImage: "trash")
                }
            }
        }
        .sheet(isPresented: $showEditPerson) {
            PersonEditorView(
                person: person,
                onSave: { updated in
                    try? db.updatePerson(updated)
                    person = updated
                    showEditPerson = false
                    refreshToken = UUID()
                },
                onCancel: { showEditPerson = false }
            )
        }
        .sheet(isPresented: $showAddEvent) {
            EventEditorView(
                event: nil,
                ownerXref: person.xref,
                ownerType: "INDI",
                onSave: { newEvent in
                    try? db.insertEvent(newEvent)
                    showAddEvent = false
                    refreshToken = UUID()
                },
                onDelete: nil,
                onCancel: { showAddEvent = false }
            )
        }
        .sheet(item: $editingEvent) { event in
            EventEditorView(
                event: event,
                ownerXref: person.xref,
                ownerType: "INDI",
                onSave: { updated in
                    try? db.updateEvent(updated)
                    editingEvent = nil
                    refreshToken = UUID()
                },
                onDelete: {
                    try? db.deleteEvent(id: event.id)
                    editingEvent = nil
                    refreshToken = UUID()
                },
                onCancel: { editingEvent = nil }
            )
        }
        .alert("Delete Person?", isPresented: $showDeleteConfirm) {
            Button("Delete", role: .destructive) {
                try? db.deletePerson(xref: person.xref)
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will permanently delete \(person.displayName) and remove them from all families. Their events will also be deleted.")
        }
        .sheet(isPresented: $showAddToFamily) {
            AddToFamilySheet(person: person, onDone: {
                showAddToFamily = false
                refreshToken = UUID()
            })
        }
        .sheet(isPresented: $showCreateFamily) {
            CreateFamilySheet(person: person, onDone: {
                showCreateFamily = false
                refreshToken = UUID()
            })
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
            HStack {
                Label("Events", systemImage: "calendar")
                    .font(.headline)
                Spacer()
                Button {
                    showAddEvent = true
                } label: {
                    Label("Add Event", systemImage: "plus.circle")
                        .font(.subheadline)
                }
                .buttonStyle(.borderless)
            }

            if events.isEmpty {
                Text("No events recorded.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 8)
            }

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

                    Spacer()

                    Button {
                        editingEvent = event
                    } label: {
                        Image(systemName: "pencil.circle")
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.borderless)
                    .help("Edit event")
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

    // MARK: - Family Actions

    private var familyActionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label("Family Actions", systemImage: "person.2.fill")
                .font(.headline)

            HStack(spacing: 12) {
                Button {
                    showAddToFamily = true
                } label: {
                    Label("Add as Child to Family", systemImage: "figure.and.child.holdinghands")
                }

                Button {
                    showCreateFamily = true
                } label: {
                    Label("Create Family as Spouse", systemImage: "heart.fill")
                }
            }
            .buttonStyle(.bordered)
        }
        .padding()
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Add to Family Sheet

struct AddToFamilySheet: View {
    let person: GedcomPerson
    let onDone: () -> Void
    private let db = DatabaseService.shared

    @State private var selectedFamilyXref: String = ""

    private var families: [GedcomFamily] { db.fetchFamilies() }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Add \(person.displayName) as Child")
                    .font(.headline)
                Spacer()
                Button("Cancel") { onDone() }
                    .keyboardShortcut(.cancelAction)
                Button("Add") {
                    if !selectedFamilyXref.isEmpty {
                        try? db.addChildToFamily(familyXref: selectedFamilyXref, childXref: person.xref)
                    }
                    onDone()
                }
                .keyboardShortcut(.defaultAction)
                .disabled(selectedFamilyXref.isEmpty)
            }
            .padding()

            Divider()

            List(families, selection: $selectedFamilyXref) { family in
                HStack {
                    VStack(alignment: .leading) {
                        HStack(spacing: 4) {
                            if let p1 = db.fetchPerson(byXref: family.partner1Xref) {
                                Text(p1.displayName)
                            }
                            if !family.partner1Xref.isEmpty && !family.partner2Xref.isEmpty {
                                Image(systemName: "heart.fill")
                                    .font(.caption2)
                                    .foregroundStyle(.pink)
                            }
                            if let p2 = db.fetchPerson(byXref: family.partner2Xref) {
                                Text(p2.displayName)
                            }
                        }
                        .fontWeight(.medium)
                        Text(family.xref)
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                    }
                }
                .tag(family.xref)
            }
        }
        .frame(width: 500, height: 400)
    }
}

// MARK: - Create Family Sheet

struct CreateFamilySheet: View {
    let person: GedcomPerson
    let onDone: () -> Void
    private let db = DatabaseService.shared

    @State private var selectedSpouseXref: String = ""
    @State private var searchText: String = ""

    private var availablePersons: [GedcomPerson] {
        db.fetchPersons(search: searchText).filter { $0.xref != person.xref }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Create Family with \(person.displayName)")
                    .font(.headline)
                Spacer()
                Button("Cancel") { onDone() }
                    .keyboardShortcut(.cancelAction)
                Button("Create") {
                    let p1 = person.xref
                    let p2 = selectedSpouseXref
                    _ = try? db.createFamily(partner1Xref: p1, partner2Xref: p2)
                    onDone()
                }
                .keyboardShortcut(.defaultAction)
            }
            .padding()

            Divider()

            VStack(alignment: .leading, spacing: 8) {
                Text("Select a spouse (optional):")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal)
                    .padding(.top, 8)

                TextField("Search people...", text: $searchText)
                    .textFieldStyle(.roundedBorder)
                    .padding(.horizontal)

                List(availablePersons, selection: $selectedSpouseXref) { p in
                    HStack(spacing: 8) {
                        ZStack {
                            Circle()
                                .fill(p.sex == "M" ? Color.blue.opacity(0.15) : p.sex == "F" ? Color.pink.opacity(0.15) : Color.gray.opacity(0.15))
                                .frame(width: 28, height: 28)
                            Text(p.initials)
                                .font(.caption2.bold())
                                .foregroundStyle(p.sex == "M" ? .blue : p.sex == "F" ? .pink : .gray)
                        }
                        Text(p.displayName)
                    }
                    .tag(p.xref)
                }
            }
        }
        .frame(width: 500, height: 450)
    }
}
