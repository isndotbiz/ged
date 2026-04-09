import Foundation
import GRDB

// MARK: - Issue Models

struct TreeIssue: Identifiable, Hashable {
    let id: String
    let category: IssueCategory
    let severity: IssueSeverity
    let personXref: String?
    let familyXref: String?
    let title: String
    let detail: String
    let suggestion: String
    let isAutoFixable: Bool

    init(
        category: IssueCategory,
        severity: IssueSeverity,
        personXref: String? = nil,
        familyXref: String? = nil,
        title: String,
        detail: String,
        suggestion: String,
        isAutoFixable: Bool = false
    ) {
        self.id = UUID().uuidString
        self.category = category
        self.severity = severity
        self.personXref = personXref
        self.familyXref = familyXref
        self.title = title
        self.detail = detail
        self.suggestion = suggestion
        self.isAutoFixable = isAutoFixable
    }
}

enum IssueCategory: String, CaseIterable, Identifiable, Codable {
    case dateIssue = "Date Issues"
    case relationshipIssue = "Relationship Issues"
    case dataQuality = "Data Quality"
    case potentialDuplicate = "Potential Duplicates"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .dateIssue: return "calendar.badge.exclamationmark"
        case .relationshipIssue: return "person.2.slash"
        case .dataQuality: return "checkmark.seal"
        case .potentialDuplicate: return "person.2.fill"
        }
    }

    var colorName: String {
        switch self {
        case .dateIssue: return "red"
        case .relationshipIssue: return "orange"
        case .dataQuality: return "yellow"
        case .potentialDuplicate: return "blue"
        }
    }
}

enum IssueSeverity: String, CaseIterable, Comparable, Identifiable, Codable {
    case critical
    case warning
    case info

    var id: String { rawValue }

    var label: String {
        switch self {
        case .critical: return "Critical"
        case .warning: return "Warning"
        case .info: return "Info"
        }
    }

    var icon: String {
        switch self {
        case .critical: return "xmark.octagon.fill"
        case .warning: return "exclamationmark.triangle.fill"
        case .info: return "info.circle.fill"
        }
    }

    static func < (lhs: IssueSeverity, rhs: IssueSeverity) -> Bool {
        let order: [IssueSeverity] = [.critical, .warning, .info]
        return order.firstIndex(of: lhs)! < order.firstIndex(of: rhs)!
    }
}

// MARK: - Tree Analyzer

@MainActor
final class TreeAnalyzer {
    private let db: DatabaseService

    init(db: DatabaseService = .shared) {
        self.db = db
    }

    /// Run all analysis checks and return collected issues.
    func analyze() -> [TreeIssue] {
        guard let dbQueue = db.dbQueue else { return [] }

        var issues: [TreeIssue] = []

        do {
            try dbQueue.read { database in
                let persons = try GedcomPerson.fetchAll(database)
                let families = try GedcomFamily.fetchAll(database)
                let events = try GedcomEvent.fetchAll(database)
                let childLinks = try GedcomChildLink.fetchAll(database)

                // Build lookup tables
                let personByXref = Dictionary(uniqueKeysWithValues: persons.map { ($0.xref, $0) })
                let eventsByOwner = Dictionary(grouping: events, by: \.ownerXref)
                let childLinksByFamily = Dictionary(grouping: childLinks, by: \.familyXref)
                let childLinksByChild = Dictionary(grouping: childLinks, by: \.childXref)
                let familiesByXref = Dictionary(uniqueKeysWithValues: families.map { ($0.xref, $0) })

                // All spouse xrefs for quick lookup
                var spouseXrefs = Set<String>()
                for fam in families {
                    if !fam.partner1Xref.isEmpty { spouseXrefs.insert(fam.partner1Xref) }
                    if !fam.partner2Xref.isEmpty { spouseXrefs.insert(fam.partner2Xref) }
                }
                let childXrefs = Set(childLinks.map(\.childXref))

                // --- Date Issues ---
                issues.append(contentsOf: checkDateIssues(
                    persons: persons,
                    families: families,
                    eventsByOwner: eventsByOwner,
                    personByXref: personByXref,
                    childLinksByFamily: childLinksByFamily
                ))

                // --- Relationship Issues ---
                issues.append(contentsOf: checkRelationshipIssues(
                    persons: persons,
                    families: families,
                    eventsByOwner: eventsByOwner,
                    childXrefs: childXrefs,
                    spouseXrefs: spouseXrefs,
                    childLinksByChild: childLinksByChild,
                    childLinksByFamily: childLinksByFamily,
                    familiesByXref: familiesByXref,
                    personByXref: personByXref
                ))

                // --- Data Quality Issues ---
                issues.append(contentsOf: checkDataQuality(
                    persons: persons,
                    eventsByOwner: eventsByOwner
                ))

                // --- Potential Duplicates ---
                issues.append(contentsOf: checkDuplicates(persons: persons, eventsByOwner: eventsByOwner))
            }
        } catch {
            issues.append(TreeIssue(
                category: .dataQuality,
                severity: .critical,
                title: "Analysis Error",
                detail: "Failed to read database: \(error.localizedDescription)",
                suggestion: "Try re-importing the GEDCOM file."
            ))
        }

        return issues.sorted { $0.severity < $1.severity }
    }

