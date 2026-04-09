import Foundation

/// Result of parsing a GEDCOM file
struct GedcomParseResult: Sendable {
    var persons: [GedcomPerson]
    var families: [GedcomFamily]
    var childLinks: [GedcomChildLink]
    var events: [GedcomEvent]
    var places: [GedcomPlace]
    var sources: [GedcomSource]
    var lineCount: Int
}

/// A single parsed GEDCOM line
private struct GedLine {
    let level: Int
    let xref: String?  // e.g. @I123@
    let tag: String
    let value: String
}

/// Line-oriented GEDCOM 5.5.1 parser
/// Ported from gedfix/checker.py (Python)
enum GedcomParser {

    // MARK: - Public API

    static func parse(fileURL: URL) throws -> GedcomParseResult {
        let data = try Data(contentsOf: fileURL)
        // Handle BOM
        let text: String
        if data.starts(with: [0xEF, 0xBB, 0xBF]) {
            text = String(data: data.dropFirst(3), encoding: .utf8) ?? ""
        } else {
            text = String(data: data, encoding: .utf8)
                ?? String(data: data, encoding: .isoLatin1)
                ?? ""
        }

        let rawLines = text.components(separatedBy: .newlines)
        let parsedLines = rawLines.compactMap { parseLine($0) }
        let records = groupRecords(parsedLines)

        var persons: [GedcomPerson] = []
        var families: [GedcomFamily] = []
        var childLinks: [GedcomChildLink] = []
        var events: [GedcomEvent] = []
        var placesDict: [String: GedcomPlace] = [:]
        var sources: [GedcomSource] = []

        for record in records {
            guard let header = record.first else { continue }

            if header.tag == "INDI", let xref = header.xref {
                let (person, personEvents) = parseINDI(xref: xref, lines: record)
                persons.append(person)
                for evt in personEvents {
                    events.append(evt)
                    if !evt.place.isEmpty {
                        trackPlace(evt.place, in: &placesDict)
                    }
                }
            } else if header.tag == "FAM", let xref = header.xref {
                let (family, links, famEvents) = parseFAM(xref: xref, lines: record)
                families.append(family)
                childLinks.append(contentsOf: links)
                for evt in famEvents {
                    events.append(evt)
                    if !evt.place.isEmpty {
                        trackPlace(evt.place, in: &placesDict)
                    }
                }
            } else if header.tag == "SOUR", let xref = header.xref {
                let source = parseSOUR(xref: xref, lines: record)
                sources.append(source)
            }
        }

        // Detect living persons
        let currentYear = Calendar.current.component(.year, from: Date())
        for i in persons.indices {
            let xref = persons[i].xref
            let personEvents = events.filter { $0.ownerXref == xref }
            let hasDeath = personEvents.contains { $0.eventType == "DEAT" || $0.eventType == "BURI" }

            if hasDeath {
                persons[i].isLiving = false
            } else {
                let birthYear = personEvents
                    .filter { $0.eventType == "BIRT" }
                    .compactMap { extractYear($0.dateValue) }
                    .first

                if let by = birthYear, (currentYear - by) > 110 {
                    persons[i].isLiving = false
                } else {
                    persons[i].isLiving = true
                }
            }
        }

        return GedcomParseResult(
            persons: persons,
            families: families,
            childLinks: childLinks,
            events: events,
            places: Array(placesDict.values),
            sources: sources,
            lineCount: rawLines.count
        )
    }

    // MARK: - Line Parsing

    private static func parseLine(_ raw: String) -> GedLine? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        let parts = trimmed.split(separator: " ", maxSplits: 2, omittingEmptySubsequences: true)
        guard parts.count >= 2, let level = Int(parts[0]) else { return nil }

        let second = String(parts[1])

        // Check if second token is an xref (@X@)
        if second.hasPrefix("@") && second.hasSuffix("@") && parts.count >= 3 {
            let xref = second
            let tag = String(parts[2].split(separator: " ", maxSplits: 1).first ?? "")
            let value = parts[2].split(separator: " ", maxSplits: 1).dropFirst().first.map(String.init) ?? ""
            return GedLine(level: level, xref: xref, tag: tag, value: value)
        }

