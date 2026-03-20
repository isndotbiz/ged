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

    enum PersonSort {
        case surname, givenName
    }
}