    // MARK: - Date Checks

    private func checkDateIssues(
        persons: [GedcomPerson],
        families: [GedcomFamily],
        eventsByOwner: [String: [GedcomEvent]],
        personByXref: [String: GedcomPerson],
        childLinksByFamily: [String: [GedcomChildLink]]
    ) -> [TreeIssue] {
        var issues: [TreeIssue] = []
        let currentYear = Calendar.current.component(.year, from: Date())

        for person in persons {
            let events = eventsByOwner[person.xref] ?? []
            let birthYear = extractFirstYear(events: events, type: "BIRT")
            let deathYear = extractFirstYear(events: events, type: "DEAT")

            // Born after death
            if let b = birthYear, let d = deathYear, b > d {
                issues.append(TreeIssue(
                    category: .dateIssue,
                    severity: .critical,
                    personXref: person.xref,
                    title: "Born after death",
                    detail: "\(person.displayName) has birth year \(b) but death year \(d).",
                    suggestion: "Check and correct the birth or death date.",
                    isAutoFixable: false
                ))
            }

            // Future dates
            for event in events {
                if let year = extractYear(event.dateValue), year > currentYear {
                    issues.append(TreeIssue(
                        category: .dateIssue,
                        severity: .critical,
                        personXref: person.xref,
                        title: "Future date on \(event.displayType)",
                        detail: "\(person.displayName) has \(event.displayType) in year \(year), which is in the future.",
                        suggestion: "Verify and correct this date.",
                        isAutoFixable: false
                    ))
                }
            }

            // Missing birth date
            if birthYear == nil {
                issues.append(TreeIssue(
                    category: .dateIssue,
                    severity: .info,
                    personXref: person.xref,
                    title: "Missing birth date",
                    detail: "\(person.displayName) has no birth date recorded.",
                    suggestion: "Add a birth date if known.",
                    isAutoFixable: false
                ))
            }

            // Missing death date for people born > 110 years ago
            if let b = birthYear, (currentYear - b) > 110, deathYear == nil, !person.isLiving {
                let hasDeath = events.contains { $0.eventType == "DEAT" || $0.eventType == "BURI" }
                if !hasDeath {
                    issues.append(TreeIssue(
                        category: .dateIssue,
                        severity: .warning,
                        personXref: person.xref,
                        title: "Missing death date (born \(b))",
                        detail: "\(person.displayName) was born in \(b) but has no death record. Likely deceased.",
                        suggestion: "Add a death date or mark as deceased.",
                        isAutoFixable: false
                    ))
                }
            }
        }

        // Family-level date checks
        for family in families {
            let familyEvents = eventsByOwner[family.xref] ?? []
            let marriageYear = extractFirstYear(events: familyEvents, type: "MARR")

            let children = childLinksByFamily[family.xref] ?? []
            var childBirthYears: [(String, Int)] = []

            for link in children {
                let childEvents = eventsByOwner[link.childXref] ?? []
                if let by = extractFirstYear(events: childEvents, type: "BIRT") {
                    let childName = personByXref[link.childXref]?.displayName ?? link.childXref
                    childBirthYears.append((childName, by))
                }
            }

            // Check partners married before birth
            for partnerXref in [family.partner1Xref, family.partner2Xref] where !partnerXref.isEmpty {
                guard let partner = personByXref[partnerXref] else { continue }
                let partnerEvents = eventsByOwner[partnerXref] ?? []
                let partnerBirth = extractFirstYear(events: partnerEvents, type: "BIRT")

                if let mb = partnerBirth, let my = marriageYear, my < mb {
                    issues.append(TreeIssue(
                        category: .dateIssue,
                        severity: .critical,
                        personXref: partnerXref,
                        familyXref: family.xref,
                        title: "Married before birth",
                        detail: "\(partner.displayName) was born in \(mb) but married in \(my).",
                        suggestion: "Check marriage and birth dates.",
                        isAutoFixable: false
                    ))
                }
            }

            // Child born before parent
            for partnerXref in [family.partner1Xref, family.partner2Xref] where !partnerXref.isEmpty {
                guard let partner = personByXref[partnerXref] else { continue }
                let partnerEvents = eventsByOwner[partnerXref] ?? []
                let parentBirth = extractFirstYear(events: partnerEvents, type: "BIRT")

                if let pb = parentBirth {
                    for (childName, childBirth) in childBirthYears where childBirth <= pb {
                        issues.append(TreeIssue(
                            category: .dateIssue,
                            severity: .critical,
                            personXref: partnerXref,
                            familyXref: family.xref,
                            title: "Child born before parent",
                            detail: "\(childName) born in \(childBirth), but parent \(partner.displayName) born in \(pb).",
                            suggestion: "Verify birth dates of parent and child.",
                            isAutoFixable: false
                        ))
                    }
                }
            }

            // Children born too close together (< 9 months approximation: same year check)
            let sortedChildYears = childBirthYears.sorted { $0.1 < $1.1 }
            // Note: we only have year-level precision from GEDCOM, so we check for same year with different children
            // and for gaps > 30 years
            for i in 1..<sortedChildYears.count {
                let gap = sortedChildYears[i].1 - sortedChildYears[i-1].1
                if gap > 30 {
                    issues.append(TreeIssue(
                        category: .dateIssue,
                        severity: .warning,
                        familyXref: family.xref,
                        title: "Large gap between children",
                        detail: "\(sortedChildYears[i-1].0) (born \(sortedChildYears[i-1].1)) and \(sortedChildYears[i].0) (born \(sortedChildYears[i].1)) are \(gap) years apart.",
                        suggestion: "Verify these children belong to the same family.",
                        isAutoFixable: false
                    ))
                }
            }
        }

        return issues
    }

