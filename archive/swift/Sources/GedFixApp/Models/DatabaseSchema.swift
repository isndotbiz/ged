import Foundation
import GRDB

struct DatabaseSchema {
    static var migrator: DatabaseMigrator {
        var migrator = DatabaseMigrator()

        migrator.registerMigration("v1-create-tables") { db in
            try db.create(table: "person") { t in
                t.primaryKey("id", .text)
                t.column("xref", .text).notNull().unique()
                t.column("givenName", .text).notNull().defaults(to: "")
                t.column("surname", .text).notNull().defaults(to: "")
                t.column("suffix", .text).notNull().defaults(to: "")
                t.column("sex", .text).notNull().defaults(to: "U")
                t.column("isLiving", .boolean).notNull().defaults(to: false)
            }

            try db.create(table: "family") { t in
                t.primaryKey("id", .text)
                t.column("xref", .text).notNull().unique()
                t.column("partner1Xref", .text).notNull().defaults(to: "")
                t.column("partner2Xref", .text).notNull().defaults(to: "")
            }

            try db.create(table: "childLink") { t in
                t.column("familyXref", .text).notNull()
                t.column("childXref", .text).notNull()
                t.column("childOrder", .integer).notNull().defaults(to: 0)
                t.primaryKey(["familyXref", "childXref"])
            }

            try db.create(table: "event") { t in
                t.primaryKey("id", .text)
                t.column("ownerXref", .text).notNull()
                t.column("ownerType", .text).notNull().defaults(to: "INDI")
                t.column("eventType", .text).notNull()
                t.column("dateValue", .text).notNull().defaults(to: "")
                t.column("place", .text).notNull().defaults(to: "")
                t.column("description", .text).notNull().defaults(to: "")
            }
            try db.create(index: "event_owner", on: "event", columns: ["ownerXref"])

            try db.create(table: "place") { t in
                t.primaryKey("id", .text)
                t.column("name", .text).notNull()
                t.column("normalized", .text).notNull().defaults(to: "")
                t.column("latitude", .double)
                t.column("longitude", .double)
                t.column("eventCount", .integer).notNull().defaults(to: 0)
            }
            try db.create(index: "place_name", on: "place", columns: ["name"], unique: true)

            try db.create(table: "source") { t in
                t.primaryKey("id", .text)
                t.column("xref", .text).notNull().unique()
                t.column("title", .text).notNull().defaults(to: "")
                t.column("author", .text).notNull().defaults(to: "")
                t.column("publisher", .text).notNull().defaults(to: "")
                t.column("repository", .text).notNull().defaults(to: "")
            }
        }

        return migrator
    }
}
