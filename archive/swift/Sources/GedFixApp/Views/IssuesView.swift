import SwiftUI

struct IssuesView: View {
    @Environment(AppState.self) private var appState
    private let db = DatabaseService.shared

    @State private var issues: [TreeIssue] = []
    @State private var isAnalyzing = false
    @State private var hasAnalyzed = false
    @State private var searchText = ""
    @State private var selectedCategory: IssueCategory?
    @State private var selectedSeverity: IssueSeverity?
    @State private var dismissedIssueIDs: Set<String> = []

    private var activeIssues: [TreeIssue] {
        issues.filter { !dismissedIssueIDs.contains($0.id) }
    }

    private var filteredIssues: [TreeIssue] {
        activeIssues.filter { issue in
            if let cat = selectedCategory, issue.category != cat { return false }
            if let sev = selectedSeverity, issue.severity != sev { return false }
            if !searchText.isEmpty {
                let text = searchText.lowercased()
                return issue.title.lowercased().contains(text)
                    || issue.detail.lowercased().contains(text)
                    || (issue.personXref?.lowercased().contains(text) ?? false)
            }
            return true
        }
    }

    private var issuesByCategory: [IssueCategory: [TreeIssue]] {
        Dictionary(grouping: filteredIssues, by: \.category)
    }