    // MARK: - Relationship Checks

    private func checkRelationshipIssues(
        persons: [GedcomPerson],
        families: [GedcomFamily],
        eventsByOwner: [String: [GedcomEvent]],
        childXrefs: Set<String>,
        spouseXrefs: Set<String>,
        childLinksByChild: [String: [GedcomChildLink]],
        childLinksByFamily: [String: [GedcomChildLink]],
        familiesByXref: [String: GedcomFamily],
        personByXref: [String: GedcomPerson]
    ) -> [TreeIssue] {
        var issues: [TreeIssue] = []

        for person in persons {
            let isChild = childXrefs.contains(person.xref)
            let isSpouse = spouseXrefs.contains(person.xref)

            // Missing parents
            if !isChild {
                issues.append(TreeIssue(
                    category: .relationshipIssue,
                    severity: .info,
                    personXref: person.xref,
                    title: "No parents linked",
                    detail: "\(person.displayName) is not linked to any family as a child.",
                    suggestion: "Add parent information if known.",
                    isAutoFixable: false
                ))
            }

            // Orphaned records (not connected to any family at all)
            if !isChild && !isSpouse {
                issues.append(TreeIssue(
                    category: .relationshipIssue,
                    severity: .warning,
                    personXref: person.xref,
                    title: "Orphaned record",
                    detail: "\(person.displayName) is not connected to any family as child or spouse.",
                    suggestion: "Link this person to the appropriate family.",
                    isAutoFixable: false
                ))
            }

            // Missing spouse (has marriage-related events but no FAMS link)
            let events = eventsByOwner[person.xref] ?? []
            let hasMarriageHint = events.contains { $0.eventType == "MARR" || $0.eventType == "DIV" }
            if hasMarriageHint && !isSpouse {
                issues.append(TreeIssue(
                    category: .relationshipIssue,
                    severity: .warning,
                    personXref: person.xref,
                    title: "Marriage event but no spouse",
                    detail: "\(person.displayName) has marriage/divorce events but is not linked as a spouse in any family.",
                    suggestion: "Link this person to the correct family record.",
                    isAutoFixable: false
                ))
            }
        }

        // Circular relationship detection (person is their own ancestor)
        // Use DFS from each person following parent links
        issues.append(contentsOf: detectCircularRelationships(
            persons: persons,
            childLinksByChild: childLinksByChild,
            familiesByXref: familiesByXref,
            personByXref: personByXref
        ))

        return issues
    }

