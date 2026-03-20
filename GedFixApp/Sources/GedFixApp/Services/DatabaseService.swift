import Foundation
import GRDB

@MainActor
@Observable
final class DatabaseService {
    static let shared = DatabaseService()

    private(set) var dbQueue: DatabaseQueue?

    var personCount: Int = 0
    var familyCount: Int = 0
    var eventCount: Int = 0
    var placeCount: Int = 0
    var sourceCount: Int = 0

    private init() {}

    func openInMemory() throws {
        let db = try DatabaseQueue()
        try DatabaseSchema.migrator.migrate(db)
        self.dbQueue = db
    }

    func openFile(_ url: URL) throws {
        let db = try DatabaseQueue(path: url.path)
        try DatabaseSchema.migrator.migrate(db)
        self.dbQueue = db
    }

    // MARK: - Import

    func importParseResult(_ result: GedcomParseResult) throws {
        guard let db = dbQueue else { throw DatabaseError(message: "No database open") }

        try db.write { db in
            // Clear existing data
            try db.execute(sql: "DELETE FROM event")
            try db.execute(sql: "DELETE FROM childLink")
            try db.execute(sql: "DELETE FROM family")
            try db.execute(sql: "DELETE FROM person")
            try db.execute(sql: "DELETE FROM place")
            try db.execute(sql: "DELETE FROM source")

            for person in result.persons {
                try person.insert(db)
            }
            for family in result.families {
                try family.insert(db)
            }
            for link in result.childLinks {
                try link.insert(db)
            }
            for event in result.events {
                try event.insert(db)
            }
            for place in result.places {
                try place.insert(db)
            }
            for source in result.sources {
                try source.insert(db)
            }
        }

        refreshCounts()
    }

    // MARK: - Queries

    func refreshCounts() {
        guard let db = dbQueue else { return }
        do {
            try db.read { db in
                self.personCount = try GedcomPerson.fetchCount(db)
                self.familyCount = try GedcomFamily.fetchCount(db)
                self.eventCount = try GedcomEvent.fetchCount(db)
                self.placeCount = try GedcomPlace.fetchCount(db)
                self.sourceCount = try GedcomSource.fetchCount(db)
            }
        } catch {
            print("Count refresh error: \(error)")
        }
    }

    func fetchPersons(search: String = "", sortBy: PersonSort = .surname) -> [GedcomPerson] {
        guard let db = dbQueue else { return [] }
        do {
            return try db.read { db in
                var request = GedcomPerson.all()
                if !search.isEmpty {
                    let pattern = "%\(search)%"
                    request = request.filter(
                        Column("givenName").like(pattern) || Column("surname").like(pattern)
                    )
                }
                switch sortBy {
                case .surname:
                    request = request.order(Column("surname"), Column("givenName"))
                case .givenName:
                    request = request.order(Column("givenName"), Column("surname"))
                }
                return try request.fetchAll(db)
            }
        } catch {
            print("Fetch error: \(error)")
            return []
        }
    }

