import SwiftUI

struct EventEditorView: View {
    let event: GedcomEvent?
    let ownerXref: String
    let ownerType: String
    let onSave: (GedcomEvent) -> Void
    let onDelete: (() -> Void)?
    let onCancel: () -> Void

    @State private var eventType: String = "BIRT"
    @State private var dateValue: String = ""
    @State private var place: String = ""
    @State private var description: String = ""
    @State private var showDeleteConfirm = false

    private var isNew: Bool { event == nil }

    private static let eventTypes: [(code: String, label: String, icon: String)] = [
        ("BIRT", "Birth", "figure.and.child.holdinghands"),
        ("DEAT", "Death", "leaf.fill"),
        ("MARR", "Marriage", "heart.fill"),
        ("BURI", "Burial", "cross.fill"),
        ("CHR", "Christening", "drop.fill"),
        ("BAPM", "Baptism", "drop.fill"),
        ("RESI", "Residence", "house.fill"),
        ("CENS", "Census", "house.fill"),
        ("IMMI", "Immigration", "airplane"),
        ("EMIG", "Emigration", "airplane"),
        ("NATU", "Naturalization", "flag.fill"),
        ("GRAD", "Graduation", "graduationcap.fill"),
        ("RETI", "Retirement", "figure.walk"),
        ("PROB", "Probate", "doc.text.fill"),
        ("WILL", "Will", "doc.text.fill"),
        ("DIV", "Divorce", "arrow.left.arrow.right"),
    ]

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text(isNew ? "New Event" : "Edit Event")
                    .font(.headline)
                Spacer()
                if !isNew, onDelete != nil {
                    Button("Delete", role: .destructive) {
                        showDeleteConfirm = true
                    }
                }
                Button("Cancel", role: .cancel) { onCancel() }
                    .keyboardShortcut(.cancelAction)
                Button(isNew ? "Add" : "Save") { save() }
                    .keyboardShortcut(.defaultAction)
            }
            .padding()

            Divider()

            Form {
                Section("Event Type") {
                    Picker("Type", selection: $eventType) {
                        ForEach(Self.eventTypes, id: \.code) { type in
                            Label(type.label, systemImage: type.icon)
                                .tag(type.code)
                        }
                    }
                    .labelsHidden()
                }

                Section("Details") {
                    TextField("Date (e.g. 15 MAR 1892)", text: $dateValue)
                        .textFieldStyle(.roundedBorder)
                    TextField("Place", text: $place)
                        .textFieldStyle(.roundedBorder)
                    TextField("Description / Notes", text: $description)
                        .textFieldStyle(.roundedBorder)
                }
            }
            .formStyle(.grouped)
            .padding(.bottom, 8)
        }
        .frame(width: 440, height: 340)
        .onAppear {
            if let event = event {
                eventType = event.eventType
                dateValue = event.dateValue
                place = event.place
                description = event.description
            }
        }
        .alert("Delete Event?", isPresented: $showDeleteConfirm) {
            Button("Delete", role: .destructive) { onDelete?() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This event will be permanently removed.")
        }
    }

    private func save() {
        if var existing = event {
            existing.eventType = eventType
            existing.dateValue = dateValue.trimmingCharacters(in: .whitespaces)
            existing.place = place.trimmingCharacters(in: .whitespaces)
            existing.description = description.trimmingCharacters(in: .whitespaces)
            onSave(existing)
        } else {
            let newEvent = GedcomEvent(
                id: UUID().uuidString,
                ownerXref: ownerXref,
                ownerType: ownerType,
                eventType: eventType,
                dateValue: dateValue.trimmingCharacters(in: .whitespaces),
                place: place.trimmingCharacters(in: .whitespaces),
                description: description.trimmingCharacters(in: .whitespaces)
            )
            onSave(newEvent)
        }
    }
}