    private func detectCircularRelationships(
        persons: [GedcomPerson],
        childLinksByChild: [String: [GedcomChildLink]],
        familiesByXref: [String: GedcomFamily],
        personByXref: [String: GedcomPerson]
    ) -> [TreeIssue] {
        var issues: [TreeIssue] = []
        var reportedCircles = Set<String>()

        for person in persons {
            var visited = Set<String>()
            var stack = [person.xref]

            while let current = stack.popLast() {
                if visited.contains(current) {
                    if current == person.xref && visited.count > 1 && !reportedCircles.contains(current) {
                        reportedCircles.insert(current)
                        issues.append(TreeIssue(
                            category: .relationshipIssue,
                            severity: .critical,
                            personXref: person.xref,
                            title: "Circular relationship detected",
                            detail: "\(person.displayName) appears to be their own ancestor through a chain of parent links.",
                            suggestion: "Review parent-child links for errors in the ancestor chain.",
                            isAutoFixable: false
                        ))
                    }
                    continue
                }
                visited.insert(current)

                // Find parents of current
                let familyLinks = childLinksByChild[current] ?? []
                for link in familyLinks {
                    guard let family = familiesByXref[link.familyXref] else { continue }
                    if !family.partner1Xref.isEmpty { stack.append(family.partner1Xref) }
                    if !family.partner2Xref.isEmpty { stack.append(family.partner2Xref) }
                }
            }
        }

        return issues
    }

    // MARK: - Data Quality Checks

