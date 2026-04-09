import SwiftUI

struct PedigreeChartView: View {
    @Environment(AppState.self) private var appState
    @State private var rootXref: String
    @State private var generations: Int = 4
    @State private var history: [String] = []
    @State private var scale: CGFloat = 1.0
    @State private var showingPersonDetail: PedigreeDetailItem?

    private let db = DatabaseService.shared

    init(rootXref: String?) {
        _rootXref = State(initialValue: rootXref ?? "")
    }

    private var rootPerson: GedcomPerson? {
        rootXref.isEmpty ? nil : db.fetchPerson(byXref: rootXref)
    }

    var body: some View {
        VStack(spacing: 0) {
            if rootXref.isEmpty || rootPerson == nil {
                emptyState
            } else {
                chartContent
            }
        }
        .sheet(item: $showingPersonDetail) { item in
            if let person = db.fetchPerson(byXref: item.xref) {
                NavigationStack {
                    PersonDetailView(person: person)
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button("Done") { showingPersonDetail = nil }
                            }
                        }
                }
                .frame(minWidth: 500, minHeight: 400)
            }
        }
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "chart.bar.doc.horizontal")
                .font(.system(size: 48))
                .foregroundStyle(.tertiary)
            Text("Select a Person")
                .font(.title2)
                .foregroundStyle(.secondary)
            Text("Choose someone from the People list to view their pedigree chart")
                .font(.subheadline)
                .foregroundStyle(.tertiary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Chart Content

    private var chartContent: some View {
        VStack(spacing: 0) {
            chartToolbar
            Divider()
            generationLabels
            ScrollView([.horizontal, .vertical]) {
                chartTree
                    .scaleEffect(scale)
                    .padding(32)
            }
        }
    }

    // MARK: - Toolbar

    private var chartToolbar: some View {
        HStack(spacing: 12) {
            // Back button
            Button {
                if let previous = history.popLast() {
                    rootXref = previous
                }
            } label: {
                Label("Back", systemImage: "chevron.left")
            }
            .disabled(history.isEmpty)

            Divider().frame(height: 20)

            // Root person name
            if let person = rootPerson {
                HStack(spacing: 6) {
                    Image(systemName: person.sex == "M" ? "person.fill" : person.sex == "F" ? "person.fill" : "person.fill")
                        .foregroundStyle(person.sex == "M" ? .blue : person.sex == "F" ? .pink : .gray)
                    Text(person.displayName)
                        .fontWeight(.semibold)
                }
            }

            Spacer()

            // Generation picker
            HStack(spacing: 6) {
                Text("Generations:")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Picker("", selection: $generations) {
                    Text("3").tag(3)
                    Text("4").tag(4)
                    Text("5").tag(5)
                }
                .pickerStyle(.segmented)
                .frame(width: 120)
            }

            Divider().frame(height: 20)

            // Zoom controls
            HStack(spacing: 4) {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        scale = max(0.4, scale - 0.1)
                    }
                } label: {
                    Image(systemName: "minus.magnifyingglass")
                }

                Text("\(Int(scale * 100))%")
                    .font(.caption)
                    .monospacedDigit()
                    .frame(width: 40)

                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        scale = min(2.0, scale + 0.1)
                    }
                } label: {
                    Image(systemName: "plus.magnifyingglass")
                }

                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        scale = 1.0
                    }
                } label: {
                    Image(systemName: "arrow.counterclockwise")
                }
                .help("Reset zoom")
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(.bar)
    }

    // MARK: - Generation Labels

    private var generationLabels: some View {
        HStack(spacing: 0) {
            ForEach(0..<generations, id: \.self) { gen in
                Text(generationLabel(gen))
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 32)
        .padding(.vertical, 6)
        .background(.background.secondary)
    }

    private func generationLabel(_ gen: Int) -> String {
        switch gen {
        case 0: return "Root"
        case 1: return "Parents"
        case 2: return "Grandparents"
        case 3: return "Great-Grandparents"
        case 4: return "2x Great-Grandparents"
        default: return "Gen \(gen)"
        }
    }

    // MARK: - Chart Tree

    private var chartTree: some View {
        PedigreeTreeNode(
            xref: rootXref,
            depth: 0,
            maxDepth: generations,
            onNavigate: { xref in
                if xref != rootXref {
                    history.append(rootXref)
                    withAnimation(.easeInOut(duration: 0.3)) {
                        rootXref = xref
                    }
                }
            },
            onDetail: { xref in
                showingPersonDetail = PedigreeDetailItem(xref: xref)
            }
        )
    }
}