    func fetchFamilies() -> [GedcomFamily] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { try GedcomFamily.fetchAll($0) }) ?? []
    }

    func fetchEvents(forXref xref: String) -> [GedcomEvent] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { db in
            try GedcomEvent.filter(Column("ownerXref") == xref)
                .order(Column("eventType"))
                .fetchAll(db)
        }) ?? []
    }

    func fetchPerson(byXref xref: String) -> GedcomPerson? {
        guard let db = dbQueue else { return nil }
        return try? db.read { db in
            try GedcomPerson.filter(Column("xref") == xref).fetchOne(db)
        }
    }

    func fetchChildLinks(forFamily xref: String) -> [GedcomChildLink] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { db in
            try GedcomChildLink.filter(Column("familyXref") == xref)
                .order(Column("childOrder"))
                .fetchAll(db)
        }) ?? []
    }

    func fetchFamiliesAsSpouse(forXref xref: String) -> [GedcomFamily] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { db in
            try GedcomFamily.filter(
                Column("partner1Xref") == xref || Column("partner2Xref") == xref
            ).fetchAll(db)
        }) ?? []
    }

    func fetchFamiliesAsChild(forXref xref: String) -> [GedcomFamily] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { db in
            let childFamilyXrefs = try GedcomChildLink
                .filter(Column("childXref") == xref)
                .select(Column("familyXref"))
                .fetchAll(db)
                .map(\.familyXref)
            return try GedcomFamily.filter(childFamilyXrefs.contains(Column("xref"))).fetchAll(db)
        }) ?? []
    }

    func fetchPlaces() -> [GedcomPlace] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { db in
            try GedcomPlace.order(Column("eventCount").desc).fetchAll(db)
        }) ?? []
    }

    func fetchSources() -> [GedcomSource] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { try GedcomSource.order(Column("title")).fetchAll($0) }) ?? []
    }

    func fetchTopSurnames(limit: Int = 10) -> [(String, Int)] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { db in
            let rows = try Row.fetchAll(db, sql: """
                SELECT surname, COUNT(*) as cnt FROM person
                WHERE surname != '' GROUP BY surname ORDER BY cnt DESC LIMIT ?
                """, arguments: [limit])
            return rows.map { (row: Row) -> (String, Int) in
                (row["surname"] as String, row["cnt"] as Int)
            }
        }) ?? []
    }

    // MARK: - Mutations

    func updatePerson(_ person: GedcomPerson) throws {
        guard let db = dbQueue else { throw DatabaseError(message: "No database open") }
        try db.write { db in
            try person.update(db)
        }
        refreshCounts()
    }

    func insertPerson(_ person: GedcomPerson) throws {
        guard let db = dbQueue else { throw DatabaseError(message: "No database open") }
        try db.write { db in
            try person.insert(db)
        }
        refreshCounts()
    }

    func deletePerson(xref: String) throws {
        guard let db = dbQueue else { throw DatabaseError(message: "No database open") }
        try db.write { db in
            // Delete events owned by this person
            try GedcomEvent.filter(Column("ownerXref") == xref).deleteAll(db)
            // Remove child links referencing this person
            try GedcomChildLink.filter(Column("childXref") == xref).deleteAll(db)
            // Remove families where this person is a partner (and their child links)
            let partnerFamilies = try GedcomFamily.filter(
                Column("partner1Xref") == xref || Column("partner2Xref") == xref
            ).fetchAll(db)
            for family in partnerFamilies {
                try GedcomChildLink.filter(Column("familyXref") == family.xref).deleteAll(db)
                try GedcomEvent.filter(Column("ownerXref") == family.xref).deleteAll(db)
                try family.delete(db)
            }
            // Delete the person
            try GedcomPerson.filter(Column("xref") == xref).deleteAll(db)
        }
        refreshCounts()
    }

    func insertEvent(_ event: GedcomEvent) throws {
        guard let db = dbQueue else { throw DatabaseError(message: "No database open") }
        try db.write { db in
            try event.insert(db)
        }
        refreshCounts()
    }

    func updateEvent(_ event: GedcomEvent) throws {
        guard let db = dbQueue else { throw DatabaseError(message: "No database open") }
        try db.write { db in
            try event.update(db)
        }
    }

    func deleteEvent(id: String) throws {
        guard let db = dbQueue else { throw DatabaseError(message: "No database open") }
        try db.write { db in
            try GedcomEvent.filter(Column("id") == id).deleteAll(db)
        }
        refreshCounts()
    }

    func addChildToFamily(familyXref: String, childXref: String) throws {
        guard let db = dbQueue else { throw DatabaseError(message: "No database open") }
        try db.write { db in
            let maxOrder = try GedcomChildLink
                .filter(Column("familyXref") == familyXref)
                .select(max(Column("childOrder")))
                .fetchOne(db) as Int? ?? 0
            let link = GedcomChildLink(familyXref: familyXref, childXref: childXref, childOrder: maxOrder + 1)
            try link.insert(db)
        }
    }

    func removeChildFromFamily(familyXref: String, childXref: String) throws {
        guard let db = dbQueue else { throw DatabaseError(message: "No database open") }
        try db.write { db in
            try GedcomChildLink.filter(
                Column("familyXref") == familyXref && Column("childXref") == childXref
            ).deleteAll(db)
        }
    }

    func createFamily(partner1Xref: String, partner2Xref: String) throws -> GedcomFamily {
        guard let db = dbQueue else { throw DatabaseError(message: "No database open") }
        let maxNum = try db.read { db -> Int in
            let rows = try Row.fetchAll(db, sql: """
                SELECT xref FROM family WHERE xref LIKE '@F%'
                ORDER BY CAST(SUBSTR(xref, 3, LENGTH(xref) - 3) AS INTEGER) DESC LIMIT 1
                """)
            if let row = rows.first {
                let xref: String = row["xref"]
                let numStr = xref.replacingOccurrences(of: "@F", with: "").replacingOccurrences(of: "@", with: "")
                return Int(numStr) ?? 0
            }
            return 0
        }
        let newXref = "@F\(maxNum + 1)@"
        let family = GedcomFamily(
            id: UUID().uuidString,
            xref: newXref,
            partner1Xref: partner1Xref,
            partner2Xref: partner2Xref
        )
        try db.write { db in
            try family.insert(db)
        }
        refreshCounts()
        return family
    }

    func nextPersonXref() throws -> String {
        guard let db = dbQueue else { throw DatabaseError(message: "No database open") }
        let maxNum = try db.read { db -> Int in
            let rows = try Row.fetchAll(db, sql: """
                SELECT xref FROM person WHERE xref LIKE '@I%'
                ORDER BY CAST(SUBSTR(xref, 3, LENGTH(xref) - 3) AS INTEGER) DESC LIMIT 1
                """)
            if let row = rows.first {
                let xref: String = row["xref"]
                let numStr = xref.replacingOccurrences(of: "@I", with: "").replacingOccurrences(of: "@", with: "")
                return Int(numStr) ?? 0
            }
            return 0
        }
        return "@I\(maxNum + 1)@"
    }

    func fetchAllPersons() -> [GedcomPerson] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { try GedcomPerson.order(Column("surname"), Column("givenName")).fetchAll($0) }) ?? []
    }

    func fetchAllFamilies() -> [GedcomFamily] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { try GedcomFamily.fetchAll($0) }) ?? []
    }

    func fetchAllEvents() -> [GedcomEvent] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { try GedcomEvent.fetchAll($0) }) ?? []
    }

    func fetchAllChildLinks() -> [GedcomChildLink] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { try GedcomChildLink.fetchAll($0) }) ?? []
    }

    func fetchAllSources() -> [GedcomSource] {
        guard let db = dbQueue else { return [] }
        return (try? db.read { try GedcomSource.fetchAll($0) }) ?? []
    }

    // MARK: - Pedigree Queries

    func fetchParents(ofXref xref: String) -> (father: GedcomPerson?, mother: GedcomPerson?) {
        let families = fetchFamiliesAsChild(forXref: xref)
        guard let family = families.first else { return (nil, nil) }
        let father = family.partner1Xref.isEmpty ? nil : fetchPerson(byXref: family.partner1Xref)
        let mother = family.partner2Xref.isEmpty ? nil : fetchPerson(byXref: family.partner2Xref)
        return (father, mother)
    }

    func fetchBirthEvent(forXref xref: String) -> GedcomEvent? {
        let events = fetchEvents(forXref: xref)
        return events.first(where: { $0.eventType == "BIRT" })
    }

    func fetchDeathEvent(forXref xref: String) -> GedcomEvent? {
        let events = fetchEvents(forXref: xref)
        return events.first(where: { $0.eventType == "DEAT" })
    }

    enum PersonSort {
        case surname, givenName
    }
}