        let tag = second
        let value = parts.count >= 3 ? String(parts[2]) : ""
        return GedLine(level: level, xref: nil, tag: tag, value: value)
    }

    // MARK: - Record Grouping

    private static func groupRecords(_ lines: [GedLine]) -> [[GedLine]] {
        var records: [[GedLine]] = []
        var current: [GedLine] = []

        for line in lines {
            if line.level == 0 {
                if !current.isEmpty {
                    records.append(current)
                }
                current = [line]
            } else {
                current.append(line)
            }
        }
        if !current.isEmpty {
            records.append(current)
        }
        return records
    }

    // MARK: - INDI Parsing

    private static func parseINDI(xref: String, lines: [GedLine]) -> (GedcomPerson, [GedcomEvent]) {
        var givenName = ""
        var surname = ""
        var suffix = ""
        var sex = "U"
        var events: [GedcomEvent] = []

        var currentTag: String?
        var currentDate = ""
        var currentPlace = ""

        for line in lines.dropFirst() {
            if line.level == 1 {
                // Flush previous event
                if let tag = currentTag, isEventTag(tag) {
                    events.append(makeEvent(owner: xref, ownerType: "INDI", type: tag, date: currentDate, place: currentPlace))
                }
                currentTag = line.tag
                currentDate = ""
                currentPlace = ""

                switch line.tag {
                case "NAME":
                    let parsed = parseName(line.value)
                    if givenName.isEmpty {
                        givenName = parsed.given
                        surname = parsed.surname
                        suffix = parsed.suffix
                    }
                case "SEX":
                    sex = line.value.trimmingCharacters(in: .whitespaces)
                default:
                    break
                }
            } else if line.level == 2 {
                switch line.tag {
                case "DATE":
                    currentDate = line.value
                case "PLAC":
                    currentPlace = line.value
                case "GIVN":
                    if givenName.isEmpty { givenName = line.value }
                case "SURN":
                    if surname.isEmpty { surname = line.value }
                case "NSFX":
                    if suffix.isEmpty { suffix = line.value }
                default:
                    break
                }
            }
        }

        // Flush last event
        if let tag = currentTag, isEventTag(tag) {
            events.append(makeEvent(owner: xref, ownerType: "INDI", type: tag, date: currentDate, place: currentPlace))
        }

        let person = GedcomPerson(
            id: UUID().uuidString,
            xref: xref,
            givenName: givenName.trimmingCharacters(in: .whitespaces),
            surname: surname.trimmingCharacters(in: .whitespaces),
            suffix: suffix.trimmingCharacters(in: .whitespaces),
            sex: sex,
            isLiving: false  // set later
        )
        return (person, events)
    }

    // MARK: - FAM Parsing

    private static func parseFAM(xref: String, lines: [GedLine]) -> (GedcomFamily, [GedcomChildLink], [GedcomEvent]) {
        var husbXref = ""
        var wifeXref = ""
        var children: [String] = []
        var events: [GedcomEvent] = []

        var currentTag: String?
        var currentDate = ""
        var currentPlace = ""

        for line in lines.dropFirst() {
            if line.level == 1 {
                // Flush event
                if let tag = currentTag, isEventTag(tag) {
                    events.append(makeEvent(owner: xref, ownerType: "FAM", type: tag, date: currentDate, place: currentPlace))
                }
                currentTag = line.tag
                currentDate = ""
                currentPlace = ""

                switch line.tag {
                case "HUSB":
                    husbXref = line.value
                case "WIFE":
                    wifeXref = line.value
                case "CHIL":
                    children.append(line.value)
                default:
                    break
                }
            } else if line.level == 2 {
                if line.tag == "DATE" { currentDate = line.value }
                if line.tag == "PLAC" { currentPlace = line.value }
            }
        }

        if let tag = currentTag, isEventTag(tag) {
            events.append(makeEvent(owner: xref, ownerType: "FAM", type: tag, date: currentDate, place: currentPlace))
        }

        let family = GedcomFamily(
            id: UUID().uuidString,
            xref: xref,
            partner1Xref: husbXref,
            partner2Xref: wifeXref
        )

        let links = children.enumerated().map { (i, childXref) in
            GedcomChildLink(familyXref: xref, childXref: childXref, childOrder: i)
        }

        return (family, links, events)
    }

    // MARK: - SOUR Parsing

    private static func parseSOUR(xref: String, lines: [GedLine]) -> GedcomSource {
        var title = ""
        var author = ""
        var publisher = ""
        var repository = ""

        for line in lines.dropFirst() {
            if line.level == 1 {
                switch line.tag {
                case "TITL": title = line.value
                case "AUTH": author = line.value
                case "PUBL": publisher = line.value
                case "REPO": repository = line.value
                default: break
                }
            }
        }

        return GedcomSource(
            id: UUID().uuidString,
            xref: xref,
            title: title,
            author: author,
            publisher: publisher,
            repository: repository
        )
    }

    // MARK: - Helpers

    private static func parseName(_ raw: String) -> (given: String, surname: String, suffix: String) {
        // GEDCOM name format: "Given /Surname/ Suffix"
        let parts = raw.split(separator: "/", omittingEmptySubsequences: false)
        if parts.count >= 2 {
            let given = String(parts[0]).trimmingCharacters(in: .whitespaces)
            let surname = String(parts[1]).trimmingCharacters(in: .whitespaces)
            let suffix = parts.count >= 3 ? String(parts[2]).trimmingCharacters(in: .whitespaces) : ""
            return (given, surname, suffix)
        }
        return (raw.trimmingCharacters(in: .whitespaces), "", "")
    }

    private static let eventTags: Set<String> = [
        "BIRT", "DEAT", "MARR", "BURI", "CHR", "BAPM", "RESI", "CENS",
        "IMMI", "EMIG", "NATU", "GRAD", "RETI", "PROB", "WILL", "DIV",
        "EVEN", "CREM", "ADOP", "CONF", "FCOM", "ORDN", "ANUL"
    ]

    private static func isEventTag(_ tag: String) -> Bool {
        eventTags.contains(tag)
    }

    private static func makeEvent(owner: String, ownerType: String, type: String, date: String, place: String) -> GedcomEvent {
        GedcomEvent(
            id: UUID().uuidString,
            ownerXref: owner,
            ownerType: ownerType,
            eventType: type,
            dateValue: date.trimmingCharacters(in: .whitespaces),
            place: place.trimmingCharacters(in: .whitespaces),
            description: ""
        )
    }

    private static func trackPlace(_ name: String, in dict: inout [String: GedcomPlace]) {
        if var existing = dict[name] {
            existing.eventCount += 1
            dict[name] = existing
        } else {
            dict[name] = GedcomPlace(
                id: UUID().uuidString,
                name: name,
                normalized: name,
                latitude: nil,
                longitude: nil,
                eventCount: 1
            )
        }
    }

    private static func extractYear(_ dateString: String) -> Int? {
        let pattern = /(\d{4})/
        guard let match = dateString.firstMatch(of: pattern) else { return nil }
        return Int(match.1)
    }
}