// MARK: - Pedigree Tree Node (Recursive)

private struct PedigreeTreeNode: View {
    let xref: String
    let depth: Int
    let maxDepth: Int
    let onNavigate: (String) -> Void
    let onDetail: (String) -> Void

    private let db = DatabaseService.shared

    private var person: GedcomPerson? {
        xref.isEmpty ? nil : db.fetchPerson(byXref: xref)
    }

    private var parents: (father: GedcomPerson?, mother: GedcomPerson?) {
        xref.isEmpty ? (nil, nil) : db.fetchParents(ofXref: xref)
    }

    private let verticalSpacings: [Int: CGFloat] = [
        0: 0,
        1: 16,
        2: 8,
        3: 4,
        4: 2,
    ]

    var body: some View {
        HStack(alignment: .center, spacing: 0) {
            // This person's card
            PedigreePersonCard(
                person: person,
                generation: depth,
                onTap: onNavigate,
                onDoubleTap: onDetail
            )

            // Connector + parent subtrees
            if depth < maxDepth - 1 {
                connectorAndParents
            }
        }
    }

    @ViewBuilder
    private var connectorAndParents: some View {
        let parentData = parents
        let hasAnyParent = parentData.father != nil || parentData.mother != nil
        let spacing = verticalSpacings[depth + 1, default: 2]

        // Horizontal connector line from card
        PedigreeConnectorLine()
            .frame(width: 24)

        // Parent subtrees stacked vertically
        VStack(spacing: spacing) {
            // Father branch (top)
            PedigreeTreeNode(
                xref: parentData.father?.xref ?? "",
                depth: depth + 1,
                maxDepth: maxDepth,
                onNavigate: onNavigate,
                onDetail: onDetail
            )

            // Mother branch (bottom)
            PedigreeTreeNode(
                xref: parentData.mother?.xref ?? "",
                depth: depth + 1,
                maxDepth: maxDepth,
                onNavigate: onNavigate,
                onDetail: onDetail
            )
        }
        .overlay(alignment: .leading) {
            // Vertical bracket connecting the two parent branches
            if hasAnyParent || depth < maxDepth - 2 {
                PedigreeBracket()
                    .stroke(.gray.opacity(0.3), lineWidth: 1)
            }
        }
    }
}

// MARK: - Connector Line (horizontal)

private struct PedigreeConnectorLine: View {
    var body: some View {
        GeometryReader { geo in
            Path { path in
                let midY = geo.size.height / 2
                path.move(to: CGPoint(x: 0, y: midY))
                path.addLine(to: CGPoint(x: geo.size.width, y: midY))
            }
            .stroke(.gray.opacity(0.3), lineWidth: 1)
        }
    }
}

// MARK: - Bracket (vertical connector between parent pair)

private struct PedigreeBracket: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let topQuarter = rect.height * 0.25
        let bottomQuarter = rect.height * 0.75

        // Vertical line connecting father and mother branches
        path.move(to: CGPoint(x: 0, y: topQuarter))
        path.addLine(to: CGPoint(x: 0, y: bottomQuarter))

        return path
    }
}

// MARK: - Detail Item Wrapper

struct PedigreeDetailItem: Identifiable {
    let id = UUID()
    let xref: String
}
