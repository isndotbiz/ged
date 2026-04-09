import Foundation

@MainActor
struct GedcomExporter {
    var filterLiving: Bool = false

    func export() -> String {
        let db = DatabaseService.shared
        var lines: [String] = []

        // HEAD
        lines.append("0 HEAD")
        lines.append("1 SOUR GedFixApp")
        lines.append("2 VERS 1.0")
        lines.append("2 NAME GedFixApp")
        lines.append("1 GEDC")
        lines.append("2 VERS 5.5.1")
        lines.append("2 FORM LINEAGE-LINKED")
        lines.append("1 CHAR UTF-8")

        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "dd MMM yyyy"
        dateFormatter.locale = Locale(identifier: "en_US_POSIX")
        lines.append("1 DATE \(dateFormatter.string(from: Date()).uppercased())")

        // INDI records
        let persons = db.fetchAllPersons()
        for person in persons {
            let isRestricted = filterLiving && person.isLiving
            lines.append("0 \(person.xref) INDI")

            if isRestricted {
                lines.append("1 NAME Living /\(person.surname)/")
            } else {
                var nameStr = "\(person.givenName) /\(person.surname)/"
                if !person.suffix.isEmpty {
                    nameStr += " \(person.suffix)"
                }
                lines.append("1 NAME \(nameStr)")
                if !person.givenName.isEmpty {
                    lines.append("2 GIVN \(person.givenName)")
                }
                if !person.surname.isEmpty {
                    lines.append("2 SURN \(person.surname)")
                }
                if !person.suffix.isEmpty {
                    lines.append("2 NSFX \(person.suffix)")
                }
            }

            lines.append("1 SEX \(person.sex)")

            // Events for this person
            if !isRestricted {
                let events = db.fetchEvents(forXref: person.xref)
                for event in events {
                    lines.append("1 \(event.eventType)")
                    if !event.dateValue.isEmpty {
                        lines.append("2 DATE \(event.dateValue)")
                    }
                    if !event.place.isEmpty {
                        lines.append("2 PLAC \(event.place)")
                    }
                    if !event.description.isEmpty {
                        lines.append("2 NOTE \(event.description)")
                    }
                }
            }

            // Family links as spouse
            let spouseFamilies = db.fetchFamiliesAsSpouse(forXref: person.xref)
            for fam in spouseFamilies {
                lines.append("1 FAMS \(fam.xref)")
            }

            // Family links as child
            let childFamilies = db.fetchFamiliesAsChild(forXref: person.xref)
            for fam in childFamilies {
                lines.append("1 FAMC \(fam.xref)")
            }
        }

        // FAM records
        let families = db.fetchAllFamilies()
        for family in families {
            lines.append("0 \(family.xref) FAM")
            if !family.partner1Xref.isEmpty {
                lines.append("1 HUSB \(family.partner1Xref)")
            }
            if !family.partner2Xref.isEmpty {
                lines.append("1 WIFE \(family.partner2Xref)")
            }

            // Children
            let childLinks = db.fetchChildLinks(forFamily: family.xref)
            for link in childLinks {
                lines.append("1 CHIL \(link.childXref)")
            }

            // Family events
            let events = db.fetchEvents(forXref: family.xref)
            for event in events {
                lines.append("1 \(event.eventType)")
                if !event.dateValue.isEmpty {
                    lines.append("2 DATE \(event.dateValue)")
                }
                if !event.place.isEmpty {
                    lines.append("2 PLAC \(event.place)")
                }
                if !event.description.isEmpty {
                    lines.append("2 NOTE \(event.description)")
                }
            }
        }

        // SOUR records
        let sources = db.fetchAllSources()
        for source in sources {
            lines.append("0 \(source.xref) SOUR")
            if !source.title.isEmpty {
                lines.append("1 TITL \(source.title)")
            }
            if !source.author.isEmpty {
                lines.append("1 AUTH \(source.author)")
            }
            if !source.publisher.isEmpty {
                lines.append("1 PUBL \(source.publisher)")
            }
            if !source.repository.isEmpty {
                lines.append("1 REPO")
                lines.append("2 NAME \(source.repository)")
            }
        }

        // TRLR
        lines.append("0 TRLR")

        return lines.joined(separator: "\n") + "\n"
    }
}
