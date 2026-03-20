import Foundation
import GRDB

// MARK: - Person

struct GedcomPerson: Codable, FetchableRecord, PersistableRecord, Identifiable, Hashable {
    static let databaseTableName = "person"

    var id: String  // UUID string
    var xref: String  // GEDCOM xref like @I123@
    var givenName: String
    var surname: String
    var suffix: String
    var sex: String  // M, F, U
    var isLiving: Bool

    var displayName: String {
        let parts = [givenName, surname, suffix].filter { !$0.isEmpty }
        return parts.joined(separator: " ")
    }

    var initials: String {
        let g = givenName.first.map(String.init) ?? ""
        let s = surname.first.map(String.init) ?? ""
        return (g + s).uppercased()
    }
}

// MARK: - Family

struct GedcomFamily: Codable, FetchableRecord, PersistableRecord, Identifiable, Hashable {
    static let databaseTableName = "family"

    var id: String
    var xref: String
    var partner1Xref: String  // husband/partner1
    var partner2Xref: String  // wife/partner2
}

// MARK: - Child Link

struct GedcomChildLink: Codable, FetchableRecord, PersistableRecord, Hashable {
    static let databaseTableName = "childLink"

    var familyXref: String
    var childXref: String
    var childOrder: Int
}

// MARK: - Event

struct GedcomEvent: Codable, FetchableRecord, PersistableRecord, Identifiable, Hashable {
    static let databaseTableName = "event"

    var id: String
    var ownerXref: String  // person or family xref
    var ownerType: String  // "INDI" or "FAM"
    var eventType: String  // BIRT, DEAT, MARR, BURI, CHR, etc.
    var dateValue: String  // raw GEDCOM date string
    var place: String
    var description: String

    var displayDate: String {
        dateValue.isEmpty ? "" : dateValue
    }

    var displayType: String {
        switch eventType {
        case "BIRT": return "Birth"
        case "DEAT": return "Death"
        case "MARR": return "Marriage"
        case "BURI": return "Burial"
        case "CHR": return "Christening"
        case "BAPM": return "Baptism"
        case "RESI": return "Residence"
        case "CENS": return "Census"
        case "IMMI": return "Immigration"
        case "EMIG": return "Emigration"
        case "NATU": return "Naturalization"
        case "GRAD": return "Graduation"
        case "RETI": return "Retirement"
        case "PROB": return "Probate"
        case "WILL": return "Will"
        case "DIV": return "Divorce"
        default: return eventType
        }
    }

    var eventIcon: String {
        switch eventType {
        case "BIRT": return "figure.and.child.holdinghands"
        case "DEAT": return "leaf.fill"
        case "MARR": return "heart.fill"
        case "BURI": return "cross.fill"
        case "CHR", "BAPM": return "drop.fill"
        case "RESI", "CENS": return "house.fill"
        case "IMMI", "EMIG": return "airplane"
        case "NATU": return "flag.fill"
        case "DIV": return "arrow.left.arrow.right"
        default: return "calendar"
        }
    }
}

// MARK: - Place

struct GedcomPlace: Codable, FetchableRecord, PersistableRecord, Identifiable, Hashable {
    static let databaseTableName = "place"

    var id: String
    var name: String  // full place string
    var normalized: String
    var latitude: Double?
    var longitude: Double?
    var eventCount: Int  // how many events reference this place
}

// MARK: - Source

struct GedcomSource: Codable, FetchableRecord, PersistableRecord, Identifiable, Hashable {
    static let databaseTableName = "source"

    var id: String
    var xref: String
    var title: String
    var author: String
    var publisher: String
    var repository: String
}