    private func checkDataQuality(
        persons: [GedcomPerson],
        eventsByOwner: [String: [GedcomEvent]]
    ) -> [TreeIssue] {
        var issues: [TreeIssue] = []

        for person in persons {
            let events = eventsByOwner[person.xref] ?? []

            // Missing sex/gender
            if person.sex == "U" || person.sex.isEmpty {
                issues.append(TreeIssue(
                    category: .dataQuality,
                    severity: .info,
                    personXref: person.xref,
                    title: "Missing gender",
                    detail: "\(person.displayName) has no sex/gender recorded.",
                    suggestion: "Set the sex field to M or F.",
                    isAutoFixable: false
                ))
            }

            // Names with suspicious characters
            let fullName = person.givenName + " " + person.surname
            let suspiciousPattern = /[0-9@#$%^&*()_+=\[\]{}|\\<>~`]/
            if fullName.firstMatch(of: suspiciousPattern) != nil {
                issues.append(TreeIssue(
                    category: .dataQuality,
                    severity: .warning,
                    personXref: person.xref,
                    title: "Suspicious characters in name",
                    detail: "\(person.displayName) contains unusual characters.",
                    suggestion: "Review and clean up the name field.",
                    isAutoFixable: true
                ))
            }

            // Empty name
            if person.givenName.isEmpty && person.surname.isEmpty {
                issues.append(TreeIssue(
                    category: .dataQuality,
                    severity: .warning,
                    personXref: person.xref,
                    title: "Missing name",
                    detail: "Person \(person.xref) has no given name or surname.",
                    suggestion: "Add a name for this person.",
                    isAutoFixable: false
                ))
            }

            // Duplicate facts (same event type with same date)
            let eventsByType = Dictionary(grouping: events.filter { !$0.dateValue.isEmpty }, by: \.eventType)
            for (_, typeEvents) in eventsByType {
                let dateGroups = Dictionary(grouping: typeEvents, by: \.dateValue)
                for (date, dupes) in dateGroups where dupes.count > 1 {
                    let displayType = dupes[0].displayType
                    issues.append(TreeIssue(
                        category: .dataQuality,
                        severity: .warning,
                        personXref: person.xref,
                        title: "Duplicate \(displayType) fact",
                        detail: "\(person.displayName) has \(dupes.count) \(displayType) events with date \(date).",
                        suggestion: "Remove the duplicate \(displayType) record.",
                        isAutoFixable: true
                    ))
                }
            }

            // Missing sources on key events (birth, death, marriage)
            let keyEventTypes: Set<String> = ["BIRT", "DEAT"]
            for event in events where keyEventTypes.contains(event.eventType) {
                // Since we don't have source citation links in the DB,
                // we flag key events that have no date AND no place as likely unsourced
                if event.dateValue.isEmpty && event.place.isEmpty {
                    issues.append(TreeIssue(
                        category: .dataQuality,
                        severity: .info,
                        personXref: person.xref,
                        title: "Sparse \(event.displayType) record",
                        detail: "\(person.displayName) has a \(event.displayType) event with no date and no place.",
                        suggestion: "Add date and place details for this event.",
                        isAutoFixable: false
                    ))
                }
            }

            // Place formatting inconsistencies — flag places without commas (likely incomplete)
            let placesUsed = events.map(\.place).filter { !$0.isEmpty }
            for place in placesUsed {
                if !place.contains(",") && place.count > 3 {
                    issues.append(TreeIssue(
                        category: .dataQuality,
                        severity: .info,
                        personXref: person.xref,
                        title: "Incomplete place format",
                        detail: "Place \"\(place)\" on \(person.displayName) may be missing locality details (no commas found).",
                        suggestion: "Use hierarchical format: City, County, State, Country.",
                        isAutoFixable: false
                    ))
                    break  // Only flag once per person
                }
            }
        }

        return issues
    }

    // MARK: - Duplicate Detection

    private func checkDuplicates(
        persons: [GedcomPerson],
        eventsByOwner: [String: [GedcomEvent]]
    ) -> [TreeIssue] {
        var issues: [TreeIssue] = []
        var reportedPairs = Set<String>()

        // Build lookup: normalized name -> persons
        struct PersonKey: Hashable {
            let normalizedSurname: String
            let birthYear: Int?
        }

        struct PersonRecord {
            let person: GedcomPerson
            let birthYear: Int?
            let normalizedGiven: String
            let normalizedSurname: String
        }

        var records: [PersonRecord] = []
        for person in persons {
            let events = eventsByOwner[person.xref] ?? []
            let by = extractFirstYear(events: events, type: "BIRT")
            records.append(PersonRecord(
                person: person,
                birthYear: by,
                normalizedGiven: person.givenName.lowercased().trimmingCharacters(in: .whitespaces),
                normalizedSurname: person.surname.lowercased().trimmingCharacters(in: .whitespaces)
            ))
        }

        // Group by surname for efficiency
        let bySurname = Dictionary(grouping: records, by: \.normalizedSurname)

        for (_, group) in bySurname where !group.isEmpty {
            for i in 0..<group.count {
                for j in (i+1)..<group.count {
                    let a = group[i]
                    let b = group[j]

                    // Same given name (exact or fuzzy)
                    let givenMatch = a.normalizedGiven == b.normalizedGiven
                        || (a.normalizedGiven.count > 2 && b.normalizedGiven.count > 2
                            && levenshteinSimilarity(a.normalizedGiven, b.normalizedGiven) > 0.80)

                    guard givenMatch else { continue }

                    // Birth year within 5 years
                    let yearMatch: Bool
                    if let ay = a.birthYear, let by = b.birthYear {
                        yearMatch = abs(ay - by) <= 5
                    } else {
                        // If either has no birth year, still flag if names match exactly
                        yearMatch = a.normalizedGiven == b.normalizedGiven
                    }

                    guard yearMatch else { continue }

                    let pairKey = [a.person.xref, b.person.xref].sorted().joined(separator: "-")
                    guard !reportedPairs.contains(pairKey) else { continue }
                    reportedPairs.insert(pairKey)

                    let yearDetail: String
                    if let ay = a.birthYear, let by = b.birthYear {
                        yearDetail = " (born \(ay) vs \(by))"
                    } else {
                        yearDetail = ""
                    }

                    issues.append(TreeIssue(
                        category: .potentialDuplicate,
                        severity: .warning,
                        personXref: a.person.xref,
                        title: "Possible duplicate",
                        detail: "\(a.person.displayName) and \(b.person.displayName) have similar names\(yearDetail).",
                        suggestion: "Review and merge if these are the same person.",
                        isAutoFixable: false
                    ))
                }
            }
        }

        return issues
    }

    // MARK: - Helpers

    private func extractFirstYear(events: [GedcomEvent], type: String) -> Int? {
        events
            .filter { $0.eventType == type }
            .compactMap { extractYear($0.dateValue) }
            .first
    }

    private func extractYear(_ dateString: String) -> Int? {
        let pattern = /(\d{4})/
        guard let match = dateString.firstMatch(of: pattern) else { return nil }
        return Int(match.1)
    }

    /// Simple Levenshtein-based similarity (0.0 to 1.0)
    private func levenshteinSimilarity(_ a: String, _ b: String) -> Double {
        let aChars = Array(a)
        let bChars = Array(b)
        let aLen = aChars.count
        let bLen = bChars.count

        if aLen == 0 && bLen == 0 { return 1.0 }
        if aLen == 0 || bLen == 0 { return 0.0 }

        var prev = Array(0...bLen)
        var curr = Array(repeating: 0, count: bLen + 1)

        for i in 1...aLen {
            curr[0] = i
            for j in 1...bLen {
                let cost = aChars[i-1] == bChars[j-1] ? 0 : 1
                curr[j] = min(prev[j] + 1, curr[j-1] + 1, prev[j-1] + cost)
            }
            prev = curr
        }

        let maxLen = max(aLen, bLen)
        return 1.0 - Double(prev[bLen]) / Double(maxLen)
    }
}