    private var healthScore: Double {
        let total = db.personCount + db.familyCount
        guard total > 0 else { return 100.0 }
        let issueCount = activeIssues.count
        let score = max(0.0, 1.0 - Double(issueCount) / Double(total)) * 100.0
        return score
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header
                if hasAnalyzed {
                    summarySection
                    filterBar
                    issuesList
                } else {
                    emptyState
                }
            }
            .padding(24)
        }
        .searchable(text: $searchText, prompt: "Search issues...")
    }

    // MARK: - Header

    private var header: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Tree Consistency Checker")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                if hasAnalyzed {
                    Text("\(activeIssues.count) issues found")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            Button {
                runAnalysis()
            } label: {
                if isAnalyzing {
                    ProgressView()
                        .controlSize(.small)
                        .padding(.trailing, 4)
                    Text("Analyzing...")
                } else {
                    Label(hasAnalyzed ? "Re-Analyze" : "Analyze Tree", systemImage: "wand.and.stars")
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isAnalyzing || db.personCount == 0)
        }
    }

    // MARK: - Summary

    private var summarySection: some View {
        HStack(spacing: 16) {
            // Health score circle
            healthScoreCard

            // Category summary cards
            ForEach(IssueCategory.allCases) { category in
                let count = activeIssues.filter { $0.category == category }.count
                categorySummaryCard(category: category, count: count)
            }
        }
    }

    private var healthScoreCard: some View {
        VStack(spacing: 8) {
            ZStack {
                Circle()
                    .stroke(Color.secondary.opacity(0.2), lineWidth: 8)
                Circle()
                    .trim(from: 0, to: healthScore / 100.0)
                    .stroke(healthScoreColor, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .animation(.easeInOut(duration: 0.6), value: healthScore)
                VStack(spacing: 2) {
                    Text("\(Int(healthScore))")
                        .font(.system(size: 22, weight: .bold, design: .rounded))
                    Text("%")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: 64, height: 64)
            Text("Health")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 12))
    }

    private var healthScoreColor: Color {
        if healthScore >= 80 { return .green }
        if healthScore >= 50 { return .orange }
        return .red
    }

    private func categorySummaryCard(category: IssueCategory, count: Int) -> some View {
        Button {
            if selectedCategory == category {
                selectedCategory = nil
            } else {
                selectedCategory = category
            }
        } label: {
            VStack(spacing: 8) {
                Image(systemName: category.icon)
                    .font(.title2)
                    .foregroundStyle(colorForCategory(category))
                Text("\(count)")
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .monospacedDigit()
                Text(category.rawValue)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(
                selectedCategory == category
                    ? AnyShapeStyle(colorForCategory(category).opacity(0.15))
                    : AnyShapeStyle(.background.secondary),
                in: RoundedRectangle(cornerRadius: 12)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(selectedCategory == category ? colorForCategory(category) : .clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Filter Bar

    private var filterBar: some View {
        HStack(spacing: 12) {
            // Severity filter
            Picker("Severity", selection: $selectedSeverity) {
                Text("All Severities").tag(IssueSeverity?.none)
                Divider()
                ForEach(IssueSeverity.allCases) { sev in
                    Label(sev.label, systemImage: sev.icon).tag(IssueSeverity?.some(sev))
                }
            }
            .pickerStyle(.menu)
            .frame(width: 160)

            if selectedCategory != nil || selectedSeverity != nil {
                Button("Clear Filters") {
                    selectedCategory = nil
                    selectedSeverity = nil
                }
                .buttonStyle(.borderless)
                .foregroundStyle(.secondary)
            }

            Spacer()

            let autoFixCount = filteredIssues.filter(\.isAutoFixable).count
            if autoFixCount > 0 {
                Button {
                    // Future: batch auto-fix
                } label: {
                    Label("Fix All (\(autoFixCount))", systemImage: "wrench.and.screwdriver")
                }
                .buttonStyle(.bordered)
                .disabled(true)
                .help("Auto-fix coming in a future update")
            }

            Text("\(filteredIssues.count) of \(activeIssues.count) shown")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Issues List

    private var issuesList: some View {
        VStack(alignment: .leading, spacing: 16) {
            if filteredIssues.isEmpty {
                ContentUnavailableView {
                    Label("No Issues Match", systemImage: "magnifyingglass")
                } description: {
                    Text("Try adjusting your filters or search terms.")
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 40)
            } else {
                ForEach(IssueCategory.allCases) { category in
                    let categoryIssues = issuesByCategory[category] ?? []
                    if !categoryIssues.isEmpty {
                        issueCategorySection(category: category, issues: categoryIssues)
                    }
                }
            }
        }
    }

    private func issueCategorySection(category: IssueCategory, issues: [TreeIssue]) -> some View {
        DisclosureGroup {
            LazyVStack(alignment: .leading, spacing: 8) {
                ForEach(issues) { issue in
                    issueRow(issue)
                }
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: category.icon)
                    .foregroundStyle(colorForCategory(category))
                Text(category.rawValue)
                    .font(.headline)
                Spacer()
                Text("\(issues.count)")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(colorForCategory(category).opacity(0.15), in: Capsule())
            }
        }
    }

    private func issueRow(_ issue: TreeIssue) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: issue.severity.icon)
                .foregroundStyle(colorForSeverity(issue.severity))
                .font(.body)
                .frame(width: 20)

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(issue.title)
                        .fontWeight(.medium)
                    if issue.isAutoFixable {
                        Text("Auto-fixable")
                            .font(.caption2)
                            .fontWeight(.semibold)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 1)
                            .background(.green.opacity(0.15), in: Capsule())
                            .foregroundStyle(.green)
                    }
                }
                Text(issue.detail)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                HStack(spacing: 4) {
                    Image(systemName: "lightbulb.fill")
                        .font(.caption2)
                        .foregroundStyle(.yellow)
                    Text(issue.suggestion)
                        .font(.caption)
                        .foregroundStyle(.tertiary)
                }
            }

            Spacer()

            HStack(spacing: 8) {
                if let xref = issue.personXref {
                    Button {
                        appState.selectedSection = .people
                        appState.selectedPersonXref = xref
                    } label: {
                        Image(systemName: "person.fill")
                            .help("Go to person")
                    }
                    .buttonStyle(.borderless)
                }
                Button {
                    dismissedIssueIDs.insert(issue.id)
                } label: {
                    Image(systemName: "xmark.circle")
                        .foregroundStyle(.secondary)
                        .help("Dismiss")
                }
                .buttonStyle(.borderless)
            }
        }
        .padding(12)
        .background(.background.secondary, in: RoundedRectangle(cornerRadius: 8))
    }

    // MARK: - Empty State

    private var emptyState: some View {
        Group {
            if db.personCount == 0 {
                ContentUnavailableView {
                    Label("No Tree Loaded", systemImage: "doc.badge.plus")
                } description: {
                    Text("Import a GEDCOM file first, then analyze it for issues.")
                }
            } else {
                ContentUnavailableView {
                    Label("Ready to Analyze", systemImage: "wand.and.stars")
                } description: {
                    Text("Click \"Analyze Tree\" to check \(db.personCount) people and \(db.familyCount) families for consistency issues.")
                } actions: {
                    Button("Analyze Tree") {
                        runAnalysis()
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
    }

    // MARK: - Actions

    private func runAnalysis() {
        isAnalyzing = true
        dismissedIssueIDs = []
        Task {
            let analyzer = TreeAnalyzer(db: db)
            let result = analyzer.analyze()
            issues = result
            hasAnalyzed = true
            isAnalyzing = false
            appState.issueCount = result.count
        }
    }

    // MARK: - Colors

    private func colorForCategory(_ category: IssueCategory) -> Color {
        switch category {
        case .dateIssue: return .red
        case .relationshipIssue: return .orange
        case .dataQuality: return .yellow
        case .potentialDuplicate: return .blue
        }
    }

    private func colorForSeverity(_ severity: IssueSeverity) -> Color {
        switch severity {
        case .critical: return .red
        case .warning: return .orange
        case .info: return .blue
        }
    }
}
