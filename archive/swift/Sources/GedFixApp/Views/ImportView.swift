import SwiftUI
import UniformTypeIdentifiers

extension UTType {
    static let gedcom = UTType(filenameExtension: "ged", conformingTo: .plainText) ?? .plainText
}

struct ImportView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss
    @State private var selectedURL: URL?
    @State private var showFilePicker = false

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "doc.badge.plus")
                .font(.system(size: 48))
                .foregroundStyle(.tint)

            Text("Import GEDCOM File")
                .font(.title2)
                .fontWeight(.bold)

            Text("Select a GEDCOM 5.5.1 file to import into GedFix.")
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)

            if let url = selectedURL {
                HStack(spacing: 8) {
                    Image(systemName: "doc.fill")
                        .foregroundStyle(.blue)
                    Text(url.lastPathComponent)
                        .fontWeight(.medium)
                    Spacer()
                    Button("Change") {
                        showFilePicker = true
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(.tint)
                }
                .padding()
                .background(.background.secondary, in: RoundedRectangle(cornerRadius: 8))
            }

            HStack(spacing: 16) {
                Button("Cancel") {
                    dismiss()
                }
                .keyboardShortcut(.escape)

                if selectedURL == nil {
                    Button("Choose File...") {
                        showFilePicker = true
                    }
                    .buttonStyle(.borderedProminent)
                } else {
                    Button("Import") {
                        if let url = selectedURL {
                            dismiss()
                            appState.importGEDCOM(url: url)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .keyboardShortcut(.return)
                }
            }
        }
        .padding(32)
        .frame(width: 440)
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [.gedcom, .plainText],
            allowsMultipleSelection: false
        ) { result in
            switch result {
            case .success(let urls):
                selectedURL = urls.first
            case .failure(let error):
                print("File picker error: \(error)")
            }
        }
        .onAppear {
            showFilePicker = true
        }
    }
}
