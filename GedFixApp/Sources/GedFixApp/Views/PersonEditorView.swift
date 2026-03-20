import SwiftUI

struct PersonEditorView: View {
    let person: GedcomPerson?
    let onSave: (GedcomPerson) -> Void
    let onCancel: () -> Void

    @State private var givenName: String = ""
    @State private var surname: String = ""
    @State private var suffix: String = ""
    @State private var sex: String = "U"
    @State private var isLiving: Bool = false
    @State private var validationError: String?

    private var isNew: Bool { person == nil }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text(isNew ? "New Person" : "Edit Person")
                    .font(.headline)
                Spacer()
                Button("Cancel", role: .cancel) { onCancel() }
                    .keyboardShortcut(.cancelAction)
                Button(isNew ? "Create" : "Save") { save() }
                    .keyboardShortcut(.defaultAction)
                    .disabled(surname.trimmingCharacters(in: .whitespaces).isEmpty
                              && givenName.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding()

            Divider()

            Form {
                Section("Name") {
                    TextField("Given Name", text: $givenName)
                        .textFieldStyle(.roundedBorder)
                    TextField("Surname", text: $surname)
                        .textFieldStyle(.roundedBorder)
                    TextField("Suffix (Jr., Sr., III, etc.)", text: $suffix)
                        .textFieldStyle(.roundedBorder)
                }

                Section("Sex") {
                    Picker("Sex", selection: $sex) {
                        Text("Male").tag("M")
                        Text("Female").tag("F")
                        Text("Unknown").tag("U")
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                }

                Section("Status") {
                    Toggle("Living Person", isOn: $isLiving)
                }

                if let error = validationError {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.red)
                    }
                }
            }
            .formStyle(.grouped)
            .padding(.bottom, 8)
        }
        .frame(width: 420, height: 380)
        .onAppear {
            if let person = person {
                givenName = person.givenName
                surname = person.surname
                suffix = person.suffix
                sex = person.sex
                isLiving = person.isLiving
            }
        }
    }

    private func save() {
        let trimmedGiven = givenName.trimmingCharacters(in: .whitespaces)
        let trimmedSurname = surname.trimmingCharacters(in: .whitespaces)

        if trimmedGiven.isEmpty && trimmedSurname.isEmpty {
            validationError = "At least a given name or surname is required."
            return
        }

        validationError = nil

        if var existing = person {
            existing.givenName = trimmedGiven
            existing.surname = trimmedSurname
            existing.suffix = suffix.trimmingCharacters(in: .whitespaces)
            existing.sex = sex
            existing.isLiving = isLiving
            onSave(existing)
        } else {
            let db = DatabaseService.shared
            let xref = (try? db.nextPersonXref()) ?? "@I\(Int.random(in: 10000...99999))@"
            let newPerson = GedcomPerson(
                id: UUID().uuidString,
                xref: xref,
                givenName: trimmedGiven,
                surname: trimmedSurname,
                suffix: suffix.trimmingCharacters(in: .whitespaces),
                sex: sex,
                isLiving: isLiving
            )
            onSave(newPerson)
        }
    }
}
